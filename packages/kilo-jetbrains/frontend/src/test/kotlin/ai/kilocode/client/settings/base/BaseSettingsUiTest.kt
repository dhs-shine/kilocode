package ai.kilocode.client.settings.base

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.text.JTextComponent

class BaseSettingsUiTest : BasePlatformTestCase() {
    private lateinit var scope: CoroutineScope
    private var panel: FakePanel? = null

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob())
    }

    override fun tearDown() {
        try {
            val view = panel
            if (view != null) edt { view.dispose() }
            panel = null
            scope.cancel()
        } finally {
            super.tearDown()
        }
    }

    fun `test modified and reset use baseline`() {
        val view = create()

        edt {
            view.edit("new")
            assertTrue(view.modified())
            view.resetDraft()
            assertEquals("old", view.value())
            assertFalse(view.modified())
        }
    }

    fun `test pending save target is not modified`() {
        val view = create()

        edt {
            view.edit("new")
            view.applyDraft()
            assertFalse(view.modified())
            view.edit("other")
            assertTrue(view.modified())
        }
    }

    fun `test failed save keeps draft dirty and shows error`() {
        val view = create()

        edt {
            view.edit("new")
            view.applyDraft()
            view.fail()
        }
        flush()

        edt {
            assertEquals("new", view.value())
            assertTrue(view.modified())
            assertTrue(text(view.progress).contains("Failed"))
        }
    }

    fun `test edit clears save error`() {
        val view = create()

        edt {
            view.edit("new")
            view.applyDraft()
            view.fail()
        }
        flush()
        edt { view.edit("other") }
        flush()

        edt { assertFalse(text(view.progress).contains("Failed")) }
    }

    fun `test successful save preserves concurrent edit`() {
        val view = create()

        edt {
            view.edit("new")
            view.applyDraft()
            view.edit("other")
            view.succeed("new")
        }
        flush()

        edt {
            assertEquals("other", view.value())
            assertTrue(view.modified())
        }
    }

    fun `test failed save after dispose calls failure hook`() {
        val view = create()

        edt {
            view.edit("new")
            view.applyDraft()
            view.dispose()
            view.fail()
        }
        panel = null
        flush()

        assertEquals(1, view.disposedFailures)
    }

    fun `test login banner can be shown`() {
        val view = create()

        edt { view.banner(true) }

        edt { assertTrue(text(view).contains("Sign in to Kilo Code")) }
    }

    fun `test login banner can be disabled`() {
        val view = create(login = false)

        edt { view.banner(true) }

        edt { assertFalse(text(view).contains("Sign in to Kilo Code")) }
    }

    private fun create(login: Boolean = true): FakePanel {
        val view = edt { FakePanel(scope, login) }
        panel = view
        return view
    }

    private fun flush() = runBlocking {
        edt { UIUtil.dispatchAllInvocationEvents() }
    }

    private fun <T> edt(block: () -> T): T {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun text(root: Container): String {
        val out = mutableListOf<String>()
        for (comp in components(root)) {
            if (!comp.isVisible) continue
            when (comp) {
                is AbstractButton -> comp.text?.let { out.add(it) }
                is JLabel -> comp.text?.let { out.add(it) }
                is JTextComponent -> comp.text?.let { out.add(it) }
            }
        }
        return out.joinToString("\n")
    }

    private fun components(root: Container): List<java.awt.Component> = buildList {
        fun visit(comp: java.awt.Component) {
            add(comp)
            if (comp is Container) comp.components.forEach { visit(it) }
        }
        visit(root)
    }

    private data class Draft(val value: String)
    private data class Change(val value: String)

    private class FakeContent : BaseContentPanel()

    private class FakePanel(
        cs: CoroutineScope,
        login: Boolean,
    ) : BaseSettingsUi<FakeContent, Draft, Change, Draft>(cs, Draft("old"), login) {
        private val callbacks = mutableListOf<(Draft?) -> Unit>()
        var disposedFailures = 0
            private set

        init {
            setSettingsContent(FakeContent())
            syncContent()
        }

        fun edit(value: String) = updateDraft { copy(value = value) }

        fun value(): String = draft.value

        fun succeed(value: String) = callbacks.removeAt(0)(Draft(value))

        fun fail() = callbacks.removeAt(0)(null)

        fun banner(login: Boolean) = syncLoginBanner(login) { top.hideBanner() }

        override fun change(from: Draft, to: Draft): Change? = if (from == to) null else Change(to.value)

        override fun save(change: Change, done: (Draft?) -> Unit) {
            callbacks += done
        }

        override fun base(result: Draft): Draft = result

        override fun syncContent() {
            val err = saveError
            if (saving) {
                showProgress(pendingText())
                return
            }
            if (err != null) {
                showError(err)
                return
            }
            clearProgress()
        }

        override fun pendingText(): String = "Saving"

        override fun failedText(): String = "Failed"

        override fun onSaveFailedAfterDispose(change: Change) {
            disposedFailures++
        }
    }
}
