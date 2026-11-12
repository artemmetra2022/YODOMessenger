@file:Suppress("unused", "FunctionName")

package app.yodo.messenger.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable as foundationCombinedClickable
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem as materialNavigationBarItem
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay

/*
 * ТАКТИЛЬНЫЙ ОТКЛИК (squish).
 *
 * Любая кнопка / переключатель / кликабельный элемент при нажатии мягко
 * «сжимается» (сильнее по вертикали, чуть меньше по горизонтали), а при
 * отпускании пружинит с лёгким перелётом — как желе.
 *
 * Как это подключено ко всему приложению:
 *  1. Modifier.clickable / combinedClickable из этого файла заменяют
 *     стандартные (в файлах просто заменён import).
 *  2. Button, TextButton, OutlinedButton, FilledTonalButton, IconButton,
 *     FloatingActionButton, Switch, Checkbox, RadioButton, FilterChip,
 *     DropdownMenuItem, NavigationBarItem — тонкие обёртки над Material3
 *     с тем же именем и теми же параметрами + squish().
 *
 * Если нужно отключить эффект для конкретного места — используйте
 * androidx.compose.foundation.clickable напрямую или Modifier.squish(enabled = false).
 * Общая сила эффекта — SquishDefaults.
 */
object SquishDefaults {
    /** Глубина сжатия по горизонтали, dp на сторону (ограничена долей от ширины). */
    const val INSET_X_DP = 5f
    /** Глубина сжатия по вертикали, dp на сторону (ограничена долей от высоты). */
    const val INSET_Y_DP = 6f
    const val MAX_FRACTION_X = 0.06f
    const val MAX_FRACTION_Y = 0.09f
    const val MIN_FRACTION = 0.012f
    /** Минимальное время, которое «вдавленность» видна даже при очень быстром тапе. */
    const val MIN_PRESS_VISIBLE_MS = 70L
}

/**
 * Множитель силы сжатия для целого поддерева (например, контекстного меню сообщения,
 * где пункты хотелось сделать отзывчивее). По умолчанию 1f — ничего не меняется.
 */
val LocalSquishScale = compositionLocalOf { 1f }

/**
 * Рисует содержимое элемента сжатым, пока он нажат.
 * Только отрисовка: размеры и зона нажатия не меняются, поэтому палец
 * на краю кнопки не «срывает» нажатие.
 *
 * @param strength 1f — стандартно, <1f — мягче (большие строки списка), >1f — сильнее.
 */
fun Modifier.squish(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    strength: Float = 1f
): Modifier = if (!enabled) this else composed {
    val pressed by interactionSource.collectIsPressedAsState()
    // 0f — покой, 1f — полностью вдавлено. Пружина на отпускании даёт перелёт (<0) = лёгкое растяжение.
    val progress = remember { Animatable(0f) }
    val pressStart = remember { longArrayOf(0L) }
    val scale = LocalSquishScale.current

    LaunchedEffect(pressed) {
        if (pressed) {
            pressStart[0] = System.currentTimeMillis()
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 1100f)
            )
        } else if (progress.value != 0f) {
            val shown = System.currentTimeMillis() - pressStart[0]
            val wait = SquishDefaults.MIN_PRESS_VISIBLE_MS - shown
            if (wait > 0) delay(wait)
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.38f, stiffness = 380f)
            )
        }
    }

    this.drawWithContent {
        val p = progress.value
        if (p == 0f) {
            drawContent()
        } else {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val fx = ((SquishDefaults.INSET_X_DP * density) / w)
                .coerceIn(SquishDefaults.MIN_FRACTION, SquishDefaults.MAX_FRACTION_X) * strength * scale
            val fy = ((SquishDefaults.INSET_Y_DP * density) / h)
                .coerceIn(SquishDefaults.MIN_FRACTION, SquishDefaults.MAX_FRACTION_Y) * strength * scale
            withTransform({
                scale(
                    scaleX = 1f - fx * p,
                    scaleY = 1f - fy * p,
                    pivot = Offset(w / 2f, h / 2f)
                )
            }) {
                this@drawWithContent.drawContent()
            }
        }
    }
}

/* ───────────────────────────── clickable ───────────────────────────── */

fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .squish(source, enabled, strength = 0.9f)
        .foundationClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick
        )
}

fun Modifier.clickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = this
    // indication = null — место, где разработчик сознательно отключил визуальный отклик; не трогаем.
    .squish(interactionSource, enabled && indication != null, strength = 0.9f)
    .foundationClickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .squish(source, enabled, strength = 0.8f)
        .foundationCombinedClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
            onClick = onClick
        )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = this
    .squish(interactionSource, enabled && indication != null, strength = 0.8f)
    .foundationCombinedClickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        onClick = onClick
    )

/* ───────────────────────── Material3 обёртки ───────────────────────── */

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.squish(interactionSource, enabled),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier = modifier.squish(interactionSource, enabled),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.squish(interactionSource, enabled),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier.squish(interactionSource, enabled),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier.squish(interactionSource, enabled, strength = 1.25f),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier = modifier.squish(interactionSource, strength = 1.3f),
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.squish(interactionSource, enabled && onCheckedChange != null, strength = 1.3f),
        thumbContent = thumbContent,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource
    )
}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    androidx.compose.material3.Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.squish(interactionSource, enabled && onCheckedChange != null, strength = 1.3f),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource
    )
}

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    androidx.compose.material3.RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier.squish(interactionSource, enabled && onClick != null, strength = 1.3f),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource
    )
}

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.squish(interactionSource, enabled),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource
    )
}

@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    androidx.compose.material3.DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.squish(interactionSource, enabled, strength = 0.7f),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    )
}

@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = NavigationBarItemDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    materialNavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        modifier = modifier.squish(interactionSource, enabled, strength = 1.2f),
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
        colors = colors,
        interactionSource = interactionSource
    )
}
