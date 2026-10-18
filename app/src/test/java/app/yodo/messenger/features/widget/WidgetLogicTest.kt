package app.yodo.messenger.features.widget

import app.yodo.messenger.features.weather.CurrentWeatherResponse
import app.yodo.messenger.features.weather.ForecastItem
import app.yodo.messenger.features.weather.ForecastResponse
import app.yodo.messenger.features.weather.WeatherBundle
import app.yodo.messenger.features.weather.WeatherCondition
import app.yodo.messenger.features.weather.WeatherMain
import app.yodo.messenger.features.weather.WeatherWind
import org.junit.Assert.*
import org.junit.Test

class WidgetLogicTest {
    private fun chat(id: String, unread: Int, ts: Long = 1L, muted: Boolean = false, archived: Boolean = false) =
        RawChatUnread(id, "Чат $id", unread, muted, archived, ts)

    @Test fun `muted archived and hidden chats are not counted`() {
        val snap = buildUnreadSnapshot(
            "me",
            listOf(chat("a", 2), chat("b", 5, muted = true), chat("c", 7, archived = true), chat("d", 9)),
            hiddenChatIds = setOf("d")
        )
        assertEquals(2, snap.total)
        assertEquals(1, snap.chatsWithUnread)
        assertEquals(listOf("a"), snap.items.map { it.chatId })
    }

    @Test fun `hidden chat title never reaches the widget cache`() {
        val snap = buildUnreadSnapshot("me", listOf(chat("secret", 3)), setOf("secret"))
        assertTrue(snap.items.isEmpty())
        assertEquals(0, snap.total)
    }

    @Test fun `list is newest first and capped`() {
        val chats = (1..12).map { chat("c$it", 1, ts = it.toLong()) }
        val snap = buildUnreadSnapshot("me", chats, emptySet())
        assertEquals(12, snap.total)
        assertEquals(WIDGET_MAX_ITEMS, snap.items.size)
        assertEquals("c12", snap.items.first().chatId)
    }

    @Test fun `total never overflows`() {
        val snap = buildUnreadSnapshot("me", listOf(chat("a", Int.MAX_VALUE), chat("b", Int.MAX_VALUE)), emptySet())
        assertEquals(Int.MAX_VALUE, snap.total)
    }

    @Test fun `badge text is capped`() {
        assertEquals("5", badgeText(5))
        assertEquals("99", badgeText(99))
        assertEquals("99+", badgeText(100))
    }

    private fun main(t: Double) = WeatherMain(temp = t, feelsLike = t - 2, humidity = 70)

    @Test fun `weather snapshot keeps range for next day and six hours`() {
        val now = 1_000_000L * 1000
        val nowSec = now / 1000
        val forecast = (1..30).map {
            ForecastItem(nowSec + it * 3600L, main(10.0 + it), listOf(WeatherCondition("rain", "дождь")))
        }
        val bundle = WeatherBundle(
            current = CurrentWeatherResponse(
                name = "Москва", weather = listOf(WeatherCondition("clearsky_day", "ясно")),
                main = main(9.6), wind = WeatherWind(speed = 3.4), dt = nowSec, precipProbability = 20
            ),
            forecast = ForecastResponse(forecast)
        )
        val s = bundle.toWidgetSnapshot(now)
        assertEquals("Москва", s.city)
        assertEquals(10, s.temp)
        assertEquals(3, s.windSpeed)
        assertEquals(6, s.hourly.size)
        assertEquals(11, s.hourly.first().temp)
        assertEquals(10, s.tempMin) // минимум — текущая 9.6 → 10
        assertEquals(34, s.tempMax) // 24 часа вперёд: 10 + 24
    }
}
