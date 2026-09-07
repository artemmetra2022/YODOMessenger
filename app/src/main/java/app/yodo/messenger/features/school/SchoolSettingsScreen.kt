package app.yodo.messenger.features.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.data.local.SchoolPreferences
import app.yodo.messenger.features.settings.SettingsCard
import app.yodo.messenger.features.settings.SettingsSectionHeader
import app.yodo.messenger.features.settings.SettingsToggleRow
import app.yodo.messenger.ui.theme.LocalColorTheme

private data class SchoolSectionUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

/**
 * НОВОЕ (раздел «Школа»): экран настройки отображения раздела «Школа» —
 * главный переключатель (показывать ли пункт в настройках вообще) и
 * переключатели отдельных подразделов. Открывается из Настройки → Аккаунт.
 */
@Composable
fun SchoolSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SchoolSettingsViewModel = hiltViewModel()
) {
    val sectionEnabled by viewModel.sectionEnabled.collectAsState()
    val visibleSections by viewModel.visibleSections.collectAsState()
    val colorTheme = LocalColorTheme.current

    val sections = listOf(
        SchoolSectionUi(SchoolPreferences.SectionIds.NEWS, "Новости",
            "Новости гимназии с закреплёнными объявлениями", Icons.Filled.Event),
        SchoolSectionUi(SchoolPreferences.SectionIds.TEACHERS, "Учителя",
            "Выбор предмета и страницы учителей", Icons.Filled.Extension),
        SchoolSectionUi(SchoolPreferences.SectionIds.SCHEDULE, "Расписание и звонки",
            "Расписание уроков, звонки и отсчёт до каникул", Icons.Filled.Schedule),
        SchoolSectionUi(SchoolPreferences.SectionIds.POLLS, "Опросы",
            "Голосования с одним голосом на опрос", Icons.Filled.Poll),
        SchoolSectionUi(SchoolPreferences.SectionIds.QUIZ, "Викторина",
            "20 вопросов по школьной программе", Icons.Filled.EmojiEvents),
        SchoolSectionUi(SchoolPreferences.SectionIds.GAME, "Игра",
            "Камень, ножницы, бумага", Icons.Filled.SportsEsports),
        SchoolSectionUi(SchoolPreferences.SectionIds.REVIEW, "Оценка и идеи",
            "Оценка раздела по звёздам и предложения разработчикам", Icons.Filled.RateReview),
        SchoolSectionUi(SchoolPreferences.SectionIds.FAQ, "FAQ",
            "Часто задаваемые вопросы", Icons.Filled.HelpOutline),
        SchoolSectionUi(SchoolPreferences.SectionIds.PARENTS, "Родителям",
            "Адрес, телефон, почта, сайт и ВКонтакте гимназии", Icons.Filled.Home)
    )

    Scaffold(
        topBar = { SchoolTopBar("Настройки раздела «Школа»", onBackClick) },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.School,
                    title = "Отображение",
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.School,
                        title = "Показывать раздел «Школа»",
                        subtitle = "Пункт «Школа» в Настройки → Аккаунт",
                        checked = sectionEnabled,
                        onCheckedChange = { viewModel.setSectionEnabled(it) },
                        colorTheme = colorTheme
                    )
                }
            }
            item {
                Text(
                    "Отдельные подразделы — уберите ненужные с главного экрана «Школы»:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
            item {
                SettingsCard {
                    sections.forEachIndexed { index, section ->
                        SettingsToggleRow(
                            icon = section.icon,
                            title = section.title,
                            subtitle = section.subtitle,
                            checked = section.id in visibleSections,
                            onCheckedChange = { viewModel.setSectionVisible(section.id, it) },
                            colorTheme = colorTheme
                        )
                        if (index < sections.size - 1) {
                            androidx.compose.material3.HorizontalDivider(
                                modifier = Modifier.padding(start = 52.dp)
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
