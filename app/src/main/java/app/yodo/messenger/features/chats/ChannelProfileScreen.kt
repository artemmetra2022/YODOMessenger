package app.yodo.messenger.features.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.yodo.messenger.util.ImageUtils
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.ui.theme.LocalColorTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * НОВОЕ (переработка каналов): публичный профиль канала.
 * Вид — шапка с аватаром в медленно вращающемся градиентном кольце,
 * блок статистики (подписчики/посты/админы), CTA подписки, последние посты,
 * состав администрации и (для владельца) кнопка редактирования.
 */
@Composable
fun ChannelProfileScreen(
    onBackClick: () -> Unit,
    onChatOpened: (String) -> Unit,
    onEditChannel: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    // НОВОЕ (система ролей + журнал администраторов): переход к экрану управления ролями.
    onManageRoles: (String) -> Unit = {},
    // НОВОЕ: канал удалён владельцем — экран должен закрыться в список чатов.
    onChannelDeleted: () -> Unit = onBackClick,
    viewModel: ChannelProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val openChatId by viewModel.openChatId.collectAsState()
    val channelDeleted by viewModel.channelDeleted.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val colorTheme = LocalColorTheme.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(openChatId) {
        openChatId?.let {
            onChatOpened(it)
            viewModel.consumeOpenChatId()
        }
    }
    LaunchedEffect(channelDeleted) {
        if (channelDeleted) onChannelDeleted()
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
    }

    // Обновляем данные при возврате (например, с экрана редактирования канала).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("О канале", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.notFound -> Text(
                    "Канал не найден",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                uiState.profile != null -> {
                    val profile = uiState.profile!!
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    ) {
                        // ═══ Hero-шапка ═══
                        ChannelHero(
                            profile = profile,
                            uiState = uiState,
                            colorTheme = colorTheme
                        )

                        // ═══ Кнопки подписки ═══
                        // У официального канала подписки нет — он виден всем и закреплён в списке.
                        if (!profile.isVerified) {
                            if (!profile.isSubscribed) {
                                GradientSubscribeButton(
                                    colorTheme = colorTheme,
                                    onClick = { viewModel.toggleSubscription() },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            } else if (uiState.isOwner) {
                                // НОВОЕ: владелец не может отписаться от своего канала —
                                // вместо кнопки отписки показываем только переход к постам.
                                Button(
                                    onClick = { viewModel.openChat() },
                                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)
                                ) { Text("Открыть посты", color = Color.White, fontWeight = FontWeight.SemiBold) }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                    Button(
                                        onClick = { viewModel.openChat() },
                                        colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) { Text("Открыть посты", color = Color.White, fontWeight = FontWeight.SemiBold) }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.toggleSubscription() },
                                        modifier = Modifier.height(48.dp)
                                    ) { Text("Отписаться") }
                                }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.openChat() },
                                colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)
                            ) { Text("Открыть посты", color = Color.White, fontWeight = FontWeight.SemiBold) }
                        }

                        // ═══ Последние посты ═══
                        AnimatedVisibility(
                            visible = uiState.recentPosts.isNotEmpty(),
                            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 8 }
                        ) {
                            Column {
                                ChannelSectionTitle("Последние посты")
                                uiState.recentPosts.forEach { post ->
                                    ChannelPostPreviewCard(
                                        post = post,
                                        colorTheme = colorTheme,
                                        onClick = { viewModel.openChat() }
                                    )
                                }
                            }
                        }

                        // ═══ Администрация ═══
                        ChannelSectionTitle("Администрация")
                        uiState.owner?.let { owner ->
                            ChannelAdminRow(user = owner, role = "Владелец", colorTheme = colorTheme,
                                onClick = { onOpenUserProfile(owner.uid) })
                        }
                        uiState.admins.forEach { admin ->
                            ChannelAdminRow(user = admin, role = "Администратор", colorTheme = colorTheme,
                                onClick = { onOpenUserProfile(admin.uid) })
                        }

                        // ═══ Действия владельца ═══
                        if (uiState.isOwner) {
                            OutlinedButton(
                                onClick = { onEditChannel(viewModel.chatId) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Редактировать канал")
                            }
                            // НОВОЕ (система ролей + журнал администраторов): переход к управлению
                            // ролями участников канала и просмотру журнала действий администраторов.
                            OutlinedButton(
                                onClick = { onManageRoles(viewModel.chatId) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Роли и права")
                            }
                            // НОВОЕ: удаление канала владельцем — необратимое действие,
                            // требует явного подтверждения в диалоге.
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Удалить канал")
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        } else {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить канал?") },
            text = { Text("Канал будет удалён безвозвратно для всех подписчиков. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.deleteChannel() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }
}

/**
 * Hero-шапка канала: полноширинный градиентный фон "от края до края",
 * аватар со смещением вниз (тень выходит за пределы градиента), заголовок
 * и статистика в одном визуальном блоке — без разрывов между секциями.
 */
@Composable
private fun ChannelHero(
    profile: app.yodo.messenger.domain.model.ChannelProfile,
    uiState: ChannelProfileUiState,
    colorTheme: ColorTheme
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // НОВОЕ (F5): обложка (баннер) канала над шапкой.
        profile.coverBase64?.let { cover ->
            val coverBmp = remember(cover) { ImageUtils.decodeBase64ToBitmap(cover) }
            if (coverBmp != null) {
                Image(
                    bitmap = coverBmp.asImageBitmap(),
                    contentDescription = "Обложка канала",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(colorTheme.primary, colorTheme.accent),
                    )
                )
                .padding(top = 28.dp, bottom = 56.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        textAlign = TextAlign.Center
                    )
                    if (profile.isVerified) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(22.dp).clip(CircleShape).background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Verified, contentDescription = "Верифицирован",
                                tint = Color(0xFF1D9BF0), modifier = Modifier.size(15.dp))
                        }
                    }
                }
                if (profile.description.isNotBlank()) {
                    Text(
                        profile.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                // НОВОЕ (F5): чипы категории и тегов.
                if (!profile.category.isNullOrBlank() || profile.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profile.category?.takeIf { it.isNotBlank() }?.let { cat ->
                            ChannelMetaChip(text = cat, highlighted = true)
                        }
                        profile.tags.take(3).forEach { tag ->
                            ChannelMetaChip(text = "#$tag", highlighted = false)
                        }
                    }
                }
                Text(
                    buildString {
                        if (profile.isVerified) append("Официальный канал")
                        if (profile.createdAt > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("создан ${formatChannelDate(profile.createdAt)}")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        // Аватар и статистика внахлёст на границу градиента и фона — единая композиция.
        Column(
            modifier = Modifier.fillMaxWidth().offset(y = (-40).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ChannelAvatarFlat(avatarBase64 = profile.avatarBase64, title = profile.title)
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .shadow(2.dp, RoundedCornerShape(18.dp))
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelStat(
                    value = profile.subscriberCount,
                    label = pluralRu(profile.subscriberCount, "подписчик", "подписчика", "подписчиков"),
                    colorTheme = colorTheme,
                    modifier = Modifier.weight(1f)
                )
                StatDivider()
                ChannelStat(
                    value = uiState.postsCount,
                    label = pluralRu(uiState.postsCount, "пост", "поста", "постов"),
                    colorTheme = colorTheme,
                    modifier = Modifier.weight(1f)
                )
                StatDivider()
                ChannelStat(
                    value = uiState.admins.size + (if (uiState.owner != null) 1 else 0),
                    label = pluralRu(uiState.admins.size + 1, "админ", "админа", "админов"),
                    colorTheme = colorTheme,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** НОВОЕ (F5): чип категории/тега канала на полупрозрачном фоне. */
@Composable
private fun ChannelMetaChip(text: String, highlighted: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = if (highlighted) 0.28f else 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Плоский аватар канала на белой подложке с тонкой обводкой — без вращающегося кольца. */
@Composable
private fun ChannelAvatarFlat(avatarBase64: String?, title: String) {
    // Увеличенный аватар канала (переработка каналов — крупнее интерфейс).
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .shadow(4.dp, CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        UserAvatar(
            displayName = title,
            photoUrl = null,
            avatarBase64 = avatarBase64,
            size = 110.dp
        )
    }
}

@Composable
private fun ChannelStat(value: Int, label: String, colorTheme: ColorTheme, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            String.format(Locale("ru"), "%,d", value),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colorTheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    )
}

/** Градиентная CTA-кнопка «Подписаться» с мягко пульсирующим колокольчиком. */
@Composable
private fun GradientSubscribeButton(
    colorTheme: ColorTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "subscribe_pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "bell_pulse"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Brush.horizontalGradient(listOf(colorTheme.primary, colorTheme.accent)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Notifications, contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Подписаться", color = Color.White,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChannelSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = LocalColorTheme.current.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/** Карточка поста в профиле канала — редакторский стиль: акцентная плашка, крупный текст. */
@Composable
private fun ChannelPostPreviewCard(post: Message, colorTheme: ColorTheme, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .shadow(1.dp, shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colorTheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Campaign, contentDescription = null,
                        tint = colorTheme.primary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ПОСТ", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = colorTheme.primary)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                formatChannelDateTime(post.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            post.previewText(),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (post.commentsCount > 0) {
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null,
                    tint = colorTheme.primary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${post.commentsCount} ${pluralRu(post.commentsCount, "комментарий", "комментария", "комментариев")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorTheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ChannelAdminRow(user: YodoUser, role: String, colorTheme: ColorTheme, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            displayName = user.displayName,
            photoUrl = user.photoUrl,
            avatarBase64 = user.avatarBase64,
            size = 44.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(role, style = MaterialTheme.typography.labelSmall, color = colorTheme.primary)
        }
    }
}

private fun formatChannelDate(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(millis))

private fun formatChannelDateTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))

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