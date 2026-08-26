package app.yodo.messenger.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yodo.messenger.ui.theme.LocalColorTheme
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * НОВОЕ: красивый ввод PIN-кода — каждая цифра в своей клеточке (как OTP).
 * Невидимое поле BasicTextField ловит ввод, а цифры рисуются клетками.
 */
@Composable
fun PinCellsInput(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 4,
    mask: Boolean = true,
    isError: Boolean = false
) {
    val colorTheme = LocalColorTheme.current
    val focusRequester = remember { FocusRequester() }

    Box(modifier = modifier) {
        // Прозрачное поле поверх клеток ловит нажатие и ввод напрямую.
        // Раньше был requestFocus() на поле размером 0.dp — это вызывало краш
        // (узел нулевого размера нельзя сфокусировать). Теперь фокус нативный по тапу.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(length) { index ->
                val filled = index < pin.length
                val borderColor = when {
                    isError -> MaterialTheme.colorScheme.error
                    filled -> colorTheme.primary
                    else -> MaterialTheme.colorScheme.outline
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorTheme.primary.copy(alpha = if (filled) 0.10f else 0.03f))
                        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                        ,
                    contentAlignment = Alignment.Center
                ) {
                    if (filled) {
                        Text(
                            text = if (mask) "•" else pin[index].toString(),
                            fontSize = if (mask) 28.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        BasicTextField(
            value = pin,
            onValueChange = { new ->
                val digits = new.filter(Char::isDigit).take(length)
                onPinChange(digits)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.matchParentSize(),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent)
        )
    }
}

// Клик без визуального ripple, чтобы фокус переходил на скрытое поле по нажатию на клетку.
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
        indication = null,
        onClick = onClick
    )
