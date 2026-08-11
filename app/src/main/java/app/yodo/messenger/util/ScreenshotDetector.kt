package app.yodo.messenger.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

// НОВОЕ (одноразовые медиа — детектор скриншотов): FLAG_SECURE (см. ViewOnceImageOverlay)
// физически блокирует стандартный системный скриншот/запись экрана, но не гарантирует
// это на 100% на всех прошивках/рутованных устройствах (кастомные снифферы кадра,
// сторонние "screen recorder" приложения с доступом к MediaProjection в обход системного
// UI и т.п.). Этот детектор — вторая, независимая линия: слушает появление новых файлов
// в галерее (Images/Video content provider) пока активен наблюдаемый экран (view-once
// оверлей), и если файл появился и похож на скриншот по имени/пути — считаем это попыткой
// сохранить/заскриншотить показанное фото и уведомляем колбэком.
//
// Ограничение (честно): это эвристика по ContentObserver + имени файла, а не
// криптографическая гарантия — некоторые сторонние скриншотеры не проходят через
// MediaStore вовсе (например, сохраняют файл напрямую в приватную папку приложения) и
// такие случаи детектор не увидит. Так же ведут себя аналогичные механизмы в других
// мессенджерах.
object ScreenshotDetector {

    private val screenshotNameHints = listOf(
        "screenshot", "screen_shot", "screen-shot", "снимок экрана", "скриншот"
    )

    private fun looksLikeScreenshotPath(path: String?, displayName: String?): Boolean {
        val haystack = ((path ?: "") + " " + (displayName ?: "")).lowercase()
        return screenshotNameHints.any { haystack.contains(it) }
    }

    /**
     * Регистрирует наблюдение за галереей на время жизни компонента и вызывает [onScreenshotDetected]
     * при обнаружении нового файла, похожего на скриншот. Снимает наблюдение в onDispose.
     */
    fun register(context: Context, onScreenshotDetected: () -> Unit): ContentObserver {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri == null) return
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val pathIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                            val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                            val path = if (pathIdx >= 0) cursor.getString(pathIdx) else null
                            val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                            if (looksLikeScreenshotPath(path, name)) {
                                onScreenshotDetected()
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Нет доступа к деталям файла (разрешения/OEM-ограничения) — молча
                    // пропускаем это событие, детектор не критичен для основной функции.
                }
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        return observer
    }

    fun unregister(context: Context, observer: ContentObserver) {
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
        }
    }
}

/**
 * Composable-обёртка: пока текущий composable находится в композиции, слушает скриншоты
 * и вызывает [onScreenshotDetected] (последняя актуальная лямбда, см. rememberUpdatedState).
 */
@Composable
fun DetectScreenshots(onScreenshotDetected: () -> Unit) {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onScreenshotDetected)
    DisposableEffect(Unit) {
        val observer = ScreenshotDetector.register(context) { callback.value() }
        onDispose { ScreenshotDetector.unregister(context, observer) }
    }
}
