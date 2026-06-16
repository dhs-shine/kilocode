package ai.kilocode.client.session.ui.prompt

import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.testing.FakeWorkspaceRpcApi
import ai.kilocode.rpc.dto.FileSearchResultDto
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
            actions = emptyList(),
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
        rpc.searchResult = FileSearchResultDto(git = true, terminal = true)

        complete("@git<caret>")

        assertContainsElements(myFixture.lookupElementStrings.orEmpty(), "git-changes")
        assertEquals(listOf("git"), rpc.searchQueries)
    }

    private fun complete(text: String) {
        val file = myFixture.configureByText("prompt.txt", text)
        TextCompletionUtil.installProvider(file, provider, true)
        myFixture.completeBasic()
    }

    private fun file(path: String) = WorkspaceFileDto(path = path, name = path.substringAfterLast('/'))
}
