package plus.wcj.jetbrains.plugins.serialconsole.session

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.NlsContexts
import com.intellij.testFramework.LightVirtualFile
import javax.swing.Icon

class SerialSessionVirtualFile(
    val sessionId: String,
    sessionName: String,
) : LightVirtualFile("$sessionName.serial-session", SerialSessionFileType, "") {
    override fun isWritable(): Boolean = false
}

object SerialSessionFileType : FileType {
    override fun getName(): String = "Serial Session"

    override fun getDescription(): @NlsContexts.Label String = "Serial monitor session"

    override fun getDefaultExtension(): String = "serial-session"

    override fun getIcon(): Icon? = null

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = true
}
