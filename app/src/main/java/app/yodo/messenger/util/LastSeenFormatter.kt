package app.yodo.messenger.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Единый формат подписи "был(а) ..." (вставляется после слов "был(а) ").
 *
 * Правила:
 *  - меньше минуты        -> "недавно" (без секунд, чтобы не мигало 10, 11, 12 сек);
 *  - 1-4 минуты           -> "несколько минут назад";
 *  - 5-59 минут           -> округление вниз до 5 минут: "5 мин назад", "10 мин назад"...;
 *  - до суток             -> "N ч назад";
 *  - до недели            -> "Вторник, 14:26";
 *  - дальше               -> "5 окт, 14:26".
 */
object LastSeenFormatter {

    fun format(millis: Long, now: Long = System.currentTimeMillis()): String {
        val diffMinutes = ((now - millis).coerceAtLeast(0L)) / 60_000L
        return when {
            diffMinutes < 1 -> "недавно"
            diffMinutes < 5 -> "несколько минут назад"
            diffMinutes < 60 -> "${(diffMinutes / 5) * 5} мин назад"
            diffMinutes < 24 * 60 -> "${diffMinutes / 60} ч назад"
            diffMinutes < 7 * 24 * 60 ->
                SimpleDateFormat("EEEE, HH:mm", Locale("ru")).format(Date(millis))
                    .replaceFirstChar { it.uppercase() }
            else -> SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))
        }
    }
}
