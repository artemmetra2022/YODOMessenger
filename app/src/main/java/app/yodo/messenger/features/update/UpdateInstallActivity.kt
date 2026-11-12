package app.yodo.messenger.features.update

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.yodo.messenger.core.util.ApkInstaller
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Non-exported notification destination. A tap never silently installs or trusts an intent file path. */
@AndroidEntryPoint
class UpdateInstallActivity : ComponentActivity() {
    @Inject internal lateinit var store: InstantUpdateStore
    private var dialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onResume() {
        super.onResume()
        if (dialog?.isShowing == true) return
        val ready = store.state.value
        val file = ready.file
        val dir = java.io.File(cacheDir, "updates").canonicalFile
        val valid = ready.phase == InstantPhase.READY && file != null && file.isFile && file.length() > 0 &&
            file.canonicalFile.parentFile == dir && file.extension.equals("apk", true)
        if (!valid) {
            AlertDialog.Builder(this).setTitle("Файл обновления недоступен")
                .setMessage("Откройте «О приложении» и загрузите обновление повторно.")
                .setPositiveButton("Закрыть") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
            return
        }
        dialog = AlertDialog.Builder(this).setTitle("Обновление YodoMessenger ${ready.versionName}")
            .setMessage("APK скачан. Установка начнётся только после вашего подтверждения в системном окне Android.")
            .setPositiveButton(if (ApkInstaller.canInstall(this)) "Продолжить установку" else "Разрешить установку") { _, _ ->
                dialog = null
                try {
                    if (ApkInstaller.canInstall(this)) { ApkInstaller.install(this, file!!); finish() }
                    else ApkInstaller.openInstallPermissionSettings(this)
                } catch (_: Exception) {
                    AlertDialog.Builder(this).setTitle("Не удалось открыть установщик")
                        .setMessage("Проверьте файл или загрузите APK повторно в «О приложении».")
                        .setPositiveButton("Закрыть") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
                }
            }.setNegativeButton("Позже") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }
    override fun onDestroy() { dialog?.dismiss(); super.onDestroy() }
}
