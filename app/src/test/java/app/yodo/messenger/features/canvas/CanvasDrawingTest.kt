package app.yodo.messenger.features.canvas

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/** Этап 4: новые фигуры, маркер, «умные фигуры» и привязка к сетке. */
class CanvasDrawingTest {
    private fun p(x: Float, y: Float) = CanvasPoint(x, y)
    private fun reject(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }
    private fun doc(vararg objects: CanvasObject) = CanvasDocument(title = "Рисование", objects = objects.toList())
    private fun shape(kind: CanvasObjectKind) = CanvasObject(kind = kind, points = listOf(p(100f, 100f), p(300f, 220f)))

    // Детерминированный «дрожащий» штрих: точки вдоль замкнутой ломаной с лёгким отклонением.
    private fun resample(poly: List<CanvasPoint>, closed: Boolean, step: Float = 6f): List<CanvasPoint> {
        val out = mutableListOf<CanvasPoint>()
        val n = poly.size
        for (i in 0 until if (closed) n else n - 1) {
            val a = poly[i]; val b = poly[(i + 1) % n]
            val count = max(1, (hypot(b.x - a.x, b.y - a.y) / step).toInt())
            for (j in 0 until count) out.add(p(a.x + (b.x - a.x) * j / count, a.y + (b.y - a.y) * j / count + sin(out.size * 1.7f) * 1.2f))
        }
        return out
    }
    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, n: Int = 70, over: Float = 0.05f) =
        (0 until n).map { i -> val t = (2 * PI * i / n * (1 + over)).toFloat(); p(cx + rx * cos(t), cy + ry * sin(t)) }
    private fun recognize(points: List<CanvasPoint>) = recognizeCanvasShape(points, 1200f, 900f)

    @Test fun newShapesRoundTripAsVersion3() {
        val d = doc(*CanvasStage4Kinds.map(::shape).toTypedArray())
        val raw = CanvasCodec.encode(d)
        assertTrue(raw.contains("\"version\":3"))
        assertEquals(d, CanvasCodec.decode(raw))
    }
    @Test fun oldVersionsRejectNewShapes() {
        val raw = CanvasCodec.encode(doc(shape(CanvasObjectKind.ELLIPSE)))
        reject { CanvasCodec.decode(raw.replace("\"version\":3", "\"version\":2")) }
        reject { CanvasCodec.decode(raw.replace("\"version\":3", "\"version\":1")) }
    }
    @Test fun documentsWithoutNewShapesStayOnOlderVersions() {
        assertTrue(CanvasCodec.encode(doc(shape(CanvasObjectKind.RECTANGLE))).contains("\"version\":1"))
    }
    @Test fun newShapesNeedTwoPoints() {
        CanvasStage4Kinds.forEach { kind ->
            reject { CanvasLimits.validate(doc(CanvasObject(kind = kind, points = listOf(p(1f, 1f))))) }
            reject { CanvasLimits.validate(doc(CanvasObject(kind = kind, points = listOf(p(1f, 1f), p(2f, 2f), p(3f, 3f))))) }
        }
    }
    @Test fun highlighterIsAnOrdinaryTranslucentPen() {
        val color = (0xff2563eb.toInt() and 0x00ffffff) or 0x66000000
        val stroke = CanvasObject(kind = CanvasObjectKind.PEN, points = listOf(p(10f, 10f), p(200f, 20f)), color = color, width = 28f)
        val d = doc(stroke)
        val raw = CanvasCodec.encode(d)
        assertTrue(raw.contains("\"version\":1"))
        assertEquals(d, CanvasCodec.decode(raw))
        assertEquals(0x66, (color ushr 24) and 0xff)
    }
    @Test fun polygonsAndCornerRadius() {
        assertEquals(listOf(p(200f, 100f), p(300f, 220f), p(100f, 220f)), canvasPolygon(CanvasObjectKind.TRIANGLE, p(300f, 220f), p(100f, 100f)))
        assertEquals(4, canvasPolygon(CanvasObjectKind.DIAMOND, p(100f, 100f), p(300f, 220f)).size)
        assertTrue(canvasPolygon(CanvasObjectKind.ELLIPSE, p(0f, 0f), p(10f, 10f)).isEmpty())
        assertEquals(24f, canvasCornerRadius(p(100f, 100f), p(300f, 220f)), 0.001f)
    }
    @Test fun ellipseTriangleAndDiamondAreHitOnOutlineOnly() {
        val e = shape(CanvasObjectKind.ELLIPSE)
        assertTrue(hitCanvasObject(e, p(300f, 160f), 2f)); assertTrue(hitCanvasObject(e, p(200f, 100f), 2f))
        assertFalse(hitCanvasObject(e, p(200f, 160f), 2f))
        val t = shape(CanvasObjectKind.TRIANGLE)
        assertTrue(hitCanvasObject(t, p(200f, 220f), 2f)); assertFalse(hitCanvasObject(t, p(200f, 190f), 2f))
        val dm = shape(CanvasObjectKind.DIAMOND)
        assertTrue(hitCanvasObject(dm, p(200f, 100f), 2f)); assertFalse(hitCanvasObject(dm, p(200f, 160f), 2f))
        val r = shape(CanvasObjectKind.ROUNDED_RECT)
        assertTrue(hitCanvasObject(r, p(100f, 160f), 2f)); assertFalse(hitCanvasObject(r, p(200f, 160f), 2f))
    }
    @Test fun degenerateEllipseDoesNotCrash() {
        val e = CanvasObject(kind = CanvasObjectKind.ELLIPSE, points = listOf(p(50f, 50f), p(50f, 50f)))
        hitCanvasObject(e, p(50f, 50f), 2f) // главное — без деления на ноль и исключений
    }
    @Test fun newShapesWorkInCollaborationDelta() {
        val before = doc(); val obj = shape(CanvasObjectKind.TRIANGLE)
        val after = CanvasDelta(adds = listOf(obj)).apply(before)
        assertEquals(listOf(obj), after.objects)
        assertEquals(CanvasDelta(adds = listOf(obj)), CanvasDelta.between(before, after))
    }

    @Test fun straightStrokeBecomesLine() {
        val shape = recognize(resample(listOf(p(100f, 100f), p(400f, 180f)), false))!!
        assertEquals(CanvasObjectKind.LINE, shape.kind); assertEquals(2, shape.points.size)
    }
    @Test fun roundStrokeBecomesCircle() {
        val shape = recognize(ellipse(300f, 300f, 80f, 80f))!!
        assertEquals(CanvasObjectKind.CIRCLE, shape.kind)
        assertEquals(80f, shape.points[1].x - shape.points[0].x, 6f)
    }
    @Test fun stretchedStrokeBecomesEllipse() =
        assertEquals(CanvasObjectKind.ELLIPSE, recognize(ellipse(300f, 300f, 140f, 70f))!!.kind)
    @Test fun squareStrokeBecomesRectangle() {
        val s = recognize(resample(listOf(p(100f, 100f), p(300f, 100f), p(300f, 220f), p(100f, 220f)), true) + p(100f, 104f))!!
        assertEquals(CanvasObjectKind.RECTANGLE, s.kind)
        assertEquals(p(100f, 100f).x, s.points[0].x, 3f); assertEquals(300f, s.points[1].x, 3f)
    }
    @Test fun threeCornerStrokeBecomesTriangle() =
        assertEquals(CanvasObjectKind.TRIANGLE, recognize(resample(listOf(p(200f, 100f), p(300f, 260f), p(100f, 260f)), true) + p(198f, 104f))!!.kind)
    @Test fun fourCornerDiamondStroke() =
        assertEquals(CanvasObjectKind.DIAMOND, recognize(resample(listOf(p(200f, 100f), p(300f, 200f), p(200f, 300f), p(100f, 200f)), true) + p(198f, 104f))!!.kind)
    @Test fun nonShapesStayPencil() {
        assertNull(recognize((100..400 step 5).map { p(it.toFloat(), 200f + 40f * sin(it / 25f)) }))
        assertNull(recognize(resample(listOf(p(100f, 100f), p(200f, 200f), p(100f, 300f), p(200f, 400f)), false)))
        assertNull(recognize((0 until 90).map { i -> p(300f + (5f + i * 0.6f) * cos(i / 6f), 300f + (5f + i * 0.6f) * sin(i / 6f)) }))
        assertNull(recognize(ellipse(300f, 300f, 100f, 100f, 60, -0.5f)))
    }
    @Test fun tinyOrShortStrokesAreIgnored() {
        assertNull(recognize(List(5) { p(it * 10f, 10f) }))
        assertNull(recognize(List(20) { p(100f + it * 0.5f, 100f) }))
        assertNull(recognize(emptyList()))
    }
    @Test fun recognizedPointsStayInsideTheSheet() {
        val s = recognizeCanvasShape(ellipse(1190f, 890f, 80f, 80f), 1200f, 900f)
        s?.points?.forEach { assertTrue(it.x in 0f..1200f && it.y in 0f..900f) }
    }
    @Test fun nonFinitePointsAreIgnored() =
        assertNull(recognize(List(20) { p(if (it == 3) Float.NaN else it * 10f, 10f) }))

    @Test fun gridSnapRoundsAndClamps() {
        assertEquals(p(48f, 24f), snapCanvasPoint(p(50f, 20f), 1200f, 900f))
        assertEquals(p(0f, 0f), snapCanvasPoint(p(5f, 8f), 1200f, 900f))
        assertEquals(p(1200f, 888f), snapCanvasPoint(p(1199f, 899f), 1200f, 900f))
        assertEquals(p(1200f, 900f), snapCanvasPoint(p(1260f, 960f), 1200f, 900f))
    }
}
