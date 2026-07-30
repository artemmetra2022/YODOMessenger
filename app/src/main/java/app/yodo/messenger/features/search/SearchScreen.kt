package app.yodo.messenger.features.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChannelSearchItem
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * ПЕРЕДЕЛАНО (редизайн поиска): та же цветовая палитра, что в ChannelProfileScreen —
 * градиентная шапка (primary → прозрачный), поле ввода в скруглённой "пилюле"
 * с мягкой тенью, карточки каналов и людей с приподнятыми тенями и градиентным
 * кольцом вокруг аватара канала, иконки-бейджи вместо голых текстовых подписей.
 * Ищет и людей, и каналы; каналы выводятся отдельной секцией сверху; тап по
 * каналу — в его профиль (если не подписан) или сразу в чат (если подписан).
 */
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onChatOpened: (String) -> Unit,
    onViewProfile: (String) -> Unit,
    onOpenChannelProfile: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val openChatId by viewModel.openChatId.collectAsState()
    val openChannelProfileId by viewModel.openChannelProfileId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val colorTheme = LocalColorTheme.current

    LaunchedEffect(openChatId) {
        openChatId?.let {
            onChatOpened(it)
            viewModel.consumeOpenChatId()
        }
    }
    LaunchedEffect(openChannelProfileId) {
        openChannelProfileId?.let {
            onOpenChannelProfile(it)
            viewModel.consumeOpenChannelProfileId()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ═══ Градиентная шапка с полем поиска — палитра профиля канала ═══
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(colorTheme.primary.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    SearchPill(
                        query = query,
                        onQueryChanged = {
                            query = it
                            viewModel.onQueryChanged(it)
                        },
                        colorTheme = colorTheme,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is SearchUiState.Idle -> {
                        EmptyStateHint(
                            icon = Icons.Filled.Search,
                            text = "Найдите людей по имени или @username —\nили каналы по названию",
                            colorTheme = colorTheme
                        )
                    }
                    is SearchUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = colorTheme.primary
                        )
                    }
                    is SearchUiState.NoResults -> {
                        EmptyStateHint(
                            icon = Icons.Filled.Search,
                            text = "Никого и ничего не нашли",
                            colorTheme = colorTheme
                        )
                    }
                    is SearchUiState.Results -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            // ═══ Секция каналов ═══
                            if (state.channels.isNotEmpty()) {
                                item { SearchSectionHeader(title = "Каналы", count = state.channels.size, colorTheme = colorTheme) }
                                items(state.channels, key = { "ch_${it.chatId}" }) { channel ->
                                    ChannelResultCard(
                                        channel = channel,
                                        colorTheme = colorTheme,
                                        onClick = { viewModel.openChannel(channel) }
                                    )
                                }
                            }
                            // ═══ Секция людей ═══
                            if (state.users.isNotEmpty()) {
                                item { SearchSectionHeader(title = "Люди", count = state.users.size, colorTheme = colorTheme) }
                                items(state.users, key = { "us_${it.uid}" }) { user ->
                                    UserResultCard(
                                        user = user,
                                        colorTheme = colorTheme,
                                        onClick = { viewModel.openChatWith(user) },
                                        onAvatarClick = { onViewProfile(user.uid) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Поле поиска-«пилюля» с мягкой тенью и акцентной иконкой — как в профиле канала. */
@Composable
private fun SearchPill(
    query: String,
    onQueryChanged: (String) -> Unit,
    colorTheme: ColorTheme,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .height(48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Имя, @username или канал",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(colorTheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChanged("") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Clear, contentDescription = "Очистить", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Заголовок секции с акцентной точкой и счётчиком найденного — единый стиль с профилем канала. */
@Composable
private fun SearchSectionHeader(title: String, count: Int, colorTheme: ColorTheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colorTheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colorTheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
            shape = CircleShape,
            color = colorTheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = colorTheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateHint(icon: ImageVector, text: String, colorTheme: ColorTheme) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colorTheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Карточка канала в выдаче поиска — приподнятая, с градиентным кольцом вокруг аватара. */
@Composable
private fun ChannelResultCard(channel: ChannelSearchItem, colorTheme: ColorTheme, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(colorTheme.primary, colorTheme.accent, colorTheme.primary)))
                    .padding(2.dp)
            )
            UserAvatar(
                displayName = channel.title,
                photoUrl = null,
                avatarBase64 = channel.avatarBase64,
                size = 48.dp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Campaign, contentDescription = null,
                    tint = colorTheme.primary, modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (channel.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier.size(16.dp)
                            .clip(CircleShape).background(Color(0xFF22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = "Верифицирован",
                            tint = Color(0xFF1D9BF0), modifier = Modifier.size(10.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = channel.description.ifBlank {
                        "${channel.subscriberCount} ${pluralSubscribers(channel.subscriberCount)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (channel.isSubscribed) {
            Surface(
                shape = CircleShape,
                color = colorTheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    "Подписан",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = colorTheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colorTheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ChevronRight, contentDescription = null,
                    tint = colorTheme.primary, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Карточка человека в выдаче поиска — тот же приподнятый стиль, что и у карточки канала. */
@Composable
private fun UserResultCard(user: YodoUser, colorTheme: ColorTheme, onClick: () -> Unit, onAvatarClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            displayName = user.displayName,
            photoUrl = user.photoUrl,
            avatarBase64 = user.avatarBase64,
            size = 48.dp,
            modifier = Modifier.clickable(onClick = onAvatarClick)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            user.username?.let {
                Text(
                    text = "@$it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorTheme.primary
                )
            }
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colorTheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = colorTheme.primary, modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun pluralSubscribers(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "подписчиков"
        mod10 == 1 -> "подписчик"
        mod10 in 2..4 -> "подписчика"
        else -> "подписчиков"
    }
}
