package app.yodo.messenger.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Валидация и форматирование даты рождения.
 * Формат хранения — "дд.мм.гггг" (строка, как и раньше в Firestore),
 * но теперь значение всегда приходит из DatePicker'а, а не свободного ввода.
 */
object BirthDateValidator {

    const val DISPLAY_PATTERN = "dd.MM.yyyy"
    private const val MIN_AGE_YEARS = 13
    private const val MAX_AGE_YEARS = 120

    private fun formatter(): SimpleDateFormat =
        SimpleDateFormat(DISPLAY_PATTERN, Locale.getDefault()).apply {
            isLenient = false
        }

    /**
     * Проверяет строку "дд.мм.гггг". Возвращает сообщение об ошибке
     * (для показа пользователю) или null, если дата корректна.
     */
    fun validate(dateText: String): String? {
        val trimmed = dateText.trim()
        if (trimmed.isBlank()) return "Введите дату рождения"

        if (!trimmed.matches(Regex("^\\d{2}\\.\\d{2}\\.\\d{4}$"))) {
            return "Дата должна быть в формате дд.мм.гггг"
        }

        val parsed = try {
            formatter().parse(trimmed)
        } catch (e: Exception) {
            null
        } ?: return "Некорректная дата (например, 31 февраля не существует)"

        return validateMillis(parsed.time)
    }

    /** Та же валидация, но по timestamp'у из Material3 DatePicker (millis, UTC-полночь). */
    fun validateMillis(millisUtc: Long): String? {
        val today = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millisUtc }

        if (picked.after(today)) {
            return "Дата рождения не может быть в будущем"
        }

        val minAllowedDate = (today.clone() as Calendar).apply { add(Calendar.YEAR, -MAX_AGE_YEARS) }
        if (picked.before(minAllowedDate)) {
            return "Проверьте год рождения — указана слишком ранняя дата"
        }

        val maxAllowedDate = (today.clone() as Calendar).apply { add(Calendar.YEAR, -MIN_AGE_YEARS) }
        if (picked.after(maxAllowedDate)) {
            return "Минимальный возраст для регистрации — $MIN_AGE_YEARS лет"
        }

        return null
    }

    /** Форматирует millis из DatePicker'а (UTC) в строку "дд.мм.гггг" для отображения и хранения. */
    fun millisToDisplayString(millisUtc: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millisUtc }
        val formatterUtc = SimpleDateFormat(DISPLAY_PATTERN, Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatterUtc.format(calendar.time)
    }

    /** Парсит строку "дд.мм.гггг" обратно в millis (UTC-полночь) — нужно для предзаполнения DatePicker'а. */
    fun displayStringToMillis(dateText: String): Long? {
        val trimmed = dateText.trim()
        if (!trimmed.matches(Regex("^\\d{2}\\.\\d{2}\\.\\d{4}$"))) return null
        return try {
            val formatterUtc = SimpleDateFormat(DISPLAY_PATTERN, Locale.getDefault()).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            formatterUtc.parse(trimmed)?.time
        } catch (e: Exception) {
            null
        }
    }
}
