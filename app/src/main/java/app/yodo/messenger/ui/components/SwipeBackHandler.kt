package app.yodo.messenger.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * НОВОЕ (п.8): свайп слева направо для возврата на предыдущий экран — как в Telegram/iOS.
 *
 * Срабатывает, только если жест НАЧАЛСЯ у самого левого края экрана (первые edgeWidthDp),
 * и только если суммарное смещение вправо превысило thresholdDp. Это специально сделано
 * узким — чтобы не конфликтовать с уже существующими горизонтальными свайпами внутри
 * контента (свайп по сообщению для ответа в ChatScreen, свайп по чату для удаления
 * в ChatListScreen) — те жесты стартуют не у самого края экрана.
 *
 * Использование — просто добавить модификатор на корневой контейнер экрана:
 *   Box(modifier = Modifier.fillMaxSize().swipeToGoBack(onBack = onBackClick)) { ... }
 */
fun Modifier.swipeToGoBack(
    edgeWidthDp: Float = 24f,
    thresholdDp: Float = 60f,
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
