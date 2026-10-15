package app.yodo.messenger.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

/**
 * НОВОЕ: файловые вложения. Как и фото (ImageUtils) и голосовые (AudioUtils), файл
 * хранится в base64 прямо в документе сообщения в Firestore — Storage не используется,
 * так как требует платный план Blaze (см. комментарии в UserAvatar.kt / AudioUtils.kt).
 *
 * Из-за лимита документа Firestore в 1 МБ на файлы установлен потолок в районе ~700 КБ
 * (с запасом под остальные поля сообщения и base64-раздутие +33%). Это действительно
 * означает, что по-настоящему большие файлы (видео, архивы, документы на несколько МБ)
 * этим способом отправить нельзя — честно показываем пользователю понятную ошибку
 * с точным лимитом, а не тихо обрезаем файл или роняем отправку с непонятной ошибкой.
 * Если позже в проекте будет включён план Blaze — тогда есть смысл перевести файлы
 * (и фото/голосовые) на настоящую загрузку в Firebase Storage без ограничения размера.
 */
object FileUtils {

    // Лимит на итоговую base64-строку файла — с запасом под остальные поля документа.
    const val MAX_FILE_BASE64_LENGTH = 700_000
    // Соответствует примерно следующему пределу на сам файл (до base64, которое даёт +33%):
    const val MAX_FILE_SIZE_BYTES = (MAX_FILE_BASE64_LENGTH * 3 / 4).toLong()

    data class PickedFile(
        val base64: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long
    )

    sealed class PickResult {
        data class Success(val file: PickedFile) : PickResult()
        // Файл существует и прочитан, но не влезает в лимит — возвращаем его реальный
        // размер, чтобы показать пользователю точную цифру в сообщении об ошибке.
        data class TooLarge(val actualSizeBytes: Long) : PickResult()
        data object Error : PickResult()
    }

    /** Читает файл по Uri, кодирует в base64 и достаёт имя/MIME/размер. */
    fun prepareFileForSending(context: Context, uri: Uri): PickResult {
        return try {
            val (fileName, sizeHint) = queryNameAndSize(context, uri)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return PickResult.Error
            if (bytes.size.toLong() > MAX_FILE_SIZE_BYTES) {
                return PickResult.TooLarge(actualSizeBytes = bytes.size.toLong())
            }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (base64.length > MAX_FILE_BASE64_LENGTH) {
                return PickResult.TooLarge(actualSizeBytes = bytes.size.toLong())
            }
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            PickResult.Success(
                PickedFile(
                    base64 = base64,
                    fileName = fileName ?: "Файл",
                    mimeType = mimeType,
                    sizeBytes = sizeHint ?: bytes.size.toLong()
                )
            )
        } catch (e: Exception) { PickResult.Error }
    }

    private fun queryNameAndSize(context: Context, uri: Uri): Pair<String?, Long?> {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else null
                    name to size
                } else null to null
            } ?: (null to null)
        } catch (e: Exception) { null to null }
    }

    /** Декодирует base64 файла во временный файл кэша для открытия/шаринга через FileProvider. */
    fun base64ToTempFile(context: Context, base64: String, messageId: String, fileName: String): File? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val safeName = fileName.ifBlank { "file_$messageId" }
            val file = File(dir, "${messageId}_$safeName")
            if (!file.exists()) {
                FileOutputStream(file).use { it.write(bytes) }
            }
            file
        } catch (e: Exception) { null }
    }

    /** Человекочитаемый размер файла: "128 КБ", "3.4 МБ" и т.п. */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 КБ"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.0f КБ".format(kb).let { if (it == "0 КБ") "1 КБ" else it }
        val mb = kb / 1024.0
        return "%.1f МБ".format(mb)
    }

    /** Короткое расширение файла в верхнем регистре для значка-плашки ("PDF", "DOCX"...). */
    fun extensionLabel(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "")
        return if (ext.isNotBlank() && ext.length <= 5) ext.uppercase() else "ФАЙЛ"
    }
}
