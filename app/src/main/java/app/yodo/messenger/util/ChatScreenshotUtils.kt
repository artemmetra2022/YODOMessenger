package app.yodo.messenger.util

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.core.content.ContextCompat
import app.yodo.messenger.R
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

// НОВОЕ: "Сделать скриншот" — пункт меню чата (3 точки). Захватывает ВЕСЬ экран чата —
// включая шапку с контактом и статус-бар, но БЕЗ поля ввода (см. requirement), —
// накладывает сверху справа полупрозрачный логотип мессенджера и сохраняет получившееся
// изображение в галерею через MediaStore (без нужды в WRITE_EXTERNAL_STORAGE на Android 10+).
object ChatScreenshotUtils {

    /**
     * Достаёт Activity из Compose-контекста, который часто обёрнут в один и более
     * ContextWrapper (ContextThemeWrapper и т.п.) — прямой `context as? Activity`
     * в таких случаях возвращает null, поэтому разворачиваем цепочку до конца.
     */
    fun findActivity(context: Context): android.app.Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is android.app.Activity) return current
            current = current.baseContext
        }
        return current as? android.app.Activity
    }

    /**
     * Делает скриншот произвольной области экрана (заданной [bounds] в системе координат окна)
     * через PixelCopy — так снимок корректно захватывает и Compose-контент, и системные
     * элементы поверх него (в отличие от ручной отрисовки View.draw, которая для Compose
     * иногда даёт пустой кадр при наличии аппаратного ускорения).
     */
    private suspend fun captureWindowRegion(window: Window, bounds: Rect): Bitmap? {
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { continuation ->
            try {
                PixelCopy.request(
                    window,
                    bounds,
                    bitmap,
                    { result ->
                        if (result == PixelCopy.SUCCESS) {
                            continuation.resume(bitmap)
                        } else {
                            continuation.resume(null)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }
    }

    /**
     * Снимает весь экран чата — от верхнего края окна (шапка с контактом + статус-бар)
     * до нижней границы, вычисленной вызывающей стороной (верх поля ввода). Накладывает
     * логотип мессенджера в правом верхнем углу и сохраняет итоговый Bitmap в галерею устройства.
     *
     * @param window текущее окно Activity (для PixelCopy)
     * @param screenBoundsInWindow границы области скриншота в координатах окна (без поля ввода/клавиатуры)
     * @return true, если скриншот успешно сделан и сохранён
     */
    suspend fun captureAndSaveChatScreenshot(
        context: Context,
        window: Window,
        screenBoundsInWindow: Rect
    ): Boolean {
        val captured = captureWindowRegion(window, screenBoundsInWindow) ?: return false
        val withLogo = try {
            overlayLogoWatermark(context, captured)
        } catch (e: Exception) {
            captured // если наложение логотипа не удалось — сохраняем хотя бы чистый скриншот
        }
        return saveBitmapToGallery(context, withLogo)
    }

    /** Рисует логотип YODOMessenger (ic_launcher_foreground) в правом верхнем углу скриншота. */
    private fun overlayLogoWatermark(context: Context, source: Bitmap): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val logoDrawable: Drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
            ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            ?: return result

        // Логотип — фиксированная доля от ширины скриншота, с отступом от края,
        // чтобы одинаково хорошо смотреться и на телефонах, и на планшетах.
        val logoSize = (source.width * 0.11f).toInt().coerceIn(48, 220)
        val margin = (source.width * 0.03f).toInt().coerceAtLeast(12)

        val left = source.width - logoSize - margin
        val top = margin
        val right = left + logoSize
        val bottom = top + logoSize

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 235 // едва заметная прозрачность — логотип виден, но не перекрывает текст под ним
        }
        // Полупрозрачная подложка под логотип, чтобы он не терялся на светлом/тёмном фоне сообщений.
        val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
        }
        val backdropRadius = logoSize / 2f + (logoSize * 0.12f)
        canvas.drawCircle(
            (left + right) / 2f, (top + bottom) / 2f, backdropRadius, backdropPaint
        )

        logoDrawable.setBounds(left, top, right, bottom)
        logoDrawable.alpha = paint.alpha
        logoDrawable.draw(canvas)

        return result
    }

    /** Сохраняет Bitmap в галерею (папка Pictures/YODOMessenger) через MediaStore. */
    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val fileName = "YODOMessenger_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YODOMessenger")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val uri = resolver.insert(collection, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
