package ai.kilocode.client.session.ui.prompt

import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.testing.FakeWorkspaceRpcApi
import ai.kilocode.rpc.dto.CommandDto
import ai.kilocode.rpc.dto.FileSearchResultDto
import ai.kilocode.rpc.dto.KiloWorkspaceStateDto
import ai.kilocode.rpc.dto.KiloWorkspaceStatusDto
import ai.kilocode.rpc.dto.WorkspaceFileDto
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.textCompletion.TextCompletionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@Suppress("UnstableApiUsage")
class KiloPromptCompletionProviderTest : BasePlatformTestCase() {
    private lateinit var scope: CoroutineScope
    private lateinit var rpc: FakeWorkspaceRpcApi
    private lateinit var provider: KiloPromptCompletionProvider

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        rpc = FakeWorkspaceRpcApi()
        val workspaces = KiloWorkspaceService(scope, rpc)
        provider = KiloPromptCompletionProvider(
            workspace = workspaces.workspace("/test"),
            service = workspaces,
            actions = listOf(KiloPromptCompletionProvider.SlashAction("new", "New") {}),
        )
    }

    override fun tearDown() {
        try {
            scope.cancel()
        } finally {
            super.tearDown()
        }
    }

    fun `test mention completion shows backend fuzzy results without local filtering`() {
        rpc.searchResult = FileSearchResultDto(files = listOf(file("src/foo/Bar.kt")))

        complete("@sfb<caret>")

        assertContainsElements(myFixture.lookupElementStrings.orEmpty(), "src/foo/Bar.kt")
        assertEquals(listOf("sfb"), rpc.searchQueries)
    }

    fun `test mention completion reuses identical prefix result`() {
        rpc.searchResult = FileSearchResultDto(files = listOf(file("src/Main.kt")))

        complete("@main<caret>")
        complete("@main<caret>")

        assertEquals(listOf("main"), rpc.searchQueries)
    }

    fun `test clearing mentions resets cached prefix result`() {
        rpc.searchResult = FileSearchResultDto(files = listOf(file("src/Main.kt")))

        complete("@main<caret>")
        provider.clearMentions()
        complete("@main<caret>")

        assertEquals(listOf("main", "main"), rpc.searchQueries)
    }

    fun `test mention completion includes matching special items`() {
        rpc.searchResult = FileSearchResultDto(git = true)

        complete("@git<caret>")

        assertContainsElements(myFixture.lookupElementStrings.orEmpty(), "git-changes")
        assertEquals(listOf("git"), rpc.searchQueries)
    }

    fun `test blank mention completion includes special and root entries`() {
        rpc.searchResult = FileSearchResultDto(
            files = listOf(file("src", directory = true), file("README.md")),
            git = true,
        )

        complete("@<caret>")

        assertContainsElements(myFixture.lookupElementStrings.orEmpty(), "git-changes", "src", "README.md")
        assertEquals(listOf(""), rpc.searchQueries)
    }

    fun `test highlights known slash command at start`() {
        assertEquals(
            listOf(KiloPromptCompletionProvider.Highlight(0, 4, KiloPromptCompletionProvider.HighlightKind.COMMAND)),
            provider.highlights("/new start fresh"),
        )
    }

    fun `test highlights server slash command at start`() {
        rpc.state.value = KiloWorkspaceStateDto(KiloWorkspaceStatusDto.READY, commands = listOf(CommandDto("deploy")))

        waitFor { provider.highlights("/deploy prod").isNotEmpty() }

        assertEquals(
            listOf(KiloPromptCompletionProvider.Highlight(0, 7, KiloPromptCompletionProvider.HighlightKind.COMMAND)),
            provider.highlights("/deploy prod"),
        )
    }

    fun `test highlights ignore unknown and non-leading slash commands`() {
        assertTrue(provider.highlights("/bogus now").isEmpty())
        assertTrue(provider.highlights("hi /new").isEmpty())
    }

    fun `test highlights special mentions without tracked paths`() {
        assertEquals(
            listOf(
                KiloPromptCompletionProvider.Highlight(4, 16, KiloPromptCompletionProvider.HighlightKind.MENTION),
            ),
            provider.highlights("use @git-changes").sortedBy { it.start },
        )
    }

    fun `test serverCommand routes only known server commands`() {
        rpc.state.value = KiloWorkspaceStateDto(KiloWorkspaceStatusDto.READY, commands = listOf(CommandDto("deploy")))

        waitFor { provider.serverCommand("/deploy x") != null }

        assertEquals("deploy" to "x", provider.serverCommand("/deploy x"))
        assertNull(provider.serverCommand("/new"))
        assertNull(provider.serverCommand("hi /deploy"))
        assertNull(provider.serverCommand("/unknown"))
    }

    fun `test highlights tracked mentions longest first`() {
        addMention("src/a.ts", "@ts")
        addMention("src/a.tsx", "@tsx")

        assertEquals(
            listOf(KiloPromptCompletionProvider.Highlight(4, 14, KiloPromptCompletionProvider.HighlightKind.MENTION)),
            provider.highlights("see @src/a.tsx"),
        )
    }

    fun `test highlights ignore untracked mentions`() {
        assertTrue(provider.highlights("see @unknownPath").isEmpty())
    }

    private fun complete(text: String) {
        val file = myFixture.configureByText("prompt.txt", text)
        TextCompletionUtil.installProvider(file, provider, true)
        myFixture.completeBasic()
    }

    private fun addMention(path: String, query: String) {
        rpc.searchResult = FileSearchResultDto(files = listOf(file(path)))
        complete("$query<caret>")
        myFixture.type('\n')
        assertTrue(provider.mentionPaths().contains(path))
    }

    private fun waitFor(done: () -> Boolean) {
        repeat(50) {
            com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
            if (done()) return
            Thread.sleep(20)
        }
    }

    private fun file(path: String, directory: Boolean = false) = WorkspaceFileDto(
        path = path,
        name = path.substringAfterLast('/'),
        directory = directory,
    )
}
