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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
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

    Scaffold(
        topBar = { SchoolTopBar("Расписание и звонки", onBackClick) },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                    SchoolData.BELL_SCHEDULE.forEach { (name, time) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Text(time, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary)
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
