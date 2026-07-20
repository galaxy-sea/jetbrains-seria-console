package plus.wcj.jetbrains.plugins.serialconsole.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import plus.wcj.jetbrains.plugins.serialconsole.model.ConnectionStatus
import plus.wcj.jetbrains.plugins.serialconsole.model.PacketMode
import plus.wcj.jetbrains.plugins.serialconsole.model.ReceiveConfig
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialConnectionConfig
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialLineState
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialMessage
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialMessageDirection
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialPortDescriptor
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialProjectInfo
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialSession
import plus.wcj.jetbrains.plugins.serialconsole.model.SendConfig
import plus.wcj.jetbrains.plugins.serialconsole.serial.SerialConnectionManager
import plus.wcj.jetbrains.plugins.serialconsole.serial.SerialPortScanner
import plus.wcj.jetbrains.plugins.serialconsole.ui.SerialBundle
import plus.wcj.jetbrains.plugins.serialconsole.ui.SerialLanguage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import javax.swing.Timer

@Service(Service.Level.PROJECT)
class SerialWorkspaceService(private val project: Project) : Disposable {
    private val dispatcher = EventDispatcher.create(SerialWorkspaceListener::class.java)
    private val settings = SerialWorkspaceSettings.getInstance(project)
    private val sessionFiles = linkedMapOf<String, SerialSessionVirtualFile>()
    private val connectionManager = SerialConnectionManager()
    private val receiveBuffers = mutableMapOf<String, ByteArrayOutputStream>()
    private val receiveTimers = mutableMapOf<String, Timer>()
    private val customPorts = linkedMapOf<String, SerialPortDescriptor>()

    var ports: List<SerialPortDescriptor> = emptyList()
        private set

    var language: SerialLanguage = settings.language()
        private set

    val sessions: MutableList<SerialSession> = mutableListOf()

    init {
        val scannedPorts = SerialPortScanner.scan()
        settings.customPorts()
            .filter { portNameExists(it.alias, scannedPorts) }
            .forEach { port ->
                customPorts[port.path] = port
            }
        settings.saveCustomPorts(customPorts.values)
        ports = mergedPorts(scannedPorts)
    }

    fun addListener(listener: SerialWorkspaceListener) {
        dispatcher.addListener(listener, project)
    }

    fun removeListener(listener: SerialWorkspaceListener) {
        dispatcher.removeListener(listener)
    }

    fun refreshPorts() {
        val scannedPorts = SerialPortScanner.scan()
        pruneMissingCustomPorts(scannedPorts)
        ports = mergedPorts(scannedPorts)
        dispatcher.multicaster.workspaceChanged()
    }

    fun setLanguage(language: SerialLanguage) {
        if (this.language == language) return
        this.language = language
        settings.setLanguage(language)
        dispatcher.multicaster.workspaceChanged()
    }

    fun nextLanguage() {
        val languages = SerialLanguage.values()
        setLanguage(languages[(languages.indexOf(language) + 1) % languages.size])
    }

    fun openSession(port: SerialPortDescriptor) {
        if (port.path.isBlank()) return

        val portKey = port.path
        val existing = sessions.firstOrNull { it.port.path == portKey }
        if (existing != null) {
            focusSession(existing.id)
            return
        }

        val session = SerialSession(
            id = UUID.randomUUID().toString(),
            name = port.alias,
            port = port,
            serialConfig = SerialConnectionConfig(portName = port.path),
            receiveConfig = ReceiveConfig(),
            sendConfig = SendConfig(),
            status = ConnectionStatus.Disconnected,
        )
        settings.applySessionSettings(session)
        appendLogMessage(session, SerialProjectInfo.message())
        sessions += session

        val file = SerialSessionVirtualFile(session.id, session.name)
        sessionFiles[session.id] = file
        FileEditorManager.getInstance(project).openFile(file, true)
        dispatcher.multicaster.workspaceChanged()
    }

    fun openCustomPort(portName: String) {
        val trimmed = portName.trim()
        val scannedPorts = SerialPortScanner.scan()
        if (trimmed.isEmpty() || !portNameExists(trimmed, scannedPorts)) return

        val resolvedPath = SerialPortScanner.resolveSystemPortPath(trimmed)
        val portPath = resolvedPath.ifBlank { trimmed }
        val portAlias = trimmed.takeIf { resolvedPath.isNotBlank() && resolvedPath != trimmed } ?: portPath
        val port = customPorts.getOrPut(portPath) {
            SerialPortDescriptor(
                description = "Custom",
                path = portPath,
                alias = portAlias,
                status = ConnectionStatus.Disconnected,
            )
        }
        settings.saveCustomPorts(customPorts.values)
        ports = mergedPorts(scannedPorts)
        openSession(port)
    }

    private fun pruneMissingCustomPorts(scannedPorts: List<SerialPortDescriptor>) {
        val missingKeys = customPorts.values
            .filterNot { portNameExists(it.alias, scannedPorts) }
            .map { it.path }
        if (missingKeys.isEmpty()) return

        missingKeys.forEach(customPorts::remove)
        settings.saveCustomPorts(customPorts.values)
    }

    private fun portNameExists(portName: String, scannedPorts: List<SerialPortDescriptor>): Boolean {
        if (portName.isBlank()) return false

        val resolvedPath = SerialPortScanner.resolveSystemPortPath(portName)
        return scannedPorts.any { port ->
            port.path == portName ||
                port.alias == portName ||
                (resolvedPath.isNotBlank() && port.path == resolvedPath)
        }
    }

    private fun mergedPorts(scannedPorts: List<SerialPortDescriptor>): List<SerialPortDescriptor> {
        val detectedPorts = scannedPorts.filter { it.path.isNotBlank() }
        val mergedPorts = linkedMapOf<String, SerialPortDescriptor>()
        detectedPorts.forEach { port ->
            mergedPorts[port.path] = port
        }
        customPorts.values.forEach { port ->
            mergedPorts[port.path] = port
        }
        return mergedPorts.values.toList().ifEmpty { scannedPorts }
    }

    fun focusSession(sessionId: String) {
        val file = sessionFiles[sessionId] ?: return
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    fun connectSession(sessionId: String): Boolean {
        val session = findSession(sessionId) ?: return false
        val connected = connectionManager.connect(
            sessionId = sessionId,
            config = session.serialConfig,
            onData = { bytes ->
                ApplicationManager.getApplication().invokeLater {
                    handleReceivedBytes(session, bytes)
                }
            },
            onDisconnected = {
                ApplicationManager.getApplication().invokeLater {
                    handleConnectionInterrupted(sessionId)
                }
            },
            onLineState = { lineState ->
                ApplicationManager.getApplication().invokeLater {
                    updateSessionLineState(sessionId, lineState)
                }
            },
        )
        session.status = if (connected) ConnectionStatus.Connected else ConnectionStatus.Disconnected
        if (!connected) {
            updateLineState(session.lineState, SerialLineState())
        }
        appendLogMessage(
            session,
            if (connected) {
                t("connectionLog.connected", session.serialConfig.portName)
            } else {
                t("connectionLog.failed", session.serialConfig.portName)
            },
        )
        dispatcher.multicaster.workspaceChanged()
        return connected
    }

    fun disconnectSession(sessionId: String) {
        flushReceivePacket(sessionId)
        findSession(sessionId)?.let { session ->
            session.status = ConnectionStatus.Disconnected
            updateLineState(session.lineState, SerialLineState())
            appendLogMessage(session, t("connectionLog.disconnected", session.serialConfig.portName))
        }
        connectionManager.disconnect(sessionId)
        dispatcher.multicaster.workspaceChanged()
    }

    fun applySessionFlowControl(sessionId: String): Boolean {
        val session = findSession(sessionId) ?: return false
        if (session.status != ConnectionStatus.Connected) return false
        val applied = connectionManager.setFlowControl(sessionId, session.serialConfig.flowControl)
        refreshSessionLineState(sessionId)
        dispatcher.multicaster.workspaceChanged()
        return applied
    }

    fun setSessionRts(sessionId: String, enabled: Boolean): Boolean {
        val session = findSession(sessionId) ?: return false
        if (session.status != ConnectionStatus.Connected) return false
        val updated = connectionManager.setRts(sessionId, enabled)
        refreshSessionLineState(sessionId)
        dispatcher.multicaster.workspaceChanged()
        return updated
    }

    fun setSessionDtr(sessionId: String, enabled: Boolean): Boolean {
        val session = findSession(sessionId) ?: return false
        if (session.status != ConnectionStatus.Connected) return false
        val updated = connectionManager.setDtr(sessionId, enabled)
        refreshSessionLineState(sessionId)
        dispatcher.multicaster.workspaceChanged()
        return updated
    }

    fun writeSession(sessionId: String, bytes: ByteArray): Boolean {
        return connectionManager.write(sessionId, bytes)
    }

    fun clearSessionMessages(sessionId: String) {
        findSession(sessionId)?.let { session ->
            session.messages.clear()
            appendLogMessage(session, SerialProjectInfo.message())
        }
        dispatcher.multicaster.workspaceChanged()
    }

    fun closeSession(sessionId: String) {
        flushReceivePacket(sessionId)
        findSession(sessionId)?.let { session ->
            session.status = ConnectionStatus.Disconnected
            updateLineState(session.lineState, SerialLineState())
        }
        connectionManager.disconnect(sessionId)
        sessions.removeIf { it.id == sessionId }
        sessionFiles.remove(sessionId)
        dispatcher.multicaster.workspaceChanged()
    }

    fun editorClosed(sessionId: String) {
        val session = findSession(sessionId) ?: return
        if (session.status == ConnectionStatus.Connected) {
            dispatcher.multicaster.workspaceChanged()
            return
        }

        closeSession(sessionId)
    }

    fun findSession(sessionId: String): SerialSession? {
        return sessions.firstOrNull { it.id == sessionId }
    }

    private fun handleReceivedBytes(session: SerialSession, bytes: ByteArray) {
        when (session.receiveConfig.packetMode) {
            PacketMode.None -> {
                appendReceivedMessage(session, bytes)
                dispatcher.multicaster.workspaceChanged()
            }
            PacketMode.Timeout -> bufferReceivedBytes(session, bytes)
        }
    }

    private fun handleConnectionInterrupted(sessionId: String) {
        val session = findSession(sessionId) ?: return
        if (session.status == ConnectionStatus.Disconnected) return

        flushReceivePacket(sessionId)
        session.status = ConnectionStatus.Disconnected
        updateLineState(session.lineState, SerialLineState())
        appendLogMessage(session, t("connectionLog.interrupted", session.serialConfig.portName))
        refreshPorts()
    }

    private fun refreshSessionLineState(sessionId: String) {
        val lineState = connectionManager.lineState(sessionId) ?: return
        updateSessionLineState(sessionId, lineState, notify = false)
    }

    private fun updateSessionLineState(sessionId: String, lineState: SerialLineState, notify: Boolean = true) {
        val session = findSession(sessionId) ?: return
        updateLineState(session.lineState, lineState)
        if (notify) {
            dispatcher.multicaster.workspaceChanged()
        }
    }

    private fun updateLineState(target: SerialLineState, source: SerialLineState) {
        target.rts = source.rts
        target.dtr = source.dtr
        target.cts = source.cts
        target.dsr = source.dsr
        target.dcd = source.dcd
        target.ri = source.ri
    }

    private fun bufferReceivedBytes(session: SerialSession, bytes: ByteArray) {
        val buffer = receiveBuffers.getOrPut(session.id) { ByteArrayOutputStream() }
        buffer.write(bytes, 0, bytes.size)

        receiveTimers.remove(session.id)?.stop()
        val timer = Timer(session.receiveConfig.packetTimeoutMs.coerceAtLeast(1)) {
            flushReceivePacket(session.id)
        }
        timer.isRepeats = false
        receiveTimers[session.id] = timer
        timer.start()
    }

    private fun flushReceivePacket(sessionId: String) {
        receiveTimers.remove(sessionId)?.stop()
        val buffer = receiveBuffers.remove(sessionId) ?: return
        val bytes = buffer.toByteArray()
        if (bytes.isEmpty()) return

        val session = findSession(sessionId) ?: return
        appendReceivedMessage(session, bytes)
        dispatcher.multicaster.workspaceChanged()
    }

    private fun appendReceivedMessage(session: SerialSession, bytes: ByteArray) {
        session.statistics.rxBytes += bytes.size
        session.statistics.rxPackets += 1
        if (session.messagePaused) return

        session.messages += SerialMessage(
            time = Instant.now(),
            direction = SerialMessageDirection.Input,
            data = bytes,
        )
    }

    private fun appendLogMessage(session: SerialSession, text: String) {
        session.messages += SerialMessage(
            time = Instant.now(),
            direction = SerialMessageDirection.Log,
            data = text.toByteArray(Charsets.UTF_8),
        )
    }

    private fun t(key: String, vararg params: Any): String {
        return SerialBundle.message(language, key, *params)
    }

    override fun dispose() {
        sessions.map { it.id }.forEach(connectionManager::disconnect)
        receiveTimers.values.forEach { it.stop() }
        receiveTimers.clear()
        receiveBuffers.clear()
        sessions.clear()
        sessionFiles.clear()
        customPorts.clear()
    }

    companion object {
        fun getInstance(project: Project): SerialWorkspaceService = project.service()
    }
}
