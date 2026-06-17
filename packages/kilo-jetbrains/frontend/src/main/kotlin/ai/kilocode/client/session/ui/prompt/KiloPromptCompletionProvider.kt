package ai.kilocode.client.session.ui.prompt

import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.app.Workspace
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.rpc.dto.CommandDto
import ai.kilocode.rpc.dto.FileSearchResultDto
import ai.kilocode.rpc.dto.WorkspaceFileDto
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.textCompletion.TextCompletionProvider
import java.util.Collections

class KiloPromptCompletionProvider(
    private val workspace: Workspace,
    private val service: KiloWorkspaceService,
    private val actions: List<SlashAction>,
) : TextCompletionProvider, DumbAware {
    private val paths = Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var cached: Pair<String, FileSearchResultDto>? = null

    data class SlashAction(
        val name: String,
        val description: String,
        val hints: List<String> = emptyList(),
        val action: () -> Unit,
    )

    data class Highlight(val start: Int, val end: Int, val kind: HighlightKind)

    enum class HighlightKind { MENTION, COMMAND }

    fun mentionPaths(): Set<String> = paths.toSet()

    fun clearMentions() {
        paths.clear()
        cached = null
    }

    fun clientNames(): Set<String> = actions.mapTo(mutableSetOf()) { it.name }

    fun serverCommand(text: String): Pair<String, String>? {
        val raw = text.trimStart()
        if (!raw.startsWith('/')) return null
        val name = raw.drop(1).takeWhile { !it.isWhitespace() }
        if (name.isBlank()) return null
        if (name in clientNames()) return null
        if (workspace.state.value.commands.none { it.name == name }) return null
        return name to raw.drop(name.length + 1).trimStart()
    }

    fun highlights(text: String): List<Highlight> = buildList {
        val command = text.takeIf { it.startsWith('/') }
            ?.drop(1)
            ?.takeWhile { !it.isWhitespace() }
            ?.takeIf { it.isNotBlank() }
        val commands = workspace.state.value.commands.mapTo(mutableSetOf()) { it.name }
        if (command != null && (command in clientNames() || command in commands)) {
            add(Highlight(0, command.length + 1, HighlightKind.COMMAND))
        }

        val ranges = mutableListOf<IntRange>()
        val values = (mentionPaths() + setOf("git-changes"))
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length }
        values.forEach { value ->
            val raw = "@$value"
            var idx = text.indexOf(raw)
            while (idx >= 0) {
                val end = idx + raw.length
                val valid = end == text.length || text[end].isWhitespace()
                val range = idx until end
                if (valid && ranges.none { it.first < end && idx < it.last + 1 }) {
                    ranges += range
                    add(Highlight(idx, end, HighlightKind.MENTION))
                }
                idx = text.indexOf(raw, idx + 1)
            }
        }
    }

    override fun getAdvertisement(): String? = null

    override fun getPrefix(text: String, offset: Int): String? = token(text, offset)?.prefix

    override fun applyPrefixMatcher(result: CompletionResultSet, prefix: String): CompletionResultSet =
        result.withPrefixMatcher(PlainPrefixMatcher(prefix)).caseInsensitive()

    override fun acceptChar(c: Char): CharFilter.Result = when {
        c.isWhitespace() -> CharFilter.Result.HIDE_LOOKUP
        else -> CharFilter.Result.ADD_TO_PREFIX
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, prefix: String, result: CompletionResultSet) {
        when (token(parameters.originalFile.text, parameters.offset)?.kind) {
            Kind.SLASH -> slash(prefix, result)
            Kind.MENTION -> mention(prefix, result)
            null -> Unit
        }
        result.stopHere()
    }

    private fun slash(prefix: String, result: CompletionResultSet) {
        val out = applyPrefixMatcher(result, prefix)
        val names = clientNames()
        actions.forEach { action -> out.addElement(client(action)) }
        workspace.state.value.commands
            .filter { it.name !in names }
            .forEach { command -> out.addElement(server(command)) }
    }

    private fun mention(prefix: String, result: CompletionResultSet) {
        result.restartCompletionOnAnyPrefixChange()
        val out = result.withPrefixMatcher(PlainPrefixMatcher.ALWAYS_TRUE)
        val search = search(prefix)
        if ("git-changes".startsWith(prefix, ignoreCase = true) && search.git) {
            out.addElement(prioritize(special("git-changes", KiloBundle.message("prompt.mention.gitChanges"))))
        }
        if (search.indexing) {
            val msg = KiloBundle.message("prompt.mention.indexing")
            result.addLookupAdvertisement(msg)
            out.addElement(LookupElementBuilder.create(msg)
                .withPresentableText(msg)
                .withIcon(AllIcons.General.Information)
                .withInsertHandler { _, _ -> })
            return
        }
        search.files.forEach { file -> out.addElement(file(file)) }
    }

    private fun search(prefix: String): FileSearchResultDto {
        cached?.takeIf { it.first == prefix }?.let { return it.second }
        val result = runBlockingCancellable { service.searchFiles(workspace.directory, prefix, 50) }
        cached = prefix to result
        return result
    }

    private fun client(action: SlashAction): LookupElement = LookupElementBuilder.create(action.name)
        .withPresentableText("/${action.name}")
        .withTailText("  ${action.description}", true)
        .withLookupStrings(action.hints)
        .withIcon(AllIcons.Actions.Execute)
        .withInsertHandler { ctx, _ ->
            ctx.document.setText("")
            ApplicationManager.getApplication().invokeLater { action.action() }
        }

    private fun server(command: CommandDto): LookupElement = LookupElementBuilder.create(command.name)
        .withPresentableText("/${command.name}")
        .withTailText(command.description?.let { "  $it" } ?: "", true)
        .withTypeText(command.source)
        .withLookupStrings(command.hints)
        .withIcon(AllIcons.Nodes.Function)
        .withInsertHandler { ctx, _ -> replace(ctx, "/${command.name} ", false) }

    private fun special(name: String, text: String): LookupElement = LookupElementBuilder.create(name)
        .withPresentableText("@$name")
        .withTailText("  $text", true)
        .withIcon(AllIcons.Nodes.Tag)
        .withInsertHandler { ctx, _ -> replace(ctx, "@$name ", false) }

    private fun prioritize(element: LookupElement): LookupElement =
        PrioritizedLookupElement.withGrouping(PrioritizedLookupElement.withPriority(element, 100.0), 100)

    private fun file(file: WorkspaceFileDto): LookupElement = LookupElementBuilder.create(file.path)
        .withPresentableText("@${file.path}")
        .withTailText(parent(file.path), true)
        .withIcon(icon(file))
        .withLookupString(file.name)
        .withInsertHandler { ctx, _ -> replace(ctx, "@${file.path} ", true, file.path) }

    private fun icon(file: WorkspaceFileDto) = when {
        file.directory -> AllIcons.Nodes.Folder
        else -> FileTypeManager.getInstance().getFileTypeByFileName(file.name).icon ?: AllIcons.FileTypes.Text
    }

    private fun replace(ctx: InsertionContext, value: String, trim: Boolean, path: String? = null) {
        val text = ctx.document.text
        val offset = ctx.startOffset.coerceAtMost(text.length)
        val start = (offset - 1 downTo 0).firstOrNull { text[it].isWhitespace() }?.plus(1) ?: 0
        val end = ctx.tailOffset.coerceAtMost(text.length)
        val next = text.getOrNull(end)
        val insert = if (trim && next?.isWhitespace() == true) value.trimEnd() else value
        ctx.document.replaceString(start, end, insert)
        ctx.editor.caretModel.moveToOffset(start + insert.length)
        path?.let(paths::add)
    }

    private fun parent(path: String): String {
        val idx = path.lastIndexOf('/')
        if (idx <= 0) return ""
        return "  ${path.substring(0, idx)}"
    }

    private fun token(text: String, offset: Int): Token? {
        val head = text.take(offset.coerceIn(0, text.length))
        val start = (head.length - 1 downTo 0).firstOrNull { head[it].isWhitespace() }?.plus(1) ?: 0
        val raw = head.substring(start)
        if (raw.startsWith("/") && head.take(start).isBlank() && raw.indexOf(' ') < 0) return Token(Kind.SLASH, raw.drop(1))
        if (raw.startsWith("@") && raw.indexOf(' ') < 0) return Token(Kind.MENTION, raw.drop(1))
        return null
    }

    private data class Token(val kind: Kind, val prefix: String)

    private enum class Kind { SLASH, MENTION }
}
