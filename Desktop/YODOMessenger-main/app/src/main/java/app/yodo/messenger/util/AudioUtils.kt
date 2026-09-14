package app.yodo.messenger.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

/**
 * НОВОЕ (п.37): голосовые сообщения. Как и с фото (см. ImageUtils), храним запись
 * в самом документе сообщения в Firestore — в виде base64 — а не в отдельном Storage.
 * Формат записи: AAC в контейнере M4A, низкий битрейт (32 kbps, моно, 22050 Гц) —
 * этого достаточно для разборчивой речи и при этом голосовые укладываются в разумные
 * килобайты (минута записи ≈ 240 КБ) даже с учётом base64-раздутия (+33%).
 */
object AudioUtils {

    // Лимит на итоговый base64 голосового сообщения — как и для фото, с запасом под
    // остальные поля документа Firestore (лимит документа — 1 МБ).
    const val MAX_VOICE_BASE64_LENGTH = 900_000
    // Максимальная длительность записи — ограничиваем, чтобы гарантированно уложиться
    // в лимит документа при выбранном битрейте (≈ 240 КБ/мин на base64 → до ~3.5 минут).
    const val MAX_RECORDING_MS = 3 * 60 * 1000L

    private const val SAMPLE_RATE = 22_050
    private const val BIT_RATE = 32_000

    fun startRecording(context: Context): Pair<MediaRecorder, File>? {
        return try {
            val outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setAudioChannels(1)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recorder to outputFile
        } catch (e: Exception) { null }
    }

    fun stopRecording(recorder: MediaRecorder): Boolean {
        return try {
            recorder.stop()
            recorder.release()
            true
        } catch (e: Exception) {
            try { recorder.release() } catch (_: Exception) {}
            false
        }
    }

    fun cancelRecording(recorder: MediaRecorder, file: File) {
        try { recorder.stop() } catch (_: Exception) {}
        try { recorder.release() } catch (_: Exception) {}
        try { file.delete() } catch (_: Exception) {}
    }

    /** Возвращает base64 записи и её длительность в мс, либо null если файл слишком большой/повреждён. */
    fun fileToBase64(file: File): Pair<String, Long>? {
        return try {
            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (base64.length > MAX_VOICE_BASE64_LENGTH) {
                file.delete()
                return null
            }
            val duration = getDurationMs(file)
            file.delete()
            base64 to duration
        } catch (e: Exception) { null }
    }

    private fun getDurationMs(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            duration
        } catch (e: Exception) { 0L }
    }

    /** Декодирует base64 голосового сообщения во временный файл для воспроизведения через MediaPlayer. */
    fun base64ToTempFile(context: Context, base64: String, messageId: String): File? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val file = File(context.cacheDir, "voice_playback_$messageId.m4a")
            if (!file.exists()) {
                FileOutputStream(file).use { it.write(bytes) }
            }
            file
        } catch (e: Exception) { null }
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
