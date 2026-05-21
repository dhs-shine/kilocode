package ai.kilocode.client.session.ui.shared

import ai.kilocode.client.session.ui.style.SessionEditorStyle
import ai.kilocode.client.ui.UiStyle
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class BaseSessionQuestionPanelTest : BasePlatformTestCase() {

    // ------ initial state ------

    fun `test headerText and descriptionText are in the component tree by default`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            assertNotNull("headerText should be present", find(panel, panel.headerText))
            assertNotNull("descriptionText should be present", find(panel, panel.descriptionText))
        }
    }

    fun `test header and description have correct initial text`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            assertEquals("", panel.headerText.text)
            assertEquals("", panel.descriptionText.text)
        }
    }

    // ------ setTopPanel ------

    fun `test setTopPanel adds component before header`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val top = JLabel("top")
            panel.setTopPanel(top)

            val col = findCol(panel)!!
            val comps = col.components.toList()
            val topIdx = comps.indexOf(top)
            val headerIdx = comps.indexOf(panel.headerText.parent)
            assertTrue("top should appear before headerText", topIdx < headerIdx)
        }
    }

    fun `test setTopPanel null removes top component`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val top = JLabel("top")
            panel.setTopPanel(top)
            panel.setTopPanel(null)

            assertNull("top should be removed after setTopPanel(null)", find(panel, top))
            assertNotNull("headerText should still be present", find(panel, panel.headerText))
        }
    }

    fun `test setTopPanel replaces previous top without duplicates`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val first = JLabel("first")
            val second = JLabel("second")
            panel.setTopPanel(first)
            panel.setTopPanel(second)

            assertNull("first top should be gone after replacement", find(panel, first))
            assertNotNull("second top should be present", find(panel, second))
        }
    }

    // ------ setBody ------

    fun `test setBody adds component after descriptionText`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val body = JLabel("body")
            panel.setBody(body)

            val col = findCol(panel)!!
            val comps = col.components.toList()
            val descIdx = comps.indexOf(panel.descriptionText)
            val bodyIdx = comps.indexOf(body)
            assertTrue("body should appear after descriptionText", descIdx < bodyIdx)
        }
    }

    fun `test setBody null removes body`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val body = JLabel("body")
            panel.setBody(body)
            panel.setBody(null)

            assertNull("body should be removed after setBody(null)", find(panel, body))
            assertNotNull("headerText should still be present", find(panel, panel.headerText))
        }
    }

    fun `test setBody replaces previous body without duplicates`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val first = JLabel("first body")
            val second = JLabel("second body")
            panel.setBody(first)
            panel.setBody(second)

            assertNull("first body should be gone", find(panel, first))
            assertNotNull("second body should be present", find(panel, second))
        }
    }

    // ------ setFooter ------

    fun `test setFooter adds component after body`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val body = JLabel("body")
            val footer = JLabel("footer")
            panel.setBody(body)
            panel.setFooter(footer)

            val col = findCol(panel)!!
            val comps = col.components.toList()
            val bodyIdx = comps.indexOf(body)
            val footerIdx = comps.indexOf(footer)
            assertTrue("footer should appear after body", bodyIdx < footerIdx)
        }
    }

    fun `test setFooter null removes footer`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val footer = JLabel("footer")
            panel.setFooter(footer)
            panel.setFooter(null)

            assertNull("footer should be removed after setFooter(null)", find(panel, footer))
            assertNotNull("headerText should still be present", find(panel, panel.headerText))
        }
    }

    fun `test setFooter replaces existing footer without duplicates`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val first = JLabel("first footer")
            val second = JLabel("second footer")
            panel.setFooter(first)
            panel.setFooter(second)

            assertNull("first footer should be gone", find(panel, first))
            assertNotNull("second footer should be present", find(panel, second))
        }
    }

    // ------ ordering with all slots ------

    fun `test all slots appear in correct order top-header-desc-body-footer`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val top = JLabel("top")
            val body = JLabel("body")
            val footer = JLabel("footer")
            panel.setTopPanel(top)
            panel.setBody(body)
            panel.setFooter(footer)

            val col = findCol(panel)!!
            val comps = col.components.toList()
            val topIdx = comps.indexOf(top)
            val headerIdx = comps.indexOf(panel.headerText.parent)
            val descIdx = comps.indexOf(panel.descriptionText)
            val bodyIdx = comps.indexOf(body)
            val footerIdx = comps.indexOf(footer)
            assertTrue("top < header", topIdx < headerIdx)
            assertTrue("header < desc", headerIdx < descIdx)
            assertTrue("desc < body", descIdx < bodyIdx)
            assertTrue("body < footer", bodyIdx < footerIdx)
        }
    }

    fun `test header and description survive multiple setBody calls`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            repeat(3) { i -> panel.setBody(JLabel("body $i")) }
            assertNotNull(find(panel, panel.headerText))
            assertNotNull(find(panel, panel.descriptionText))
        }
    }

    // ------ column child count sanity ------

    fun `test col has exactly two children with no optional slots`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val col = findCol(panel)!!
            assertEquals("header row + descriptionText only", 2, col.componentCount)
        }
    }

    // ------ header left icon ------

    fun `test setHeaderIcon adds icon to the left side of header row`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            panel.setHeaderIcon(AllIcons.General.Warning, "warning")

            val header = panel.headerText.parent as JPanel
            val layout = header.layout as BorderLayout
            val labels = findAll<JBLabel>(header).filter { it.icon != null }
            assertEquals("Expected one header icon", 1, labels.size)
            assertSame(AllIcons.General.Warning, labels[0].icon)
            assertEquals("warning", labels[0].toolTipText)
            assertEquals(BorderLayout.WEST, layout.getConstraints(labels[0]))
            assertEquals(BorderLayout.CENTER, layout.getConstraints(panel.headerText))
        }
    }

    fun `test setHeaderIcon null hides header icon without removing header row`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            panel.setHeaderIcon(AllIcons.General.Warning)
            panel.setHeaderIcon(null)

            val header = panel.headerText.parent as Container
            val labels = findAll<JBLabel>(header).filter { it.icon != null && it.isVisible }
            assertTrue("Header icon should be hidden after setHeaderIcon(null)", labels.isEmpty())
            assertSame(header, panel.headerText.parent)
        }
    }

    fun `test col child count includes spacing before body and footer slots`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            panel.setTopPanel(JLabel("top"))
            assertEquals(3, findCol(panel)!!.componentCount)
            panel.setBody(JLabel("body"))
            assertEquals(5, findCol(panel)!!.componentCount)
            panel.setFooter(JLabel("footer"))
            assertEquals(7, findCol(panel)!!.componentCount)
        }
    }

    fun `test body and footer spacing use matching standard insets`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val body = JLabel("body")
            val footer = JLabel("footer")
            panel.setBody(body)
            panel.setFooter(footer)

            val col = findCol(panel)!!
            val comps = col.components.toList()
            val bodyGap = comps[comps.indexOf(body) - 1]
            val footerGap = comps[comps.indexOf(footer) - 1]

            assertEquals(bodyGap.preferredSize.height, footerGap.preferredSize.height)
        }
    }

    fun `test col shrinks back after removing optional slots`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            panel.setTopPanel(JLabel("top"))
            panel.setBody(JLabel("body"))
            panel.setFooter(JLabel("footer"))

            panel.setTopPanel(null)
            panel.setBody(null)
            panel.setFooter(null)

            assertEquals(2, findCol(panel)!!.componentCount)
        }
    }

    // ------ applyStyle: UI fonts ------

    fun `test applyStyle applies enlarged boldUiFont to header and enlarged uiFont to description`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val style = SessionEditorStyle.create(family = "Courier New", size = 20)
            panel.applyStyle(style)

            assertEquals("headerText should keep boldUiFont family", style.boldUiFont.name, panel.headerText.font.name)
            assertEquals("descriptionText should keep uiFont family", style.uiFont.name, panel.descriptionText.font.name)
            assertEquals("headerText should use next font size", style.boldUiFont.size + 1, panel.headerText.font.size)
            assertEquals("descriptionText should use next font size", style.uiFont.size + 1, panel.descriptionText.font.size)
        }
    }

    fun `test description uses next standard top padding`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val ins = panel.descriptionText.border.getBorderInsets(panel.descriptionText)

            assertEquals("description top padding should use next standard gap", UiStyle.Gap.sm(), ins.top)
        }
    }

    fun `test applyStyle does not apply editor font family to header or description`() {
        edt {
            val panel = BaseSessionQuestionPanel()
            val style = SessionEditorStyle.create(family = "Courier New", size = 20)
            panel.applyStyle(style)

            assertFalse(
                "headerText should not use editor font family",
                panel.headerText.font.name == "Courier New",
            )
            assertFalse(
                "descriptionText should not use editor font family",
                panel.descriptionText.font.name == "Courier New",
            )
        }
    }

    // ------ helpers ------

    private fun <T> edt(block: () -> T): T {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun findCol(panel: BaseSessionQuestionPanel): JPanel? {
        for (child in panel.components) {
            if (child is JPanel) return child
        }
        return null
    }

    private fun find(root: Container, target: JComponent): JComponent? {
        if (root === target) return target
        for (child in root.components) {
            if (child === target) return target
            if (child is Container) {
                val found = find(child, target)
                if (found != null) return found
            }
        }
        return null
    }

    private inline fun <reified T> findAll(root: Container): List<T> = findAllCls(root, T::class.java)

    private fun <T> findAllCls(root: Container, cls: Class<T>): List<T> {
        val result = mutableListOf<T>()
        if (cls.isInstance(root)) result.add(cls.cast(root))
        for (child in root.components) {
            if (child is Container) result.addAll(findAllCls(child, cls))
        }
        return result
    }
}
