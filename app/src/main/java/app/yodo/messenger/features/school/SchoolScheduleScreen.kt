package app.yodo.messenger.features.school

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * НОВОЕ (пометка расписания): ViewModel экрана расписания — статус
 * актуальности, который ставит админ (config/appSettings).
 */
@HiltViewModel
class SchoolScheduleViewModel @Inject constructor(
    appSettingsRepository: app.yodo.messenger.domain.repository.AppSettingsRepository
) : ViewModel() {
    val scheduleStatus: StateFlow<app.yodo.messenger.domain.repository.SchoolScheduleStatus?> =
        appSettingsRepository.observeSchoolScheduleStatus()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

/**
 * НОВОЕ (раздел «Школа»): расписание звонков, ссылка на расписание уроков и
 * обратный отсчёт до каникул — перенос раздела «Для учеников» из бота.
 */
@Composable
fun SchoolScheduleScreen(
    onBackClick: () -> Unit,
    viewModel: SchoolScheduleViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    // НОВОЕ (пометка расписания): статус от админа (null — пометка не ставилась).
    val scheduleStatus by viewModel.scheduleStatus.collectAsState()

    // НОВОЕ (живой статус уроков): минутный тикер, выровненный по границе
    // минуты, чтобы статус переключался ровно со звонком.
    var nowMinutes by remember {
        mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute })
    }
    LaunchedEffect(Unit) {
        while (true) {
            val ts = System.currentTimeMillis()
            delay(60_000 - ts % 60_000 + 50)
            nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
        }
    }
    val lessonStatus = remember(nowMinutes) {
        currentLessonStatus(nowMinutes / 60, nowMinutes % 60)
    }

    Scaffold(
        topBar = { SchoolTopBar("Расписание и звонки", onBackClick) },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // НОВОЕ (живой статус уроков): карточка «что сейчас в школе».
            item { LessonStatusCard(lessonStatus) }
            item {
                Column {
                    SchoolRowCard(
                        icon = Icons.Filled.Event,
                        title = "📄 Расписание уроков",
                        subtitle = "Открывается в Google Drive",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(SchoolData.LESSONS_SCHEDULE_URL))
                                )
                            }
                        }
                    )
                    // НОВОЕ (пометка расписания): зелёная галочка или жёлтое
                    // предупреждение от админа; ничего — пометку не ставили.
                    scheduleStatus?.let { status ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (status.actual) {
                                if (status.untilDate.isBlank()) "✅ Расписание актуально"
                                else "✅ Расписание актуально на ${status.untilDate}"
                            } else {
                                if (status.untilDate.isBlank())
                                    "⚠️ Расписание может быть неактуальным — уточните у учителя"
                                else "⚠️ Расписание может быть неактуальным (было актуально на ${status.untilDate})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.actual) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Text(
                        "🔔 Расписание звонков",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SchoolData.BELL_SCHEDULE.forEachIndexed { index, (name, time) ->
                        // НОВОЕ (живой статус уроков): текущий урок подсвечен,
                        // следующий на перемене помечен «скоро».
                        val isCurrent = lessonStatus is LessonStatus.InLesson &&
                                lessonStatus.number == index + 1
                        val isNext = lessonStatus is LessonStatus.Break &&
                                lessonStatus.beforeLesson == index + 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        isNext -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(
                                    horizontal = if (isCurrent || isNext) 8.dp else 0.dp,
                                    vertical = 6.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f))
                            Text(time, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary)
                            if (isCurrent || isNext) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isCurrent) "сейчас" else "скоро",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
            item {
                HolidaysCard()
            }
        }
    }
}

@Composable
private fun HolidaysCard() {
    val holidayDate = runCatching { LocalDate.parse(SchoolData.HOLIDAY_DATE_ISO) }.getOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            "📅 До каникул",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        val today = LocalDate.now()
        when {
            holidayDate == null -> Text(
                "Дата каникул не задана",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            today.isAfter(holidayDate) -> Text(
                "🎉 Каникулы уже идут! Отдыхайте!",
                style = MaterialTheme.typography.bodyLarge
            )
            today.isEqual(holidayDate) -> Text(
                "🎉 Сегодня начинаются каникулы!",
                style = MaterialTheme.typography.bodyLarge
            )
            else -> {
                val days = ChronoUnit.DAYS.between(today, holidayDate)
                Text(
                    "До каникул осталось: $days дн.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Каникулы с ${holidayDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * НОВОЕ (живой статус уроков): карточка «что сейчас в школе» — идёт урок,
 * перемена, до начала занятий или после их окончания. Обновляется тикером из
 * SchoolScheduleScreen раз в минуту.
 */
@Composable
private fun LessonStatusCard(status: LessonStatus) {
    val (title, subtitle, tint) = when (status) {
        is LessonStatus.InLesson -> Triple(
            "🟢 Сейчас идёт урок ${status.number}",
            "До звонка ${status.minutesLeft} ${pluralRu(status.minutesLeft, "минута", "минуты", "минут")}",
            MaterialTheme.colorScheme.primary
        )
        is LessonStatus.Break -> Triple(
            "🟡 Перемена",
            "Урок ${status.beforeLesson} начнётся через ${status.minutesLeft} " +
                pluralRu(status.minutesLeft, "минуту", "минуты", "минут"),
            MaterialTheme.colorScheme.tertiary
        )
        is LessonStatus.BeforeSchool -> Triple(
            "⏰ Уроки ещё не начались",
            "Первый звонок через ${status.minutesLeft} " +
                pluralRu(status.minutesLeft, "минуту", "минуты", "минут"),
            MaterialTheme.colorScheme.tertiary
        )
        LessonStatus.AfterSchool -> Triple(
            "🎉 Уроки закончились",
            "Отдыхайте!",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Русская плюрализация минут для живого статуса уроков. */
private fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
