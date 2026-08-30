package app.yodo.messenger.offline

import org.json.JSONArray
import org.json.JSONObject

private const val MEDIA_CHUNK_SIZE = 16_000
const val MAX_OFFLINE_MEDIA_BYTES = 2_200_000
const val MAX_OFFLINE_MEDIA_BASE64_LENGTH = 3_000_000

enum class OfflineMediaType { PHOTO, ALBUM, AUDIO }

data class OfflineMediaPayload(
    val type: OfflineMediaType,
    val itemsBase64: List<String>,
    val durationMs: Long = 0L
)

data class OfflineMediaMeta(
    val transferId: String,
    val type: OfflineMediaType,
    val chunkCount: Int,
    val durationMs: Long
)

object OfflineMediaCodec {
    fun encode(payload: OfflineMediaPayload): String {
        val items = JSONArray()
        payload.itemsBase64.forEach(items::put)
        return JSONObject()
            .put("type", payload.type.name)
            .put("items", items)
            .put("duration", payload.durationMs)
            .toString()
    }

    fun decode(raw: String): OfflineMediaPayload? = try {
        val json = JSONObject(raw)
        val itemsJson = json.getJSONArray("items")
        val items = List(itemsJson.length()) { index -> itemsJson.getString(index) }
        OfflineMediaPayload(
            type = OfflineMediaType.valueOf(json.getString("type")),
            itemsBase64 = items,
            durationMs = json.optLong("duration")
        )
    } catch (_: Exception) {
        null
    }

    fun chunks(encoded: String): List<String> = encoded.chunked(MEDIA_CHUNK_SIZE)

    fun metaToJson(meta: OfflineMediaMeta): String = JSONObject()
        .put("type", meta.type.name)
        .put("chunks", meta.chunkCount)
        .put("duration", meta.durationMs)
        .toString()

    fun metaFromPacket(packet: MeshPacket): OfflineMediaMeta? = try {
        val json = JSONObject(packet.text)
        OfflineMediaMeta(
            transferId = packet.packetId,
            type = OfflineMediaType.valueOf(json.getString("type")),
            chunkCount = json.getInt("chunks"),
            durationMs = json.optLong("duration")
        ).takeIf { it.chunkCount > 0 }
    } catch (_: Exception) {
        null
    }

    fun chunkText(index: Int, value: String): String = "$index:$value"

    fun parseChunk(text: String): Pair<Int, String>? {
        val separator = text.indexOf(':')
        if (separator <= 0) return null
        val index = text.substring(0, separator).toIntOrNull() ?: return null
        return index to text.substring(separator + 1)
    }
}

class OfflineMediaAssembler {
    private data class Transfer(
        var meta: OfflineMediaMeta? = null,
        val chunks: MutableMap<Int, String> = HashMap()
    )

    private val transfers = HashMap<String, Transfer>()

    @Synchronized
    fun addMeta(meta: OfflineMediaMeta): OfflineMediaPayload? {
        val transfer = transfers.getOrPut(meta.transferId) { Transfer() }
        transfer.meta = meta
        return assemble(meta.transferId, transfer)
    }

    @Synchronized
    fun addChunk(transferId: String, index: Int, value: String): OfflineMediaPayload? {
        val transfer = transfers.getOrPut(transferId) { Transfer() }
        transfer.chunks[index] = value
        return assemble(transferId, transfer)
    }

    private fun assemble(transferId: String, transfer: Transfer): OfflineMediaPayload? {
        val meta = transfer.meta ?: return null
        if (transfer.chunks.size < meta.chunkCount) return null
        val encoded = buildString {
            repeat(meta.chunkCount) { index -> append(transfer.chunks[index] ?: return null) }
        }
        val payload = OfflineMediaCodec.decode(encoded) ?: return null
        transfers.remove(transferId)
        return payload
    }

    @Synchronized
    fun clear() = transfers.clear()
}
