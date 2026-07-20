package app.yodo.messenger.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Firebase Storage теперь требует платный план (Blaze) для новых проектов — раз это недоступно
 * без карты, аватарки храним прямо в Firestore как сжатую Base64-строку в поле "avatarBase64".
 * Firestore ограничивает документ 1 МБ — сжимаем агрессивно, чтобы уложиться с большим запасом.
 */
object ImageUtils {

    private const val MAX_DIMENSION_PX = 1280 // Увеличено с 400 до 1280 для лучшего качества
    private const val JPEG_QUALITY = 85 // Увеличено с 65 до 85 для лучшего качества
    private const val MAX_BASE64_LENGTH = 900_000 // ~900 КБ, запас от лимита в 1 МБ на документ

    /** Возвращает Base64-строку (без префикса data:) или null при ошибке/слишком большом файле. */
    fun compressImageToBase64(context: Context, uri: Uri): String? {
        return try {
            val original = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null

            val resized = resizeBitmap(original, MAX_DIMENSION_PX)

            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (base64.length > MAX_BASE64_LENGTH) null else base64
        } catch (e: Exception) {
            null
        }
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
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
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
