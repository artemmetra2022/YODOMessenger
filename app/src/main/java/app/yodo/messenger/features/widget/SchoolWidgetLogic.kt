package app.yodo.messenger.features.widget

import app.yodo.messenger.features.school.LessonStatus
import app.yodo.messenger.features.school.SchoolData
import app.yodo.messenger.features.school.currentLessonStatus
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Логика виджета «Школа». Чистые функции без Android-зависимостей — их можно тестировать
 * обычными юнит-тестами. Данные берутся из [SchoolData] (расписание звонков) и из даты каникул,
 * которую админ задаёт в config/appSettings (пустая строка — зашитая SchoolData.HOLIDAY_DATE_ISO).
 *
 * Личных данных здесь нет вообще: ни имени, ни класса, ни чатов.
 */

/** Учебные дни недели. Если гимназия учится и в субботу — добавьте DayOfWeek.SATURDAY. */
internal val SCHOOL_DAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
)

enum class SchoolPhase { IN_LESSON, BREAK, BEFORE_SCHOOL, AFTER_SCHOOL, DAY_OFF }

sealed interface HolidayInfo {
    /** Дата не задана или не разобралась. */
    data object Unknown : HolidayInfo
    /** До каникул осталось [days] дней (всегда ≥ 1). */
    data class InDays(val days: Int) : HolidayInfo
    /** Каникулы начинаются сегодня. */
    data object Today : HolidayInfo
    /** Дата каникул уже прошла. */
    data object Ongoing : HolidayInfo
}

/**
 * @property lessonNumber текущий урок (IN_LESSON) или ближайший (BREAK, BEFORE_SCHOOL); 0 — нет.
 * @property untilTime «ЧЧ:ММ»: когда закончится урок (IN_LESSON) или начнётся ближайший (BREAK, BEFORE_SCHOOL).
 * @property lessonsLeft сколько уроков ещё не закончилось сегодня (текущий считается).
 * @property nextSchoolDay для DAY_OFF — ближайший учебный день недели.
 */
data class SchoolWidgetState(
    val phase: SchoolPhase,
    val lessonNumber: Int,
    val untilTime: String,
    val lessonsLeft: Int,
    val nextSchoolDay: DayOfWeek?,
    val holiday: HolidayInfo
)

internal fun clockText(minutesOfDay: Int): String =
    "${minutesOfDay / 60}:${(minutesOfDay % 60).toString().padStart(2, '0')}"

internal fun nextSchoolDay(from: DayOfWeek): DayOfWeek {
    for (shift in 1L..7L) {
        val candidate = from.plus(shift)
        if (candidate in SCHOOL_DAYS) return candidate
    }
    return DayOfWeek.MONDAY
}

internal fun holidayInfo(today: LocalDate, adminHolidayIso: String): HolidayInfo {
    val date = runCatching { LocalDate.parse(adminHolidayIso.ifBlank { SchoolData.HOLIDAY_DATE_ISO }) }
        .getOrNull() ?: return HolidayInfo.Unknown
    return when {
        today.isAfter(date) -> HolidayInfo.Ongoing
        today.isEqual(date) -> HolidayInfo.Today
        else -> HolidayInfo.InDays(ChronoUnit.DAYS.between(today, date).toInt())
    }
}

fun buildSchoolWidgetState(now: LocalDateTime, adminHolidayIso: String = ""): SchoolWidgetState {
    val holiday = holidayInfo(now.toLocalDate(), adminHolidayIso)
    if (now.dayOfWeek !in SCHOOL_DAYS) {
        return SchoolWidgetState(SchoolPhase.DAY_OFF, 0, "", 0, nextSchoolDay(now.dayOfWeek), holiday)
    }
    val nowMin = now.hour * 60 + now.minute
    val lessonsLeft = SchoolData.BELL_SCHEDULE.count { (_, time) ->
        SchoolData.parseBellRange(time)?.let { it.second > nowMin } == true
    }
    return when (val status = currentLessonStatus(now.hour, now.minute)) {
        is LessonStatus.InLesson -> SchoolWidgetState(
            SchoolPhase.IN_LESSON, status.number, clockText(nowMin + status.minutesLeft), lessonsLeft, null, holiday
        )
        is LessonStatus.Break -> SchoolWidgetState(
            SchoolPhase.BREAK, status.beforeLesson, clockText(nowMin + status.minutesLeft), lessonsLeft, null, holiday
        )
        is LessonStatus.BeforeSchool -> SchoolWidgetState(
            SchoolPhase.BEFORE_SCHOOL, 1, clockText(nowMin + status.minutesLeft), lessonsLeft, null, holiday
        )
        LessonStatus.AfterSchool -> SchoolWidgetState(
            SchoolPhase.AFTER_SCHOOL, 0, "", 0, null, holiday
        )
    }
}

/**
 * Когда в следующий раз нужно перерисовать виджет: ближайший звонок (начало или конец урока)
 * плюс 5 секунд, чтобы минутный расчёт уже показал новое состояние. После последнего звонка
 * и в выходные — сразу после полуночи (смена дня, счётчик каникул).
 */
fun nextSchoolTick(now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate()
    if (now.dayOfWeek in SCHOOL_DAYS) {
        val nowMin = now.hour * 60 + now.minute
        val next = SchoolData.BELL_SCHEDULE
            .mapNotNull { (_, time) -> SchoolData.parseBellRange(time) }
            .flatMap { listOf(it.first, it.second) }
            .filter { it > nowMin }
            .minOrNull()
        if (next != null) return today.atTime(next / 60, next % 60, 5)
    }
    return today.plusDays(1).atTime(0, 1)
}

fun millisUntilNextSchoolTick(now: LocalDateTime): Long =
    Duration.between(now, nextSchoolTick(now)).toMillis().coerceAtLeast(30_000L)
