package app.yodo.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun supportAccentContent(accent: Color): Color = if (accent.luminance() > .179f) Color.Black else Color.White

@Composable
internal fun SupportBotAvatar(modifier: Modifier = Modifier, size: Dp = 48.dp,
    accent: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * .32f)).background(accent),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Default.SmartToy, contentDescription = "Помощник поддержки YodoMessenger",
            tint = supportAccentContent(accent), modifier = Modifier.size(size * .56f))
    }
}
