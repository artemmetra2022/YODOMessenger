package app.yodo.messenger.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

enum class ChatImageQuality(
    val maxDimension: Int,
    val startingQuality: Int
) {
    DATA_SAVER(maxDimension = 960, startingQuality = 72),
    STANDARD(maxDimension = 1280, startingQuality = 84),
    HIGH(maxDimension = 1600, startingQuality = 92)
}

object ImageUtils {

    // Аватарки: Firestore-документ пользователя ограничен 1 МБ суммарно по всем полям —
    // оставляем большой запас под остальные поля профиля.
    private const val AVATAR_MAX_DIMENSION = 512
    private const val AVATAR_STARTING_QUALITY = 88
    private const val AVATAR_MAX_BASE64 = 550_000

    // ИСПРАВЛЕНИЕ (баг "очень плохое качество фото"): раньше был фиксированный quality=90
    // и жёсткий лимит 2 500 000 байт — то есть ВЫШЕ лимита документа Firestore в 1 МБ (1 048 576).
    // Из-за этого часть фото либо не проходила (null → "не удалось обработать фото"),
    // либо сохранялась пограничным по размеру. Теперь: адаптивное сжатие — пробуем максимально
    // высокое качество и постепенно снижаем, пока base64 не влезет в реальный лимит документа.
    private const val CHAT_MIN_QUALITY = 40
    private const val CHAT_MAX_BASE64 = 900_000 // с запасом под остальные поля сообщения

    fun compressAvatarToBase64(context: Context, uri: Uri): String? {
        return compressAdaptive(context, uri, AVATAR_MAX_DIMENSION, AVATAR_STARTING_QUALITY, 60, AVATAR_MAX_BASE64)
    }

    // Вариант для уже готового битмапа (например, результата экрана кропа аватарки) —
    // используется та же адаптивная логика подбора качества/размера, что и для Uri.
    fun compressAvatarToBase64(bitmap: Bitmap): String? {
        return compressAdaptive(bitmap, AVATAR_MAX_DIMENSION, AVATAR_STARTING_QUALITY, 60, AVATAR_MAX_BASE64)
    }

    fun compressChatImageToBase64(
        context: Context,
        uri: Uri,
        quality: ChatImageQuality = ChatImageQuality.HIGH
    ): String? {
        return compressAdaptive(
            context,
            uri,
            quality.maxDimension,
            quality.startingQuality,
            CHAT_MIN_QUALITY,
            CHAT_MAX_BASE64
        )
    }

    // Фото для постов профиля — та же адаптивная логика, что и для фото в чате
    // (документ поста тоже ограничен 1 МБ Firestore, оставляем такой же запас).
    private const val POST_MAX_DIMENSION = 1600
    private const val POST_STARTING_QUALITY = 90
    private const val POST_MIN_QUALITY = 40
    private const val POST_MAX_BASE64 = 900_000

    fun compressPostImageToBase64(context: Context, uri: Uri): String? {
        return compressAdaptive(context, uri, POST_MAX_DIMENSION, POST_STARTING_QUALITY, POST_MIN_QUALITY, POST_MAX_BASE64)
    }

    @Deprecated("Use compressChatImageToBase64 or compressAvatarToBase64")
    fun compressImageToBase64(context: Context, uri: Uri): String? {
        return compressChatImageToBase64(context, uri)
    }

    /**
     * Сжимает изображение, постепенно уменьшая JPEG-качество (шагом 8), пока результат
     * не влезет в maxBase64Length. Так мы всегда сохраняем максимально возможное качество
     * при данном лимите, а не режем фото на фиксированном (иногда избыточно низком) качестве.
     * Если даже минимальное качество не помогает — дополнительно уменьшаем разрешение вдвое.
     */
    private fun compressAdaptive(
        context: Context, uri: Uri,
        maxDimension: Int, startingQuality: Int, minQuality: Int, maxBase64Length: Int
    ): String? {
        return try {
            val original = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null
            compressAdaptive(original, maxDimension, startingQuality, minQuality, maxBase64Length)
        } catch (e: Exception) { null }
    }

    private fun compressAdaptive(
        original: Bitmap,
        maxDimension: Int, startingQuality: Int, minQuality: Int, maxBase64Length: Int
    ): String? {
        return try {
            var currentDimension = maxDimension
            var attempt = 0
            while (attempt < 3) {
                val resized = resizeBitmap(original, currentDimension)
                var quality = startingQuality
                while (quality >= minQuality) {
                    val outputStream = ByteArrayOutputStream()
                    resized.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                    val bytes = outputStream.toByteArray()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    if (base64.length <= maxBase64Length) return base64
                    quality -= 8
                }
                // Даже на минимальном качестве не влезли — уменьшаем разрешение и пробуем снова
                currentDimension = (currentDimension * 0.75f).toInt()
                attempt++
            }
            null
        } catch (e: Exception) { null }
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
    }

    // НОВОЕ (п.16, оптимизация прокрутки): LRU-кеш декодированных картинок.
    // Аватары/фото хранятся в Firestore как base64, и раньше каждый раз при
    // композиции строки (в т.ч. при каждой повторной прокрутке) выполнялся
    // Base64-декод + BitmapFactory.decodeByteArray НА ГЛАВНОМ ПОТОКЕ — десятки
    // миллисекунд на строку, что и давало лаги при быстрой прокрутке. Теперь:
    //   1) результат кешируется по ключу (хеш строки + целевой размер),
    //   2) декод идёт с пониженным разрешением (inSampleSize) под фактический
    //      размер картинки на экране.
    private class Base64BitmapCache(private val maxSize: Int) {
        private val map = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
                size > maxSize
        }
        fun get(key: String): Bitmap? = synchronized(map) { map[key] }
        fun put(key: String, bitmap: Bitmap) = synchronized(map) { map[key] = bitmap }
    }

    private fun decodeBase64BitmapCached(
        cache: Base64BitmapCache, base64: String?, maxDimPx: Int
    ): Bitmap? {
        if (base64.isNullOrBlank()) return null
        // Ключ по хешу, а не по самой строке — чтобы не держать в памяти
        // сотни килобайт base64 ради одного ключа кеша.
        val key = base64.length.toString() + "_" + base64.hashCode() + "_" + maxDimPx
        cache.get(key)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            // Сначала читаем только размеры, чтобы выбрать inSampleSize
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDimPx && bounds.outHeight / (sample * 2) >= maxDimPx) {
                sample *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null
            cache.put(key, bitmap)
            bitmap
        } catch (e: Exception) { null }
    }

    // Аватарки: ~256px хватает для 56dp при 3x-плотности; кешируем щедро (они маленькие).
    private val avatarCache = Base64BitmapCache(64)

    fun decodeAvatarBitmapCached(base64: String?, maxDimPx: Int = 256): Bitmap? =
        decodeBase64BitmapCached(avatarCache, base64, maxDimPx)

    // Фото в сообщениях: пузырь максимум ~260dp → 1024px за глаза; кеш меньше,
    // т.к. каждый битмап тяжелее (примерно 1024*1024*4 ≈ 4 МБ; 16 штук — приемлемо).
    private val chatImageCache = Base64BitmapCache(16)

    fun decodeChatImageBitmapCached(base64: String?, maxDimPx: Int = 1024): Bitmap? =
        decodeBase64BitmapCached(chatImageCache, base64, maxDimPx)

    fun decodeBase64ToBytes(base64: String): ByteArray? {
        return try { Base64.decode(base64, Base64.NO_WRAP) }
        catch (e: Exception) { null }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (width > height) {
            maxDimension to (maxDimension / ratio).toInt()
        } else {
            (maxDimension * ratio).toInt() to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth.coerceAtLeast(1), newHeight.coerceAtLeast(1), true)
    }
}
