package app.yodo.messenger.features.profile

import app.yodo.messenger.ui.components.GlassScaffold
import app.yodo.messenger.ui.components.Button
import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.IconButton
import app.yodo.messenger.ui.components.DeveloperVerifiedBadge
import app.yodo.messenger.ui.components.isDeveloperAccount
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.yodo.messenger.domain.repository.PresenceRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError
import app.yodo.messenger.ui.theme.YodoSuccess

@Composable
fun UserProfileScreen(
    onBackClick: () -> Unit,
    onChatOpened: (String) -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel(),
    postsViewModel: PostsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val openChatId by viewModel.openChatId.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    // НОВОЕ (AD): глобальная блокировка аккаунта админом (2 почты).
    val isGloballyBlocked by viewModel.isGloballyBlocked.collectAsState()
    // ИСПРАВЛЕНО (AT): результат блокировки теперь виден админу.
    val globalBlockError by viewModel.globalBlockError.collectAsState()
    val globalBlockInfo by viewModel.globalBlockInfo.collectAsState()
    var showGlobalBlockDialog by remember { mutableStateOf(false) }
    var globalBlockReason by remember { mutableStateOf("") }
    val colorTheme = LocalColorTheme.current

    LaunchedEffect(openChatId) {
        openChatId?.let {
            onChatOpened(it)
            viewModel.consumeOpenChatId()
        }
    }

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        when (val state = uiState) {
            is UserProfileUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            is UserProfileUiState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        text = stringResource(R.string.user_profile_not_found),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // НОВОЕ (п.15): владелец ограничил просмотр профиля настройкой приватности.
            is UserProfileUiState.Restricted -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Профиль скрыт",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Пользователь ограничил, кто может видеть его профиль",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            is UserProfileUiState.Content -> {
                val user = state.user
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // ════════════════════════════════════════
                    // Шапка: двухцветный градиент, аватар в кольце, имя, чипы, статус
                    // ════════════════════════════════════════
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    val effectivelyOnline = isPresenceEffectivelyOnline(state.presence)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colorTheme.primary.copy(alpha = 0.24f),
                                        colorTheme.accent.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(top = padding.calculateTopPadding())
                            .padding(horizontal = 24.dp)
                            .padding(top = 8.dp, bottom = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        UserAvatarRing(
                            displayName = user.displayName,
                            photoUrl = user.photoUrl,
                            avatarBase64 = user.avatarBase64,
                            isOnline = effectivelyOnline,
                            colorTheme = colorTheme
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Имя + галочка разработчика
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user.displayName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isDeveloperAccount(user.email)) {
                                Spacer(modifier = Modifier.width(6.dp))
                                DeveloperVerifiedBadge(size = 24.dp)
                            }
                        }

                        // Статус онлайн / «был(а)…» (обновляется каждую секунду)
                        state.presence?.let { presence ->
                            val statusText = when {
                                effectivelyOnline -> stringResource(R.string.user_profile_online)
                                presence.lastSeenMillis > 0 ->
                                    stringResource(R.string.chat_list_was_online, formatProfileLastSeen(presence.lastSeenMillis))
                                else -> null
                            }
                            statusText?.let {
                                Row(
                                    modifier = Modifier.padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (effectivelyOnline) Color(0xFF22C55E) else Color.Gray.copy(alpha = 0.6f))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (effectivelyOnline) colorTheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Чипы: @username и публичный ID (тап — копирование)
                        val username = user.username
                        val publicId = user.publicId?.takeIf { it.isNotBlank() }
                        if (!username.isNullOrBlank() || publicId != null) {
                            Row(
                                modifier = Modifier.padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!username.isNullOrBlank()) {
                                    ProfileChip(
                                        text = "@$username",
                                        accent = colorTheme.primary,
                                        filled = true,
                                        onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString("@$username")) }
                                    )
                                }
                                if (publicId != null) {
                                    ProfileChip(
                                        text = "ID $publicId",
                                        accent = MaterialTheme.colorScheme.onSurfaceVariant,
                                        filled = false,
                                        onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(publicId)) }
                                    )
                                }
                            }
                        }

                        // Эмодзи-статус и текстовый статус
                        val statusLine = listOf(user.emojiStatus, user.customStatus)
                            .mapNotNull { it?.takeIf { s -> s.isNotBlank() } }
                            .joinToString(" ")
                        if (statusLine.isNotBlank()) {
                            Text(
                                text = statusLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorTheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(colorTheme.primary.copy(alpha = 0.10f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        // Краткое bio
                        if (!user.bio.isNullOrBlank()) {
                            Text(
                                text = user.bio,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }

                    // ════════════════════════════════════════
                    // Действия: «Написать» и блокировка в одном ряду
                    // ════════════════════════════════════════
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.4f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Brush.horizontalGradient(listOf(colorTheme.primary, colorTheme.accent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = { viewModel.openChat() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.user_profile_send_message),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                        val blockColor = if (isBlocked) colorTheme.primary else Color(0xFFE53935)
                        Button(
                            onClick = { viewModel.toggleBlock() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = blockColor.copy(alpha = 0.12f),
                                contentColor = blockColor
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp)
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isBlocked) "Разблок." else "Блок",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    // Админская глобальная блокировка аккаунта (только 2 почты).
                    if (viewModel.isAppAdmin) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (isGloballyBlocked) viewModel.removeGlobalBlock()
                                else showGlobalBlockDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8E24AA).copy(alpha = 0.12f),
                                contentColor = Color(0xFF8E24AA)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isGloballyBlocked) "Снять блокировку приложения" else "Заблокировать в приложении (админ)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    // ════════════════════════════════════════
                    // Расширенный профиль
                    // ════════════════════════════════════════
                    // НОВОЕ (email-статус): для поля email дополнительно передаём флаг подтверждения,
                    // остальным полям он не нужен (isVerified = null).
                    val extendedFields = buildList {
                        if (user.showAboutMe && !user.aboutMe.isNullOrBlank())
                            add(ProfileExtendedField(Icons.Filled.Info, stringResource(R.string.user_profile_about), user.aboutMe!!))
                        if (user.showBirthDate && !user.birthDate.isNullOrBlank())
                            add(ProfileExtendedField(Icons.Filled.CalendarMonth, stringResource(R.string.user_profile_birth_date), user.birthDate!!))
                        if (user.showLocation && !user.location.isNullOrBlank())
                            add(ProfileExtendedField(Icons.Filled.LocationOn, stringResource(R.string.user_profile_location), user.location!!))
                        if (user.showWebsite && !user.website.isNullOrBlank())
                            add(ProfileExtendedField(Icons.Filled.Language, stringResource(R.string.user_profile_website), user.website!!))
                        if (user.showPhoneNumber && !user.phoneNumber.isNullOrBlank())
                            add(ProfileExtendedField(Icons.Filled.Phone, stringResource(R.string.user_profile_phone), user.phoneNumber!!))
                        if (user.showEmail && !user.email.isNullOrBlank())
                            add(ProfileExtendedField(Icons.Filled.Email, "Email", user.email!!, isVerified = user.isEmailVerified))
                    }

                    if (extendedFields.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Заголовок секции
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(
                                        Brush.verticalGradient(listOf(colorTheme.primary, colorTheme.accent)),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.user_profile_more),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colorTheme.primary
                            )
                        }

                        // Карточка с полями
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    0.6.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    RoundedCornerShape(20.dp)
                                )
                        ) {
                            val infoClipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                            extendedFields.forEachIndexed { index, field ->
                                ProfileInfoRow(
                                    onClick = { infoClipboard.setText(androidx.compose.ui.text.AnnotatedString(field.value)) },
                                    icon = field.icon,
                                    label = field.label,
                                    value = field.value,
                                    colorTheme = colorTheme,
                                    isVerified = field.isVerified
                                )
                                if (index < extendedFields.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                                }
                            }
                        }
                    }

                    // ════════════════════════════════════════
                    // Посты пользователя — видны всем, как во ВКонтакте
                    // ════════════════════════════════════════
                    ProfilePostsSection(
                        userId = user.uid,
                        isOwnProfile = false,
                        colorTheme = colorTheme,
                        viewModel = postsViewModel
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // НОВОЕ (AD): диалог с причиной глобальной блокировки аккаунта.
    if (showGlobalBlockDialog) {
        AlertDialog(
            onDismissRequest = { showGlobalBlockDialog = false },
            title = { Text("Блокировка аккаунта") },
            text = {
                Column {
                    Text("Причина будет показана пользователю. Он сможет только подать обжалование.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = globalBlockReason,
                        onValueChange = { globalBlockReason = it },
                        label = { Text("Причина") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setGlobalBlock(globalBlockReason.trim())
                        showGlobalBlockDialog = false
                        globalBlockReason = ""
                    },
                    enabled = globalBlockReason.isNotBlank()
                ) { Text("Заблокировать") }
            },
            dismissButton = {
                TextButton(onClick = { showGlobalBlockDialog = false }) { Text("Отмена") }
            }
        )
    }

    // ИСПРАВЛЕНО (AT): сообщение об успехе или ошибке блокировки аккаунта.
    if (globalBlockError != null || globalBlockInfo != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearGlobalBlockMessages() },
            title = { Text(if (globalBlockError != null) "Не получилось" else "Готово") },
            text = { Text(globalBlockError ?: globalBlockInfo ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearGlobalBlockMessages() }) { Text("ОК") }
            }
        )
    }
}

/**
 * Аватар пользователя в медленно вращающемся градиентном кольце —
 * тот же паттерн, что ChannelAvatarRing в ChannelProfileScreen.
 */
@Composable
private fun UserAvatarRing(
    displayName: String,
    photoUrl: String?,
    avatarBase64: String?,
    isOnline: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme
) {
    val transition = rememberInfiniteTransition(label = "user_ring")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "ring_angle"
    )
    Box(modifier = Modifier.size(126.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(126.dp).graphicsLayer { rotationZ = angle }) {
            val stroke = 3.dp.toPx()
            drawCircle(
                brush = Brush.sweepGradient(listOf(colorTheme.primary, colorTheme.accent, colorTheme.primary)),
                radius = size.minDimension / 2 - stroke / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
            )
        }
        UserAvatar(
            displayName = displayName,
            photoUrl = photoUrl,
            avatarBase64 = avatarBase64,
            size = 110.dp
        )
        // Зелёный индикатор онлайн в правом нижнем углу
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                )
            }
        }
    }
}

/** Небольшой чип под именем (@username, ID). Тап копирует значение. */
@Composable
private fun ProfileChip(text: String, accent: Color, filled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = if (filled) 0.dp else 0.8.dp,
                color = accent.copy(alpha = 0.4f),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

// НОВОЕ (email-статус): контейнер для поля расширенного профиля. isVerified используется
// только строкой email — null означает "бейдж не показываем" (для остальных полей).
private data class ProfileExtendedField(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val isVerified: Boolean? = null
)

/** Строка расширенного профиля в карточке. */
@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    isVerified: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colorTheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        // НОВОЕ (email-статус): бейдж "подтверждена/не подтверждена" рядом с email в чужом профиле.
        if (isVerified != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isVerified) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = if (isVerified) "Почта подтверждена" else "Почта не подтверждена",
                tint = if (isVerified) YodoSuccess else YodoError,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// НОВОЕ (баг 12): посекундное обновление статуса онлайн в профиле
// ---------------------------------------------------------------------------

/**
 * Посекундный "тик": composables, читающие это значение, пересчитываются раз в секунду.
 */
@Composable
private fun rememberProfileNowTick(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun isPresenceEffectivelyOnline(presence: app.yodo.messenger.domain.model.UserPresence?): Boolean {
    val now = rememberProfileNowTick()
    if (presence == null || !presence.isOnline) return false
    // lastSeen == 0 — старые данные без времени; доверяем isOnline как есть.
    return presence.lastSeenMillis == 0L ||
        (now - presence.lastSeenMillis) <= PresenceRepository.PRESENCE_STALE_THRESHOLD_MILLIS
}

@Composable
private fun formatProfileLastSeen(millis: Long): String {
    return app.yodo.messenger.util.LastSeenFormatter.format(millis)
}
