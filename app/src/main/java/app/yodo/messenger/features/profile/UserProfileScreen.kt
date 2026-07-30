package app.yodo.messenger.features.profile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme

@Composable
fun UserProfileScreen(
    onBackClick: () -> Unit,
    onChatOpened: (String) -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel(),
    postsViewModel: PostsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val openChatId by viewModel.openChatId.collectAsState()
    val colorTheme = LocalColorTheme.current

    LaunchedEffect(openChatId) {
        openChatId?.let {
            onChatOpened(it)
            viewModel.consumeOpenChatId()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                        text = "Пользователь не найден",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
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
                    // Шапка с градиентом — как у канала
                    // ════════════════════════════════════════
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(colorTheme.primary.copy(alpha = 0.14f), Color.Transparent)
                                )
                            )
                            .padding(top = padding.calculateTopPadding())
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Аватар в градиентном кольце — как ChannelAvatarRing
                        UserAvatarRing(
                            displayName = user.displayName,
                            photoUrl = user.photoUrl,
                            avatarBase64 = user.avatarBase64,
                            isOnline = state.presence?.isOnline == true,
                            colorTheme = colorTheme
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Имя
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        // @username
                        user.username?.let {
                            Text(
                                text = "@$it",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorTheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Статус онлайн
                        state.presence?.let { presence ->
                            val statusText = if (presence.isOnline) "в сети"
                            else if (presence.lastSeenMillis > 0) "был(а) в сети недавно"
                            else null
                            statusText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (presence.isOnline) colorTheme.primary
                                    else Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
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
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    // ════════════════════════════════════════
                    // Кнопка "Написать"
                    // ════════════════════════════════════════
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(colorTheme.primary, colorTheme.accent)
                                )
                            ),
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
                                "Написать сообщение",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // ════════════════════════════════════════
                    // Расширенный профиль
                    // ════════════════════════════════════════
                    val extendedFields = buildList {
                        if (user.showAboutMe && !user.aboutMe.isNullOrBlank())
                            add(Triple(Icons.Filled.Info, "О себе", user.aboutMe!!))
                        if (user.showBirthDate && !user.birthDate.isNullOrBlank())
                            add(Triple(Icons.Filled.CalendarMonth, "Дата рождения", user.birthDate!!))
                        if (user.showLocation && !user.location.isNullOrBlank())
                            add(Triple(Icons.Filled.LocationOn, "Местоположение", user.location!!))
                        if (user.showWebsite && !user.website.isNullOrBlank())
                            add(Triple(Icons.Filled.Language, "Сайт", user.website!!))
                        if (user.showPhoneNumber && !user.phoneNumber.isNullOrBlank())
                            add(Triple(Icons.Filled.Phone, "Телефон", user.phoneNumber!!))
                        if (user.showEmail && !user.email.isNullOrBlank())
                            add(Triple(Icons.Filled.Email, "Email", user.email!!))
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
                                "Подробнее",
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
                                .shadow(1.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            extendedFields.forEachIndexed { index, (icon, label, value) ->
                                ProfileInfoRow(icon = icon, label = label, value = value, colorTheme = colorTheme)
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

/** Строка расширенного профиля в карточке. */
@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        Column {
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
    }
}
