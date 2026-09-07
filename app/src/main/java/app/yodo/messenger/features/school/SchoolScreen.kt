package app.yodo.messenger.features.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AdminPanelSettings
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.data.local.SchoolPreferences

/**
 * НОВОЕ (раздел «Школа»): главный экран школьного раздела — перенос главного
 * меню Telegram-бота (Для родителей / Для учителей / Для учеников / Выбор
 * Предмета / Новости / О разработке) в мессенджер. Пункты, выключенные в
 * «Настройках раздела „Школа“», скрываются отсюда.
 */
@Composable
fun SchoolScreen(
    onBackClick: () -> Unit,
    onOpenTeachers: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenPolls: () -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenGame: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenParents: () -> Unit,
    onOpenAdmin: () -> Unit = {},
    viewModel: SchoolViewModel = hiltViewModel()
) {
    val visibleSections by viewModel.visibleSections.collectAsState()
    val news by viewModel.news.collectAsState()
    val polls by viewModel.polls.collectAsState()
    val gameWins by viewModel.gameWins.collectAsState()
    val quizCorrect by viewModel.quizCorrect.collectAsState()
    val quizTotal by viewModel.quizTotal.collectAsState()
    val myStars by viewModel.myStars.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    fun isShown(id: String) = id in visibleSections

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Школа", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                // Школьная админ-панель — только для админов приложения.
                actions = {
                    IconButton(onClick = onOpenAdmin) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Администрирование")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SchoolSectionCard(
                    icon = Icons.Filled.School,
                    title = SchoolData.SCHOOL_NAME,
                    subtitle = "Справочник гимназии: учителя, новости, расписание и другое",
                    tint = MaterialTheme.colorScheme.primary,
                    subtitleMaxLines = 3
                )
            }
            if (isShown(SchoolPreferences.SectionIds.NEWS)) {
                item {
                    SchoolSectionCard(
                        icon = Icons.Filled.Event,
                        title = "Новости",
                        subtitle = if (news.isEmpty()) "Новостей пока нет"
                        else "Всего новостей: ${news.size}" + (news.firstOrNull()?.pinned?.let { " · есть закреплённая" } ?: ""),
                        onClick = onOpenNews
                    )
                }
            }
            if (isShown(SchoolPreferences.SectionIds.TEACHERS)) {
                item {
                    SchoolSectionCard(
                        icon = Icons.Filled.Extension,
                        title = "Учителя",
                        subtitle = "Выбор предмета: ${SchoolData.SUBJECTS.size} предметов и страницы учителей",
                        onClick = onOpenTeachers
                    )
                }
            }
            if (isShown(SchoolPreferences.SectionIds.SCHEDULE)) {
                item {
                    SchoolSectionCard(
                        icon = Icons.Filled.Schedule,
                        title = "Расписание и звонки",
                        subtitle = "Расписание уроков, звонки и отсчёт до каникул",
                        onClick = onOpenSchedule
                    )
                }
            }
            if (isShown(SchoolPreferences.SectionIds.POLLS)) {
                item {
                    SchoolSectionCard(
                        icon = Icons.Filled.Poll,
                        title = "Опросы",
                        subtitle = if (polls.isEmpty()) "Активных опросов пока нет"
                        else "Голосуйте — один голос на опрос",
                        onClick = onOpenPolls
                    )
                }
            }
            if (isShown(SchoolPreferences.SectionIds.QUIZ)) {
                item {
                    SchoolSectionCard(
                        icon = Icons.Filled.EmojiEvents,
                        title = "Викторина",
                        subtitle = if (quizCorrect > 0) "Правильных: $quizCorrect из $quizTotal"
                        else "20 вопросов по школьной программе",
                        onClick = onOpenQuiz
                    )
                }
            }
            if (isShown(SchoolPreferences.SectionIds.GAME)) {
                item {
                    SchoolSectionCard(
                        icon = Icons.Filled.SportsEsports,
                        title = "Игра",
                        subtitle = if (gameWins > 0) "Камень, ножницы, бумага · побед: $gameWins"
                        else "Камень, ножницы, бумага против приложения",
                        onClick = onOpenGame
                    )
                }
            }
            if (isShown(SchoolPreferences.SectionIds.REVIEW)) {
                item {
                    SchoolSectionCard(
                        icon = Icons.Filled.RateReview,
                        title = "Оценка и идеи",
                        subtitle = if (myStars > 0) "Ваша оценка: ${"⭐".repeat(myStars)} — можно изменить"
                        else "Оцените раздел и предложите идею",
                        onClick = onOpenReview
                    )
                }
            }
            if (isShown(SchoolPreferences.SectionIds.FAQ)) {
                item {
                    SchoolSectionCard(
                        icon = Icons.Filled.HelpOutline,
                        title = "FAQ",
                        subtitle = "Часто задаваемые вопросы о разделе",
                        onClick = onOpenFaq
                    )
                }
            }
            if (isShown(SchoolPreferences.SectionIds.PARENTS)) {
                item {
                    SchoolSectionCard(
                        icon = Icons.Filled.Home,
                        title = "Родителям",
                        subtitle = "Контакты гимназии, сайт и группа ВКонтакте",
                        onClick = onOpenParents
                    )
                }
            }
        }
    }
}

/** Карточка-пункт главного экрана «Школы» (аналог кнопок меню бота). */
@Composable
fun SchoolSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    subtitleMaxLines: Int = 2,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleMaxLines
            )
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Общий TopAppBar для подэкранов «Школы». */
@Composable
fun SchoolTopBar(title: String, onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
