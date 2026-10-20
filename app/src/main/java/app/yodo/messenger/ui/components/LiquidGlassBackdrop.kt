package app.yodo.messenger.ui.components

import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.ceil
import kotlin.math.min

/*
 * Настоящее «жидкое стекло» (преломление + размытие фона) для экспериментального интерфейса.
 *
 * Идея перенесена из Telegram (org.telegram.ui.Components.blur3.LiquidGlassEffect):
 *   1. Контент, который должен «просвечивать» через стекло (список чатов / сообщения),
 *      записывается в общий RenderNode — это «источник» (glassBackdropSource).
 *   2. Каждая стеклянная панель (liquidGlass) рисует этот же RenderNode в собственный
 *      маленький RenderNode, сдвинутый так, чтобы под панелью оказался нужный кусок фона,
 *      и вешает на него RenderEffect: размытие + AGSL-шейдер преломления у краёв.
 *   3. Шейдер (RuntimeShader, API 33+) скругляет форму по SDF, искажает пиксели у границы
 *      как линза, даёт лёгкую хроматическую дисперсию и блик по кромке.
 *
 * На Android < 13 (и когда источник не подключён) liquidGlass() работает как раньше —
 * через градиенты, без преломления.
 */

/** Общий «источник фона» для стеклянных панелей одного экрана. */
@Stable
class GlassBackdrop internal constructor() {
    internal val node: RenderNode? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RenderNode("yodo-glass-backdrop") else null

    /** Позиция источника в окне — нужна, чтобы совместить координаты панели и фона. */
    internal var origin by mutableStateOf(Offset.Zero)

    /** true, пока источник записывает контент (панель внутри источника рисовать нельзя). */
    internal var isRecording = false

    /** true после первой успешной записи. */
    internal var hasContent = false
}

/** Кадр на экран: создаётся в корне экрана и раздаётся панелям через [LocalGlassBackdrop]. */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop() }

/** null — стекло рисуется без преломления (классический интерфейс, Android < 13, нет источника). */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/**
 * Помечает контент как «то, что видно сквозь стекло». Модификатор нужно ставить ПЕРЕД
 * фоном/содержимым, которое должно попасть в источник, и НЕ оборачивать им сами стеклянные
 * панели (иначе панель будет отражать саму себя). Содержимое внутри обычно оборачивают в
 * `CompositionLocalProvider(LocalGlassBackdrop provides null)`.
 */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop?): Modifier {
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return this
        .onGloballyPositioned { backdrop.origin = it.positionInWindow() }
        .drawWithContent { recordBackdrop(backdrop) }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun ContentDrawScope.recordBackdrop(backdrop: GlassBackdrop) {
    val node = backdrop.node
    val width = size.width.toInt()
    val height = size.height.toInt()
    val target = drawContext.canvas
    if (node == null || width <= 0 || height <= 0 || !target.nativeCanvas.isHardwareAccelerated) {
        backdrop.hasContent = false
        drawContent()
        return
    }
    node.setPosition(0, 0, width, height)
    val recording = node.beginRecording(width, height)
    backdrop.isRecording = true
    drawContext.canvas = androidx.compose.ui.graphics.Canvas(recording)
    try {
        drawContent()
    } finally {
        drawContext.canvas = target
        backdrop.isRecording = false
        node.endRecording()
    }
    target.nativeCanvas.drawRenderNode(node)
    backdrop.hasContent = true
}

/**
 * Рисует под содержимым панели преломлённый и размытый фон из [backdrop].
 * Вызывается из liquidGlass(); сам по себе обычно не нужен.
 *
 * @param glass 0..1 — «плотность» стекла (настройка интенсивности): размытие и тонировка.
 */
@Composable
internal fun Modifier.glassRefraction(
    backdrop: GlassBackdrop,
    shape: Shape,
    tint: Color,
    glass: Float,
    dark: Boolean
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val renderer = remember { GlassRefractionRenderer() }
    var position by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { position = it.positionInWindow() }
        .drawBehind {
            val node = backdrop.node
            val offset = position - backdrop.origin
            if (node != null && backdrop.hasContent && !backdrop.isRecording &&
                drawContext.canvas.nativeCanvas.isHardwareAccelerated
            ) {
                renderer.draw(this, node, offset.x, offset.y, shape, tint, glass, dark)
            } else {
                // Источник ещё не записан (первый кадр) — простая тонировка вместо стекла.
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = tint.copy(alpha = 0.18f + 0.35f * glass)
                )
            }
        }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlassRefractionRenderer {
    private val node = RenderNode("yodo-liquid-glass").apply { setClipToBounds(true) }
    private val shader = RuntimeShader(LIQUID_GLASS_AGSL)
    private var lastParams = FloatArray(0)

    fun draw(
        scope: DrawScope,
        backdropNode: RenderNode,
        offsetX: Float,
        offsetY: Float,
        shape: Shape,
        tint: Color,
        glass: Float,
        dark: Boolean
    ) {
        val width = scope.size.width
        val height = scope.size.height
        if (width < 2f || height < 2f) return
        val density = scope.density

        // Размытие: от почти прозрачного стекла (0%) до плотного матового (100%).
        val sigma = (0.3f + glass * 7f) * density
        val blurred = sigma >= 0.75f
        val pad = if (blurred) ceil(sigma * 2.5f).toInt() + 2 else 0

        val widthPx = ceil(width).toInt()
        val heightPx = ceil(height).toInt()
        val nodeWidth = widthPx + pad * 2
        val nodeHeight = heightPx + pad * 2

        // Радиусы углов берём у формы, как в LiquidGlassEffect.update() в Telegram.
        var leftTop = 0f
        var rightTop = 0f
        var rightBottom = 0f
        var leftBottom = 0f
        val outline = shape.createOutline(scope.size, scope.layoutDirection, scope)
        if (outline is Outline.Rounded) {
            val r = outline.roundRect
            leftTop = r.topLeftCornerRadius.x
            rightTop = r.topRightCornerRadius.x
            rightBottom = r.bottomRightCornerRadius.x
            leftBottom = r.bottomLeftCornerRadius.x
        }
        if (leftTop + leftBottom > height) {
            val a = leftTop / (leftTop + leftBottom)
            leftTop = height * a
            leftBottom = height * (1f - a)
        }
        if (rightTop + rightBottom > height) {
            val a = rightTop / (rightTop + rightBottom)
            rightTop = height * a
            rightBottom = height * (1f - a)
        }

        val thickness = min(16f * density, min(width, height) * 0.45f)
        val intensity = 1.0f
        val index = 1.5f
        val highlight = if (dark) 0.55f else 0.85f
        val tintAlpha = ((if (dark) 0.10f else 0.12f) + glass * (if (dark) 0.34f else 0.36f)).coerceIn(0f, 0.8f)

        val params = floatArrayOf(
            width, height, pad.toFloat(), leftTop, rightTop, rightBottom, leftBottom,
            thickness, intensity, index, highlight, sigma,
            tint.red, tint.green, tint.blue, tintAlpha
        )

        node.setPosition(-pad, -pad, widthPx + pad, heightPx + pad)

        if (!params.contentEquals(lastParams)) {
            lastParams = params
            shader.setFloatUniform("resolution", nodeWidth.toFloat(), nodeHeight.toFloat())
            shader.setFloatUniform("center", pad + width / 2f, pad + height / 2f)
            shader.setFloatUniform("size", width / 2f, height / 2f)
            // Порядок как в Telegram: rightBottom, rightTop, leftBottom, leftTop.
            shader.setFloatUniform("radius", rightBottom, rightTop, leftBottom, leftTop)
            shader.setFloatUniform("thickness", thickness)
            shader.setFloatUniform("refract_intensity", intensity)
            shader.setFloatUniform("refract_index", index)
            shader.setFloatUniform("highlight", highlight)
            shader.setFloatUniform(
                "foreground_color_premultiplied",
                tint.red * tintAlpha, tint.green * tintAlpha, tint.blue * tintAlpha, tintAlpha
            )
            val refraction = RenderEffect.createRuntimeShaderEffect(shader, "img")
            node.setRenderEffect(
                if (blurred) {
                    // inner применяется первым: сначала размытие, потом преломление.
                    RenderEffect.createChainEffect(
                        refraction,
                        RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)
                    )
                } else {
                    refraction
                }
            )
        }

        // Кладём нужный кусок фона в локальный RenderNode панели.
        val recording = node.beginRecording(nodeWidth, nodeHeight)
        try {
            recording.translate(pad - offsetX, pad - offsetY)
            recording.drawRenderNode(backdropNode)
        } finally {
            node.endRecording()
        }
        scope.drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
    }
}

/**
 * AGSL-шейдер жидкого стекла. Имена uniform-ов совпадают с LiquidGlassEffect из Telegram,
 * добавлен только `highlight` (сила блика по кромке).
 *
 * Внутри скруглённого прямоугольника (SDF) пиксели берутся из точки, смещённой вглубь
 * панели — чем ближе к краю, тем сильнее (профиль выпуклой линзы). Каналы R/G/B
 * сдвигаются чуть по-разному (дисперсия), поверх добавляются тонировка и блик.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader img;
uniform float2 resolution;
uniform float2 center;
uniform float2 size;
uniform float4 radius;
uniform float thickness;
uniform float refract_intensity;
uniform float refract_index;
uniform float highlight;
uniform float4 foreground_color_premultiplied;

float sdRoundBox(float2 p) {
    float rad;
    if (p.x > 0.0) {
        rad = (p.y > 0.0) ? radius.x : radius.y;
    } else {
        rad = (p.y > 0.0) ? radius.z : radius.w;
    }
    float2 q = abs(p) - size + float2(rad);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - rad;
}

float2 clampUv(float2 uv) {
    return clamp(uv, float2(0.5), resolution - float2(0.5));
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord - center;
    float d = sdRoundBox(p);
    float mask = clamp(0.5 - d, 0.0, 1.0);
    if (mask <= 0.0) {
        return half4(0.0);
    }

    float2 e = float2(0.75, 0.0);
    float2 grad = float2(
        sdRoundBox(p + e.xy) - sdRoundBox(p - e.xy),
        sdRoundBox(p + e.yx) - sdRoundBox(p - e.yx)
    );
    float2 n = grad / max(length(grad), 0.0001);

    float t = 1.0 - clamp(-d, 0.0, thickness) / thickness;
    float bend = 1.0 - sqrt(max(1.0 - t * t, 0.0));
    float shift = bend * thickness * (refract_index - 1.0) * refract_intensity;
    float2 dir = -n;

    float4 cr = float4(img.eval(clampUv(fragCoord + dir * shift * 1.06)));
    float4 cg = float4(img.eval(clampUv(fragCoord + dir * shift)));
    float4 cb = float4(img.eval(clampUv(fragCoord + dir * shift * 0.94)));
    float4 base = float4(cr.r, cg.g, cb.b, cg.a);

    float4 fg = foreground_color_premultiplied;
    float4 col = base * (1.0 - fg.a) + fg;

    float rimLine = 1.0 - clamp(-d / 1.5, 0.0, 1.0);
    float glow = t * t * t;
    float facing = dot(n, normalize(float2(-0.6, -0.8)));
    float spec = (rimLine * 0.55 + glow * 0.30) * (0.25 + 0.75 * max(facing, 0.0))
               + (rimLine * 0.25 + glow * 0.10) * max(-facing, 0.0);
    col.rgb = min(col.rgb + float3(spec * highlight), float3(1.0));

    return half4(col * mask);
}
"""
