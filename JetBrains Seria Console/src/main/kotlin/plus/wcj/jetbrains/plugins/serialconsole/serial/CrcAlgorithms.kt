package plus.wcj.jetbrains.plugins.serialconsole.serial

import plus.wcj.crc.CRCModel
import plus.wcj.crc.bitwise.BitwiseBigCRC
import plus.wcj.jetbrains.plugins.serialconsole.model.ByteOrderMode

object CrcAlgorithms {
    const val NONE_ID = "None"

    fun all(): List<CrcAlgorithm> {
        return listOf(CrcAlgorithm(NONE_ID, "None", null)) + CRCModel.values.flatMap { model ->
            val names = model.names.toList()
            val displayNames = names.ifEmpty { listOf(model.toString()) }
            displayNames.map { name -> CrcAlgorithm(name, name, model) }
        }
    }

    fun calculate(bytes: ByteArray, algorithmId: String, byteOrder: ByteOrderMode): ByteArray {
        if (algorithmId == NONE_ID) return byteArrayOf()

        val model = find(algorithmId)?.model ?: return byteArrayOf()
        return BitwiseBigCRC(model).array(bytes, byteOrder == ByteOrderMode.BigEndian)
    }

    fun find(algorithmId: String): CrcAlgorithm? {
        val normalized = normalize(algorithmId)
        return all().firstOrNull { normalize(it.id) == normalized }
            ?: all().firstOrNull { normalize(it.label).contains(normalized) }
            ?: all().firstOrNull { normalized.contains(normalize(it.id)) }
    }

    private fun normalize(value: String): String {
        return value.replace(Regex("""[^A-Za-z0-9]"""), "").uppercase()
    }
}

data class CrcAlgorithm(
    val id: String,
    val label: String,
    val model: CRCModel<*>?,
)
