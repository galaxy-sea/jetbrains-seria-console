package plus.wcj.jetbrains.plugins.serialconsole.serial

import com.fazecast.jSerialComm.SerialPort
import plus.wcj.jetbrains.plugins.serialconsole.model.ConnectionStatus
import plus.wcj.jetbrains.plugins.serialconsole.model.SerialPortDescriptor

object SerialPortScanner {
    fun scan(): List<SerialPortDescriptor> {
        return SerialPort.getCommPorts()
            .map { port ->
                val systemPath = port.systemPortPath.orEmpty().ifBlank { port.systemPortName.orEmpty() }
                SerialPortDescriptor(
                    description = port.descriptivePortName.orEmpty().ifBlank { port.portDescription.orEmpty() },
                    path = systemPath,
                    alias = systemPath,
                    vendor = port.manufacturer.orEmpty().ifBlank { "Unknown" },
                    vid = port.vendorID.toHexId(),
                    pid = port.productID.toHexId(),
                    serialNumber = port.serialNumber.orEmpty().ifBlank { "-" },
                    status = ConnectionStatus.Connected,
                )
            }
            .ifEmpty {
                listOf(
                    SerialPortDescriptor(
                        description = "No serial ports detected",
                        alias = "No Ports",
                        status = ConnectionStatus.Disconnected,
                    )
                )
            }
    }

    fun resolveSystemPortPath(portName: String): String {
        return runCatching {
            SerialPort.getCommPort(portName).systemPortPath.orEmpty()
        }.getOrDefault("")
    }

    private fun Int.toHexId(): String {
        return if (this < 0) "-" else toString(16).uppercase().padStart(4, '0')
    }
}
