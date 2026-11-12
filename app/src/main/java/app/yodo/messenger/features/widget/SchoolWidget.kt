package app.yodo.messenger.features.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import app.yodo.messenger.R
import app.yodo.messenger.features.school.SchoolData
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Виджет «Школа»: какой сейчас урок и до скольки он идёт, перемена или нет занятий,
 * расписание звонков с подсветкой и дней до каникул.
 * 1×1 — номер урока; 2×1 — урок и время; 2×2 — плюс дни до каникул; 4×2 — плюс ближайшие 3 урока;
 * 4×4 — всё расписание звонков.
 *
 * Состояние считается в момент отрисовки по системным часам и расписанию звонков, сеть не нужна.
 * Перерисовку по звонкам делает [SchoolTicker].
 */
class SchoolWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val uid = WidgetEntryPoint.get(context).auth().currentUser?.uid
        val state = buildSchoolWidgetState(LocalDateTime.now(), WidgetStore.readSchoolHoliday(context))
        provideContent {
            GlanceTheme(colors = YodoWidgetColors) {
                SchoolContent(context, uid, state)
            }
        }
    }
}

private data class SchoolText(val emoji: String, val title: String, val subtitle: String)

private fun schoolText(context: Context, s: SchoolWidgetState): SchoolText = when (s.phase) {
    SchoolPhase.IN_LESSON -> SchoolText(
        "🟢",
        context.getString(R.string.widget_school_lesson, s.lessonNumber),
        context.getString(R.string.widget_school_until, s.untilTime)
    )
    SchoolPhase.BREAK -> SchoolText(
        "🟡",
        context.getString(R.string.widget_school_break),
        context.getString(R.string.widget_school_lesson_at, s.lessonNumber, s.untilTime)
    )
    SchoolPhase.BEFORE_SCHOOL -> SchoolText(
        "⏰",
        context.getString(R.string.widget_school_before),
        context.getString(R.string.widget_school_first_bell, s.untilTime)
    )
    SchoolPhase.AFTER_SCHOOL -> SchoolText(
        "🎉",
        context.getString(R.string.widget_school_after),
        context.getString(R.string.widget_school_rest)
    )
    SchoolPhase.DAY_OFF -> SchoolText(
        "🏠",
        context.getString(R.string.widget_school_day_off),
        context.getString(
            R.string.widget_school_next_lessons,
            (s.nextSchoolDay ?: java.time.DayOfWeek.MONDAY).getDisplayName(TextStyle.FULL, Locale.getDefault())
        )
    )
}

/** Строка про каникулы; null — дата не задана, ничего не показываем. */
private fun holidayLine(context: Context, info: HolidayInfo): String? = when (info) {
    HolidayInfo.Unknown -> null
    is HolidayInfo.InDays -> context.resources.getQuantityString(
        R.plurals.widget_school_holiday_in_days, info.days, info.days
    )
    HolidayInfo.Today -> context.getString(R.string.widget_school_holiday_today)
    HolidayInfo.Ongoing -> context.getString(R.string.widget_school_holiday_ongoing)
}

/** Для 1×1: эмодзи и одно короткое значение. */
private fun tinyContent(context: Context, s: SchoolWidgetState): Pair<String, String> = when (s.phase) {
    SchoolPhase.IN_LESSON -> "🟢" to s.lessonNumber.toString()
    SchoolPhase.BREAK -> "🟡" to s.untilTime
    SchoolPhase.BEFORE_SCHOOL -> "⏰" to s.untilTime
    SchoolPhase.AFTER_SCHOOL, SchoolPhase.DAY_OFF -> {
        val h = s.holiday
        if (h is HolidayInfo.InDays) "📅" to context.getString(R.string.widget_school_days_short, h.days)
        else "🎉" to ""
    }
}

/** Подсвечиваем текущий урок, а на перемене и до начала занятий — ближайший. */
private fun isHighlighted(s: SchoolWidgetState, lesson: Int): Boolean =
    (s.phase == SchoolPhase.IN_LESSON || s.phase == SchoolPhase.BREAK || s.phase == SchoolPhase.BEFORE_SCHOOL) &&
        s.lessonNumber == lesson

@Composable
private fun BellRow(context: Context, number: Int, time: String, highlighted: Boolean, size: Int) {
    val base = GlanceModifier.fillMaxWidth()
    val modifier = (if (highlighted) base.background(GlanceTheme.colors.primaryContainer).cornerRadius(8.dp) else base)
        .padding(horizontal = 6.dp, vertical = 1.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        WText(
            context.getString(R.string.widget_school_lesson, number), size,
            bold = highlighted, modifier = GlanceModifier.defaultWeight()
        )
        WText(time, size, bold = highlighted)
    }
}

@Composable
private fun SchoolContent(context: Context, uid: String?, s: SchoolWidgetState) {
    val layout = layoutFor(LocalSize.current)
    val open = WidgetIntents.open(context, WidgetIntents.TARGET_SCHOOL, uid)
    val text = schoolText(context, s)
    val holiday = holidayLine(context, s.holiday)
    WidgetRoot(open, padding = if (layout == WidgetLayout.TINY) 6.dp else 12.dp) {
        when (layout) {
            WidgetLayout.TINY -> {
                val (emoji, value) = tinyContent(context, s)
                Column(
                    GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WText(emoji, 20)
                    if (value.isNotEmpty()) WText(value, 18, bold = true)
                }
            }
            WidgetLayout.COMPACT -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                WText(text.emoji, 28)
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    WText(text.title, 16, bold = true)
                    WText(text.subtitle, 12, secondary = true)
                }
            }
            WidgetLayout.SQUARE -> Column(GlanceModifier.fillMaxSize()) {
                WText(context.getString(R.string.widget_school_label), 12, secondary = true)
                Spacer(GlanceModifier.defaultWeight())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WText(text.emoji, 26)
                    Spacer(GlanceModifier.width(6.dp))
                    WText(text.title, 22, bold = true)
                }
                WText(text.subtitle, 13, maxLines = 2)
                if (holiday != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    WText(holiday, 11, secondary = true, maxLines = 2)
                }
            }
            WidgetLayout.WIDE -> Row(GlanceModifier.fillMaxSize()) {
                Column(GlanceModifier.width(120.dp).fillMaxHeight()) {
                    WText(context.getString(R.string.widget_school_label), 12, secondary = true)
                    Spacer(GlanceModifier.defaultWeight())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WText(text.emoji, 24)
                        Spacer(GlanceModifier.width(6.dp))
                        WText(text.title, 20, bold = true)
                    }
                    WText(text.subtitle, 12, maxLines = 2)
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    val active = s.phase == SchoolPhase.IN_LESSON || s.phase == SchoolPhase.BREAK ||
                        s.phase == SchoolPhase.BEFORE_SCHOOL
                    if (active) {
                        // Окно из трёх уроков, начиная с текущего/ближайшего.
                        val first = (s.lessonNumber - 1).coerceIn(0, (SchoolData.BELL_SCHEDULE.size - 3).coerceAtLeast(0))
                        SchoolData.BELL_SCHEDULE.drop(first).take(3).forEachIndexed { i, (_, time) ->
                            BellRow(context, first + i + 1, time, isHighlighted(s, first + i + 1), 13)
                        }
                    } else if (holiday != null) {
                        WText(holiday, 14, bold = true, maxLines = 3)
                    }
                    if (active && holiday != null) {
                        Spacer(GlanceModifier.height(4.dp))
                        WText(holiday, 11, secondary = true, maxLines = 2)
                    }
                }
            }
            WidgetLayout.LARGE -> Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WText(text.emoji, 30)
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        WText(text.title, 18, bold = true)
                        WText(text.subtitle, 12, secondary = true)
                    }
                    if (s.lessonsLeft > 0) {
                        WText(context.getString(R.string.widget_school_lessons_left, s.lessonsLeft), 11, secondary = true)
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                SchoolData.BELL_SCHEDULE.forEachIndexed { i, (_, time) ->
                    BellRow(context, i + 1, time, isHighlighted(s, i + 1), 12)
                }
                Spacer(GlanceModifier.defaultWeight())
                if (holiday != null) WText(holiday, 12, bold = true, maxLines = 2)
            }
        }
    }
}
