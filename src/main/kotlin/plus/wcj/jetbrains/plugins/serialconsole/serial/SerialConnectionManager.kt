package plus.wcj.jetbrains.plugins.serialconsole.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import plus.wcj.jetbrains.plugins.serialconsole.model.FlowControl
import plus.wcj.jetbrains.plugins.serialconsole.model.ParityMode
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialConnectionConfig
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialLineState
import plus.wcj.jetbrains.plugins.serialconsole.model.StopBits
import java.util.concurrent.ConcurrentHashMap

class SerialConnectionManager {
    private val connections = ConcurrentHashMap<String, Connection>()

    fun connect(
        sessionId: String,
        config: SerialConnectionConfig,
        onData: (ByteArray) -> Unit,
        onDisconnected: () -> Unit,
        onLineState: (SerialLineState) -> Unit,
    ): Boolean {
        disconnect(sessionId)

        val port = SerialPort.getCommPort(config.portName)
        port.setComPortParameters(
            config.baudRate,
            config.dataBits,
            stopBits(config.stopBits),
            parity(config.parity),
        )
        port.setFlowControl(flowControl(config.flowControl))
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)

        if (!port.openPort()) return false

        connections[sessionId] = Connection(port, onDisconnected)
        val listenerAdded = port.addDataListener(object : SerialPortDataListener {
            override fun getListeningEvents(): Int {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE or
                    SerialPort.LISTENING_EVENT_PORT_DISCONNECTED or
                    SerialPort.LISTENING_EVENT_CTS or
                    SerialPort.LISTENING_EVENT_DSR or
                    SerialPort.LISTENING_EVENT_CARRIER_DETECT or
                    SerialPort.LISTENING_EVENT_RING_INDICATOR
            }

            override fun serialEvent(event: SerialPortEvent) {
                if ((event.eventType and SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) != 0) {
                    handleDisconnected(sessionId, port, onDisconnected)
                    return
                }
                if ((event.eventType and CONTROL_LINE_EVENTS) != 0) {
                    onLineState(readLineState(port))
                }
                if ((event.eventType and SerialPort.LISTENING_EVENT_DATA_AVAILABLE) == 0) return

                val available = runCatching { port.bytesAvailable() }.getOrElse {
                    handleDisconnected(sessionId, port, onDisconnected)
                    return
                }
                if (available <= 0) return
                val buffer = ByteArray(available)
                val read = runCatching { port.readBytes(buffer, available) }.getOrElse {
                    handleDisconnected(sessionId, port, onDisconnected)
                    return
                }
                if (read > 0) {
                    onData(buffer.copyOf(read))
                }
            }
        })
        if (!listenerAdded) {
            connections.remove(sessionId)
            port.closePort()
            return false
        }
        onLineState(readLineState(port))

        return true
    }

    fun disconnect(sessionId: String) {
        connections.remove(sessionId)?.let { connection ->
            connection.port.removeDataListener()
            connection.port.closePort()
        }
    }

    fun write(sessionId: String, bytes: ByteArray): Boolean {
        val connection = connections[sessionId] ?: return false
        val port = connection.port
        if (!port.isOpen) {
            handleDisconnected(sessionId, port, connection.onDisconnected)
            return false
        }

        val written = runCatching { port.writeBytes(bytes, bytes.size) }.getOrElse {
            handleDisconnected(sessionId, port, connection.onDisconnected)
            return false
        }
        if (written != bytes.size) {
            handleDisconnected(sessionId, port, connection.onDisconnected)
            return false
        }
        return true
    }

    fun isConnected(sessionId: String): Boolean = connections[sessionId]?.port?.isOpen == true

    fun setFlowControl(sessionId: String, flowControl: FlowControl): Boolean {
        val port = connections[sessionId]?.port ?: return false
        return port.setFlowControl(flowControl(flowControl))
    }

    fun setRts(sessionId: String, enabled: Boolean): Boolean {
        val port = connections[sessionId]?.port ?: return false
        return if (enabled) port.setRTS() else port.clearRTS()
    }

    fun setDtr(sessionId: String, enabled: Boolean): Boolean {
        val port = connections[sessionId]?.port ?: return false
        return if (enabled) port.setDTR() else port.clearDTR()
    }

    fun lineState(sessionId: String): SerialLineState? {
        val port = connections[sessionId]?.port ?: return null
        return readLineState(port)
    }

    private fun handleDisconnected(sessionId: String, port: SerialPort, onDisconnected: () -> Unit) {
        val removed = connections.remove(sessionId)
        if (removed?.port != port) return

        runCatching { port.removeDataListener() }
        runCatching { port.closePort() }
        onDisconnected()
    }

    private fun stopBits(value: StopBits): Int {
        return when (value) {
            StopBits.One -> SerialPort.ONE_STOP_BIT
            StopBits.OnePointFive -> SerialPort.ONE_POINT_FIVE_STOP_BITS
            StopBits.Two -> SerialPort.TWO_STOP_BITS
        }
    }

    private fun parity(value: ParityMode): Int {
        return when (value) {
            ParityMode.Odd -> SerialPort.ODD_PARITY
            ParityMode.Even -> SerialPort.EVEN_PARITY
            ParityMode.Mark -> SerialPort.MARK_PARITY
            ParityMode.Space -> SerialPort.SPACE_PARITY
            ParityMode.None -> SerialPort.NO_PARITY
        }
    }

    private fun flowControl(value: FlowControl): Int {
        return when (value) {
            FlowControl.RtsCts -> SerialPort.FLOW_CONTROL_RTS_ENABLED or SerialPort.FLOW_CONTROL_CTS_ENABLED
            FlowControl.DtrDsr -> SerialPort.FLOW_CONTROL_DTR_ENABLED or SerialPort.FLOW_CONTROL_DSR_ENABLED
            FlowControl.XonXoff -> SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED or SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED
            FlowControl.RtsCtsXonXoff -> SerialPort.FLOW_CONTROL_RTS_ENABLED or
                SerialPort.FLOW_CONTROL_CTS_ENABLED or
                SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED or
                SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED
            FlowControl.DtrDsrXonXoff -> SerialPort.FLOW_CONTROL_DTR_ENABLED or
                SerialPort.FLOW_CONTROL_DSR_ENABLED or
                SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED or
                SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED
            FlowControl.None -> SerialPort.FLOW_CONTROL_DISABLED
        }
    }

    private fun readLineState(port: SerialPort): SerialLineState {
        return SerialLineState(
            rts = runCatching { port.getRTS() }.getOrDefault(false),
            dtr = runCatching { port.getDTR() }.getOrDefault(false),
            cts = runCatching { port.getCTS() }.getOrDefault(false),
            dsr = runCatching { port.getDSR() }.getOrDefault(false),
            dcd = runCatching { port.getDCD() }.getOrDefault(false),
            ri = runCatching { port.getRI() }.getOrDefault(false),
        )
    }

    private data class Connection(
        val port: SerialPort,
        val onDisconnected: () -> Unit,
    )

    private companion object {
        val CONTROL_LINE_EVENTS: Int =
            SerialPort.LISTENING_EVENT_CTS or
                SerialPort.LISTENING_EVENT_DSR or
                SerialPort.LISTENING_EVENT_CARRIER_DETECT or
                SerialPort.LISTENING_EVENT_RING_INDICATOR
    }
}
