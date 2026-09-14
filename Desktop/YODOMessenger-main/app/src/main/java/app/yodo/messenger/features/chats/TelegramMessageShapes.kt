package app.yodo.messenger.features.chats

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

fun telegramBubbleShape(isOutgoing: Boolean, position: MessageGroupPosition): RoundedCornerShape {
    val outer = 12.dp
    val inner = 4.dp
    return when (position) {
        MessageGroupPosition.SINGLE -> RoundedCornerShape(outer)
        MessageGroupPosition.FIRST -> if (isOutgoing) {
            RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = outer, bottomEnd = inner)
        } else {
            RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = outer)
        }
        MessageGroupPosition.MIDDLE -> if (isOutgoing) {
            RoundedCornerShape(topStart = outer, topEnd = inner, bottomStart = outer, bottomEnd = inner)
        } else {
            RoundedCornerShape(topStart = inner, topEnd = outer, bottomStart = inner, bottomEnd = outer)
        }
        MessageGroupPosition.LAST -> if (isOutgoing) {
            RoundedCornerShape(topStart = outer, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        } else {
            RoundedCornerShape(topStart = inner, topEnd = outer, bottomStart = outer, bottomEnd = outer)
        }
    }
}

fun DrawScope.drawTelegramTail(color: Color, isOutgoing: Boolean) {
    val width = 8.dp.toPx()
    val height = 12.dp.toPx()
    val path = Path()
    if (isOutgoing) {
        path.moveTo(size.width - width, size.height - height * 0.72f)
        path.cubicTo(
            size.width - width * 0.25f, size.height - height * 0.55f,
            size.width - width * 0.15f, size.height - height * 0.12f,
            size.width, size.height
        )
        path.cubicTo(
            size.width - width * 0.55f, size.height - height * 0.08f,
            size.width - width * 0.45f, size.height - height * 0.05f,
            size.width - width, size.height
        )
    } else {
        path.moveTo(width, size.height - height * 0.72f)
        path.cubicTo(
            width * 0.25f, size.height - height * 0.55f,
            width * 0.15f, size.height - height * 0.12f,
            0f, size.height
        )
        path.cubicTo(
            width * 0.55f, size.height - height * 0.08f,
            width * 0.45f, size.height - height * 0.05f,
            width, size.height
        )
    }
    drawPath(path, color)
}
