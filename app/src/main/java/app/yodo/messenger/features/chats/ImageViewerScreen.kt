package app.yodo.messenger.features.chats

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

@Composable
fun ImageViewerScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Масштаб и смещение управляются напрямую пальцем (без пружин/анимации во время
    // жеста) — это даёт 1:1 отклик и абсолютно плавное перемещение без рывков.
    // Animatable используется только ПОСЛЕ отпускания пальца — чтобы плавно "довести"
    // фото обратно в допустимые границы или сбросить смещение при закрытии.
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetXValue by remember { mutableFloatStateOf(0f) }
    var offsetYValue by remember { mutableFloatStateOf(0f) }
    // Animatable используется только ПОСЛЕ отпускания пальца, чтобы плавно
    // анимировать offsetXValue/offsetYValue обратно в допустимые границы —
    // сами кадры жеста читают/пишут offsetXValue/offsetYValue напрямую.
    val settleAnim = remember { Animatable(0f) }

    // Состояние для swipe-to-dismiss
    var isDismissing by remember { mutableStateOf(false) }
    var dismissProgress by remember { mutableFloatStateOf(0f) }

    // Состояние загрузки
    var isSaving by remember { mutableStateOf(false) }
    var imageAlpha by remember { mutableFloatStateOf(0f) }
    
    // Данные изображения
    val imageBase64 = ImageViewerHolder.imageBase64
    val senderName = ImageViewerHolder.senderName ?: "Фото"
    val timestamp = ImageViewerHolder.timestamp
    
    // Очистка при выходе
    DisposableEffect(Unit) {
        onDispose { ImageViewerHolder.clear() }
    }
    
    // Декодирование изображения
    val bitmap = remember(imageBase64) {
        imageBase64?.let { ImageUtils.decodeBase64ToBitmap(it) }
    }
    
    // Плавное появление изображения
    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            imageAlpha = 1f
        }
    }
    
    val timeText = remember(timestamp) {
        if (timestamp > 0) SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(timestamp))
        else ""
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = imageAlpha,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "alpha"
    )
    
    // Автоматическое скрытие тулбара при зуме
    val showToolbar = scale < 1.1f
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
        topBar = {
            if (showToolbar) {
                TopAppBar(
                    title = {
                        Column {
                            Text(senderName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            if (timeText.isNotBlank()) {
                                Text(timeText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
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
                            scope.launch {
                                isSaving = true
                                val saved = saveImageToGallery(context, bitmap)
                                isSaving = false
                                snackbarHostState.showSnackbar(
                                    if (saved) "Фото сохранено в галерею" else "Не удалось сохранить"
                                )
                            }
                        }) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            else Icon(Icons.Filled.Download, contentDescription = "Скачать", tint = Color.White)
                        }
                        IconButton(onClick = { scope.launch { shareImage(context, bitmap) } }) {
                            Icon(Icons.Filled.Share, contentDescription = "Поделиться", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.6f))
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Просмотр фото",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetXValue
                            translationY = offsetYValue
                            alpha = animatedAlpha * (1f - dismissProgress)
                        }
                        .pointerInput(Unit) {
                            // Double-tap для зума — отдельный детектор без конфликта с
                            // перемещением, т.к. работает только по короткому двойному тапу.
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    scope.launch {
                                        if (scale > 1f) {
                                            scale = 1f
                                            animateOffsetTo(settleAnim, 0f, 0f,
                                                onXY = { x, y -> offsetXValue = x; offsetYValue = y },
                                                fromX = offsetXValue, fromY = offsetYValue)
                                        } else {
                                            val centerX = size.width / 2f
                                            val centerY = size.height / 2f
                                            scale = 2.5f
                                            animateOffsetTo(settleAnim, centerX - tapOffset.x, centerY - tapOffset.y,
                                                onXY = { x, y -> offsetXValue = x; offsetYValue = y },
                                                fromX = offsetXValue, fromY = offsetYValue)
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            // Единый обработчик жеста: перемещение (в любую сторону — вверх,
                            // вниз, влево, вправо), масштабирование щипком и свайп-закрытие —
                            // всё это один и тот же непрерывный поток касаний, поэтому палец
                            // никогда не нужно отрывать между действиями.
                            awaitEachGesture {
                                val velocityTracker = VelocityTracker()
                                awaitFirstDown(requireUnconsumed = false)
                                var pastTouchSlop = false
                                val touchSlop = viewConfiguration.touchSlop

                                do {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (!canceled) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()

                                        if (!pastTouchSlop) {
                                            val panMotion = panChange.getDistance()
                                            if (zoomChange != 1f || panMotion > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }

                                        if (pastTouchSlop) {
                                            if (zoomChange != 1f) {
                                                scale = (scale * zoomChange).coerceIn(1f, 6f)
                                            }

                                            // Смещение применяется напрямую в этом же
                                            // suspend-блоке (без launch на каждый кадр) —
                                            // так offset всегда синхронен с текущим кадром
                                            // жеста, что и даёт идеально плавное перемещение.
                                            offsetXValue += panChange.x
                                            offsetYValue += panChange.y
                                            // Свободное перемещение вверх/вниз в любом состоянии
                                            // масштаба — при scale == 1 это ведёт себя как
                                            // свайп-закрытие, при zoom — как обычный пан.
                                            if (scale <= 1.05f) {
                                                dismissProgress = (abs(offsetYValue) / 900f).coerceIn(0f, 1f)
                                            }
                                            velocityTracker.addPosition(
                                                event.changes.first().uptimeMillis,
                                                event.changes.first().position
                                            )

                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } while (!canceled && event.changes.any { it.pressed })

                                if (pastTouchSlop) {
                                    val flingVelocity = velocityTracker.calculateVelocity().y
                                    val shouldDismiss = scale <= 1.05f &&
                                        (abs(offsetYValue) > 220f || abs(flingVelocity) > 1200f)

                                    if (shouldDismiss) {
                                        isDismissing = true
                                        onBackClick()
                                    } else {
                                        // Плавно возвращаем фото в допустимые границы, если
                                        // оно было утянуто дальше своих краёв при зуме, либо
                                        // просто "докидываем" обратно к центру.
                                        val targetX: Float
                                        val targetY: Float
                                        if (scale <= 1f) {
                                            targetX = 0f
                                            targetY = 0f
                                        } else {
                                            val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                            val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                            targetX = offsetXValue.coerceIn(-maxOffsetX, maxOffsetX)
                                            targetY = offsetYValue.coerceIn(-maxOffsetY, maxOffsetY)
                                        }
                                        scope.launch {
                                            dismissProgress = 0f
                                            animateOffsetTo(
                                                settleAnim, targetX, targetY,
                                                onXY = { x, y -> offsetXValue = x; offsetYValue = y },
                                                fromX = offsetXValue, fromY = offsetYValue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                )
            } else {
                Text(
                    "Не удалось загрузить изображение",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // Индикатор масштаба
            if (scale > 1.1f) {
                Text(
                    "${"%.1f".format(scale)}x",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            // Подсказка внизу
            if (scale <= 1.05f && dismissProgress == 0f) {
                Text(
                    "Свайп — закрыть\nДвойной тап — увеличить",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * Анимирует переход offset(fromX, fromY) -> (targetX, targetY), используя один
 * общий Animatable<Float> как прогресс (0f..1f), и на каждом кадре записывает
 * интерполированные X/Y через onXY. Это позволяет держать offsetX/offsetY как
 * обычный Compose state (без suspend-only API), совместимый с чтением/записью
 * прямо внутри awaitPointerEventScope.
 */
private suspend fun animateOffsetTo(
    progress: Animatable<Float, *>,
    targetX: Float,
    targetY: Float,
    fromX: Float,
    fromY: Float,
    onXY: (Float, Float) -> Unit
) {
    progress.snapTo(0f)
    progress.animateTo(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
    ) {
        val t = value
        onXY(fromX + (targetX - fromX) * t, fromY + (targetY - fromY) * t)
    }
    onXY(targetX, targetY)
}

private suspend fun saveImageToGallery(context: Context, bitmap: Bitmap?): Boolean {
    if (bitmap == null) return false
    return withContext(Dispatchers.IO) {
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

private suspend fun shareImage(context: Context, bitmap: Bitmap?) {
    if (bitmap == null) return
    withContext(Dispatchers.IO) {
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
