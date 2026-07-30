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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yodo.messenger.util.ImageUtils
import coil.compose.AsyncImage

/**
 * Палитра цветов для аватарок-инициалов (как в Telegram).
 * Цвет выбирается по хешу userId/displayName — стабильно для одного пользователя.
 */
private val avatarColors = listOf(
    Color(0xFFE57373), // красный
    Color(0xFFFF8A65), // оранжевый
    Color(0xFFFFB74D), // янтарный
    Color(0xFFFFD54F), // жёлтый
    Color(0xFFA5D6A7), // зелёный светлый
    Color(0xFF4DB6AC), // бирюзовый
    Color(0xFF4DD0E1), // голубой
    Color(0xFF4FC3F7), // синий светлый
    Color(0xFF7986CB), // индиго
    Color(0xFFBA68C8), // фиолетовый
    Color(0xFFF06292), // розовый
    Color(0xFF90A4AE), // серо-синий
)

/**
 * Возвращает стабильный цвет по строковому ключу (userId или displayName).
 * Одинаковый ключ всегда даёт один и тот же цвет — как в Telegram.
 */
fun avatarColorFor(key: String): Color {
    val index = Math.abs(key.hashCode()) % avatarColors.size
    return avatarColors[index]
}

/**
 * Показывает аватар пользователя с приоритетом:
 * 1. photoUrl (внешняя ссылка, например фото из Google-аккаунта)
 * 2. avatarBase64 (фото, загруженное вручную)
 * 3. Цветной кружок с инициалом — цвет уникален для каждого userId (как в Telegram)
 */
@Composable
fun UserAvatar(
    displayName: String,
    photoUrl: String?,
    avatarBase64: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    // Передавай userId для стабильного цвета; если null — используется displayName
    userId: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
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
                    ColoredInitialFallback(displayName, userId)
                }
            }

            else -> ColoredInitialFallback(displayName, userId)
        }
    }
}

@Composable
private fun ColoredInitialFallback(displayName: String, userId: String?) {
    val colorKey = userId?.takeIf { it.isNotBlank() } ?: displayName
    val bgColor = remember(colorKey) { avatarColorFor(colorKey) }
    val initial = displayName.trim().take(1).uppercase().ifBlank { "?" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
    }
}
