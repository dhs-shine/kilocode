package ai.kilocode.client.session.views

import ai.kilocode.client.session.model.Question
import ai.kilocode.client.session.model.QuestionItem
import ai.kilocode.client.session.model.QuestionOption
import ai.kilocode.rpc.dto.QuestionReplyDto
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Container
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

        buttons(view).first { it.text == "Dismiss" }.doClick()

        assertFalse(view.isVisible)
        assertEquals("req_1", rejects.single())
        assertTrue(replies.isEmpty())
    }

    fun `test single question submit sends answer`() {
        view.show(
            Question(
                id = "req_2",
                items = listOf(
                    QuestionItem(
                        question = "Choose approach",
                        header = "Approach",
                        options = listOf(
                            QuestionOption("Minimal", "Keep it simple"),
                            QuestionOption("Refactor", "Full refactor"),
                        ),
                        multiple = false,
                        custom = false,
                    )
                ),
            )
        )

        buttons(view).first { it.text == "Minimal" }.doClick()
        buttons(view).first { it.text == "Submit" }.doClick()

        assertFalse(view.isVisible)
        assertEquals(1, replies.size)
        assertEquals("req_2", replies.single().first)
        assertEquals(listOf(listOf("Minimal")), replies.single().second.answers)
    }

    fun `test multi question submit sends all answers`() {
        view.show(
            Question(
                id = "q_strategy",
                items = listOf(
                    QuestionItem(
                        question = "Choose approach",
                        header = "Approach",
                        options = listOf(
                            QuestionOption("Minimal", "Keep it simple"),
                            QuestionOption("Refactor", "Full refactor"),
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
        )

        buttons(view).first { it.text == "Minimal" }.doClick()
        buttons(view).first { it.text == "Unit" }.doClick()
        buttons(view).first { it.text == "Submit" }.doClick()

        assertFalse(view.isVisible)
        assertEquals(1, replies.size)
        assertEquals("q_strategy", replies.single().first)
        assertEquals(listOf(listOf("Minimal"), listOf("Unit")), replies.single().second.answers)
    }

    fun `test multiple selection item toggles options`() {
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

        buttons(view).first { it.text == "A" }.doClick()
        buttons(view).first { it.text == "B" }.doClick()
        // Toggle B off
        buttons(view).first { it.text == "B" }.doClick()
        buttons(view).first { it.text == "Submit" }.doClick()

        assertFalse(view.isVisible)
        assertEquals(1, replies.size)
        assertEquals("req_3", replies.single().first)
        assertEquals(listOf(listOf("A")), replies.single().second.answers)
    }

    private fun buttons(root: Container): List<JButton> = root.components.flatMap { comp ->
        val item = if (comp is JButton) listOf(comp) else emptyList()
        if (comp is Container) item + buttons(comp) else item
    }
}
