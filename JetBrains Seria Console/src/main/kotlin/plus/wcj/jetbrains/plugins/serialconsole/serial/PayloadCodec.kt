package plus.wcj.jetbrains.plugins.serialconsole.serial

import plus.wcj.jetbrains.plugins.serialconsole.model.AppendMode
import plus.wcj.jetbrains.plugins.serialconsole.model.SendConfig
import java.nio.charset.Charset

object PayloadCodec {
    const val HEX_ENCODING = "HEX"

    fun encode(payload: String, config: SendConfig): ByteArray {
        val body = payloadBytes(payload, config)
        val crc = calculateCrc(payload, config)
        val eol = appendBytes(config)
        return if (config.appendCrcAtEnd) body + crc + eol else crc + body + eol
    }

    fun calculateCrc(payload: String, config: SendConfig): ByteArray {
        return CrcAlgorithms.calculate(payloadBytes(payload, config), config.crcAlgorithm, config.crcByteOrder)
    }

    fun toHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { value -> "%02X".format(value.toInt() and 0xFF) }
    }

    fun normalizeHexInput(input: String): String {
        if (!isHexInput(input)) {
            return input.toByteArray(Charsets.UTF_8).toHexString()
        }
        return compactHexInput(input)
    }

    private fun payloadBytes(payload: String, config: SendConfig): ByteArray {
        if (config.textEncoding.equals(HEX_ENCODING, ignoreCase = true)) {
            return parseHex(payload)
        }
        return payload.toByteArray(Charset.forName(config.textEncoding))
    }

    private fun parseHex(input: String): ByteArray {
        val normalized = normalizeHexInput(input)
        require(normalized.length % 2 == 0) { "HEX payload must contain complete bytes." }
        return normalized.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun isHexInput(input: String): Boolean {
        val normalized = input.replace(Regex("""0x""", RegexOption.IGNORE_CASE), "")
        return normalized.all { it.isHexDigit() || it.isHexSeparator() }
    }

    private fun compactHexInput(input: String): String {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        val normalized = input.replace(Regex("""0x""", RegexOption.IGNORE_CASE), "")

        fun appendCurrentToken() {
            if (current.isEmpty()) return
            tokens += current.toString()
            current.clear()
        }

        normalized.forEach { char ->
            when {
                char.isHexDigit() -> current.append(char.uppercaseChar())
                char.isHexSeparator() -> appendCurrentToken()
            }
        }
        appendCurrentToken()

        return tokens.joinToString("") { token ->
            if (token.length % 2 == 0) token else "0$token"
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }

    private fun appendBytes(config: SendConfig): ByteArray {
        return when (config.appendMode) {
            AppendMode.None -> byteArrayOf()
            AppendMode.Cr -> byteArrayOf('\r'.code.toByte())
            AppendMode.Lf -> byteArrayOf('\n'.code.toByte())
            AppendMode.Crlf -> byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
        }
    }

    private fun Char.isHexDigit(): Boolean {
        return this in '0'..'9' || this in 'A'..'F' || this in 'a'..'f'
    }

    private fun Char.isHexSeparator(): Boolean {
        return isWhitespace() || this in charArrayOf(',', ';', ':', '_', '-', '|')
    }

}
