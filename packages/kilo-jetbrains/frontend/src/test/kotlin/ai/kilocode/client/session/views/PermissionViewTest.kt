package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Permission
import ai.kilocode.client.session.model.PermissionFileDiff
import ai.kilocode.client.session.model.PermissionMeta
import ai.kilocode.client.session.model.PermissionRequestState
import ai.kilocode.client.session.views.base.BaseQuestionView
import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.session.ui.style.SessionUiStyle
import ai.kilocode.rpc.dto.PermissionReplyDto
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.ScrollPaneConstants

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

    fun `test bash permission shows only header and code block content`() {
        view.show(
            Permission(
                id = "perm4b",
                sessionId = "ses",
                name = "bash",
                patterns = emptyList(),
                always = emptyList(),
                meta = PermissionMeta(command = "git status --short"),
                message = "Run this command?",
            )
        )

        val text = allText(view)
        assertTrue("Expected permission header, got: $text", text.contains("Permission required"))
        assertTrue("Expected command in text, got: $text", text.contains("git status --short"))
        assertFalse("Should not show command label, got: $text", text.contains("Command"))
        assertFalse("Should not show permission message, got: $text", text.contains("Run this command?"))
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
        assertTrue("Expected path in text, got: $text", text.contains("src/"))
        assertTrue("Expected path in text, got: $text", text.contains("App"))
        assertTrue("Expected path in text, got: $text", text.contains("kt"))
    }

    fun `test non-bash patterns render as fenced code block via MdView`() {
        view.show(
            Permission(
                id = "perm_pattern_md",
                sessionId = "ses",
                name = "glob",
                patterns = listOf("packages/kilo-jetbrains/**/*.kt"),
                always = emptyList(),
                meta = PermissionMeta(),
            )
        )

        val panes = findAll<JBHtmlPane>(view)
        assertTrue("Expected at least one JBHtmlPane for pattern details", panes.isNotEmpty())
        val html = panes.first().text
        assertTrue("Expected <pre> tag in rendered HTML, got: $html", html.contains("<pre"))
        assertTrue("Expected tool label in rendered HTML, got: $html", html.contains("Glob Search"))
        assertTrue("Expected pattern in rendered HTML, got: $html", html.contains("packages/"))
    }

    fun `test diff preview is not rendered`() {
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
        assertFalse("Should not render diff patch, got: $text", text.contains("@@"))
        assertFalse("Should not render diff additions, got: $text", text.contains("+1"))
        assertFalse("Should not render diff deletions, got: $text", text.contains("-2"))
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

    // ------ new: shared card shell ------

    fun `test view contains BaseSessionQuestionPanel after show`() {
        view.show(permission())

        val panels = findAll<BaseQuestionView>(view)
        assertTrue("Expected a BaseSessionQuestionPanel after show", panels.isNotEmpty())
    }

    fun `test permission icon is rendered in header`() {
        view.show(permission())

        val labels = findAll<JBLabel>(view)
        assertTrue(
            "Expected permission warning icon in header",
            labels.any { it.icon == AllIcons.General.Warning },
        )
    }

    // ------ new: shared button types ------

    fun `test run button uses default style key`() {
        view.show(permission())

        val btn = view.runButtonForTest()
        assertEquals(true, btn.getClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY))
    }

    fun `test deny button does not have default style key`() {
        view.show(permission())

        val btn = view.denyButtonForTest()
        val key = btn.getClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY)
        assertTrue("Deny should not be primary", key == null || key == false)
    }

    fun `test session question buttons use question surface background`() {
        view.show(permission())

        assertEquals(SessionUiStyle.View.surface(), view.runButtonForTest().background)
        assertEquals(SessionUiStyle.View.surface(), view.denyButtonForTest().background)
    }

    // ------ new: command rendered via MdView ------

    fun `test bash command renders as fenced code block via MdView`() {
        view.show(
            Permission(
                id = "perm_md",
                sessionId = "ses",
                name = "bash",
                patterns = emptyList(),
                always = emptyList(),
                meta = PermissionMeta(command = "git status --short"),
            )
        )

        // Find the JBHtmlPane that MdView uses — it should contain a <pre> block
        val panes = findAll<JBHtmlPane>(view)
        assertTrue("Expected at least one JBHtmlPane for the command MdView", panes.isNotEmpty())
        val html = panes.first().text
        assertTrue("Expected <pre> tag in rendered HTML, got: $html", html.contains("<pre"))
        assertTrue("Expected command text in rendered HTML, got: $html", html.contains("git status --short"))
    }

    // ------ new: command scroll pane height cap ------

    fun `test long command scroll pane uses vertical scrolling and caps height`() {
        val longCmd = (1..20).joinToString("\n") { "echo line $it" }
        view.show(
            Permission(
                id = "perm_long",
                sessionId = "ses",
                name = "bash",
                patterns = emptyList(),
                always = emptyList(),
                meta = PermissionMeta(command = longCmd),
            )
        )

        val scrolls = findAll<JBScrollPane>(view)
        val cmdScroll = scrolls.firstOrNull { it.verticalScrollBarPolicy == ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED }
        assertNotNull("Expected a JBScrollPane with VERTICAL_SCROLLBAR_AS_NEEDED for the command", cmdScroll)

        val maxH = cmdScroll!!.maximumSize.height
        assertTrue("Maximum height should be capped (> 0)", maxH > 0)
        assertTrue("Maximum height should be finite (< Int.MAX_VALUE)", maxH < Int.MAX_VALUE)
    }

    fun `test code block scroll pane uses code background`() {
        view.show(
            Permission(
                id = "perm_bg",
                sessionId = "ses",
                name = "bash",
                patterns = emptyList(),
                always = emptyList(),
                meta = PermissionMeta(command = "pwd"),
            )
        )

        val scroll = findAll<JBScrollPane>(view).first { it.verticalScrollBarPolicy == ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED }
        assertEquals(SessionUiStyle.View.headerHover(), scroll.background)
        assertEquals(SessionUiStyle.View.headerHover(), scroll.viewport.background)
    }

    // ------ fonts: header UI family, command code block editor family ------

    fun `test permission header uses headerFont not editor font family`() {
        view.show(
            Permission(
                id = "perm_font",
                sessionId = "ses",
                name = "bash",
                patterns = emptyList(),
                always = emptyList(),
                meta = PermissionMeta(command = "ls"),
            )
        )
        val style = SessionEditorStyle.create(family = "Courier New", size = 18)
        view.applyStyle(style)

        val header = view.headerFontForTest()
        assertFalse("Permission header should not use editor font family", header.name == "Courier New")
        assertTrue("Permission header should be bold", header.isBold)
        assertEquals("Permission header should equal headerFont", style.headerFont, header)
    }

    fun `test command code block retains editor font family`() {
        view.show(
            Permission(
                id = "perm_codefont",
                sessionId = "ses",
                name = "bash",
                patterns = emptyList(),
                always = emptyList(),
                meta = PermissionMeta(command = "git log"),
            )
        )
        val style = SessionEditorStyle.create(family = "Courier New", size = 18)
        view.applyStyle(style)

        val md = view.firstCmdViewForTest()
        assertNotNull("Should have at least one command MdView", md)
        assertEquals("Code block codeFont should use editor family", "Courier New", md!!.codeFont)
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

    private inline fun <reified T> findAll(root: Container): List<T> = findAllCls(root, T::class.java)

    private fun <T> findAllCls(root: Container, cls: Class<T>): List<T> {
        val result = mutableListOf<T>()
        if (cls.isInstance(root)) result.add(cls.cast(root))
        for (child in root.components) {
            if (cls.isInstance(child)) result.add(cls.cast(child))
            if (child is Container && child !is AbstractButton) {
                result.addAll(findAllCls(child, cls))
            }
        }
        return result
    }
}
