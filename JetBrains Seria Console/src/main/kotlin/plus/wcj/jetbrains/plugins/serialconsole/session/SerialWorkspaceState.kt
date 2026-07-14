package plus.wcj.jetbrains.plugins.serialconsole.session

class SerialWorkspaceState {
    var language: String = "English"
    var customPorts: MutableList<SerialPortState> = mutableListOf()
    var portSettings: MutableList<SerialPortSettingsState> = mutableListOf()
}

class SerialPortState {
    var name: String = ""
    var description: String = "Custom"
    var path: String = ""
    var identityPath: String = ""
}

class SerialPortSettingsState {
    var key: String = ""
    var baudRate: Int = 115200
    var dataBits: Int = 8
    var stopBits: String = "One"
    var parity: String = "None"
    var flowControl: String = "None"
    var receiveTextEncoding: String = "HEX"
    var receiveTextColor: Int = 0x56A8F5
    var packetMode: String = "None"
    var packetTimeoutMs: Int = 20
    var showTimestamp: Boolean = true
    var sendTextEncoding: String = "HEX"
    var sendTextColor: Int = 0x6AAB73
    var appendMode: String = "None"
    var crcAlgorithm: String = "None"
    var crcByteOrder: String = "BigEndian"
    var appendCrcAtEnd: Boolean = true
}
