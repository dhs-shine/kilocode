package ai.kilocode.client.vfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.Icon
import javax.swing.JComponent

interface KiloEditorKind {
    val id: String

    fun title(project: Project, params: Map<String, String>): String

    fun icon(project: Project, params: Map<String, String>): Icon? = null

    fun presentablePath(project: Project, params: Map<String, String>): String = title(project, params)

    fun isValid(project: Project, params: Map<String, String>): Boolean = true

    @RequiresEdt
    fun createContent(project: Project, file: KiloVirtualFile, parent: Disposable): JComponent

    fun preferredFocus(component: JComponent): JComponent? = null
}
