package plus.wcj.jetbrains.plugins.serialconsole.session

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import plus.wcj.jetbrains.plugins.serialconsole.model.AppendMode
import plus.wcj.jetbrains.plugins.serialconsole.model.ByteOrderMode
import plus.wcj.jetbrains.plugins.serialconsole.model.ConnectionStatus
import plus.wcj.jetbrains.plugins.serialconsole.model.FlowControl
import plus.wcj.jetbrains.plugins.serialconsole.model.PacketMode
import plus.wcj.jetbrains.plugins.serialconsole.model.ParityMode
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialPortDescriptor
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialSession
import plus.wcj.jetbrains.plugins.serialconsole.model.StopBits
import plus.wcj.jetbrains.plugins.serialconsole.ui.SerialLanguage

@State(name = "SerialConsoleWorkspace", storages = [Storage("serial-console.xml")])
@Service(Service.Level.PROJECT)
class SerialWorkspaceSettings : PersistentStateComponent<SerialWorkspaceState> {
    private var state = SerialWorkspaceState()

    override fun getState(): SerialWorkspaceState {
        return state
    }

    override fun loadState(state: SerialWorkspaceState) {
        this.state = state
    }

    fun language(): SerialLanguage {
        return enumValueOrDefault(state.language, SerialLanguage.English)
    }

    fun setLanguage(language: SerialLanguage) {
        state.language = language.name
    }

    fun customPorts(): List<SerialPortDescriptor> {
        return state.customPorts.map { port ->
            SerialPortDescriptor(
                description = port.description.ifBlank { "Custom" },
                path = port.path,
                alias = port.alias,
                status = ConnectionStatus.Disconnected,
            )
        }
    }

    fun saveCustomPorts(ports: Collection<SerialPortDescriptor>) {
        state.customPorts = ports.map { port ->
            SerialPortState().apply {
                description = port.description
                path = port.path
                alias = port.alias
            }
        }.toMutableList()
    }

    fun saveSessionSettings(session: SerialSession) {
        val key = session.port.path
        val settings = state.portSettings.firstOrNull { it.key == key } ?: SerialPortSettingsState().also {
            it.key = key
            state.portSettings += it
        }
        settings.baudRate = session.serialConfig.baudRate
        settings.dataBits = session.serialConfig.dataBits
        settings.stopBits = session.serialConfig.stopBits.name
        settings.parity = session.serialConfig.parity.name
        settings.flowControl = session.serialConfig.flowControl.name
        settings.receiveTextEncoding = session.receiveConfig.textEncoding
        settings.receiveTextColor = session.receiveConfig.textColor
        settings.packetMode = session.receiveConfig.packetMode.name
        settings.packetTimeoutMs = session.receiveConfig.packetTimeoutMs
        settings.showTimestamp = session.receiveConfig.showTimestamp
        settings.sendTextEncoding = session.sendConfig.textEncoding
        settings.sendTextColor = session.sendConfig.textColor
        settings.appendMode = session.sendConfig.appendMode.name
        settings.crcAlgorithm = session.sendConfig.crcAlgorithm
        settings.crcByteOrder = session.sendConfig.crcByteOrder.name
        settings.appendCrcAtEnd = session.sendConfig.appendCrcAtEnd
    }

    fun applySessionSettings(session: SerialSession) {
        val settings = state.portSettings.firstOrNull { it.key == session.port.path } ?: return
        session.serialConfig.baudRate = settings.baudRate
        session.serialConfig.dataBits = settings.dataBits
        session.serialConfig.stopBits = enumValueOrDefault(settings.stopBits, StopBits.One)
        session.serialConfig.parity = enumValueOrDefault(settings.parity, ParityMode.None)
        session.serialConfig.flowControl = enumValueOrDefault(settings.flowControl, FlowControl.None)
        session.receiveConfig.textEncoding = settings.receiveTextEncoding.ifBlank { session.receiveConfig.textEncoding }
        session.receiveConfig.textColor = settings.receiveTextColor
        session.receiveConfig.packetMode = enumValueOrDefault(settings.packetMode, PacketMode.None)
        session.receiveConfig.packetTimeoutMs = settings.packetTimeoutMs
        session.receiveConfig.showTimestamp = settings.showTimestamp
        session.sendConfig.textEncoding = settings.sendTextEncoding.ifBlank { session.sendConfig.textEncoding }
        session.sendConfig.textColor = settings.sendTextColor
        session.sendConfig.appendMode = enumValueOrDefault(settings.appendMode, AppendMode.None)
        session.sendConfig.crcAlgorithm = settings.crcAlgorithm.ifBlank { session.sendConfig.crcAlgorithm }
        session.sendConfig.crcByteOrder = enumValueOrDefault(settings.crcByteOrder, ByteOrderMode.BigEndian)
        session.sendConfig.appendCrcAtEnd = settings.appendCrcAtEnd
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, defaultValue: T): T {
        return runCatching { enumValueOf<T>(name) }.getOrDefault(defaultValue)
    }

    companion object {
        fun getInstance(project: Project): SerialWorkspaceSettings = project.service()
    }
}
