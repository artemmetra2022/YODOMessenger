package app.yodo.messenger.features.canvas

import java.security.SecureRandom
import java.util.TreeMap

/** Код приглашения на онлайн-доску: 8 символов без похожих (нет 0/O, 1/I). ~40 бит, живёт 10 минут и одноразовый. */
object CanvasInviteCode {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 8
    fun generate(random: SecureRandom = SecureRandom()): String =
        String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })
    /** Убирает пробелы и дефисы, приводит к верхнему регистру. Возвращает null, если это не код. */
    fun normalize(raw: String): String? {
        val clean = raw.uppercase().filter { it.isLetterOrDigit() }
        return if (clean.length == LENGTH && clean.all { it in ALPHABET }) clean else null
    }
    fun display(code: String): String = code.chunked(4).joinToString("-")
    /** Идентификаторы пакетов — 10 цифр с нулями слева, чтобы Firestore сортировал их по порядку отправки. */
    fun packetId(n: Long): String { require(n in 1..9_999_999_999L); return n.toString().padStart(10, '0') }
    fun packetNumber(id: String): Long? = if (id.length == 10 && id.all { it in '0'..'9' }) id.toLong().takeIf { it >= 1 } else null
}

/**
 * Возвращает пакеты строго по порядку номеров 1, 2, 3… даже если Firestore доставил их вперемешку.
 * Дубликаты и уже обработанные номера игнорируются; «дыра» дольше лимита считается потерей данных.
 */
class CanvasPacketReorder(private val maxBuffered: Int = 600) {
    private var expected = 1L
    private val pending = TreeMap<Long, ByteArray>()
    private var gapSince: Long? = null
    val nextExpected: Long get() = expected
    fun accept(number: Long, bytes: ByteArray, nowMs: Long): List<ByteArray> {
        require(number >= 1) { "Некорректный номер пакета" }
        if (number < expected || pending.containsKey(number)) return emptyList()
        require(pending.size < maxBuffered) { "Слишком много пакетов вне очереди" }
        pending[number] = bytes
        val out = mutableListOf<ByteArray>()
        while (pending.isNotEmpty() && pending.firstKey() == expected) { out.add(pending.remove(pending.firstKey())!!); expected++ }
        gapSince = when {
            pending.isEmpty() -> null
            out.isNotEmpty() -> nowMs
            else -> gapSince ?: nowMs
        }
        return out
    }
    fun gapExceeded(nowMs: Long, limitMs: Long): Boolean = gapSince?.let { nowMs - it > limitMs } == true
}
