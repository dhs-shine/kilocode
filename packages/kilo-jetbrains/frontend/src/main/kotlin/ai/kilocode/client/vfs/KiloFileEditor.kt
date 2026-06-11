package ai.kilocode.client.vfs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.JComponent

class KiloFileEditor(
    private val project: Project,
    private val source: VirtualFile,
    private val file: KiloVirtualFile,
    private val kind: KiloEditorKind,
) : KiloFileEditorBase() {
    private val ui: JComponent by lazy { kind.createContent(project, file, this) }

    @RequiresEdt
    override fun getComponent(): JComponent = ui

    override fun getPreferredFocusedComponent(): JComponent? = kind.preferredFocus(ui)

    override fun getName(): String = kind.title(project, file.path.params)

    override fun getFile(): VirtualFile = source

    override fun isValid(): Boolean = super.isValid() && source.isValid && file.isValid && kind.isValid(project, file.path.params)
}
