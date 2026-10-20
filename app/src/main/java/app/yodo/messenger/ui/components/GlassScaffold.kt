package app.yodo.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.yodo.messenger.data.local.InterfaceStyle
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.LocalInterfaceStyle

/**
 * Отступы под стеклянными панелями. В экспериментальном интерфейсе [GlassScaffold] отдаёт
 * контенту нулевой padding, а реальную высоту верхней/нижней панели кладёт сюда — прокручиваемый
 * список передаёт её в `contentPadding`, и контент проходит под стеклом.
 * В классическом интерфейсе всегда нули.
 */
val LocalGlassContentInset = compositionLocalOf { PaddingValues(0.dp) }

@Composable
fun glassContentPadding(): PaddingValues = LocalGlassContentInset.current

/**
 * Drop-in замена [Scaffold] для экранов с прокручиваемым контентом.
 *
 * Классический интерфейс — обычный Scaffold, ничего не меняется.
 * Экспериментальный — верхняя панель становится жидким стеклом, контент записывается как
 * источник фона и проходит под верхней/нижней панелями (см. LiquidGlassBackdrop.kt).
 * Контент должен брать отступы из [glassContentPadding] (contentPadding списка или
 * padding внутри verticalScroll), а не из параметра лямбды — он в этом режиме нулевой.
 */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    // true — контент уходит под плавающие стеклянные панели (список должен брать отступы из
    // glassContentPadding()). false — контент начинается под панелью, как в обычном Scaffold:
    // безопасный режим для экранов, где отступы пока не переведены.
    contentUnderBars: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val experimental = LocalInterfaceStyle.current == InterfaceStyle.EXPERIMENTAL
    if (!experimental) {
        Scaffold(
            modifier = modifier,
            topBar = topBar ?: {},
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            floatingActionButtonPosition = floatingActionButtonPosition,
            containerColor = containerColor,
            contentColor = contentColor,
            contentWindowInsets = contentWindowInsets,
            content = content
        )
        return
    }

    val defaultContainer = MaterialTheme.colorScheme.background
    val dark = defaultContainer.luminance() < 0.5f
    val colorTheme = LocalColorTheme.current
    val backdrop = rememberGlassBackdrop()
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Scaffold(
            modifier = modifier,
            topBar = { if (topBar != null) {
                // Плавающая плашка со скруглёнными углами под статус-баром, как в Telegram.
                // statusBarsPadding() заодно «съедает» инсет статус-бара у вложенного TopAppBar.
                Box(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .liquidGlass(
                            enabled = true,
                            shape = RoundedCornerShape(26.dp),
                            tint = glassTint(dark),
                            dark = dark,
                            elevation = 10
                        )
                ) {
                    // TopAppBar по умолчанию красит себя в surface — делаем его прозрачным,
                    // чтобы было видно стекло (без правок в каждом экране).
                    val scheme = MaterialTheme.colorScheme
                    MaterialTheme(
                        colorScheme = scheme.copy(
                            surface = Color.Transparent,
                            surfaceContainer = Color.Transparent,
                            surfaceContainerHigh = Color.Transparent,
                            surfaceContainerLow = Color.Transparent
                        ),
                        typography = MaterialTheme.typography,
                        shapes = MaterialTheme.shapes
                    ) { topBar() }
                }
            } },
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            floatingActionButtonPosition = floatingActionButtonPosition,
            contentColor = contentColor,
            contentWindowInsets = contentWindowInsets,
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .glassBackdropSource(backdrop)
                    .then(
                        if (containerColor == Color.Transparent || containerColor == defaultContainer) {
                            Modifier.softMessengerBackdrop(
                                enabled = true,
                                primary = colorTheme.primary,
                                accent = colorTheme.accent,
                                dark = dark
                            )
                        } else {
                            // Экран задал собственный цвет фона (камера, кроп и т. п.) — уважаем его.
                            Modifier.background(containerColor)
                        }
                    )
            ) {
                CompositionLocalProvider(
                    LocalGlassBackdrop provides null,
                    LocalGlassContentInset provides if (contentUnderBars) {
                        PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding()
                        )
                    } else {
                        PaddingValues(0.dp)
                    }
                ) {
                    content(if (contentUnderBars) PaddingValues(0.dp) else padding)
                }
            }
        }
    }
}
