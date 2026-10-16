package app.yodo.messenger.features.canvas

import java.util.UUID
import kotlin.math.*

// HIGHLIGHTER — не отдельный вид объекта: это PEN с полупрозрачным цветом (формат не меняется).
enum class CanvasTool { PEN, HIGHLIGHTER, LINE, ARROW, RECTANGLE, ROUNDED_RECT, CIRCLE, ELLIPSE, TRIANGLE, DIAMOND, TEXT, FORMULA, ERASER, HAND }
// ROUNDED_RECT, ELLIPSE, TRIANGLE, DIAMOND — этап 4 (формат документа v3, Nearby-протокол 4).
enum class CanvasObjectKind { PEN, LINE, ARROW, RECTANGLE, CIRCLE, TEXT, FORMULA, ROUNDED_RECT, ELLIPSE, TRIANGLE, DIAMOND }
/** Виды, которых нет в документах v1/v2: старые клиенты их не прочитают. */
val CanvasStage4Kinds: Set<CanvasObjectKind> = setOf(CanvasObjectKind.ROUNDED_RECT, CanvasObjectKind.ELLIPSE, CanvasObjectKind.TRIANGLE, CanvasObjectKind.DIAMOND)
enum class CanvasGrid { NONE, SQUARES, DOTS }
enum class CanvasTemplate { NONE, AXES, TRIANGLE, RIGHT_TRIANGLE }
data class CanvasPoint(val x: Float, val y: Float)
data class CanvasObject(
    val id: String = UUID.randomUUID().toString(), val kind: CanvasObjectKind,
    val points: List<CanvasPoint>, val color: Int = 0xff1e293b.toInt(),
    val width: Float = 4f, val text: String = "", val fontSize: Float = 32f
)
data class CanvasDocument(
    val id: String = UUID.randomUUID().toString(), val title: String,
    val width: Int = 1200, val height: Int = 900,
    val grid: CanvasGrid = CanvasGrid.SQUARES, val template: CanvasTemplate = CanvasTemplate.NONE,
    val objects: List<CanvasObject> = emptyList(), val backgroundBase64: String? = null
)
object CanvasLimits {
    const val MAX_OBJECTS = 300
    const val MAX_POINTS_PER_OBJECT = 2048
    const val MAX_TOTAL_POINTS = 60000
    const val MAX_BACKGROUND = 900000
    const val MAX_JSON_BYTES = 5000000
    const val GRID_STEP = 24f
    fun validId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false)
    fun validate(doc: CanvasDocument) {
        require(validId(doc.id))
        require(doc.title.isNotBlank() && doc.title.length <= 100)
        require(doc.width in 256..4096 && doc.height in 256..4096)
        require(doc.objects.size <= MAX_OBJECTS && doc.objects.sumOf { it.points.size } <= MAX_TOTAL_POINTS)
        require((doc.backgroundBase64?.length ?: 0) <= MAX_BACKGROUND)
        require(doc.objects.map { it.id }.distinct().size == doc.objects.size)
        require(doc.objects.count{it.kind==CanvasObjectKind.FORMULA}<=CanvasMath.MAX_FORMULAS){"Не более 30 формул на доске"}
        doc.objects.forEach { obj ->
            require(validId(obj.id))
            require(obj.width.isFinite() && obj.width in 1f..32f && obj.fontSize.isFinite() && obj.fontSize in 16f..96f)
            require(obj.points.size in 1..MAX_POINTS_PER_OBJECT)
            require(obj.kind in listOf(CanvasObjectKind.PEN, CanvasObjectKind.TEXT) || obj.points.size == 2)
            require(obj.kind != CanvasObjectKind.TEXT || (obj.text.isNotBlank() && obj.text.length <= 500 && obj.text.lines().size <= 8))
            if(obj.kind==CanvasObjectKind.FORMULA){
                require(obj.text==CanvasMath.normalize(obj.text));CanvasMath.parse(obj.text)
                require(obj.points[1].x>obj.points[0].x&&obj.points[1].y>obj.points[0].y){"Некорректные границы формулы"}
            }
            require(obj.points.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..doc.width.toFloat() && it.y in 0f..doc.height.toFloat() })
        }
    }
}
/** Bounded immutable history. A complete stroke/eraser gesture is one undoable change. */
class CanvasHistory(initial: CanvasDocument, private val limit: Int = 60) {
    var current: CanvasDocument = initial; private set
    init { require(limit > 0); CanvasLimits.validate(initial) }
    private val past = ArrayDeque<CanvasDocument>()
    private val future = ArrayDeque<CanvasDocument>()
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    fun commit(next: CanvasDocument): CanvasDocument {
        CanvasLimits.validate(next)
        require(next.id == current.id)
        if (next == current) return current
        past.addLast(current); while (past.size > limit) past.removeFirst()
        current = next; future.clear(); return current
    }
    fun undo(): CanvasDocument { if (canUndo) { future.addLast(current); current = past.removeLast() }; return current }
    fun redo(): CanvasDocument { if (canRedo) { past.addLast(current); current = future.removeLast() }; return current }
}
data class CanvasTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun world(screen: CanvasPoint) = CanvasPoint((screen.x - offsetX) / scale, (screen.y - offsetY) / scale)
    fun screen(world: CanvasPoint) = CanvasPoint(world.x * scale + offsetX, world.y * scale + offsetY)
    companion object {
        fun fit(viewWidth: Float, viewHeight: Float, doc: CanvasDocument, zoom: Float = 1f, pan: CanvasPoint = CanvasPoint(0f, 0f)): CanvasTransform {
            val fit = min(viewWidth / doc.width, viewHeight / doc.height).coerceAtLeast(0.001f) * 0.95f
            val scale = fit * zoom.coerceIn(1f, 6f)
            return CanvasTransform(scale, (viewWidth - doc.width * scale) / 2f + pan.x, (viewHeight - doc.height * scale) / 2f + pan.y)
        }
    }
}
fun canImportCanvasImage(isViewOnce: Boolean, isDeleted: Boolean, expiresAt: Long?, now: Long): Boolean =
    !isViewOnce && !isDeleted && (expiresAt == null || expiresAt > now)

fun segmentDistance(point: CanvasPoint, a: CanvasPoint, b: CanvasPoint): Float {
    val dx = b.x - a.x; val dy = b.y - a.y
    val denominator = dx * dx + dy * dy
    val t = if (denominator == 0f) 0f else (((point.x - a.x) * dx + (point.y - a.y) * dy) / denominator).coerceIn(0f, 1f)
    return hypot(point.x - a.x - t * dx, point.y - a.y - t * dy)
}
fun arrowLegs(a: CanvasPoint, b: CanvasPoint, width: Float): Pair<CanvasPoint, CanvasPoint> {
    val angle = atan2(b.y - a.y, b.x - a.x); val length = 14f + width * 2f
    fun end(delta: Float) = CanvasPoint(b.x - cos(angle + delta) * length, b.y - sin(angle + delta) * length)
    return end(0.5f) to end(-0.5f)
}
fun hitCanvasObject(obj: CanvasObject, point: CanvasPoint, radius: Float): Boolean {
    val a = obj.points.first(); val b = obj.points.last(); val tolerance = radius + obj.width / 2f
    return when (obj.kind) {
        CanvasObjectKind.PEN -> if (obj.points.size == 1) segmentDistance(point, a, a) <= tolerance
            else obj.points.zipWithNext().any { (start, end) -> segmentDistance(point, start, end) <= tolerance }
        CanvasObjectKind.LINE -> segmentDistance(point, a, b) <= tolerance
        CanvasObjectKind.ARROW -> {
            val legs = arrowLegs(a, b, obj.width)
            segmentDistance(point, a, b) <= tolerance || segmentDistance(point, b, legs.first) <= tolerance || segmentDistance(point, b, legs.second) <= tolerance
        }
        CanvasObjectKind.RECTANGLE, CanvasObjectKind.ROUNDED_RECT -> {
            val c = CanvasPoint(a.x, b.y); val d = CanvasPoint(b.x, a.y)
            listOf(a to c, c to b, b to d, d to a).any { segmentDistance(point, it.first, it.second) <= tolerance }
        }
        CanvasObjectKind.TRIANGLE, CanvasObjectKind.DIAMOND -> {
            val polygon = canvasPolygon(obj.kind, a, b)
            polygon.indices.any { segmentDistance(point, polygon[it], polygon[(it + 1) % polygon.size]) <= tolerance }
        }
        CanvasObjectKind.ELLIPSE -> {
            val rx = (abs(b.x - a.x) / 2f).coerceAtLeast(0.5f); val ry = (abs(b.y - a.y) / 2f).coerceAtLeast(0.5f)
            val normalized = hypot((point.x - (a.x + b.x) / 2f) / rx, (point.y - (a.y + b.y) / 2f) / ry)
            abs(normalized - 1f) * min(rx, ry) <= tolerance
        }
        CanvasObjectKind.CIRCLE -> abs(hypot(point.x - a.x, point.y - a.y) - hypot(b.x - a.x, b.y - a.y)) <= tolerance
        CanvasObjectKind.FORMULA -> point.x>=a.x-radius&&point.x<=b.x+radius&&point.y>=a.y-radius&&point.y<=b.y+radius
        CanvasObjectKind.TEXT -> point.x >= a.x - radius && point.x <= a.x + obj.text.lines().maxOf { it.length } * obj.fontSize * 0.65f + radius &&
            point.y >= a.y - obj.fontSize - radius && point.y <= a.y + obj.text.lines().size * obj.fontSize * 1.3f + radius
    }
}
