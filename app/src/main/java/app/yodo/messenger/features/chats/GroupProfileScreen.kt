package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.domain.model.ChannelAccessMode
import app.yodo.messenger.domain.model.ChannelProfile
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * НОВОЕ (админ-функции групп): публичный профиль-превью группы из выдачи поиска.
 * Неучастник может посмотреть описание и вступить: открытая группа — «Вступить»,
 * модерируемая — «Подать заявку»/«Отменить заявку»; участник/владелец — «Открыть чат».
 */
@Composable
fun GroupProfileScreen(
    onBackClick: () -> Unit,
    onChatOpened: (String) -> Unit,
    viewModel: GroupProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val openChatId by viewModel.openChatId.collectAsState()
    val colorTheme = LocalColorTheme.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(openChatId) {
        openChatId?.let {
            onChatOpened(it)
            viewModel.consumeOpenChatId()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        GroupProfileHero(profile = profile, colorTheme = colorTheme)
                        GroupAccessModeRow(profile = profile, colorTheme = colorTheme)
                        Spacer(modifier = Modifier.height(16.dp))

                        // ═══ Кнопка вступления/перехода в чат ═══
                        // НОВОЕ: во время выполнения показываем мини-спиннер внутри
                        // кнопки — видно, что нажатие обработано (а не «зависло»).
                        when {
                            uiState.isMember || uiState.isOwner -> {
                                Button(
                                    onClick = { viewModel.openChat() },
                                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)
                                ) { Text("Открыть чат", color = Color.White, fontWeight = FontWeight.SemiBold) }
                            }
                            profile.accessMode == ChannelAccessMode.MODERATED && profile.hasPendingJoinRequest -> {
                                OutlinedButton(
                                    onClick = { viewModel.joinOrRequest() },
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)
                                ) {
                                    if (uiState.isSaving) SavingSpinner(color = colorTheme.primary)
                                    Text("Заявка отправлена · отменить")
                                }
                            }
                            profile.accessMode == ChannelAccessMode.MODERATED -> {
                                Button(
                                    onClick = { viewModel.joinOrRequest() },
                                    enabled = !uiState.isSaving,
                                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)
                                ) {
                                    if (uiState.isSaving) SavingSpinner()
                                    Text("Подать заявку", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = { viewModel.joinOrRequest() },
                                    enabled = !uiState.isSaving,
                                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)
                                ) {
                                    if (uiState.isSaving) SavingSpinner()
                                    Text("Вступить", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/** Шапка: аватар в градиентном кольце, название, описание, число участников. */
@Composable
private fun GroupProfileHero(
    profile: ChannelProfile,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(colorTheme.primary.copy(alpha = 0.16f), Color.Transparent)
                )
            )
            .padding(top = 20.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(colorTheme.primary, colorTheme.accent, colorTheme.primary)))
                    .padding(3.dp)
            )
            UserAvatar(
                displayName = profile.title,
                photoUrl = null,
                avatarBase64 = profile.avatarBase64,
                size = 98.dp
            )
        }
        Text(
            text = profile.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp)
        )
        if (profile.description.isNotBlank()) {
            Text(
                text = profile.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                // НОВОЕ: корректные склонения (1 участник / 2 участника / 5 участников).
                text = pluralStringResource(R.plurals.group_profile_members, profile.subscriberCount, profile.subscriberCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Строка режима доступа (открытая/модерируемая/скрытая). */
@Composable
private fun GroupAccessModeRow(
    profile: ChannelProfile,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (profile.accessMode) {
                ChannelAccessMode.OPEN -> Icons.Filled.Public
                ChannelAccessMode.MODERATED -> Icons.Filled.HowToReg
                ChannelAccessMode.HIDDEN -> Icons.Filled.Lock
            },
            contentDescription = null,
            tint = colorTheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(profile.accessMode.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                profile.accessMode.description,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** НОВОЕ: мини-индикатор загрузки внутри кнопки действия. */
@Composable
private fun SavingSpinner(color: Color = Color.White) {
    CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = color
    )
    Spacer(modifier = Modifier.width(8.dp))
}
