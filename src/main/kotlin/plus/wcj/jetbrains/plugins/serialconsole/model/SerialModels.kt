package plus.wcj.jetbrains.plugins.serialconsole.model

import java.time.Instant

enum class ConnectionStatus {
    Connected,
    Disconnected,
}

enum class PacketMode {
    None,
    Timeout,
}

enum class ByteOrderMode {
    LittleEndian,
    BigEndian,
}

enum class AppendMode {
    None,
    Cr,
    Lf,
    Crlf,
}

enum class FlowControl {
    None,
    RtsCts,
    DtrDsr,
    XonXoff,
    RtsCtsXonXoff,
    DtrDsrXonXoff,
}

enum class StopBits {
    One,
    OnePointFive,
    Two,
}

enum class ParityMode {
    None,
    Odd,
    Even,
    Mark,
    Space,
}

enum class SerialMessageDirection {
    Input,
    Output,
    Log,
}

data class SerialPortDescriptor(
    val description: String,
    val path: String = "",
    val alias: String = "",
    val vendor: String = "Unknown",
    val vid: String = "-",
    val pid: String = "-",
    val serialNumber: String = "-",
    val status: ConnectionStatus = ConnectionStatus.Disconnected,
)

data class SerialConnectionConfig(
    var portName: String,
    var baudRate: Int = 115200,
    var dataBits: Int = 8,
    var stopBits: StopBits = StopBits.One,
    var parity: ParityMode = ParityMode.None,
    var flowControl: FlowControl = FlowControl.None,
)

data class SerialLineState(
    var rts: Boolean = false,
    var dtr: Boolean = false,
    var cts: Boolean = false,
    var dsr: Boolean = false,
    var dcd: Boolean = false,
    var ri: Boolean = false,
)

data class ReceiveConfig(
    var textEncoding: String = "HEX",
    var textColor: Int = 0x56A8F5,
    var packetMode: PacketMode = PacketMode.None,
    var packetTimeoutMs: Int = 20,
    var showTimestamp: Boolean = true,
)

data class SendConfig(
    var textEncoding: String = "HEX",
    var textColor: Int = 0x6AAB73,
    var appendMode: AppendMode = AppendMode.None,
    var crcAlgorithm: String = "None",
    var crcByteOrder: ByteOrderMode = ByteOrderMode.BigEndian,
    var appendCrcAtEnd: Boolean = true,
)

data class SerialStatistics(
    var rxBytes: Long = 0,
    var txBytes: Long = 0,
    var rxPackets: Long = 0,
    var txPackets: Long = 0,
    var startedAt: Instant = Instant.now(),
)

data class SerialMessage(
    val time: Instant,
    val direction: SerialMessageDirection,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SerialMessage
        return time == other.time &&
            direction == other.direction &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = time.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class SerialSession(
    val id: String,
    val name: String,
    val port: SerialPortDescriptor,
    val serialConfig: SerialConnectionConfig,
    val receiveConfig: ReceiveConfig = ReceiveConfig(),
    val sendConfig: SendConfig = SendConfig(),
    val lineState: SerialLineState = SerialLineState(),
    val statistics: SerialStatistics = SerialStatistics(),
    val messages: MutableList<SerialMessage> = mutableListOf(),
    var status: ConnectionStatus = ConnectionStatus.Disconnected,
    var messagePaused: Boolean = false,
)
