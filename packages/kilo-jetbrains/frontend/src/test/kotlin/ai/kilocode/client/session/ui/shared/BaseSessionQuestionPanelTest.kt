package ai.kilocode.client.session.ui.shared

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextArea
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class BaseSessionQuestionPanelTest : BasePlatformTestCase() {

    // ------ initial state ------

    fun `test headerText and descriptionText are in the component tree by default`() {
        val panel = BaseSessionQuestionPanel()

        assertNotNull("headerText should be present", find(panel, panel.headerText))
        assertNotNull("descriptionText should be present", find(panel, panel.descriptionText))
    }

    fun `test header and description have correct initial text`() {
        val panel = BaseSessionQuestionPanel()

        assertEquals("", panel.headerText.text)
        assertEquals("", panel.descriptionText.text)
    }

    // ------ setTopPanel ------

    fun `test setTopPanel adds component before header`() {
        val panel = BaseSessionQuestionPanel()
        val top = JLabel("top")
        panel.setTopPanel(top)

        val col = findCol(panel)!!
        val comps = col.components.toList()
        val topIdx = comps.indexOf(top)
        val headerIdx = comps.indexOf(panel.headerText)
        assertTrue("top should appear before headerText", topIdx < headerIdx)
    }

    fun `test setTopPanel null removes top component`() {
        val panel = BaseSessionQuestionPanel()
        val top = JLabel("top")
        panel.setTopPanel(top)
        panel.setTopPanel(null)

        assertNull("top should be removed after setTopPanel(null)", find(panel, top))
        assertNotNull("headerText should still be present", find(panel, panel.headerText))
    }

    fun `test setTopPanel replaces previous top without duplicates`() {
        val panel = BaseSessionQuestionPanel()
        val first = JLabel("first")
        val second = JLabel("second")
        panel.setTopPanel(first)
        panel.setTopPanel(second)

        assertNull("first top should be gone after replacement", find(panel, first))
        assertNotNull("second top should be present", find(panel, second))
    }

    // ------ setBody ------

    fun `test setBody adds component after descriptionText`() {
        val panel = BaseSessionQuestionPanel()
        val body = JLabel("body")
        panel.setBody(body)

        val col = findCol(panel)!!
        val comps = col.components.toList()
        val descIdx = comps.indexOf(panel.descriptionText)
        val bodyIdx = comps.indexOf(body)
        assertTrue("body should appear after descriptionText", descIdx < bodyIdx)
    }

    fun `test setBody null removes body`() {
        val panel = BaseSessionQuestionPanel()
        val body = JLabel("body")
        panel.setBody(body)
        panel.setBody(null)

        assertNull("body should be removed after setBody(null)", find(panel, body))
        assertNotNull("headerText should still be present", find(panel, panel.headerText))
    }

    fun `test setBody replaces previous body without duplicates`() {
        val panel = BaseSessionQuestionPanel()
        val first = JLabel("first body")
        val second = JLabel("second body")
        panel.setBody(first)
        panel.setBody(second)

        assertNull("first body should be gone", find(panel, first))
        assertNotNull("second body should be present", find(panel, second))
    }

    // ------ setFooter ------

    fun `test setFooter adds component after body`() {
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

    fun `test setFooter null removes footer`() {
        val panel = BaseSessionQuestionPanel()
        val footer = JLabel("footer")
        panel.setFooter(footer)
        panel.setFooter(null)

        assertNull("footer should be removed after setFooter(null)", find(panel, footer))
        assertNotNull("headerText should still be present", find(panel, panel.headerText))
    }

    fun `test setFooter replaces existing footer without duplicates`() {
        val panel = BaseSessionQuestionPanel()
        val first = JLabel("first footer")
        val second = JLabel("second footer")
        panel.setFooter(first)
        panel.setFooter(second)

        assertNull("first footer should be gone", find(panel, first))
        assertNotNull("second footer should be present", find(panel, second))
    }

    // ------ ordering with all slots ------

    fun `test all slots appear in correct order top-header-desc-body-footer`() {
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
        val headerIdx = comps.indexOf(panel.headerText)
        val descIdx = comps.indexOf(panel.descriptionText)
        val bodyIdx = comps.indexOf(body)
        val footerIdx = comps.indexOf(footer)
        assertTrue("top < header", topIdx < headerIdx)
        assertTrue("header < desc", headerIdx < descIdx)
        assertTrue("desc < body", descIdx < bodyIdx)
        assertTrue("body < footer", bodyIdx < footerIdx)
    }

    fun `test header and description survive multiple setBody calls`() {
        val panel = BaseSessionQuestionPanel()
        repeat(3) { i -> panel.setBody(JLabel("body $i")) }

        assertNotNull(find(panel, panel.headerText))
        assertNotNull(find(panel, panel.descriptionText))
    }

    // ------ column child count sanity ------

    fun `test col has exactly two children with no optional slots`() {
        val panel = BaseSessionQuestionPanel()
        val col = findCol(panel)!!
        assertEquals("headerText + descriptionText only", 2, col.componentCount)
    }

    fun `test col child count grows by one for each optional slot added`() {
        val panel = BaseSessionQuestionPanel()
        panel.setTopPanel(JLabel("top"))
        assertEquals(3, findCol(panel)!!.componentCount)
        panel.setBody(JLabel("body"))
        assertEquals(4, findCol(panel)!!.componentCount)
        panel.setFooter(JLabel("footer"))
        assertEquals(5, findCol(panel)!!.componentCount)
    }

    fun `test col shrinks back after removing optional slots`() {
        val panel = BaseSessionQuestionPanel()
        panel.setTopPanel(JLabel("top"))
        panel.setBody(JLabel("body"))
        panel.setFooter(JLabel("footer"))

        panel.setTopPanel(null)
        panel.setBody(null)
        panel.setFooter(null)

        assertEquals(2, findCol(panel)!!.componentCount)
    }

    // ------ helpers ------

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
}
