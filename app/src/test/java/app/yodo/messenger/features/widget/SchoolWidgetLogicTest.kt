package app.yodo.messenger.features.widget

import app.yodo.messenger.features.school.SchoolData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class SchoolWidgetLogicTest {
    // 2026-10-19 — понедельник, 2026-10-24 — суббота.
    private fun at(day: Int, h: Int, m: Int, s: Int = 0) = LocalDateTime.of(2026, 10, day, h, m, s)

    @Test fun `during a lesson shows number and end time`() {
        val st = buildSchoolWidgetState(at(19, 9, 40))
        assertEquals(SchoolPhase.IN_LESSON, st.phase)
        assertEquals(2, st.lessonNumber)
        assertEquals("10:10", st.untilTime)
        assertEquals(6, st.lessonsLeft)
    }

    @Test fun `between lessons shows break and next lesson start`() {
        val st = buildSchoolWidgetState(at(19, 10, 15))
        assertEquals(SchoolPhase.BREAK, st.phase)
        assertEquals(3, st.lessonNumber)
        assertEquals("10:30", st.untilTime)
    }

    @Test fun `before first bell`() {
        val st = buildSchoolWidgetState(at(19, 7, 50))
        assertEquals(SchoolPhase.BEFORE_SCHOOL, st.phase)
        assertEquals("8:30", st.untilTime)
        assertEquals(7, st.lessonsLeft)
    }

    @Test fun `after the last lesson`() {
        val st = buildSchoolWidgetState(at(19, 16, 0))
        assertEquals(SchoolPhase.AFTER_SCHOOL, st.phase)
        assertEquals(0, st.lessonsLeft)
    }

    @Test fun `weekend is a day off and points to monday`() {
        val st = buildSchoolWidgetState(at(24, 10, 0))
        assertEquals(SchoolPhase.DAY_OFF, st.phase)
        assertEquals(DayOfWeek.MONDAY, st.nextSchoolDay)
    }

    @Test fun `next school day skips the weekend`() {
        assertEquals(DayOfWeek.MONDAY, nextSchoolDay(DayOfWeek.FRIDAY))
        assertEquals(DayOfWeek.TUESDAY, nextSchoolDay(DayOfWeek.MONDAY))
    }

    @Test fun `blank admin holiday date falls back to the bundled one`() {
        val day = at(19, 12, 0).toLocalDate()
        assertEquals(holidayInfo(day, SchoolData.HOLIDAY_DATE_ISO), buildSchoolWidgetState(at(19, 12, 0)).holiday)
        assertEquals(holidayInfo(day, SchoolData.HOLIDAY_DATE_ISO), holidayInfo(day, ""))
    }

    @Test fun `admin holiday date wins and edge cases`() {
        assertEquals(HolidayInfo.InDays(1), holidayInfo(at(19, 9, 0).toLocalDate(), "2026-10-20"))
        assertEquals(HolidayInfo.Today, holidayInfo(at(19, 9, 0).toLocalDate(), "2026-10-19"))
        assertEquals(HolidayInfo.Ongoing, holidayInfo(at(19, 9, 0).toLocalDate(), "2026-10-18"))
        assertEquals(HolidayInfo.Unknown, holidayInfo(at(19, 9, 0).toLocalDate(), "не дата"))
    }

    @Test fun `next tick is the nearest bell plus five seconds`() {
        assertEquals(at(19, 9, 15, 5), nextSchoolTick(at(19, 8, 40)))
        assertEquals(at(19, 8, 30, 5), nextSchoolTick(at(19, 7, 0)))
        // На самом звонке берём следующий, а не тот же.
        assertEquals(at(19, 9, 25, 5), nextSchoolTick(at(19, 9, 15)))
    }

    @Test fun `after school and on weekends tick goes to just after midnight`() {
        assertEquals(at(20, 0, 1), nextSchoolTick(at(19, 16, 0)))
        assertEquals(at(25, 0, 1), nextSchoolTick(at(24, 10, 0)))
    }

    @Test fun `tick delay is never shorter than thirty seconds`() {
        assertTrue(millisUntilNextSchoolTick(at(19, 9, 14, 59)) >= 30_000L)
    }

    @Test fun `state flips exactly at the tick moment`() {
        val tick = nextSchoolTick(at(19, 9, 0)) // 9:15:05
        assertEquals(SchoolPhase.BREAK, buildSchoolWidgetState(tick).phase)
        val start = nextSchoolTick(at(19, 10, 0)) // 10:10:05
        assertEquals(SchoolPhase.BREAK, buildSchoolWidgetState(start).phase)
        val bell = nextSchoolTick(at(19, 7, 0)) // 8:30:05
        assertEquals(SchoolPhase.IN_LESSON, buildSchoolWidgetState(bell).phase)
    }
}
