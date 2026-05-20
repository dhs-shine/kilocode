package ai.kilocode.client.session.views

import ai.kilocode.client.session.ui.shared.SessionQuestionButton
import ai.kilocode.client.session.ui.style.SessionUiStyle
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextArea
import java.awt.Container
import javax.swing.JButton

@Suppress("UnstableApiUsage")
class LoginRequiredViewTest : BasePlatformTestCase() {

    // ------ title and message rendering ------

    fun `test header title text is in the component tree`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("Sign in required.")

        val title = findAll<JBTextArea>(view).firstOrNull { it.text.isNotEmpty() && it.font.isBold }
        assertNotNull("Header title text area should be present", title)
    }

    fun `test description message text is in the component tree after show`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("Sign in required.")

        val desc = findAll<JBTextArea>(view).firstOrNull { it.text == "Sign in required." }
        assertNotNull("Description text area should contain the show message", desc)
    }

    fun `test show updates description without recreating title`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("First message.")

        val before = findAll<JBTextArea>(view).firstOrNull { it.text == "First message." }
        assertNotNull(before)

        view.show("Second message.")

        val after = findAll<JBTextArea>(view).firstOrNull { it.text == "Second message." }
        assertNotNull("Description should update to second message", after)
        val stale = findAll<JBTextArea>(view).firstOrNull { it.text == "First message." }
        assertNull("Old description text should not remain", stale)
    }

    // ------ open profile button style ------

    fun `test open profile button is SessionQuestionButton`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("Sign in required.")

        val btn = openProfileButton(view)
        assertTrue("Open profile button should be a SessionQuestionButton", btn is SessionQuestionButton)
    }

    fun `test open profile button is primary`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("Sign in required.")

        val btn = openProfileButton(view) as SessionQuestionButton
        assertTrue("Open profile button should be primary", btn.primary)
    }

    fun `test open profile button has DarculaButtonUI default style key`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("Sign in required.")

        val btn = openProfileButton(view)
        assertEquals(true, btn.getClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY))
    }

    fun `test open profile button uses question surface background`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("Sign in required.")

        val btn = openProfileButton(view)
        assertEquals(SessionUiStyle.View.surface(), btn.background)
    }

    // ------ dismiss button style ------

    fun `test dismiss button is SessionQuestionButton`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("Sign in required.")

        val btn = dismissButton(view)
        assertTrue("Dismiss button should be a SessionQuestionButton", btn is SessionQuestionButton)
    }

    fun `test dismiss button is not primary`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("Sign in required.")

        val btn = dismissButton(view) as SessionQuestionButton
        assertFalse("Dismiss button should not be primary", btn.primary)
    }

    // ------ callbacks ------

    fun `test open profile button click invokes openProfile callback`() {
        var called = false
        val view = LoginRequiredView(openProfile = { called = true }, dismiss = {})
        view.show("Sign in required.")

        openProfileButton(view).doClick()

        assertTrue("openProfile should have been called", called)
    }

    fun `test dismiss button click invokes dismiss callback`() {
        var called = false
        val view = LoginRequiredView(openProfile = {}, dismiss = { called = true })
        view.show("Sign in required.")

        dismissButton(view).doClick()

        assertTrue("dismiss should have been called", called)
    }

    // ------ visibility ------

    fun `test view is initially hidden`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        assertFalse(view.isVisible)
    }

    fun `test show makes view visible`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("Sign in required.")
        assertTrue(view.isVisible)
    }

    fun `test hideView makes view invisible`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.show("Sign in required.")
        view.hideView()
        assertFalse(view.isVisible)
    }

    fun `test hideView is idempotent when already hidden`() {
        val view = LoginRequiredView(openProfile = {}, dismiss = {})
        view.hideView()
        assertFalse(view.isVisible)
    }

    // ------ helpers ------

    private fun openProfileButton(view: LoginRequiredView): JButton = view.openProfileButton

    private fun dismissButton(view: LoginRequiredView): JButton = view.dismissButton

    private inline fun <reified T> findAll(root: Container): List<T> =
        findAllCls(root, T::class.java)

    private fun <T> findAllCls(root: Container, cls: Class<T>): List<T> {
        val result = mutableListOf<T>()
        if (cls.isInstance(root)) result.add(cls.cast(root))
        for (child in root.components) {
            if (cls.isInstance(child)) result.add(cls.cast(child))
            if (child is Container) result.addAll(findAllCls(child, cls))
        }
        return result
    }
}
