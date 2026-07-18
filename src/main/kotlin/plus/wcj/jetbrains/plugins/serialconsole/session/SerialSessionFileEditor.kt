package plus.wcj.jetbrains.plugins.serialconsole.session

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import plus.wcj.jetbrains.plugins.serialconsole.ui.SerialSessionEditorPanel
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class SerialSessionFileEditor(
    project: Project,
    private val sessionId: String,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {
    private val workspace = SerialWorkspaceService.getInstance(project)
    private val panel = SerialSessionEditorPanel(project, sessionId)

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel.preferredFocus

    override fun getName(): String = workspace.findSession(sessionId)?.name ?: "Serial"

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = workspace.findSession(sessionId) != null

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun dispose() {
        panel.dispose()
        workspace.editorClosed(sessionId)
    }
}
