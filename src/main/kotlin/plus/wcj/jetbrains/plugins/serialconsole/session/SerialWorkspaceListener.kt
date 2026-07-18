package plus.wcj.jetbrains.plugins.serialconsole.session

import java.util.EventListener

interface SerialWorkspaceListener : EventListener {
    fun workspaceChanged()
}
