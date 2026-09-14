package app.yodo.messenger.offline

import org.json.JSONArray
import org.json.JSONObject

/**
 * НОВОЕ (mesh-маршрутизация).
 *
 * Ядро mesh-протокола поверх Nearby Connections. Сообщение «прыгает» через
 * промежуточные узлы (multi-hop), что позволяет доставлять его адресатам вне
 * прямого радиорадиуса.
 *
 * Алгоритм: flooding с TTL и дедупликацией по packetId; обучение обратного
 * маршрута; unicast по таблице маршрутов; подтверждение доставки (ACK).
 */

/** Тип mesh-пакета. */
enum class MeshPacketType { HELLO, MSG, ACK }

/**
 * Пакет mesh-сети. Сериализуется в компактный JSON.
 *
 * @param srcShort шестизначный номер отправителя (для адресации конкретному человеку)
 * @param dstId адресат; [BROADCAST] для широковещательных сообщений
 * @param ttl оставшееся число прыжков (0 — пакет отбрасывается)
 * @param hops сколько прыжков уже пройдено
 * @param path путь пакета (для защиты от петель)
 * @param refId для ACK — packetId исходного сообщения
 */
data class MeshPacket(
    val type: MeshPacketType,
    val packetId: String,
    val srcId: String,
    val srcName: String,
    val srcShort: String,
    val dstId: String,
    val ttl: Int,
    val hops: Int,
    val text: String,
    val path: List<String>,
    val refId: String,
    val originTimestamp: Long
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("t", type.name)
        o.put("id", packetId)
        o.put("s", srcId)
        o.put("sn", srcName)
        o.put("ss", srcShort)
        o.put("d", dstId)
        o.put("ttl", ttl)
        o.put("h", hops)
        o.put("x", text)
        o.put("r", refId)
        o.put("ts", originTimestamp)
        val arr = JSONArray()
        path.forEach { arr.put(it) }
        o.put("p", arr)
        return o.toString()
    }

    fun toBytes(): ByteArray = toJson().toByteArray(Charsets.UTF_8)

    companion object {
        const val BROADCAST = "*"
        const val DEFAULT_TTL = 8
        const val BEACON_TTL = 6

        fun fromJson(s: String): MeshPacket? = try {
            val o = JSONObject(s)
            val arr = o.optJSONArray("p") ?: JSONArray()
            val path = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) path.add(arr.getString(i))
            MeshPacket(
                type = MeshPacketType.valueOf(o.getString("t")),
                packetId = o.getString("id"),
                srcId = o.getString("s"),
                srcName = o.optString("sn"),
                srcShort = o.optString("ss"),
                dstId = o.optString("d", BROADCAST),
                ttl = o.optInt("ttl", DEFAULT_TTL),
                hops = o.optInt("h", 0),
                text = o.optString("x"),
                path = path,
                refId = o.optString("r"),
                originTimestamp = o.optLong("ts", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Запись таблицы маршрутов: как доставить пакет до узла. */
data class MeshRoute(
    val nextHopEndpoint: String,
    val hopCount: Int,
    val updatedAt: Long
)

/**
 * Состояние mesh-маршрутизатора: кэш увиденных пакетов (дедупликация)
 * и таблица маршрутов. Потокобезопасен (все методы synchronized).
 */
class MeshRouter(val myNodeId: String) {

    private val seen = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean = size > 1024
    }
    private val routes = HashMap<String, MeshRoute>()
    private val nodeNames = HashMap<String, String>()
    private val nodeShorts = HashMap<String, String>()

    @Synchronized
    fun isDuplicate(packetId: String): Boolean {
        if (seen.containsKey(packetId)) return true
        seen[packetId] = System.currentTimeMillis()
        return false
    }

    @Synchronized
    fun markSeen(packetId: String) {
        seen[packetId] = System.currentTimeMillis()
    }

    /** Обучение маршрута: узел [dstNodeId] достижим через [viaEndpoint] за [hopCount] прыжков. */
    @Synchronized
    fun learnRoute(dstNodeId: String, name: String, short: String, viaEndpoint: String, hopCount: Int) {
        if (dstNodeId.isBlank() || dstNodeId == myNodeId) return
        if (name.isNotBlank()) nodeNames[dstNodeId] = name
        if (short.isNotBlank()) nodeShorts[dstNodeId] = short
        val existing = routes[dstNodeId]
        if (existing == null || hopCount <= existing.hopCount ||
            System.currentTimeMillis() - existing.updatedAt > 30_000L
        ) {
            routes[dstNodeId] = MeshRoute(viaEndpoint, hopCount, System.currentTimeMillis())
        }
    }

    @Synchronized
    fun nextHop(dstNodeId: String): String? = routes[dstNodeId]?.nextHopEndpoint

    @Synchronized
    fun removeEndpoint(endpointId: String) {
        val iterator = routes.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.nextHopEndpoint == endpointId) iterator.remove()
        }
    }

    @Synchronized
    fun nameOf(nodeId: String): String? = nodeNames[nodeId]

    @Synchronized
    fun shortOf(nodeId: String): String? = nodeShorts[nodeId]

    @Synchronized
    fun routesSnapshot(): Map<String, MeshRoute> = HashMap(routes)
}
