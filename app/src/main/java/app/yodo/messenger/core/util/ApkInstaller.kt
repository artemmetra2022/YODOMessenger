package app.yodo.messenger.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * НОВОЕ (OTA-обновления): запуск установки скачанного APK через системный
 * установщик. Файл лежит во внутреннем кэше, поэтому отдаём его только через
 * FileProvider (URI-разрешение на чтение), а не по file://.
 *
 * Начиная с Android 8 установка APK из неизвестного источника требует явного
 * разрешения пользователя — если его нет, отправляем в соответствующий экран
 * настроек, а не молча падаем.
 */
object ApkInstaller {

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** true — установка из приложения разрешена (Android 8+). */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Открывает системный экран «Установка неизвестных приложений» для этого приложения. */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Запускает установщик для [apk]. Требует разрешения (см. [canInstall]). */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Открывает ссылку в браузере (фолбэк, когда APK к релизу не прикреплён). */
    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
