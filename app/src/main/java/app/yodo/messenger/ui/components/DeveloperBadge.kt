package app.yodo.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yodo.messenger.domain.repository.ChatRepository

/** Текст, который видит пользователь при нажатии на «галочку» аккаунта разработчика. */
const val DEVELOPER_TRUST_MESSAGE = "Это канал разработчиков, ему можно доверять"

/**
 * Является ли аккаунт с данным email одним из двух аккаунтов разработчиков —
 * тех же, что могут писать в официальный канал (ChatRepository.ADMIN_EMAILS).
 */
fun isDeveloperAccount(email: String?): Boolean {
    val e = email?.trim()?.lowercase() ?: return false
    return e in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }
}

/**
 * Синяя «галочка» верификации для аккаунтов разработчиков.
 * По нажатию показывает сообщение о том, что аккаунту можно доверять.
 */
@Composable
fun DeveloperVerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    var showInfo by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable { showInfo = true },
        contentAlignment = Alignment.Center
    ) {
        // Белая «подложка», чтобы галочка читалась на любом фоне.
        Box(
            modifier = Modifier
                .size(size * 0.62f)
                .clip(CircleShape)
                .background(Color.White)
        )
        Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = DEVELOPER_TRUST_MESSAGE,
            tint = Color(0xFF1D9BF0),
            modifier = Modifier.size(size)
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Понятно") }
            },
            icon = { Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF1D9BF0)) },
            title = { Text("Аккаунт разработчика") },
            text = { Text(DEVELOPER_TRUST_MESSAGE) }
        )
    }
}
