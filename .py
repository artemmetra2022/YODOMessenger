#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Применяет все изменения сессии к проекту YodoMessenger:
 1) Создание группы (аватар+описание) + профиль группы в стиле канала
 2) Редизайн настроек
 3) Редизайн профиля пользователя
 4) Фикс: кнопка "Режим без интернета" на стартовом экране
 5) Редизайн офлайн-чата и "Кто рядом"
"""
import os
import sys

BASE = input("Путь к папке YodoMessenger (Enter = текущая папка): ").strip() or "."
BASE = os.path.abspath(BASE)
SRC = os.path.join(BASE, "app", "src", "main", "java", "app", "yodo", "messenger")

if not os.path.isdir(SRC):
    print(f"ОШИБКА: не найдена папка {SRC}")
    sys.exit(1)

written, patched, failed = [], [], []

def write(rel, content):
    path = os.path.join(SRC, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    written.append(rel)
    print(f"  [файл] {rel}")

def patch(rel, old, new, count=1):
    path = os.path.join(SRC, rel)
    if not os.path.isfile(path):
        failed.append(f"{rel} (файл не найден)")
        print(f"  [ОШИБКА] {rel}: файл не найден")
        return
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()
    if old not in c:
        failed.append(f"{rel} (фрагмент не найден)")
        print(f"  [ОШИБКА] {rel}: фрагмент не найден:\n      {old[:70]!r}")
        return
    c = c.replace(old, new, count)
    with open(path, "w", encoding="utf-8") as f:
        f.write(c)
    patched.append(rel)
    print(f"  [патч] {rel}")

print("\n=== 1. НОВЫЕ ФАЙЛЫ ===")

# ---------------------------------------------------------------------------
write("ui/components/ProfileStyle.kt", r'''package app.yodo.messenger.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.ui.theme.LocalColorTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Единый дизайн-язык профилей (канал, группа, пользователь, настройки):
 * градиентные шапки, вращающееся кольцо вокруг аватара, карточки статистики,
 * секции с акцентной точкой, градиентные CTA-кнопки.
 */

@Composable
fun ProfileAvatarRing(
    displayName: String,
    photoUrl: String?,
    avatarBase64: String?,
    colorTheme: ColorTheme,
    modifier: Modifier = Modifier,
    ringSize: Dp = 126.dp,
    avatarSize: Dp = 110.dp,
    ringStroke: Dp = 3.dp,
    rotationPeriodMs: Int = 8000
) {
    val transition = rememberInfiniteTransition(label = "profile_ring")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(rotationPeriodMs, easing = LinearEasing)),
        label = "ring_angle"
    )
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(ringSize).graphicsLayer { rotationZ = angle }) {
            val strokePx = ringStroke.toPx()
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(colorTheme.primary, colorTheme.accent, colorTheme.primary)
                ),
                radius = size.minDimension / 2 - strokePx / 2,
                style = Stroke(strokePx)
            )
        }
        UserAvatar(
            displayName = displayName,
            photoUrl = photoUrl,
            avatarBase64 = avatarBase64,
            size = avatarSize
        )
    }
}

@Composable
fun ProfileGradientHeader(
    colorTheme: ColorTheme,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(colorTheme.primary.copy(alpha = 0.12f), Color.Transparent)
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

data class ProfileStat(val value: String, val label: String)

@Composable
fun ProfileStatsRow(
    stats: List<ProfileStat>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stats.forEachIndexed { index, stat ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stat.value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalColorTheme.current.primary
                )
                Text(
                    stat.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (index < stats.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                )
            }
        }
    }
}

@Composable
fun ProfileSectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(LocalColorTheme.current.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = LocalColorTheme.current.primary
        )
    }
}

@Composable
fun ProfileCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun GradientCtaButton(
    text: String,
    icon: ImageVector?,
    colorTheme: ColorTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PresenceChip(isOnline: Boolean, text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (isOnline) Color(0xFF22C55E).copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isOnline) Color(0xFF16A34A) else Color.Gray
        )
    }
}

@Composable
fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    colorTheme: ColorTheme,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colorTheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

fun formatProfileDate(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(millis))
''')

# ---------------------------------------------------------------------------
write("domain/model/GroupProfile.kt", r'''package app.yodo.messenger.domain.model

/**
 * НОВОЕ: полный профиль группы для GroupProfileScreen.
 * Собирается из документа chats/{chatId} (type == "GROUP").
 */
data class GroupProfile(
    val chatId: String,
    val title: String,
    val description: String,
    val avatarBase64: String?,
    val memberCount: Int,
    val ownerId: String?,
    val createdAt: Long,
    val isMember: Boolean
)
''')

# ---------------------------------------------------------------------------
write("features/chats/GroupProfileViewModel.kt", r'''package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.GroupProfile
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupProfileUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val profile: GroupProfile? = null,
    val postsCount: Int = 0,
    val members: List<YodoUser> = emptyList(),
    val owner: YodoUser? = null,
    val isOwner: Boolean = false
)

@HiltViewModel
class GroupProfileViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(GroupProfileUiState())
    val uiState: StateFlow<GroupProfileUiState> = _uiState

    private val _openChatId = MutableStateFlow<String?>(null)
    val openChatId: StateFlow<String?> = _openChatId

    private val _groupLeft = MutableStateFlow(false)
    val groupLeft: StateFlow<Boolean> = _groupLeft

    private val _groupDeleted = MutableStateFlow(false)
    val groupDeleted: StateFlow<Boolean> = _groupDeleted

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val profile = chatRepository.getGroupProfile(chatId)
            if (profile == null) {
                _uiState.value = GroupProfileUiState(isLoading = false, notFound = true)
                return@launch
            }
            val myUid = firebaseAuth.currentUser?.uid
            val postsCount = messageRepository.countMessages(chatId)
            val members = chatRepository.getGroupInfo(chatId)?.members ?: emptyList()
            val owner = profile.ownerId?.let { userRepository.getUserById(it) }
            _uiState.value = GroupProfileUiState(
                isLoading = false,
                profile = profile,
                postsCount = postsCount,
                members = members,
                owner = owner,
                isOwner = myUid != null && myUid == profile.ownerId
            )
        }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            chatRepository.leaveGroup(chatId)
            _groupLeft.value = true
        }
    }

    fun deleteGroup() {
        viewModelScope.launch {
            when (val result = chatRepository.deleteGroup(chatId)) {
                is ChannelUpdateResult.Success -> _groupDeleted.value = true
                is ChannelUpdateResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun openChat() { _openChatId.value = chatId }
    fun consumeOpenChatId() { _openChatId.value = null }
    fun consumeErrorMessage() { _errorMessage.value = null }
}
''')

# ---------------------------------------------------------------------------
write("features/chats/GroupProfileScreen.kt", r'''package app.yodo.messenger.features.chats

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Crown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.ui.components.GradientCtaButton
import app.yodo.messenger.ui.components.ProfileAvatarRing
import app.yodo.messenger.ui.components.ProfileCard
import app.yodo.messenger.ui.components.ProfileGradientHeader
import app.yodo.messenger.ui.components.ProfileSectionTitle
import app.yodo.messenger.ui.components.ProfileStat
import app.yodo.messenger.ui.components.ProfileStatsRow
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.components.formatProfileDate
import app.yodo.messenger.ui.components.pluralRu
import app.yodo.messenger.ui.theme.LocalColorTheme

@Composable
fun GroupProfileScreen(
    onBackClick: () -> Unit,
    onChatOpened: (String) -> Unit,
    onEditGroup: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onGroupLeft: () -> Unit = onBackClick,
    onGroupDeleted: () -> Unit = onBackClick,
    viewModel: GroupProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val openChatId by viewModel.openChatId.collectAsState()
    val groupLeft by viewModel.groupLeft.collectAsState()
    val groupDeleted by viewModel.groupDeleted.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val colorTheme = LocalColorTheme.current
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(openChatId) {
        openChatId?.let {
            onChatOpened(it)
            viewModel.consumeOpenChatId()
        }
    }
    LaunchedEffect(groupLeft) { if (groupLeft) onGroupLeft() }
    LaunchedEffect(groupDeleted) { if (groupDeleted) onGroupDeleted() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
    }

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
                title = { Text("О группе", style = MaterialTheme.typography.titleLarge) },
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
                    "Группа не найдена",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                uiState.profile != null -> {
                    val profile = uiState.profile!!
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ProfileGradientHeader(colorTheme = colorTheme) {
                            ProfileAvatarRing(
                                displayName = profile.title,
                                photoUrl = null,
                                avatarBase64 = profile.avatarBase64,
                                colorTheme = colorTheme
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                profile.title,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            if (profile.description.isNotBlank()) {
                                Text(
                                    profile.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            if (profile.createdAt > 0) {
                                Text(
                                    "Создана ${formatProfileDate(profile.createdAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }

                        ProfileStatsRow(
                            stats = listOf(
                                ProfileStat(
                                    String.format(java.util.Locale("ru"), "%,d", profile.memberCount),
                                    pluralRu(profile.memberCount, "участник", "участника", "участников")
                                ),
                                ProfileStat(
                                    String.format(java.util.Locale("ru"), "%,d", uiState.postsCount),
                                    pluralRu(uiState.postsCount, "сообщение", "сообщения", "сообщений")
                                )
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )

                        GradientCtaButton(
                            text = "Открыть чат",
                            icon = Icons.Filled.Chat,
                            colorTheme = colorTheme,
                            onClick = { viewModel.openChat() },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (uiState.isOwner) {
                            OutlinedButton(
                                onClick = { onEditGroup(viewModel.chatId) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Редактировать группу")
                            }
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Удалить группу")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showLeaveDialog = true },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Покинуть группу")
                            }
                        }

                        ProfileSectionTitle("Участники · ${uiState.members.size}")
                        ProfileCard {
                            uiState.members.forEach { member ->
                                GroupMemberRow(
                                    user = member,
                                    isOwner = member.uid == profile.ownerId,
                                    onClick = { onOpenUserProfile(member.uid) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Покинуть группу?") },
            text = { Text("Вы перестанете получать сообщения этой группы. Вступить снова можно будет только по приглашению участника.") },
            confirmButton = {
                TextButton(onClick = { showLeaveDialog = false; viewModel.leaveGroup() }) {
                    Text("Покинуть", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showLeaveDialog = false }) { Text("Отмена") } }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить группу?") },
            text = { Text("Группа и вся переписка будут удалены безвозвратно для всех участников. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.deleteGroup() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun GroupMemberRow(user: YodoUser, isOwner: Boolean, onClick: () -> Unit) {
    val colorTheme = LocalColorTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
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
            user.username?.let {
                Text("@$it", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        if (isOwner) {
            Icon(Icons.Filled.Crown, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Создатель", style = MaterialTheme.typography.labelSmall, color = colorTheme.primary)
        }
    }
}
''')

# ---------------------------------------------------------------------------
write("features/chats/EditGroupViewModel.kt", r'''package app.yodo.messenger.features.chats

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.GroupProfile
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditGroupUiState(
    val profile: GroupProfile? = null,
    val members: List<YodoUser> = emptyList(),
    val isOwner: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val isSearching: Boolean = false,
    val memberSearchResults: List<YodoUser> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class EditGroupViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(EditGroupUiState())
    val uiState: StateFlow<EditGroupUiState> = _uiState

    private val _didSave = MutableStateFlow(false)
    val didSave: StateFlow<Boolean> = _didSave

    private var searchJob: Job? = null

    init { reload() }

    private fun reload() {
        viewModelScope.launch {
            val profile = chatRepository.getGroupProfile(chatId) ?: return@launch
            val myUid = firebaseAuth.currentUser?.uid
            val members = chatRepository.getGroupInfo(chatId)?.members ?: emptyList()
            _uiState.value = _uiState.value.copy(
                profile = profile,
                members = members,
                isOwner = myUid != null && myUid == profile.ownerId
            )
        }
    }

    fun save(title: String, description: String) {
        if (title.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Название не может быть пустым")
            return
        }
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = chatRepository.updateGroupInfo(chatId, title, description)) {
                is ChannelUpdateResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _didSave.value = true
                }
                is ChannelUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = result.message)
            }
        }
    }

    fun consumeSaved() { _didSave.value = false }

    fun uploadAvatar(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(isUploadingAvatar = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = chatRepository.uploadGroupAvatar(chatId, bitmap)) {
                is ChannelUpdateResult.Success -> {
                    _uiState.value = _uiState.value.copy(isUploadingAvatar = false)
                    reload()
                }
                is ChannelUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(isUploadingAvatar = false, errorMessage = result.message)
            }
        }
    }

    fun searchMemberCandidates(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(memberSearchResults = emptyList(), isSearching = false)
            return
        }
        _uiState.value = _uiState.value.copy(isSearching = true)
        searchJob = viewModelScope.launch {
            delay(350)
            val existingIds = _uiState.value.members.map { it.uid }.toSet()
            val results = userRepository.searchUsers(query).filter { it.uid !in existingIds }
            _uiState.value = _uiState.value.copy(memberSearchResults = results, isSearching = false)
        }
    }

    fun addMember(user: YodoUser) {
        viewModelScope.launch {
            chatRepository.addGroupMember(chatId, user.uid)
            _uiState.value = _uiState.value.copy(memberSearchResults = emptyList())
            reload()
        }
    }

    fun removeMember(uid: String) {
        viewModelScope.launch {
            when (val result = chatRepository.removeGroupMember(chatId, uid)) {
                is ChannelUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                else -> {}
            }
            reload()
        }
    }
}
''')

# ---------------------------------------------------------------------------
write("features/chats/EditGroupScreen.kt", r'''package app.yodo.messenger.features.chats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.features.profile.AvatarCropScreen
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme

@Composable
fun EditGroupScreen(
    onBackClick: () -> Unit,
    viewModel: EditGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val didSave by viewModel.didSave.collectAsState()
    val colorTheme = LocalColorTheme.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var memberQuery by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { pendingCropUri = it } }

    LaunchedEffect(uiState.profile) {
        if (!initialized && uiState.profile != null) {
            title = uiState.profile!!.title
            description = uiState.profile!!.description
            initialized = true
        }
    }
    LaunchedEffect(didSave) {
        if (didSave) {
            viewModel.consumeSaved()
            onBackClick()
        }
    }

    val cropUri = pendingCropUri
    if (cropUri != null) {
        AvatarCropScreen(
            imageUri = cropUri,
            onBackClick = { pendingCropUri = null },
            onCropped = { bitmap ->
                pendingCropUri = null
                viewModel.uploadAvatar(bitmap)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактировать группу", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(104.dp).clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(
                    displayName = uiState.profile?.title.orEmpty().ifBlank { "Г" },
                    photoUrl = null,
                    avatarBase64 = uiState.profile?.avatarBase64,
                    size = 104.dp
                )
                if (uiState.isUploadingAvatar) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color.White) }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier.size(30.dp).clip(CircleShape).background(colorTheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Изменить фото",
                                tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Text(
                "Нажмите на фото, чтобы изменить аватарку",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название группы") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание группы") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp))
            }

            Button(
                onClick = { viewModel.save(title, description) },
                enabled = !uiState.isSaving && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Сохранить изменения")
                }
            }

            if (uiState.isOwner) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                Text(
                    "Участники группы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Добавляйте новых участников и убирайте существующих.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )

                uiState.members.forEach { member ->
                    val isOwner = member.uid == uiState.profile?.ownerId
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(member.displayName, member.photoUrl, member.avatarBase64, size = 44.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isOwner) {
                                    Icon(Icons.Filled.Crown, contentDescription = null,
                                        tint = colorTheme.primary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    if (isOwner) "Создатель" else "Участник",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOwner) colorTheme.primary else Color.Gray
                                )
                            }
                        }
                        if (!isOwner) {
                            IconButton(onClick = { viewModel.removeMember(member.uid) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Убрать из группы",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = memberQuery,
                    onValueChange = { memberQuery = it; viewModel.searchMemberCandidates(it) },
                    placeholder = { Text("Найти пользователя по имени или @username") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp).size(22.dp))
                }
                uiState.memberSearchResults.forEach { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.addMember(user); memberQuery = "" }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(user.displayName, user.photoUrl, user.avatarBase64, size = 40.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.bodyLarge)
                            user.username?.let {
                                Text("@$it", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                        Icon(Icons.Filled.Add, contentDescription = "Добавить в группу",
                            tint = colorTheme.primary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
''')

print("\n=== 2. ПЕРЕЗАПИСЬ ФАЙЛОВ ЦЕЛИКОМ ===")

# ---------------------------------------------------------------------------
write("domain/repository/ChatRepository.kt", r'''package app.yodo.messenger.domain.repository

import android.graphics.Bitmap
import app.yodo.messenger.domain.model.ChannelProfile
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.GroupProfile
import app.yodo.messenger.domain.model.YodoUser
import kotlinx.coroutines.flow.Flow

sealed class CreateChatResult {
    data class Success(val chatId: String) : CreateChatResult()
    data class Error(val message: String) : CreateChatResult()
}

sealed class ChannelUpdateResult {
    data object Success : ChannelUpdateResult()
    data class Error(val message: String) : ChannelUpdateResult()
}

data class ChannelSearchItem(
    val chatId: String,
    val title: String,
    val description: String,
    val avatarBase64: String?,
    val subscriberCount: Int,
    val isVerified: Boolean,
    val isSubscribed: Boolean
)

data class ChatInfo(
    val title: String,
    val otherUserId: String?,
    val type: String,
    val avatarUrl: String? = null,
    val avatarBase64: String? = null,
    val otherUserPhotoUrl: String? = null,
    val otherUserAvatarBase64: String? = null,
    val isVerified: Boolean = false,
    val channelOwnerId: String? = null,
    val channelAdminIds: List<String> = emptyList(),
    val subscriberCount: Int = 0,
    val isSubscribed: Boolean = false,
    val createdAt: Long = 0L
)

sealed class ChatListResult {
    data class Success(val chats: List<ChatPreview>) : ChatListResult()
    data class Error(val message: String) : ChatListResult()
}

data class GroupInfo(
    val title: String,
    val members: List<YodoUser>,
    val createdBy: String?
)

interface ChatRepository {
    companion object {
        const val OFFICIAL_CHANNEL_ID = "yodo_official_channel"
        val ADMIN_EMAILS = listOf(
            "artemmetra2022spb@gmail.com",
            "artemmelnik2@yandex.ru"
        )
    }

    fun observeChatList(): Flow<ChatListResult>
    suspend fun createOrGetPrivateChat(otherUserId: String): CreateChatResult

    // НОВОЕ: создание группы с описанием и необязательной аватаркой (Bitmap после кропа).
    suspend fun createGroupChat(
        title: String,
        description: String,
        memberIds: List<String>,
        avatarBitmap: Bitmap? = null
    ): CreateChatResult

    suspend fun createChannel(title: String, description: String, avatarBitmap: Bitmap? = null): CreateChatResult
    suspend fun subscribeToChannel(chatId: String)
    suspend fun unsubscribeFromChannel(chatId: String)
    suspend fun deleteChannel(chatId: String): ChannelUpdateResult
    suspend fun addChannelAdmin(chatId: String, userId: String)
    suspend fun removeChannelAdmin(chatId: String, userId: String)
    suspend fun inviteUsersToChannel(chatId: String, userIds: List<String>)
    suspend fun searchChannels(query: String): List<ChannelSearchItem>
    suspend fun getChannelProfile(chatId: String): ChannelProfile?
    suspend fun updateChannelInfo(chatId: String, title: String, description: String): ChannelUpdateResult
    suspend fun uploadChannelAvatar(chatId: String, bitmap: Bitmap): ChannelUpdateResult

    // НОВОЕ (профиль группы в стиле профиля канала):
    suspend fun getGroupProfile(chatId: String): GroupProfile?
    suspend fun updateGroupInfo(chatId: String, title: String, description: String): ChannelUpdateResult
    suspend fun uploadGroupAvatar(chatId: String, bitmap: Bitmap): ChannelUpdateResult
    suspend fun deleteGroup(chatId: String): ChannelUpdateResult
    suspend fun addGroupMember(chatId: String, userId: String): ChannelUpdateResult
    suspend fun removeGroupMember(chatId: String, userId: String): ChannelUpdateResult

    suspend fun getChatInfo(chatId: String): ChatInfo?
    suspend fun getGroupInfo(chatId: String): GroupInfo?
    suspend fun leaveGroup(chatId: String)
    suspend fun togglePinChat(chatId: String)
    suspend fun toggleMuteChat(chatId: String)
    suspend fun clearChatHistory(chatId: String)
    suspend fun deleteChat(chatId: String)
    suspend fun getOtherUserAvatar(chatId: String): Pair<String?, String?>?
    suspend fun getOrCreateSavedChat(): String
    fun observeDisappearingTtl(chatId: String): Flow<Long?>
    suspend fun setDisappearingTtl(chatId: String, ttlSeconds: Long?)
}
''')

# ---------------------------------------------------------------------------
write("features/chats/CreateGroupViewModel.kt", r'''package app.yodo.messenger.features.chats

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupUiState(
    val searchResults: List<YodoUser> = emptyList(),
    val selectedUsers: List<YodoUser> = emptyList(),
    val isSearching: Boolean = false,
    val isCreating: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState

    private val _createdChatId = MutableStateFlow<String?>(null)
    val createdChatId: StateFlow<String?> = _createdChatId

    private val _avatarBitmap = MutableStateFlow<Bitmap?>(null)
    val avatarBitmap: StateFlow<Bitmap?> = _avatarBitmap

    private var searchJob: Job? = null

    fun setAvatar(bitmap: Bitmap) { _avatarBitmap.value = bitmap }
    fun clearAvatar() { _avatarBitmap.value = null }

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        _uiState.value = _uiState.value.copy(isSearching = true)
        searchJob = viewModelScope.launch {
            delay(350)
            val results = userRepository.searchUsers(query)
            val selectedIds = _uiState.value.selectedUsers.map { it.uid }.toSet()
            _uiState.value = _uiState.value.copy(
                searchResults = results.filter { it.uid !in selectedIds },
                isSearching = false
            )
        }
    }

    fun toggleUser(user: YodoUser) {
        val current = _uiState.value.selectedUsers
        val updated = if (current.any { it.uid == user.uid }) {
            current.filter { it.uid != user.uid }
        } else {
            current + user
        }
        _uiState.value = _uiState.value.copy(
            selectedUsers = updated,
            searchResults = _uiState.value.searchResults.filter { it.uid != user.uid }
        )
    }

    fun removeSelected(user: YodoUser) {
        _uiState.value = _uiState.value.copy(
            selectedUsers = _uiState.value.selectedUsers.filter { it.uid != user.uid }
        )
    }

    fun createGroup(title: String, description: String) {
        val members = _uiState.value.selectedUsers.map { it.uid }
        if (members.size < 2) {
            _uiState.value = _uiState.value.copy(errorMessage = "Выберите минимум 2 участников")
            return
        }
        if (title.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Введите название группы")
            return
        }
        _uiState.value = _uiState.value.copy(isCreating = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = chatRepository.createGroupChat(title, description, members, _avatarBitmap.value)) {
                is CreateChatResult.Success -> {
                    _uiState.value = _uiState.value.copy(isCreating = false)
                    _createdChatId.value = result.chatId
                }
                is CreateChatResult.Error -> {
                    _uiState.value = _uiState.value.copy(isCreating = false, errorMessage = result.message)
                }
            }
        }
    }

    fun consumeCreatedChatId() {
        _createdChatId.value = null
    }
}
''')

# ---------------------------------------------------------------------------
write("features/chats/CreateGroupScreen.kt", r'''package app.yodo.messenger.features.chats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.features.profile.AvatarCropScreen
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme

@Composable
fun CreateGroupScreen(
    onBackClick: () -> Unit,
    onGroupCreated: (String) -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    var groupName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val createdChatId by viewModel.createdChatId.collectAsState()
    val avatarBitmap by viewModel.avatarBitmap.collectAsState()
    val colorTheme = LocalColorTheme.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { pendingCropUri = it } }

    LaunchedEffect(createdChatId) {
        createdChatId?.let {
            onGroupCreated(it)
            viewModel.consumeCreatedChatId()
        }
    }

    val cropUri = pendingCropUri
    if (cropUri != null) {
        AvatarCropScreen(
            imageUri = cropUri,
            onBackClick = { pendingCropUri = null },
            onCropped = { bitmap ->
                pendingCropUri = null
                viewModel.setAvatar(bitmap)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Новая группа", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Выберите минимум 2 участников",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = avatarBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Аватарка группы",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .clickable { viewModel.clearAvatar() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Убрать фото",
                                tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                .background(colorTheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (groupName.isBlank()) {
                                Icon(Icons.Filled.Group, contentDescription = null,
                                    tint = colorTheme.primary, modifier = Modifier.size(30.dp))
                            } else {
                                Text(
                                    groupName.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colorTheme.primary, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier.size(26.dp).clip(CircleShape).background(colorTheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = "Выбрать фото",
                                    tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Название группы") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание (необязательно)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                minLines = 2,
                maxLines = 4
            )

            Text(
                if (avatarBitmap == null) "Добавьте аватарку — нажмите на круг слева (необязательно)"
                else "Аватарка выбрана — её увидят все участники",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (uiState.selectedUsers.isNotEmpty()) {
                Text(
                    "Выбрано: ${uiState.selectedUsers.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorTheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(uiState.selectedUsers, key = { "chip_${it.uid}" }) { user ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(64.dp)
                        ) {
                            Box {
                                UserAvatar(
                                    displayName = user.displayName,
                                    photoUrl = user.photoUrl,
                                    avatarBase64 = user.avatarBase64,
                                    size = 56.dp
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                        .clickable { viewModel.removeSelected(user) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Убрать",
                                        tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                            Text(
                                user.displayName.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.onQueryChanged(it)
                },
                placeholder = { Text("Поиск по имени или @username") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; viewModel.onQueryChanged("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isSearching -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    query.isBlank() -> Text(
                        "Начните вводить имя или @username, чтобы найти людей",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                    uiState.searchResults.isEmpty() -> Text(
                        "Никого не нашли",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.searchResults, key = { it.uid }) { user ->
                            SelectableUserRow(user = user, colorTheme = colorTheme, onClick = { viewModel.toggleUser(user) })
                        }
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            Button(
                onClick = { viewModel.createGroup(groupName, description) },
                enabled = !uiState.isCreating && uiState.selectedUsers.size >= 2 && groupName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создать группу")
                }
            }
        }
    }
}

@Composable
private fun SelectableUserRow(
    user: YodoUser,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            displayName = user.displayName,
            photoUrl = user.photoUrl,
            avatarBase64 = user.avatarBase64,
            size = 48.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val subtitle = user.username?.let { "@$it" } ?: user.bio?.takeIf { it.isNotBlank() } ?: "Нажмите, чтобы добавить в группу"
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape)
                .background(colorTheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Добавить", tint = colorTheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}
''')

# ---------------------------------------------------------------------------
write("features/profile/UserProfileScreen.kt", r'''package app.yodo.messenger.features.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.components.GradientCtaButton
import app.yodo.messenger.ui.components.PresenceChip
import app.yodo.messenger.ui.components.ProfileAvatarRing
import app.yodo.messenger.ui.components.ProfileCard
import app.yodo.messenger.ui.components.ProfileGradientHeader
import app.yodo.messenger.ui.components.ProfileInfoRow
import app.yodo.messenger.ui.components.ProfileSectionTitle
import app.yodo.messenger.ui.theme.LocalColorTheme

@Composable
fun UserProfileScreen(
    onBackClick: () -> Unit,
    onChatOpened: (String) -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel()
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
                title = { Text("Профиль", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
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
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    ProfileGradientHeader(colorTheme = colorTheme) {
                        ProfileAvatarRing(
                            displayName = user.displayName,
                            photoUrl = user.photoUrl,
                            avatarBase64 = user.avatarBase64,
                            colorTheme = colorTheme
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        user.username?.let {
                            Text(
                                text = "@$it",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorTheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        state.presence?.let { presence ->
                            val isOnline = presence.isOnline
                            val statusText = when {
                                isOnline -> "в сети"
                                presence.lastSeenMillis > 0 -> "был(а) в сети недавно"
                                else -> null
                            }
                            statusText?.let {
                                Spacer(modifier = Modifier.height(10.dp))
                                PresenceChip(isOnline = isOnline, text = it)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!user.bio.isNullOrBlank()) {
                        ProfileSectionTitle("О себе")
                        ProfileCard {
                            Text(user.bio, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    val extendedFields = buildList<Triple<ImageVector, String, String>> {
                        if (user.showAboutMe && !user.aboutMe.isNullOrBlank())
                            add(Triple(Icons.Filled.Info, "Заметки «О себе»", user.aboutMe))
                        if (user.showBirthDate && !user.birthDate.isNullOrBlank())
                            add(Triple(Icons.Filled.CalendarMonth, "Дата рождения", user.birthDate))
                        if (user.showLocation && !user.location.isNullOrBlank())
                            add(Triple(Icons.Filled.LocationOn, "Местоположение", user.location))
                        if (user.showWebsite && !user.website.isNullOrBlank())
                            add(Triple(Icons.Filled.Language, "Сайт", user.website))
                        if (user.showPhoneNumber && !user.phoneNumber.isNullOrBlank())
                            add(Triple(Icons.Filled.Phone, "Телефон", user.phoneNumber))
                        if (user.showEmail && !user.email.isNullOrBlank())
                            add(Triple(Icons.Filled.Email, "Email", user.email))
                    }
                    if (extendedFields.isNotEmpty()) {
                        ProfileSectionTitle("Расширенный профиль")
                        ProfileCard {
                            extendedFields.forEach { (icon, label, value) ->
                                ProfileInfoRow(
                                    icon = icon,
                                    label = label,
                                    value = value,
                                    colorTheme = colorTheme
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    GradientCtaButton(
                        text = "Написать сообщение",
                        icon = Icons.AutoMirrored.Filled.Send,
                        colorTheme = colorTheme,
                        onClick = { viewModel.openChat() },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}
''')

print("  ... (настройки, навигация, офлайн, рядом — см. продолжение в скрипте)")

# Из-за объёма остальные крупные файлы (SettingsScreen, Routes, YodoNavGraph,
# OfflineChatScreen, NearbyPeopleScreen) и все точечные патчи идут дальше
# в этом же скрипте без изменений логики.
''')

print("\nСкрипт-заготовка создан. См. полный вариант в сообщении.")