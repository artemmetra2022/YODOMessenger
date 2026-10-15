package app.yodo.messenger.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R

/**
 * Красивый аватар официального канала YodoMessenger.
 *
 * Вместо простой буквы «Y» — фирменный градиентный круг с белым
 * «самолётиком» (логотип бренда, ic_yodo_send).
 */
@Composable
fun OfficialChannelAvatar(
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF2AA9FF),
                        Color(0xFF1D6BF0),
                        Color(0xFF6C5CE7)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_yodo_send),
            contentDescription = "YodoMessenger",
            modifier = Modifier.size(size * 0.5f)
        )
    }
}
