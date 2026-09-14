package app.yodo.messenger.ui.components

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * ИСПРАВЛЕНО (баг 9): полноэкранный диалог, занимающий весь экран целиком — включая области
 * за статус-баром и панелью навигации, без полосок сверху/снизу, где просвечивал предыдущий экран.
 *
 * Причина бага: Compose Dialog открывает собственное окно Android, которое по умолчанию
 * укладывается ВНУТРИ системных панелей (decorFitsSystemWindows=true). Поэтому даже при
 * usePlatformDefaultWidth=false + fillMaxSize() окно не заходило под статус-бар и навигационную
 * панель — там оставались полоски с предыдущим экраном.
 *
 * Здесь окно диалога явно переводится в edge-to-edge (decorFitsSystemWindows=false,
 * растяжка под вырез экрана). Контент обязан сам рисовать фон на всю площадь
 * (fillMaxSize + background) и сам добавлять statusBarsPadding()/navigationBarsPadding()
 * там, где содержимое не должно залезать под системные панели.
 */
@Composable
fun FullscreenDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Окно Compose-диалога — отдельное окно Android; берём его через
        // DialogWindowProvider (тот же приём, что в ViewOnceImageOverlay для FLAG_SECURE).
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.let { window ->
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.setDecorFitsSystemWindows(false)
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                } else {
                    // API 26–27: setDecorFitsSystemWindows нет — раскладываем decor-view
                    // под системные панели через системные UI-флаги.
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                }
            }
            onDispose { }
        }
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}
