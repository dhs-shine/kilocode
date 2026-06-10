package ai.kilocode.client.vfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

abstract class KiloVfsTestBase : BasePlatformTestCase() {
    protected lateinit var kind: KiloVfsTestKind

    override fun setUp() {
        super.setUp()
        kind = KiloVfsTestKind()
        service<KiloVfsRegistry>().register(kind)
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(KiloFileEditorProvider(), myFixture.testRootDisposable)
    }

    override fun tearDown() {
        try {
            edt {
                FileEditorManager.getInstance(project).openFiles.toList().forEach { file ->
                    FileEditorManager.getInstance(project).closeFile(file)
                }
                UIUtil.dispatchAllInvocationEvents()
            }
            service<KiloVfsRegistry>().unregister(KiloVfsTestKind.ID)
            service<KiloVfsRegistry>().unregister("missing")
        } finally {
            super.tearDown()
        }
    }

    protected fun path(params: Map<String, String> = mapOf("id" to "1"), kind: String = KiloVfsTestKind.ID): KiloPath {
        return KiloPath(project.locationHash, kind, params)
    }

    protected fun edt(block: () -> Unit) {
        ApplicationManager.getApplication().invokeAndWait(block)
    }

    protected fun <T> edtValue(block: () -> T): T {
        var result: T? = null
        ApplicationManager.getApplication().invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

class KiloVfsTestKind : KiloEditorKind {
    val components = mutableListOf<JComponent>()
    val disposables = mutableListOf<TestDisposable>()

    override val id: String = ID

    override fun title(project: com.intellij.openapi.project.Project, params: Map<String, String>): String {
        return "Test ${params["id"] ?: "new"}"
    }

    override fun presentablePath(project: com.intellij.openapi.project.Project, params: Map<String, String>): String {
        return "Kilo Test / ${params["id"] ?: "new"}"
    }

    override fun isValid(project: com.intellij.openapi.project.Project, params: Map<String, String>): Boolean {
        return params["valid"] != "false"
    }

    override fun createContent(project: com.intellij.openapi.project.Project, file: KiloVirtualFile, parent: Disposable): JComponent {
        val child = TestDisposable()
        Disposer.register(parent, child)
        disposables.add(child)
        return JBLabel("content:${file.path.params["id"] ?: "new"}").also { components.add(it) }
    }

    companion object {
        const val ID = "test"
    }
}

class TestDisposable : Disposable {
    var disposed = false

    override fun dispose() {
        disposed = true
    }
}
