package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Question
import ai.kilocode.client.session.model.QuestionItem
import ai.kilocode.client.session.model.QuestionOption
import ai.kilocode.client.ui.HoverIcon
import ai.kilocode.rpc.dto.QuestionReplyDto
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import java.awt.Container
import java.awt.Font
import javax.swing.AbstractButton
import javax.swing.JButton

@Suppress("UnstableApiUsage")
class QuestionViewTest : BasePlatformTestCase() {

    private val replies = mutableListOf<Pair<String, QuestionReplyDto>>()
    private val rejects = mutableListOf<String>()
    private lateinit var view: QuestionView

    override fun setUp() {
        super.setUp()
        view = QuestionView(
            reply = { id, dto -> replies.add(id to dto) },
            reject = { id -> rejects.add(id) },
        )
    }

    // ------ empty question ------

    fun `test empty question hides view and clears stale request id`() {
        view.show(
            Question(
                id = "req_old",
                items = listOf(
                    QuestionItem(
                        question = "Pick one",
                        header = "Header",
                        options = listOf(QuestionOption("Yes", "desc")),
                        multiple = false,
                        custom = true,
                    )
                ),
            )
        )
        assertTrue(view.isVisible)

        view.show(Question(id = "req_new", items = emptyList()))

        assertFalse(view.isVisible)
        assertTrue(replies.isEmpty())
        assertTrue(rejects.isEmpty())
    }

    // ------ dismiss ------

    fun `test dismiss button uses bundle text and rejects question`() {
        view.show(
            Question(
                id = "req_1",
                items = listOf(
                    QuestionItem(
                        question = "Pick one",
                        header = "Header",
                        options = listOf(QuestionOption("Yes", "desc")),
                        multiple = false,
                        custom = true,
                    )
                ),
            )
        )

        button(view, "Dismiss").doClick()

        assertFalse(view.isVisible)
        assertEquals("req_1", rejects.single())
        assertTrue(replies.isEmpty())
    }

    // ------ radio options ------

    fun `test single question renders radio options`() {
        view.show(singleSelectQuestion("req_r"))

        val radios = findAll<JBRadioButton>(view)
        assertEquals(2, radios.size)
        assertEquals("Minimal", radios[0].text)
        assertEquals("Balanced", radios[1].text)
        assertTrue(findAll<JBCheckBox>(view).isEmpty())
    }

    fun `test single question submit sends selected answer`() {
        view.show(singleSelectQuestion("req_2"))

        // Select via radio button
        findAll<JBRadioButton>(view).first { it.text == "Minimal" }.doClick()
        button(view, "Submit").doClick()

        assertFalse(view.isVisible)
        assertEquals(1, replies.size)
        assertEquals("req_2", replies.single().first)
        assertEquals(listOf(listOf("Minimal")), replies.single().second.answers)
    }

    // ------ option label and description ------

    fun `test option row shows bold label and regular description`() {
        view.show(
            Question(
                id = "desc_test",
                items = listOf(
                    QuestionItem(
                        question = "How to proceed?",
                        header = "Approach",
                        options = listOf(
                            QuestionOption("Minimal", "Smallest safe change"),
                        ),
                        multiple = false,
                        custom = false,
                    )
                ),
            )
        )

        // The option label is the radio button text (bold)
        val radio = findAll<JBRadioButton>(view).first { it.text == "Minimal" }
        assertTrue("option label should be bold", radio.font.isBold)

        // The description is a JBLabel with regular weight
        val desc = findAll<JBLabel>(view).firstOrNull { it.text == "Smallest safe change" }
        assertNotNull("description label should be present", desc)
        assertFalse("description should not be bold", desc!!.font.style == Font.BOLD)
    }

    // ------ multi-question navigation ------

    fun `test multi question shows one question at a time and navigates`() {
        view.show(twoItemQuestion("q_nav"))

        // First question shown, second not
        assertLabelsContain(view, "Choose approach")
        assertLabelsDoNotContain(view, "Choose test level")
        assertLabelsContain(view, "1 of 2 questions")

        // Select an answer on first question, then click Next
        findAll<JBRadioButton>(view).first { it.text == "Minimal" }.doClick()
        button(view, "Next").doClick()

        // Second question shown, first not
        assertLabelsContain(view, "Choose test level")
        assertLabelsDoNotContain(view, "Choose approach")
        assertLabelsContain(view, "2 of 2 questions")

        // Select answer on second question, submit
        findAll<JBRadioButton>(view).first { it.text == "Unit" }.doClick()
        button(view, "Submit").doClick()

        assertFalse(view.isVisible)
        assertEquals(1, replies.size)
        assertEquals("q_nav", replies.single().first)
        assertEquals(listOf(listOf("Minimal"), listOf("Unit")), replies.single().second.answers)
    }

    fun `test back preserves previous selection`() {
        view.show(twoItemQuestion("q_back"))

        // Answer first question
        findAll<JBRadioButton>(view).first { it.text == "Minimal" }.doClick()
        button(view, "Next").doClick()

        // Go back via header nav icon
        navButton(view, "Back").doClick()

        // First question visible again, selection preserved
        assertLabelsContain(view, "Choose approach")
        assertLabelsContain(view, "1 of 2 questions")
        val radios = findAll<JBRadioButton>(view)
        assertTrue("Minimal should still be selected", radios.first { it.text == "Minimal" }.isSelected)

        // Change selection to Balanced, go forward, submit
        findAll<JBRadioButton>(view).first { it.text == "Balanced" }.doClick()
        button(view, "Next").doClick()
        findAll<JBRadioButton>(view).first { it.text == "Unit" }.doClick()
        button(view, "Submit").doClick()

        assertEquals(listOf(listOf("Balanced"), listOf("Unit")), replies.single().second.answers)
    }

    fun `test next is disabled until current question is answered`() {
        view.show(twoItemQuestion("q_disabled"))

        val next = button(view, "Next")
        assertFalse("Next should be disabled before selection", next.isEnabled)

        findAll<JBRadioButton>(view).first { it.text == "Minimal" }.doClick()

        // Re-render happened; get fresh reference
        val nextAfter = button(view, "Next")
        assertTrue("Next should be enabled after selection", nextAfter.isEnabled)
    }

    // ------ multi-select checkboxes ------

    fun `test multiple selection item uses checkboxes and toggles options`() {
        view.show(
            Question(
                id = "req_3",
                items = listOf(
                    QuestionItem(
                        question = "Select features",
                        header = "Features",
                        options = listOf(
                            QuestionOption("A", "Feature A"),
                            QuestionOption("B", "Feature B"),
                            QuestionOption("C", "Feature C"),
                        ),
                        multiple = true,
                        custom = false,
                    )
                ),
            )
        )

        val boxes = findAll<JBCheckBox>(view)
        assertEquals(3, boxes.size)
        assertTrue(findAll<JBRadioButton>(view).isEmpty())

        boxes.first { it.text == "A" }.doClick()
        boxes.first { it.text == "B" }.doClick()
        // Toggle B off — need fresh refs after re-render
        findAll<JBCheckBox>(view).first { it.text == "B" }.doClick()
        button(view, "Submit").doClick()

        assertFalse(view.isVisible)
        assertEquals(1, replies.size)
        assertEquals("req_3", replies.single().first)
        assertEquals(listOf(listOf("A")), replies.single().second.answers)
    }

    // ------ helpers ------

    /**
     * Find a [JButton] by button text — covers footer buttons (Dismiss, Next, Submit).
     * For icon-only nav buttons (Back/Forward) that use tooltip, use [navButton].
     */
    private fun button(root: Container, text: String): JButton =
        findAll<JButton>(root).first { it.text == text }

    /** Find a [HoverIcon] nav button by tooltip text (Back / Next nav arrows). */
    private fun navButton(root: Container, tooltip: String): HoverIcon =
        findAll<HoverIcon>(root).first { it.toolTipText == tooltip }

    private fun singleSelectQuestion(id: String) = Question(
        id = id,
        items = listOf(
            QuestionItem(
                question = "Choose approach",
                header = "Approach",
                options = listOf(
                    QuestionOption("Minimal", "Smallest safe change"),
                    QuestionOption("Balanced", "Focused implementation with tests"),
                ),
                multiple = false,
                custom = false,
            )
        ),
    )

    private fun twoItemQuestion(id: String) = Question(
        id = id,
        items = listOf(
            QuestionItem(
                question = "Choose approach",
                header = "Approach",
                options = listOf(
                    QuestionOption("Minimal", "Smallest safe change"),
                    QuestionOption("Balanced", "Focused implementation"),
                ),
                multiple = false,
                custom = false,
            ),
            QuestionItem(
                question = "Choose test level",
                header = "Test Level",
                options = listOf(
                    QuestionOption("Unit", "Unit tests"),
                    QuestionOption("Integration", "Integration tests"),
                ),
                multiple = false,
                custom = false,
            ),
        ),
    )

    private fun assertLabelsContain(root: Container, text: String) {
        val found = findAll<JBLabel>(root).any { it.text == text }
        assertTrue("Expected label '$text' to be present", found)
    }

    private fun assertLabelsDoNotContain(root: Container, text: String) {
        val found = findAll<JBLabel>(root).any { it.text == text }
        assertFalse("Expected label '$text' to be absent, but it was found", found)
    }

    private inline fun <reified T> findAll(root: Container): List<T> = findAllCls(root, T::class.java)

    /**
     * Recursively find all components of type [cls], but do NOT recurse into
     * [AbstractButton] subtypes — buttons may have internal sub-components
     * (e.g. IntelliJ UI delegate children) that would produce spurious matches.
     */
    private fun <T> findAllCls(root: Container, cls: Class<T>): List<T> {
        val result = mutableListOf<T>()
        if (cls.isInstance(root)) result.add(cls.cast(root))
        for (child in root.components) {
            if (cls.isInstance(child)) result.add(cls.cast(child))
            // Do not recurse into button internals to avoid double-counting
            if (child is Container && child !is AbstractButton) {
                result.addAll(findAllCls(child, cls))
            }
        }
        return result
    }
}
