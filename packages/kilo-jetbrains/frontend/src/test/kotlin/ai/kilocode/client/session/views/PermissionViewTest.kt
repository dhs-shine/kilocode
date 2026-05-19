package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Permission
import ai.kilocode.client.session.model.PermissionFileDiff
import ai.kilocode.client.session.model.PermissionMeta
import ai.kilocode.client.session.model.PermissionRequestState
import ai.kilocode.rpc.dto.PermissionReplyDto
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Container
import javax.swing.AbstractButton

@Suppress("UnstableApiUsage")
class PermissionViewTest : BasePlatformTestCase() {

    private val replies = mutableListOf<Pair<String, PermissionReplyDto>>()
    private lateinit var view: PermissionView

    override fun setUp() {
        super.setUp()
        view = PermissionView(
            reply = { id, dto -> replies.add(id to dto) },
        )
    }

    fun `test run button replies once`() {
        view.show(permission())

        view.runButtonForTest().doClick()

        assertEquals(1, replies.size)
        assertEquals("perm1", replies.single().first)
        assertEquals("once", replies.single().second.reply)
        assertFalse(view.runButtonForTest().isEnabled)
        assertFalse(view.denyButtonForTest().isEnabled)
    }

    fun `test deny button rejects`() {
        view.show(permission())

        view.denyButtonForTest().doClick()

        assertEquals(1, replies.size)
        assertEquals("perm1", replies.single().first)
        assertEquals("reject", replies.single().second.reply)
    }

    fun `test view is visible after show`() {
        view.show(permission())
        assertTrue(view.isVisible)
    }

    fun `test hideView makes invisible`() {
        view.show(permission())
        view.hideView()
        assertFalse(view.isVisible)
    }

    fun `test blank patterns display no-details fallback`() {
        view.show(
            Permission(
                id = "perm2",
                sessionId = "ses",
                name = "edit",
                patterns = emptyList(),
                always = emptyList(),
                meta = PermissionMeta(),
            )
        )

        assertTrue(view.isVisible)
        // Should have text saying edit requires permission
        val text = allText(view)
        assertTrue("Expected tool label in text, got: $text", text.contains("Edit"))
    }

    fun `test star-only patterns use no-details fallback`() {
        view.show(
            Permission(
                id = "perm3",
                sessionId = "ses",
                name = "read",
                patterns = listOf("*"),
                always = emptyList(),
                meta = PermissionMeta(),
            )
        )

        assertTrue(view.isVisible)
        val text = allText(view)
        assertTrue("Expected Read label in text, got: $text", text.contains("Read"))
    }

    fun `test bash permission shows command`() {
        view.show(
            Permission(
                id = "perm4",
                sessionId = "ses",
                name = "bash",
                patterns = emptyList(),
                always = emptyList(),
                meta = PermissionMeta(command = "git status --short"),
            )
        )

        val text = allText(view)
        assertTrue("Expected command in text, got: $text", text.contains("git status --short"))
    }

    fun `test non-bash patterns show tool and path`() {
        view.show(
            Permission(
                id = "perm5",
                sessionId = "ses",
                name = "read",
                patterns = listOf("src/App.kt"),
                always = emptyList(),
                meta = PermissionMeta(),
            )
        )

        val text = allText(view)
        assertTrue("Expected 'Read' in text, got: $text", text.contains("Read"))
        assertTrue("Expected path in text, got: $text", text.contains("src/App.kt"))
    }

    fun `test diff preview renders file and patch`() {
        view.show(
            Permission(
                id = "perm6",
                sessionId = "ses",
                name = "edit",
                patterns = listOf("src/A.kt"),
                always = emptyList(),
                meta = PermissionMeta(
                    fileDiffs = listOf(
                        PermissionFileDiff(
                            file = "src/A.kt",
                            patch = "@@ -1 +1 @@",
                            additions = 1,
                            deletions = 2,
                        )
                    ),
                ),
            )
        )

        val text = allText(view)
        assertTrue("Expected file name in text, got: $text", text.contains("src/A.kt"))
        assertTrue("Expected patch in text, got: $text", text.contains("@@"))
        assertTrue("Expected additions in text, got: $text", text.contains("+1"))
        assertTrue("Expected deletions in text, got: $text", text.contains("-2"))
    }

    fun `test no rule controls rendered`() {
        view.show(
            Permission(
                id = "perm7",
                sessionId = "ses",
                name = "edit",
                patterns = listOf("*.kt"),
                always = listOf("src/**"),
                meta = PermissionMeta(rules = listOf("rule1")),
            )
        )

        val text = allText(view)
        assertFalse("Should not contain 'Manage Auto-Approve Rules'", text.contains("Manage Auto-Approve Rules"))
        // Only Run and Deny buttons — not extra rule toggle buttons
        val btns = buttons(view)
        assertEquals("Expected exactly 2 buttons (Run and Deny)", 2, btns.size)
    }

    fun `test responding state disables buttons`() {
        view.show(
            Permission(
                id = "perm8",
                sessionId = "ses",
                name = "edit",
                patterns = listOf("*.kt"),
                always = emptyList(),
                meta = PermissionMeta(),
                state = PermissionRequestState.RESPONDING,
            )
        )

        assertFalse(view.runButtonForTest().isEnabled)
        assertFalse(view.denyButtonForTest().isEnabled)
    }

    fun `test allow button uses bundle text and replies once`() {
        view.show(permission())

        // run button (previously "Allow") should trigger once reply
        view.runButtonForTest().doClick()

        assertEquals(1, replies.size)
        assertEquals("once", replies.single().second.reply)
    }

    fun `test deny button uses bundle text and rejects`() {
        view.show(permission())

        view.denyButtonForTest().doClick()

        assertEquals(1, replies.size)
        assertEquals("reject", replies.single().second.reply)
    }

    private fun permission() = Permission(
        id = "perm1",
        sessionId = "ses_test",
        name = "edit",
        patterns = listOf("*.kt"),
        always = emptyList(),
        meta = PermissionMeta(),
        message = "Review file changes",
    )

    private fun buttons(root: Container): List<AbstractButton> = root.components.flatMap { comp ->
        val item = if (comp is AbstractButton) listOf(comp) else emptyList()
        if (comp is Container) item + buttons(comp) else item
    }

    private fun allText(root: Container): String = buildString {
        fun collect(c: Container) {
            for (comp in c.components) {
                if (comp is javax.swing.text.JTextComponent) append(comp.text).append(" ")
                if (comp is javax.swing.JLabel) append(comp.text).append(" ")
                if (comp is AbstractButton) append(comp.text).append(" ")
                if (comp is Container) collect(comp)
            }
        }
        collect(root)
    }
}
