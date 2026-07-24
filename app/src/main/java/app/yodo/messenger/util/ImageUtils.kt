package app.yodo.messenger.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

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
    private const val CHAT_MAX_DIMENSION = 1600
    private const val CHAT_STARTING_QUALITY = 92
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

    fun compressChatImageToBase64(context: Context, uri: Uri): String? {
        return compressAdaptive(context, uri, CHAT_MAX_DIMENSION, CHAT_STARTING_QUALITY, CHAT_MIN_QUALITY, CHAT_MAX_BASE64)
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
