package app.yodo.messenger.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yodo.messenger.ui.theme.YodoPrimary
import app.yodo.messenger.util.ImageUtils
import coil.compose.AsyncImage

/**
 * Показывает аватар пользователя с приоритетом:
 * 1. photoUrl (внешняя ссылка, например фото из Google-аккаунта)
 * 2. avatarBase64 (фото, загруженное вручную — Storage требует платный план, поэтому храним в Firestore)
 * 3. Заглушка — первая буква имени
 */
@Composable
fun UserAvatar(
    displayName: String,
    photoUrl: String?,
    avatarBase64: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(YodoPrimary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            !photoUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Аватар",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }

            !avatarBase64.isNullOrBlank() -> {
                val bitmap = remember(avatarBase64) { ImageUtils.decodeBase64ToBitmap(avatarBase64) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Аватар",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    InitialFallback(displayName)
                }
            }

            else -> InitialFallback(displayName)
        }
    }
}

@Composable
private fun InitialFallback(displayName: String) {
    Text(
        text = displayName.take(1).uppercase().ifBlank { "?" },
        style = MaterialTheme.typography.titleLarge,
        color = YodoPrimary
    )
}
