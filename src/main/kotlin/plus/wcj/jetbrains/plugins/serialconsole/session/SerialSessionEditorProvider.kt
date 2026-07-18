package plus.wcj.jetbrains.plugins.serialconsole.session

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class SerialSessionEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is SerialSessionVirtualFile
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val sessionId = (file as SerialSessionVirtualFile).sessionId
        return SerialSessionFileEditor(project, sessionId, file)
    }

    override fun getEditorTypeId(): String = "serial-session-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
