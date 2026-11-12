package app.yodo.messenger.features.canvas

import kotlin.math.*

/** Чистая геометрия фигур доски и распознавание «умных фигур» (без Android-зависимостей — проверяется JVM-тестами). */
data class CanvasRecognizedShape(val kind: CanvasObjectKind, val points: List<CanvasPoint>)

/** Вершины замкнутого многоугольника в рамке a–b для TRIANGLE/DIAMOND; для остальных видов — пусто. */
fun canvasPolygon(kind: CanvasObjectKind, a: CanvasPoint, b: CanvasPoint): List<CanvasPoint> {
    val left = min(a.x, b.x); val right = max(a.x, b.x); val top = min(a.y, b.y); val bottom = max(a.y, b.y)
    val midX = (left + right) / 2f; val midY = (top + bottom) / 2f
    return when (kind) {
        CanvasObjectKind.TRIANGLE -> listOf(CanvasPoint(midX, top), CanvasPoint(right, bottom), CanvasPoint(left, bottom))
        CanvasObjectKind.DIAMOND -> listOf(CanvasPoint(midX, top), CanvasPoint(right, midY), CanvasPoint(midX, bottom), CanvasPoint(left, midY))
        else -> emptyList()
    }
}

/** Радиус скругления для ROUNDED_RECT: 20% меньшей стороны. */
fun canvasCornerRadius(a: CanvasPoint, b: CanvasPoint): Float = min(abs(b.x - a.x), abs(b.y - a.y)) * 0.2f

private fun polygonDistance(point: CanvasPoint, polygon: List<CanvasPoint>): Float =
    polygon.indices.minOf { segmentDistance(point, polygon[it], polygon[(it + 1) % polygon.size]) }

/** Точки штриха, лежащие рядом с отрезком a–b, должны покрывать не меньше половины его длины. */
private fun edgeCovered(points: List<CanvasPoint>, a: CanvasPoint, b: CanvasPoint, tolerance: Float): Boolean {
    val length = hypot(b.x - a.x, b.y - a.y)
    if (length <= 0f) return false
    var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE; var count = 0
    points.forEach { p ->
        if (segmentDistance(p, a, b) <= tolerance) {
            val t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / length
            lo = min(lo, t); hi = max(hi, t); count++
        }
    }
    return count >= 2 && hi - lo >= 0.5f * length
}

/**
 * Превращает рукописный штрих в ровную фигуру: линия, прямоугольник, ромб, треугольник,
 * окружность или эллипс. Возвращает null, если штрих не похож ни на одну из них
 * (волна, зигзаг, спираль, дуга, каракули остаются обычным карандашом).
 */
fun recognizeCanvasShape(points: List<CanvasPoint>, maxX: Float, maxY: Float): CanvasRecognizedShape? {
    if (points.size < 8 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
    val start = points.first(); val end = points.last()
    val minX = points.minOf { it.x }; val maxPX = points.maxOf { it.x }
    val minY = points.minOf { it.y }; val maxPY = points.maxOf { it.y }
    val w = maxPX - minX; val h = maxPY - minY
    var length = 0f
    points.zipWithNext { p, q -> length += hypot(q.x - p.x, q.y - p.y) }
    if (length < 48f) return null
    fun clamp(p: CanvasPoint) = CanvasPoint(p.x.coerceIn(0f, maxX), p.y.coerceIn(0f, maxY))
    fun result(kind: CanvasObjectKind, vararg pts: CanvasPoint) = CanvasRecognizedShape(kind, pts.map(::clamp))
    val chord = hypot(end.x - start.x, end.y - start.y)
    // Прямая: почти без изгиба.
    if (chord >= 0.85f * length) {
        val deviation = points.maxOf { segmentDistance(it, start, end) }
        return if (deviation <= max(8f, 0.06f * chord)) result(CanvasObjectKind.LINE, start, end) else null
    }
    val size = max(w, h); val small = min(w, h)
    // Остальные фигуры должны быть почти замкнуты.
    if (chord > 0.25f * size || small < 24f) return null
    val a = CanvasPoint(minX, minY); val b = CanvasPoint(maxPX, maxPY)
    val cx = (minX + maxPX) / 2f; val cy = (minY + maxPY) / 2f; val rx = w / 2f; val ry = h / 2f
    val radii = points.map { hypot((it.x - cx) / rx, (it.y - cy) / ry) }
    val mean = radii.average().toFloat()
    val deviation = sqrt(radii.sumOf { ((it - mean) * (it - mean)).toDouble() } / radii.size).toFloat()
    val sectors = points.map { (((atan2(it.y - cy, it.x - cx) + PI) / (2 * PI)) * 12).toInt().coerceIn(0, 11) }.toSet().size
    fun ellipse(): CanvasRecognizedShape =
        if (abs(w - h) <= 0.15f * size) result(CanvasObjectKind.CIRCLE, CanvasPoint(cx, cy), CanvasPoint(cx + (w + h) / 4f, cy))
        else result(CanvasObjectKind.ELLIPSE, a, b)
    if (abs(mean - 1f) <= 0.07f && deviation <= 0.06f && sectors >= 11) return ellipse()
    // Прямоугольник.
    val rectTolerance = max(0.1f * small, 8f)
    val nearRect = points.count {
        min(min(abs(it.x - minX), abs(it.x - maxPX)), min(abs(it.y - minY), abs(it.y - maxPY))) <= rectTolerance
    }
    if (nearRect >= 0.93f * points.size) {
        val sides = listOf(
            CanvasPoint(minX, minY) to CanvasPoint(maxPX, minY), CanvasPoint(maxPX, minY) to CanvasPoint(maxPX, maxPY),
            CanvasPoint(maxPX, maxPY) to CanvasPoint(minX, maxPY), CanvasPoint(minX, maxPY) to CanvasPoint(minX, minY)
        )
        if (sides.all { edgeCovered(points, it.first, it.second, rectTolerance) }) return result(CanvasObjectKind.RECTANGLE, a, b)
    }
    // Ромб и треугольник.
    val polyTolerance = max(0.09f * small, 8f)
    for (kind in listOf(CanvasObjectKind.DIAMOND, CanvasObjectKind.TRIANGLE)) {
        val polygon = canvasPolygon(kind, a, b)
        val near = points.count { polygonDistance(it, polygon) <= polyTolerance }
        if (near >= 0.93f * points.size &&
            polygon.indices.all { edgeCovered(points, polygon[it], polygon[(it + 1) % polygon.size], polyTolerance) }) return result(kind, a, b)
    }
    if (abs(mean - 1f) <= 0.12f && deviation <= 0.1f && sectors >= 10) return ellipse()
    return null
}

/** Привязка точки к узлу сетки (GRID_STEP), с удержанием внутри листа. */
fun snapCanvasPoint(point: CanvasPoint, maxX: Float, maxY: Float, step: Float = CanvasLimits.GRID_STEP): CanvasPoint =
    CanvasPoint((point.x / step).roundToInt() * step.coerceAtLeast(1f), (point.y / step).roundToInt() * step.coerceAtLeast(1f))
        .let { CanvasPoint(it.x.coerceIn(0f, maxX), it.y.coerceIn(0f, maxY)) }
