package app.yodo.messenger.features.chats

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.People
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.repository.ChannelCategorySection
import app.yodo.messenger.domain.repository.ChannelSearchItem
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * НОВОЕ (каталог/рекомендации каналов): витрина каналов — открывается из FAB-меню
 * списка чатов ("Каталог каналов"), без необходимости вводить поисковый запрос.
 * Сверху — горизонтальная лента "В тренде" (самые крупные каналы, на которые
 * пользователь ещё не подписан), ниже — секции по категориям.
 */
@Composable
fun DiscoverChannelsScreen(
    onBackClick: () -> Unit,
    onChatOpened: (String) -> Unit,
    onOpenChannelProfile: (String) -> Unit,
    viewModel: DiscoverChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val openChatId by viewModel.openChatId.collectAsState()
    val openChannelProfileId by viewModel.openChannelProfileId.collectAsState()
    val colorTheme = LocalColorTheme.current
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = {
                    Column {
                        Text("Каталог каналов", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Подборки и рекомендации",
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = colorTheme.primary)
                uiState.isEmpty -> Text(
                    "Пока нет доступных каналов",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        if (uiState.trending.isNotEmpty()) {
                            item {
                                DirectorySectionHeader(
                                    icon = Icons.Filled.LocalFireDepartment,
                                    title = "В тренде",
                                    colorTheme = colorTheme
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.trending, key = { "tr_${it.chatId}" }) { channel ->
                                        TrendingChannelCard(
                                            channel = channel,
                                            colorTheme = colorTheme,
                                            onClick = { viewModel.openChannel(channel) }
                                        )
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(12.dp)) }
                        }

                        uiState.byCategory.forEach { section ->
                            item(key = "hdr_${section.category}") {
                                DirectorySectionHeader(
                                    icon = Icons.Filled.Campaign,
                                    title = section.category,
                                    colorTheme = colorTheme
                                )
                            }
                            items(section.channels, key = { "cat_${section.category}_${it.chatId}" }) { channel ->
                                DirectoryChannelRow(
                                    channel = channel,
                                    colorTheme = colorTheme,
                                    onClick = { viewModel.openChannel(channel) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectorySectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    colorTheme: ColorTheme
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colorTheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Карточка канала в горизонтальной ленте "В тренде" — крупный аватар, счётчик подписчиков. */
@Composable
private fun TrendingChannelCard(channel: ChannelSearchItem, colorTheme: ColorTheme, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .widthIn(min = 120.dp, max = 130.dp)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(colorTheme.primary, colorTheme.accent, colorTheme.primary)))
                    .padding(2.dp)
            )
            UserAvatar(
                displayName = channel.title,
                photoUrl = null,
                avatarBase64 = channel.avatarBase64,
                size = 60.dp
            )
            if (channel.isVerified) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Verified, contentDescription = "Верифицирован",
                        tint = Color(0xFF1D9BF0), modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            channel.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            "${channel.subscriberCount} ${pluralSubs(channel.subscriberCount)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Строка канала в секции категории — компактная, с описанием/подписчиками. */
@Composable
private fun DirectoryChannelRow(channel: ChannelSearchItem, colorTheme: ColorTheme, onClick: () -> Unit) {
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
            displayName = channel.title,
            photoUrl = null,
            avatarBase64 = channel.avatarBase64,
            size = 48.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (channel.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, contentDescription = "Верифицирован",
                        tint = Color(0xFF1D9BF0), modifier = Modifier.size(14.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    channel.description.ifBlank { "${channel.subscriberCount} ${pluralSubs(channel.subscriberCount)}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (channel.isSubscribed) {
            Surface(shape = CircleShape, color = colorTheme.primary.copy(alpha = 0.12f)) {
                Text(
                    "Подписан",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = colorTheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun pluralSubs(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "подписчиков"
        mod10 == 1 -> "подписчик"
        mod10 in 2..4 -> "подписчика"
        else -> "подписчиков"
    }
}
