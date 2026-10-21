package app.yodo.messenger.features.chats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.util.FileUtils
import app.yodo.messenger.util.ImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * НОВОЕ (раздел «Медиа, файлы, ссылки»): все вложения чата в одном месте — фото и
 * голосовые, документы и ссылки из текста. Логика выборки — в ChatMediaViewModel:
 * сообщения читаются страницами и раскладываются по вкладкам на клиенте.
 */
@Composable
fun ChatMediaScreen(
    onBackClick: () -> Unit,
    onOpenImageViewer: (imageBase64: String, senderName: String, timestamp: Long) -> Unit,
    viewModel: ChatMediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current
    val tabs = listOf(
        ChatMediaTab.MEDIA to "Медиа",
        ChatMediaTab.FILES to "Файлы",
        ChatMediaTab.LINKS to "Ссылки"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Медиа, файлы, ссылки") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == uiState.tab }.coerceAtLeast(0)) {
                tabs.forEach { (tab, title) ->
                    Tab(
                        selected = uiState.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(title) }
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.items.isEmpty() && uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    uiState.items.isEmpty() && uiState.initialLoadDone -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val loadError = uiState.error
                            Text(
                                text = loadError ?: when (uiState.tab) {
                                    ChatMediaTab.MEDIA -> "В этом чате пока нет фото и голосовых сообщений"
                                    ChatMediaTab.FILES -> "В этом чате пока нет файлов"
                                    ChatMediaTab.LINKS -> "В этом чате пока нет ссылок"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (loadError != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (loadError != null) {
                                androidx.compose.material3.TextButton(onClick = viewModel::loadMore) {
                                    Text("Повторить")
                                }
                            } else if (!uiState.endReached) {
                                // История просматривается страницами: если вложения могут быть
                                // глубже, не сканируем чат целиком автоматически — даём кнопку.
                                androidx.compose.material3.TextButton(onClick = viewModel::loadMore) {
                                    Text("Показать ещё")
                                }
                            }
                        }
                    }
                    uiState.tab == ChatMediaTab.MEDIA -> MediaGrid(
                        messages = uiState.items,
                        accent = colorTheme.primary,
                        onOpen = onOpenImageViewer,
                        // Подгружаем следующую порцию, когда дошли до конца сетки.
                        onRequestMore = viewModel::loadMore
                    )
                    else -> AttachmentList(
                        messages = uiState.items,
                        tab = uiState.tab,
                        accent = colorTheme.primary,
                        onRequestMore = viewModel::loadMore
                    )
                }
                if (uiState.isLoading && uiState.items.isNotEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).size(22.dp),
                        strokeWidth = 2.dp
                    )
                }
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** Одна плитка медиа: изображение альбома/фото либо голосовое сообщение. */
private data class MediaTile(val message: Message, val imageBase64: String?, val index: Int)

@Composable
private fun MediaGrid(
    messages: List<Message>,
    accent: androidx.compose.ui.graphics.Color,
    onOpen: (String, String, Long) -> Unit,
    onRequestMore: () -> Unit
) {
    val tiles = remember(messages) {
        messages.flatMap { message ->
            val images = when {
                message.imagesBase64.isNotEmpty() -> message.imagesBase64
                else -> listOfNotNull(message.mediaThumbnail())
            }
            if (images.isEmpty()) listOf(MediaTile(message, null, 0))
            else images.mapIndexed { index, image -> MediaTile(message, image, index) }
        }
    }
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onRequestMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        gridItems(tiles, key = { "${it.message.id}_${it.imageBase64?.hashCode() ?: it.index}" }) { tile ->
            val bitmap = remember(tile.imageBase64) {
                tile.imageBase64?.let { ImageUtils.decodeChatImageBitmapCached(it, 360) }
            }
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = tile.imageBase64 != null) {
                        tile.imageBase64?.let { base64 ->
                            onOpen(base64, messageAuthorLabel(tile.message), tile.message.timestamp)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Счётчик фото в альбоме — на первой плитке.
                    if (tile.index == 0 && tile.message.imagesBase64.size > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${tile.message.imagesBase64.size}",
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                } else {
                    // Голосовое, одноразовое фото (превью не показываем) или
                    // картинка, которую не удалось декодировать.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when {
                                tile.message.voiceBase64 != null -> Icons.Filled.GraphicEq
                                tile.message.isViewOnce -> Icons.Filled.VisibilityOff
                                else -> Icons.Filled.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        val duration = tile.message.voiceDurationMs
                        if (duration != null) {
                            Text(
                                text = formatMediaDuration(duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentList(
    messages: List<Message>,
    tab: ChatMediaTab,
    accent: androidx.compose.ui.graphics.Color,
    onRequestMore: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onRequestMore()
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(messages, key = { it.id }) { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (tab == ChatMediaTab.FILES) openChatFile(context, message)
                        else message.firstLink()?.let { url -> openLink(context, url) }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (tab == ChatMediaTab.FILES) Icons.Filled.InsertDriveFile else Icons.Filled.Link,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (tab == ChatMediaTab.FILES) {
                            message.fileName ?: "Файл"
                        } else {
                            message.firstLink() ?: message.text
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            if (tab == ChatMediaTab.FILES) {
                                message.fileSizeBytes?.let { append(formatFileSize(it)); append(" · ") }
                            }
                            append(formatMediaDate(message.timestamp))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Имя автора для подписи в просмотрщике фото (в медиа-разделе оно не критично). */
private fun messageAuthorLabel(message: Message): String =
    message.forwardedFromSenderName ?: ""

/** Открывает документ из сообщения (та же логика, что у пузыря файла в чате). */
private fun openChatFile(context: android.content.Context, message: Message) {
    val base64 = message.fileBase64 ?: return
    val fileName = message.fileName ?: "file"
    val file = FileUtils.base64ToTempFile(context, base64, message.id, fileName) ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mimeType = message.fileMimeType ?: context.contentResolver.getType(uri) ?: "*/*"
    val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(openIntent)
    } catch (_: Exception) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(android.content.Intent.createChooser(shareIntent, "Открыть файл через")) }
    }
}

private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}

private fun formatMediaDate(millis: Long): String {
    if (millis == 0L) return ""
    return SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))
}

private fun formatMediaDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f МБ".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
}
