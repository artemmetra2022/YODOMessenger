package app.yodo.messenger.features.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.YodoPrimary

@Composable
fun UserProfileScreen(
    onBackClick: () -> Unit,
    onChatOpened: (String) -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val openChatId by viewModel.openChatId.collectAsState()

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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UserAvatar(
                        displayName = user.displayName,
                        photoUrl = user.photoUrl,
                        avatarBase64 = user.avatarBase64,
                        size = 96.dp
                    )

                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    user.username?.let {
                        Text(
                            text = "@$it",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    state.presence?.let { presence ->
                        val statusText = if (presence.isOnline) {
                            "в сети"
                        } else if (presence.lastSeenMillis > 0) {
                            "был(а) в сети недавно"
                        } else null

                        statusText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (presence.isOnline) YodoPrimary else androidx.compose.ui.graphics.Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    if (!user.bio.isNullOrBlank()) {
                        Text(
                            text = "О себе: ${user.bio}",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        )
                    }

                    // ИСПРАВЛЕНИЕ: "Расширенный профиль" раньше не отображался у других вообще —
                    // этот экран рендерил только имя, username, presence и краткое bio, хотя
                    // модель YodoUser и настройки конфиденциальности для этих полей уже
                    // существовали. Теперь показываем всё, что владелец профиля разрешил
                    // показывать (флаги show* приходят из его собственных настроек
                    // конфиденциальности и по умолчанию открыты для всего, кроме телефона/email).
                    val extendedFields = buildList {
                        if (user.showAboutMe && !user.aboutMe.isNullOrBlank()) {
                            add(Triple(Icons.Filled.Info, "О себе", user.aboutMe))
                        }
                        if (user.showBirthDate && !user.birthDate.isNullOrBlank()) {
                            add(Triple(Icons.Filled.CalendarMonth, "Дата рождения", user.birthDate))
                        }
                        if (user.showLocation && !user.location.isNullOrBlank()) {
                            add(Triple(Icons.Filled.LocationOn, "Местоположение", user.location))
                        }
                        if (user.showWebsite && !user.website.isNullOrBlank()) {
                            add(Triple(Icons.Filled.Language, "Сайт", user.website))
                        }
                        if (user.showPhoneNumber && !user.phoneNumber.isNullOrBlank()) {
                            add(Triple(Icons.Filled.Phone, "Телефон", user.phoneNumber))
                        }
                        if (user.showEmail && !user.email.isNullOrBlank()) {
                            add(Triple(Icons.Filled.Email, "Email", user.email))
                        }
                    }

                    if (extendedFields.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(
                            "Расширенный профиль",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                        extendedFields.forEach { (icon, label, value) ->
                            ExtendedProfileRow(icon = icon, label = label, value = value)
                        }
                    }

                    Button(
                        onClick = { viewModel.openChat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Написать сообщение", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtendedProfileRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = YodoPrimary, modifier = Modifier.size(20.dp))
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
