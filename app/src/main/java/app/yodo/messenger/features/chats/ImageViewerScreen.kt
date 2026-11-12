package app.yodo.messenger.features.chats

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yodo.messenger.ui.components.IconButton
import app.yodo.messenger.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Просмотр фото в стиле Telegram:
 *  • горизонтальное листание альбома свайпом (HorizontalPager) с зазором между страницами;
 *  • счётчик «2 из 5» в шапке, лента миниатюр снизу (для альбомов);
 *  • тап — скрыть/показать интерфейс, двойной тап — зум, щипок — свободный зум;
 *  • при зуме пан ограничен краями фото, у края палец отдаёт жест пейджеру;
 *  • свайп вверх/вниз при масштабе 1x — закрыть.
 */
@Composable
fun ImageViewerScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val images = remember { ImageViewerHolder.effectiveImages() }
    val senderName = remember { ImageViewerHolder.senderName ?: "Фото" }
    val timestamp = remember { ImageViewerHolder.timestamp }
    val initialPage = remember {
        ImageViewerHolder.initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    }

    DisposableEffect(Unit) {
        onDispose { ImageViewerHolder.clear() }
    }

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { images.size })
    var chromeVisible by remember { mutableStateOf(true) }
    var currentZoomed by remember { mutableStateOf(false) }
    var dismissProgress by remember { mutableFloatStateOf(0f) }
    var isSaving by remember { mutableStateOf(false) }

    val timeText = remember(timestamp) {
        if (timestamp > 0) SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(timestamp))
        else ""
    }

    // Интерфейс прячется при зуме и во время жеста закрытия.
    val showChrome = chromeVisible && !currentZoomed && dismissProgress == 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (images.isEmpty()) {
            Text(
                "Не удалось загрузить изображение",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                key = { it }
            ) { page ->
                ZoomablePage(
                    base64 = images[page],
                    isCurrent = pagerState.currentPage == page,
                    onTap = { chromeVisible = !chromeVisible },
                    onZoomedChange = { zoomed -> if (pagerState.currentPage == page) currentZoomed = zoomed },
                    onDismissProgress = { p -> if (pagerState.currentPage == page) dismissProgress = p },
                    onDismiss = onBackClick
                )
            }
        }

        // Сбрасываем флаги при смене страницы.
        LaunchedEffect(pagerState.currentPage) {
            currentZoomed = false
            dismissProgress = 0f
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (images.size > 1) "${pagerState.currentPage + 1} из ${images.size}" else senderName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        val subtitle = buildString {
                            if (images.size > 1) append(senderName)
                            if (timeText.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(timeText)
                            }
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val base64 = images.getOrNull(pagerState.currentPage) ?: return@IconButton
                        scope.launch {
                            isSaving = true
                            val saved = saveImageToGallery(context, base64)
                            isSaving = false
                            snackbarHostState.showSnackbar(
                                if (saved) "Фото сохранено в галерею" else "Не удалось сохранить"
                            )
                        }
                    }) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        else Icon(Icons.Filled.Download, contentDescription = "Скачать", tint = Color.White)
                    }
                    IconButton(onClick = {
                        val base64 = images.getOrNull(pagerState.currentPage) ?: return@IconButton
                        scope.launch { shareImage(context, base64) }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Поделиться", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.55f))
            )
        }

        // Лента миниатюр (только для альбомов).
        AnimatedVisibility(
            visible = showChrome && images.size > 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ThumbnailStrip(
                images = images,
                currentPage = pagerState.currentPage,
                onSelect = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (images.size > 1) 84.dp else 0.dp)
        )
    }
}

@Composable
private fun ThumbnailStrip(
    images: List<String>,
    currentPage: Int,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentPage) {
        listState.animateScrollToItem((currentPage - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(images) { index, base64 ->
            val selected = index == currentPage
            val thumb by produceState<Bitmap?>(null, base64) {
                value = withContext(Dispatchers.Default) { ImageUtils.decodeAvatarBitmapCached(base64, 160) }
            }
            Box(
                modifier = Modifier
                    .size(if (selected) 56.dp else 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { onSelect(index) }
            ) {
                thumb?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Фото ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Одна страница пейджера: фото с зумом, паном и свайпом-закрытием.
 *
 * Жест обрабатывается вручную, чтобы корректно делиться событиями с пейджером:
 *  • при 1x горизонтальный жест НЕ потребляется — его забирает HorizontalPager (листание);
 *  • при 1x вертикальный жест — перетаскивание/закрытие;
 *  • при зуме пан потребляется, а у горизонтального края фото отдаётся пейджеру.
 */
@Composable
private fun ZoomablePage(
    base64: String,
    isCurrent: Boolean,
    onTap: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    onDismissProgress: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }

    // Декод с ограничением размера (без общего кеша — на экране 1–3 страницы).
    val bitmap by produceState<Bitmap?>(null, base64) {
        value = withContext(Dispatchers.Default) { decodeForViewer(base64, 2048) }
    }

    // Соседние страницы всегда возвращаются к 1x.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f; offsetX = 0f; offsetY = 0f; dragY = 0f
        }
    }
    LaunchedEffect(scale) { onZoomedChange(scale > 1.05f) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp == null) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
        } else {
            val dismissP = (abs(dragY) / 900f).coerceIn(0f, 1f)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Просмотр фото",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = scale * (1f - dismissP * 0.15f)
                        scaleX = s
                        scaleY = s
                        translationX = offsetX
                        translationY = offsetY + dragY
                        alpha = 1f - dismissP * 0.6f
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = { tap ->
                                scope.launch {
                                    val next = when {
                                        scale < 1.4f -> 2.5f
                                        else -> 1f
                                    }
                                    val fromX = offsetX
                                    val fromY = offsetY
                                    val maxX = size.width * (next - 1f) / 2f
                                    val maxY = size.height * (next - 1f) / 2f
                                    val targetX = if (next == 1f) 0f else ((size.width / 2f - tap.x) * (next - 1f)).coerceIn(-maxX, maxX)
                                    val targetY = if (next == 1f) 0f else ((size.height / 2f - tap.y) * (next - 1f)).coerceIn(-maxY, maxY)
                                    val fromScale = scale
                                    settle.snapTo(0f)
                                    settle.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f)) {
                                        scale = fromScale + (next - fromScale) * value
                                        offsetX = fromX + (targetX - fromX) * value
                                        offsetY = fromY + (targetY - fromY) * value
                                    }
                                    scale = next; offsetX = targetX; offsetY = targetY
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val velocity = VelocityTracker()
                            awaitFirstDown(requireUnconsumed = false)
                            val slop = viewConfiguration.touchSlop
                            var pastSlop = false
                            var passToPager = false
                            var verticalDismiss = false
                            var totalPan = Offset.Zero

                            do {
                                val event = awaitPointerEvent()
                                val canceled = event.changes.any { it.isConsumed }
                                if (!canceled) {
                                    val zoomChange = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val multiTouch = event.changes.count { it.pressed } > 1

                                    if (!pastSlop) {
                                        totalPan += pan
                                        if (zoomChange != 1f || totalPan.getDistance() > slop) {
                                            pastSlop = true
                                            val horizontal = abs(totalPan.x) > abs(totalPan.y)
                                            if (!multiTouch && zoomChange == 1f && horizontal) {
                                                if (scale <= 1.05f) {
                                                    passToPager = true
                                                } else {
                                                    val maxX = size.width * (scale - 1f) / 2f
                                                    val atRight = offsetX >= maxX - 1f && totalPan.x > 0
                                                    val atLeft = offsetX <= -maxX + 1f && totalPan.x < 0
                                                    if (atRight || atLeft) passToPager = true
                                                }
                                            }
                                            if (!multiTouch && scale <= 1.05f && !horizontal) verticalDismiss = true
                                        }
                                    }

                                    if (pastSlop && !passToPager) {
                                        if (zoomChange != 1f) {
                                            scale = (scale * zoomChange).coerceIn(1f, 6f)
                                        }
                                        if (verticalDismiss && scale <= 1.05f) {
                                            dragY += pan.y
                                            offsetX = 0f
                                            onDismissProgress((abs(dragY) / 900f).coerceIn(0f, 1f))
                                        } else if (scale > 1.0f) {
                                            val maxX = size.width * (scale - 1f) / 2f
                                            val maxY = size.height * (scale - 1f) / 2f
                                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                        } else {
                                            offsetX = 0f; offsetY = 0f
                                        }
                                        velocity.addPosition(
                                            event.changes.first().uptimeMillis,
                                            event.changes.first().position
                                        )
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (!canceled && event.changes.any { it.pressed })

                            if (pastSlop && !passToPager) {
                                val flingY = velocity.calculateVelocity().y
                                if (verticalDismiss && scale <= 1.05f &&
                                    (abs(dragY) > 200f || abs(flingY) > 1200f)
                                ) {
                                    onDismiss()
                                } else {
                                    val startDrag = dragY
                                    val startScale = scale
                                    val endScale = if (startScale < 1.02f) 1f else startScale
                                    val fromX = offsetX
                                    val fromY = offsetY
                                    scope.launch {
                                        settle.snapTo(0f)
                                        settle.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 400f)) {
                                            dragY = startDrag * (1f - value)
                                            scale = startScale + (endScale - startScale) * value
                                            if (endScale == 1f) {
                                                offsetX = fromX * (1f - value)
                                                offsetY = fromY * (1f - value)
                                            }
                                            onDismissProgress((abs(dragY) / 900f).coerceIn(0f, 1f))
                                        }
                                        dragY = 0f
                                        if (endScale == 1f) { scale = 1f; offsetX = 0f; offsetY = 0f }
                                        onDismissProgress(0f)
                                    }
                                }
                            }
                        }
                    }
            )
        }
    }
}

/** Декод base64 с inSampleSize под maxDim (по длинной стороне). */
private fun decodeForViewer(base64: String, maxDim: Int): Bitmap? {
    val bytes = ImageUtils.decodeBase64ToBytes(base64) ?: return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    }
}

private suspend fun saveImageToGallery(context: Context, base64: String): Boolean {
    return withContext(Dispatchers.IO) {
        val bitmap = ImageUtils.decodeBase64ToBitmap(base64) ?: return@withContext false
        try {
            val fileName = "YODO_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YODOMessenger")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                    }
                }
                uri != null
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YODOMessenger")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { fos -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos) }
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

private suspend fun shareImage(context: Context, base64: String) {
    withContext(Dispatchers.IO) {
        val bitmap = ImageUtils.decodeBase64ToBitmap(base64) ?: return@withContext
        try {
            val dir = File(context.cacheDir, "shared_images")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "share_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { fos -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos) }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Поделиться фото"))
        } catch (e: Exception) {
            // Логирование ошибки опционально
        }
    }
}
