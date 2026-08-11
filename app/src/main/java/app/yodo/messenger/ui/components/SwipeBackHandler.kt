package app.yodo.messenger.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * НОВОЕ (п.8): свайп слева направо для возврата на предыдущий экран — как в Telegram/iOS.
 *
 * Срабатывает, только если жест НАЧАЛСЯ у самого левого края экрана (первые edgeWidthDp),
 * и только если суммарное смещение вправо превысило thresholdDp. Порог специально увеличен
 * (было 60dp) — раньше жест конфликтовал со свайпом "ответить" на сообщении и часто не
 * срабатывал вовсе; теперь это осознанный, более длинный свайп, который не путается с
 * коротким свайпом-ответом (см. thresholdDp у свайпа-ответа в ChatScreen — он, наоборот,
 * уменьшен).
 *
 * Использование — просто добавить модификатор на корневой контейнер экрана:
 *   Box(modifier = Modifier.fillMaxSize().swipeToGoBack(onBack = onBackClick)) { ... }
 */
fun Modifier.swipeToGoBack(
    edgeWidthDp: Float = 24f,
    thresholdDp: Float = 100f,
    onBack: () -> Unit
): Modifier = this.pointerInput(Unit) {
    val edgeWidthPx = edgeWidthDp * density
    val thresholdPx = thresholdDp * density
    var startedAtEdge = false
    var totalDrag = 0f

    detectHorizontalDragGestures(
        onDragStart = { offset ->
            startedAtEdge = offset.x <= edgeWidthPx
            totalDrag = 0f
        },
        onDragEnd = {
            if (startedAtEdge && totalDrag > thresholdPx) {
                onBack()
            }
            startedAtEdge = false
            totalDrag = 0f
        },
        onDragCancel = {
            startedAtEdge = false
            totalDrag = 0f
        },
        onHorizontalDrag = { _, dragAmount ->
            if (startedAtEdge) {
                totalDrag += dragAmount
            }
        }
    )
}
