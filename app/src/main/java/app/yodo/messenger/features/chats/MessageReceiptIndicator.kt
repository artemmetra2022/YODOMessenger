package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yodo.messenger.domain.model.MessageStatus

/** One accessible receipt language for text, media and chat previews. */
@Composable
internal fun MessageReceiptIndicator(status: MessageStatus, neutralColor: Color,
    compact: Boolean = false, recipientCount: Int = 0, readCount: Int = 0) {
    val label = when {
        status == MessageStatus.FAILED -> "Не отправлено"
        status == MessageStatus.SENDING -> "Отправка"
        recipientCount > 1 && readCount > 0 && readCount < recipientCount -> "Прочитали $readCount/$recipientCount"
        status == MessageStatus.READ && recipientCount > 1 -> "Прочитали все"
        status == MessageStatus.READ -> "Прочитано"
        status == MessageStatus.DELIVERED -> "Доставлено"
        else -> "Отправлено"
    }
    val accent = when (status) {
        MessageStatus.READ -> Color(0xFF2584E8)
        MessageStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> neutralColor
    }
    val icon = when (status) {
        MessageStatus.SENDING -> Icons.Filled.Schedule
        MessageStatus.FAILED -> Icons.Filled.ErrorOutline
        MessageStatus.SENT -> Icons.Filled.Done
        MessageStatus.DELIVERED, MessageStatus.READ -> Icons.Filled.DoneAll
    }
    Row(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label }
        .then(if (compact) Modifier else Modifier.background(accent.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(if (compact) 15.dp else 13.dp))
        if (!compact) Text(label, color = accent, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
    }
}
