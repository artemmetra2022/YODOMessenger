package app.yodo.messenger.features.widget

import app.yodo.messenger.features.weather.WeatherBundle
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

// ───────────────────────── Непрочитанные ─────────────────────────

/** Один чат в списке виджета. Текст сообщений сюда НЕ попадает — только название и счётчик. */
@Serializable
data class UnreadItem(val chatId: String, val title: String, val unread: Int)

@Serializable
data class UnreadSnapshot(
    val uid: String = "",
    val total: Int = 0,
    val chatsWithUnread: Int = 0,
    val items: List<UnreadItem> = emptyList(),
    val updatedAt: Long = 0L
)

/** Минимум полей чата, нужный виджету (общий для Firestore-запроса и тестов). */
data class RawChatUnread(
    val chatId: String,
    val title: String,
    val unread: Int,
    val muted: Boolean,
    val archived: Boolean,
    val lastTimestamp: Long
)

const val WIDGET_MAX_ITEMS = 8

/**
 * Считает данные виджета так же, как значок в приложении:
 *  - беззвучные и архивные чаты не входят в счётчик;
 *  - скрытые чаты (PIN-папка) НИКОГДА не попадают на рабочий стол;
 *  - в списке — чаты с непрочитанными, самые свежие сверху.
 */
fun buildUnreadSnapshot(
    uid: String,
    chats: List<RawChatUnread>,
    hiddenChatIds: Set<String>,
    now: Long = System.currentTimeMillis()
): UnreadSnapshot {
    val counted = chats.filter {
        it.unread > 0 && !it.muted && !it.archived && it.chatId !in hiddenChatIds
    }
    val total = counted.fold(0L) { acc, chat -> acc + chat.unread }
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val items = counted
        .sortedByDescending { it.lastTimestamp }
        .take(WIDGET_MAX_ITEMS)
        .map { UnreadItem(it.chatId, it.title.ifBlank { "—" }, it.unread) }
    return UnreadSnapshot(uid, total, counted.size, items, now)
}

fun badgeText(count: Int, cap: Int = 99): String = if (count > cap) "$cap+" else count.toString()

// ───────────────────────── Погода ─────────────────────────

@Serializable
data class HourPoint(val time: Long, val temp: Int, val symbol: String)

@Serializable
data class WeatherSnapshot(
    val city: String = "",
    val temp: Int = 0,
    val feelsLike: Int = 0,
    val symbol: String = "cloudy",
    val description: String = "",
    val humidity: Int = 0,
    val windSpeed: Int = 0,
    val tempMin: Int = 0,
    val tempMax: Int = 0,
    val precipProbability: Int? = null,
    val hourly: List<HourPoint> = emptyList(),
    val updatedAt: Long = 0L
)

fun formatTemp(value: Int): String = "$value°"

/** Сжимает полный ответ met.no до того, что нужно виджету (текущая погода, диапазон за сутки, 6 ближайших часов). */
fun WeatherBundle.toWidgetSnapshot(nowMillis: Long = System.currentTimeMillis()): WeatherSnapshot {
    val nowSec = nowMillis / 1000
    val cur = current
    val symbol = cur.weather.firstOrNull()?.symbol ?: "cloudy"
    val sorted = forecast.list.sortedBy { it.dt }
    val dayTemps = sorted.filter { it.dt in (nowSec - 3600)..(nowSec + 24 * 3600) }
        .map { it.main.temp } + cur.main.temp
    val hourly = sorted.filter { it.dt > nowSec }.take(6).map {
        HourPoint(it.dt, it.main.temp.roundToInt(), it.weather.firstOrNull()?.symbol ?: "cloudy")
    }
    return WeatherSnapshot(
        city = cur.name,
        temp = cur.main.temp.roundToInt(),
        feelsLike = cur.main.feelsLike.roundToInt(),
        symbol = symbol,
        description = cur.weather.firstOrNull()?.description.orEmpty(),
        humidity = cur.main.humidity,
        windSpeed = cur.wind.speed.roundToInt(),
        tempMin = dayTemps.min().roundToInt(),
        tempMax = dayTemps.max().roundToInt(),
        precipProbability = cur.precipProbability,
        hourly = hourly,
        updatedAt = nowMillis
    )
}
