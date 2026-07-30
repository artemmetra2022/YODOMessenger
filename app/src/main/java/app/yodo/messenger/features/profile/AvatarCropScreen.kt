package app.yodo.messenger.features.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Экран перемещения (пан) и масштабирования (зум) выбранной фотографии перед
 * сохранением в качестве аватарки. Пользователь двигает и масштабирует фото
 * внутри круглой рамки жестами, финальный кроп собирается в квадратный Bitmap.
 */
@Composable
fun AvatarCropScreen(
    imageUri: Uri,
    onBackClick: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        sourceBitmap = withContext(Dispatchers.IO) { loadBitmap(context, imageUri) }
        if (sourceBitmap == null) loadFailed = true
    }

    // Общий масштаб и смещение изображения внутри области кропа
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSizePx by remember { mutableFloatStateOf(0f) }
    // Минимальный масштаб, при котором фото полностью покрывает круг (не даём отодвинуть
    // фото настолько, чтобы в рамке появились пустые края)
    var minScale by remember { mutableFloatStateOf(1f) }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новое фото профиля") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Отмена")
                    }
                },
                actions = {
                    val bmp = sourceBitmap
                    if (bmp != null && !isSaving) {
                        IconButton(onClick = {
                            isSaving = true
                            val cropped = cropToCircleBox(
                                source = bmp,
                                containerSizePx = containerSizePx,
                                scale = scale,
                                offset = offset
                            )
                            onCropped(cropped)
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "Сохранить")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .onSizeChanged { containerSizePx = it.width.toFloat() },
                contentAlignment = Alignment.Center
            ) {
                val bmp = sourceBitmap
                when {
                    bmp != null -> {
                        // При первой загрузке считаем минимальный масштаб, чтобы фото
                        // полностью покрывало круглую область без пустых полей.
                        LaunchedEffect(bmp, containerSizePx) {
                            if (containerSizePx > 0f) {
                                val bw = bmp.width.toFloat()
                                val bh = bmp.height.toFloat()
                                val coverScale = max(containerSizePx / bw, containerSizePx / bh)
                                minScale = coverScale
                                if (scale < coverScale) scale = coverScale
                            }
                        }
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Предпросмотр аватарки",
                            // ВАЖНО: contentScale = Crop (а не Fit). minScale ниже считается как
                            // "cover"-масштаб (max, а не min), поэтому базовое отображение тоже
                            // должно быть "cover" — иначе при scale == minScale картинка была
                            // отрисована мельче, чем предполагали расчёты клампа и финального
                            // кропа, и по краям круга оставались пустые (непрозрачные/чёрные)
                            // области.
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(bmp, containerSizePx) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(minScale, minScale * 5f)
                                        scale = newScale
                                        // Ограничиваем смещение так, чтобы фото всегда покрывало рамку.
                                        // ContentScale.Crop уже сам масштабирует источник до cover
                                        // (по большей стороне) в размер контейнера — тут учитываем
                                        // именно cover-масштаб, а не исходные пиксели bitmap'а,
                                        // иначе для не-квадратных фото лимит был бы неверным и
                                        // допускал пустые поля/чрезмерный сдвиг.
                                        val coverScale = max(
                                            containerSizePx / bmp.width.toFloat(),
                                            containerSizePx / bmp.height.toFloat()
                                        )
                                        val bw = bmp.width.toFloat() * coverScale * newScale
                                        val bh = bmp.height.toFloat() * coverScale * newScale
                                        val maxX = max(0f, (bw - containerSizePx) / 2f)
                                        val maxY = max(0f, (bh - containerSizePx) / 2f)
                                        offset = Offset(
                                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                            y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                        )
                                    }
                                }
                                .graphicsLayer(
                                    scaleX = scale, scaleY = scale,
                                    translationX = offset.x, translationY = offset.y
                                )
                        )
                        // Затемнение снаружи круга + белая обводка — визуальная маска кропа
                        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                            val radius = size.minDimension / 2f
                            drawCircleMaskOverlay(radius)
                            drawCircle(
                                color = Color.White,
                                radius = radius,
                                center = center,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                    loadFailed -> Text("Не удалось загрузить изображение", color = MaterialTheme.colorScheme.error)
                    else -> CircularProgressIndicator()
                }
                if (isSaving) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
            Text(
                "Перемещайте и масштабируйте фото пальцами",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

private fun DrawScope.drawCircleMaskOverlay(radius: Float) {
    // Полупрозрачная заливка всего прямоугольника с "вырезанным" кругом посередине,
    // чтобы пользователь ясно видел границы итогового кропа.
    val path = Path().apply {
        addRect(Rect(Offset.Zero, size))
        addOval(Rect(center - Offset(radius, radius), center + Offset(radius, radius)))
        fillType = PathFillType.EvenOdd
    }
    drawPath(path, color = Color.Black.copy(alpha = 0.55f))
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
}

/**
 * Собирает финальный квадратный Bitmap из области, видимой внутри круглой рамки,
 * с учётом текущего масштаба и смещения, заданных пользователем.
 */
private fun cropToCircleBox(source: Bitmap, containerSizePx: Float, scale: Float, offset: Offset): Bitmap {
    if (containerSizePx <= 0f) return source
    val outputSize = 512
    val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    // Размер и положение исходного bitmap на экране (после cover + scale + offset).
    // Используем cover-масштаб (max), а не fit (min) — он соответствует тому, как
    // Image с ContentScale.Crop реально отображает фото на экране (см. AvatarCropScreen),
    // иначе экспортированный кроп не совпадал с тем, что видел пользователь, и по краям
    // получались пустые области.
    val coverScale = max(containerSizePx / source.width, containerSizePx / source.height)
    val displayedWidth = source.width * coverScale * scale
    val displayedHeight = source.height * coverScale * scale
    val left = (containerSizePx - displayedWidth) / 2f + offset.x
    val top = (containerSizePx - displayedHeight) / 2f + offset.y

    val destRect = RectF(
        left * (outputSize / containerSizePx),
        top * (outputSize / containerSizePx),
        (left + displayedWidth) * (outputSize / containerSizePx),
        (top + displayedHeight) * (outputSize / containerSizePx)
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(source, null, destRect, paint)

    // Обрезаем результат по кругу — за пределами круга прозрачно (совместимо с
    // отображением в UserAvatar, который дополнительно клипует по CircleShape).
    val circular = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val circularCanvas = Canvas(circular)
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    circularCanvas.drawCircle(outputSize / 2f, outputSize / 2f, outputSize / 2f, circlePaint)
    circlePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    circularCanvas.drawBitmap(output, 0f, 0f, circlePaint)

    return circular
}
