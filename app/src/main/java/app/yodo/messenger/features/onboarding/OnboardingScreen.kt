package app.yodo.messenger.features.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yodo.messenger.ui.theme.LocalColorTheme
import kotlinx.coroutines.launch

/**
 * Экран обучения (онбординг).
 *
 * Теперь с ВЫБОРОМ формата:
 *  - Короткое  — 5 обзорных карточек (как было раньше).
 *  - Расширенное — то же самое + интерактивная практика на реальных элементах
 *    интерфейса: пользователь сам отправляет демо-сообщение, делает долгий тап
 *    по сообщению, добавляет участника в демо-группу и подписывается на демо-канал.
 *
 * Всё обучение изолировано: демо-элементы никак не влияют на реальные чаты.
 */

private enum class OnboardingMode { SHORT, EXTENDED }

// ─────────────────────────────────────────────────────────────────────────────
// Модель страниц
// ─────────────────────────────────────────────────────────────────────────────

private sealed interface OnboardingStep {
    /** Обычная обзорная карточка с иконкой, заголовком и описанием. */
    data class Info(
        val icon: ImageVector,
        val title: String,
        val description: String
    ) : OnboardingStep

    /** Интерактивный шаг: пользователь выполняет действие сам. */
    data class Interactive(
        val kind: InteractiveKind
    ) : OnboardingStep
}

private enum class InteractiveKind { SEND_MESSAGE, LONG_PRESS, GROUP_ADD, CHANNEL }

// Короткий формат — как было.
private val shortSteps: List<OnboardingStep> = listOf(
    OnboardingStep.Info(
        Icons.Filled.Celebration,
        "Добро пожаловать в Yodo!",
        "Спасибо за регистрацию. Покажем за пару шагов, как всё устроено."
    ),
    OnboardingStep.Info(
        Icons.Filled.Chat,
        "Чаты и группы",
        "Переписывайтесь один на один, создавайте группы и каналы для общения с друзьями и сообществами."
    ),
    OnboardingStep.Info(
        Icons.Filled.NearMe,
        "Люди рядом",
        "Находите и добавляйте в контакты пользователей поблизости — удобно для новых знакомств."
    ),
    OnboardingStep.Info(
        Icons.Filled.WifiOff,
        "Без интернета",
        "Режим Bluetooth-чата позволяет общаться даже без сети и мобильного интернета."
    ),
    OnboardingStep.Info(
        Icons.Filled.Settings,
        "Настройки под себя",
        "Настройте тему, конфиденциальность и уведомления в разделе «Настройки» — всё в ваших руках."
    )
)

// Расширенный формат — всё из короткого + интерактивные шаги и подробные обзоры.
private val extendedSteps: List<OnboardingStep> = listOf(
    OnboardingStep.Info(
        Icons.Filled.Celebration,
        "Добро пожаловать в Yodo!",
        "Это подробное обучение. Вы не просто посмотрите — вы сами попробуете основные действия прямо здесь, на настоящих элементах интерфейса."
    ),
    // ─── Чаты ───
    OnboardingStep.Info(
        Icons.Filled.Chat,
        "Личные чаты",
        "Откройте чат с человеком и общайтесь один на один. Сообщения, фото, файлы и голосовые — всё в одном месте. Сейчас попробуем отправить сообщение."
    ),
    OnboardingStep.Interactive(InteractiveKind.SEND_MESSAGE),
    OnboardingStep.Interactive(InteractiveKind.LONG_PRESS),
    OnboardingStep.Info(
        Icons.Filled.Bolt,
        "Возможности сообщений",
        "Долгий тап по сообщению открывает меню: ответить, переслать, копировать, удалить, закрепить. Свайп по сообщению — быстрый ответ. Двойной тап — реакция."
    ),
    // ─── Группы ───
    OnboardingStep.Info(
        Icons.Filled.Groups,
        "Группы",
        "Создавайте группы для друзей и команд. Можно задать название, аватар, описание и добавить участников. Попробуйте добавить участника."
    ),
    OnboardingStep.Interactive(InteractiveKind.GROUP_ADD),
    OnboardingStep.Info(
        Icons.Filled.Settings,
        "Управление группой",
        "В настройках группы: роли и права участников, приватность (открытая / по заявке), закреплённые сообщения и модерация."
    ),
    // ─── Каналы ───
    OnboardingStep.Info(
        Icons.Filled.Campaign,
        "Каналы",
        "Каналы — для публикаций на большую аудиторию. Подписчики читают посты, ставят реакции и комментируют. Попробуйте подписаться на демо-канал."
    ),
    OnboardingStep.Interactive(InteractiveKind.CHANNEL),
    // ─── Прочее ───
    OnboardingStep.Info(
        Icons.Filled.NearMe,
        "Люди рядом",
        "Находите и добавляйте в контакты пользователей поблизости — удобно для новых знакомств без обмена номерами."
    ),
    OnboardingStep.Info(
        Icons.Filled.WifiOff,
        "Без интернета",
        "Режим Bluetooth-чата позволяет общаться даже без сети и мобильного интернета — сообщения, фото и голосовые передаются между устройствами рядом. Аккаунт не нужен: используется локальный офлайн-профиль (имя, статус, эмодзи-аватар)."
    ),
    OnboardingStep.Info(
        Icons.Filled.Settings,
        "Настройки под себя",
        "Темы оформления, конфиденциальность, двухфакторная защита, PIN-код и уведомления — всё в разделе «Настройки»."
    ),
    OnboardingStep.Info(
        Icons.Filled.CheckCircle,
        "Готово!",
        "Теперь вы знаете основное. Всё остальное освоите на практике — начинайте общаться!"
    )
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    var mode by remember { mutableStateOf<OnboardingMode?>(null) }

    when (val m = mode) {
        null -> ModeChooser(
            onShort = { mode = OnboardingMode.SHORT },
            onExtended = { mode = OnboardingMode.EXTENDED },
            onSkip = onSkip
        )
        else -> OnboardingPagerFlow(
            steps = if (m == OnboardingMode.SHORT) shortSteps else extendedSteps,
            onFinish = onFinish,
            onSkip = onSkip
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Экран выбора формата обучения
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeChooser(
    onShort: () -> Unit,
    onExtended: () -> Unit,
    onSkip: () -> Unit
) {
    val colorTheme = LocalColorTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(colorTheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Как хотите пройти обучение?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Выберите формат. Расширенное обучение можно пройти позже в Настройках.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        ModeCard(
            icon = Icons.Filled.Bolt,
            title = "Быстрое обучение",
            subtitle = "Коротко о главном — 5 карточек за минуту.",
            accent = colorTheme.primary,
            onClick = onShort
        )
        Spacer(Modifier.height(16.dp))
        ModeCard(
            icon = Icons.Filled.School,
            title = "Полное обучение",
            subtitle = "Подробный обзор чатов, групп и каналов + практика на реальном интерфейсе с примерами.",
            accent = colorTheme.accent,
            recommended = true,
            onClick = onExtended
        )

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSkip) {
            Text("Пропустить обучение", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    recommended: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (recommended) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Рекомендуем", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Общий пейджер для короткого и расширенного формата
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingPagerFlow(
    steps: List<OnboardingStep>,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    val colorTheme = LocalColorTheme.current
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == steps.lastIndex

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                Text("Пропустить", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (val step = steps[page]) {
                    is OnboardingStep.Info -> InfoStepContent(step, colorTheme.primary)
                    is OnboardingStep.Interactive -> InteractiveStepContent(step.kind, colorTheme)
                }
            }

            PagerIndicator(
                pageCount = steps.size,
                currentPage = pagerState.currentPage,
                accent = colorTheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 20.dp)
            )

            Button(
                onClick = {
                    if (isLastPage) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(52.dp)
            ) {
                Text(
                    text = if (isLastPage) "Начать общение" else "Далее",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoStepContent(step: OnboardingStep.Info, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(step.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(step.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            step.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Интерактивные шаги
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InteractiveStepContent(kind: InteractiveKind, colorTheme: app.yodo.messenger.ui.theme.ColorTheme) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        when (kind) {
            InteractiveKind.SEND_MESSAGE -> DemoSendMessage(colorTheme)
            InteractiveKind.LONG_PRESS -> DemoLongPress(colorTheme)
            InteractiveKind.GROUP_ADD -> DemoGroupAdd(colorTheme)
            InteractiveKind.CHANNEL -> DemoChannel(colorTheme)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Подсказка сверху интерактивного шага. */
@Composable
private fun PracticeHeader(title: String, hint: String, done: Boolean, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.TouchApp,
                contentDescription = null,
                tint = if (done) colorSuccess else accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (done) "Отлично, получилось!" else hint,
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) colorSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

private val colorSuccess = Color(0xFF22C55E)

/** Демо-чат: наберите текст и нажмите «отправить». */
@Composable
private fun DemoSendMessage(colorTheme: app.yodo.messenger.ui.theme.ColorTheme) {
    var text by remember { mutableStateOf("") }
    val sent = remember { mutableStateListOf<String>() }
    PracticeHeader(
        title = "Отправьте сообщение",
        hint = "Введите текст и нажмите кнопку отправки",
        done = sent.isNotEmpty(),
        accent = colorTheme.primary
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp)
    ) {
        // Входящее демо-сообщение
        DemoBubble("Привет! Напиши мне что-нибудь 👋", own = false, colorTheme = colorTheme)
        sent.forEach { msg ->
            Spacer(Modifier.height(8.dp))
            DemoBubble(msg, own = true, colorTheme = colorTheme)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Сообщение…") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colorTheme.primary)
                    .clickable {
                        if (text.isNotBlank()) {
                            sent.add(text.trim())
                            text = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Демо-долгий тап по сообщению. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DemoLongPress(colorTheme: app.yodo.messenger.ui.theme.ColorTheme) {
    var menuOpen by remember { mutableStateOf(false) }
    var used by remember { mutableStateOf(false) }
    PracticeHeader(
        title = "Долгий тап по сообщению",
        hint = "Нажмите и удерживайте сообщение ниже",
        done = used,
        accent = colorTheme.primary
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp)
    ) {
        DemoBubble("Входящее сообщение — попробуйте на нём", own = false, colorTheme = colorTheme)
        Spacer(Modifier.height(8.dp))
        Box {
            Row {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorTheme.primary)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menuOpen = true; used = true }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("Удерживайте меня 👆", color = Color.White)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Ответить") }, onClick = { menuOpen = false },
                    leadingIcon = { Icon(Icons.Filled.Reply, contentDescription = null) })
                DropdownMenuItem(text = { Text("Копировать") }, onClick = { menuOpen = false },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) })
                DropdownMenuItem(text = { Text("Переслать") }, onClick = { menuOpen = false },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) })
                DropdownMenuItem(text = { Text("Удалить") }, onClick = { menuOpen = false },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Так же меню сообщения работает в настоящих чатах.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Демо-группа: добавьте участника. */
@Composable
private fun DemoGroupAdd(colorTheme: app.yodo.messenger.ui.theme.ColorTheme) {
    val pool = remember { listOf("Аня", "Борис", "Вика", "Глеб", "Даша") }
    val added = remember { mutableStateListOf("Вы") }
    PracticeHeader(
        title = "Добавьте участника",
        hint = "Нажмите на человека, чтобы добавить его в группу",
        done = added.size >= 2,
        accent = colorTheme.primary
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp)
    ) {
        Text("Участники группы: ${added.size}", style = MaterialTheme.typography.labelLarge, color = colorTheme.primary)
        Spacer(Modifier.height(10.dp))
        pool.forEach { name ->
            val isAdded = added.contains(name)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (isAdded) added.remove(name) else added.add(name)
                    }
                    .padding(vertical = 8.dp, horizontal = 6.dp)
            ) {
                DemoAvatar(name, colorTheme)
                Spacer(Modifier.width(12.dp))
                Text(name, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isAdded) colorSuccess else colorTheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isAdded) Icons.Filled.Check else Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = if (isAdded) Color.White else colorTheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** Демо-канал: подпишитесь. */
@Composable
private fun DemoChannel(colorTheme: app.yodo.messenger.ui.theme.ColorTheme) {
    var subscribed by remember { mutableStateOf(false) }
    PracticeHeader(
        title = "Подпишитесь на канал",
        hint = "Нажмите «Подписаться», чтобы читать посты",
        done = subscribed,
        accent = colorTheme.primary
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colorTheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Демо-канал новостей", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (subscribed) "512 подписчиков" else "511 подписчиков",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { subscribed = !subscribed },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (subscribed) MaterialTheme.colorScheme.surfaceVariant else colorTheme.primary
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                if (subscribed) "Вы подписаны ✓" else "Подписаться",
                color = if (subscribed) MaterialTheme.colorScheme.onSurface else Color.White
            )
        }
        if (subscribed) {
            Spacer(Modifier.height(14.dp))
            DemoBubble("Новый пост: спасибо, что подписались! 🎉", own = false, colorTheme = colorTheme)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Мелкие демо-компоненты
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DemoBubble(text: String, own: Boolean, colorTheme: app.yodo.messenger.ui.theme.ColorTheme) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (own) Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (own) colorTheme.primary else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text, color = if (own) Color.White else MaterialTheme.colorScheme.onSurface)
        }
        if (!own) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DemoAvatar(name: String, colorTheme: app.yodo.messenger.ui.theme.ColorTheme) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colorTheme.accent),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val color by animateColorAsState(
                targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                label = "onboarding_dot"
            )
            val w by animateDpAsState(if (selected) 20.dp else 7.dp, label = "onboarding_dot_w")
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
