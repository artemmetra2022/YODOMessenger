package app.yodo.messenger.features.canvas

import app.yodo.messenger.data.local.BoardPrefs
import org.junit.Assert.*
import org.junit.Test

/** Этап 6: общая доска чата и настройки доски — чистая логика без Firebase. */
class SharedBoardTest {
    private fun p(x: Float, y: Float) = CanvasPoint(x, y)
    private fun obj(kind: CanvasObjectKind = CanvasObjectKind.RECTANGLE) = CanvasObject(kind = kind, points = listOf(p(10f, 10f), p(200f, 120f)))
    private fun prefs(
        show: Boolean = true, height: String? = null, collapsed: Boolean = false, color: Int? = null, width: Int? = null,
        assist: Boolean = true, snap: Boolean = false, grid: String? = null, haptics: Boolean = true
    ) = BoardPrefs(show, height, collapsed, color, width, assist, snap, grid, haptics)

    @Test fun everyObjectKindSurvivesTheFirestoreRoundTrip() {
        CanvasObjectKind.entries.forEach { kind ->
            val o = when (kind) {
                CanvasObjectKind.PEN -> CanvasObject(kind = kind, points = listOf(p(1f, 1f), p(50f, 20f), p(90f, 60f)))
                CanvasObjectKind.TEXT -> CanvasObject(kind = kind, points = listOf(p(40f, 40f)), text = "Привет")
                CanvasObjectKind.FORMULA -> CanvasObject(kind = kind, points = listOf(p(20f, 20f), p(220f, 90f)), text = "\\frac{a}{b}")
                else -> obj(kind)
            }
            assertEquals("kind=$kind", o, CanvasSharedCodec.decodeObject(CanvasSharedCodec.encodeObject(o)))
        }
    }
    @Test fun brokenOrForeignObjectJsonIsIgnored() {
        assertNull(CanvasSharedCodec.decodeObject(""))
        assertNull(CanvasSharedCodec.decodeObject("{not json"))
        assertNull(CanvasSharedCodec.decodeObject("x".repeat(CanvasSharedCodec.MAX_JSON + 1)))
        // Документ с другим размером листа или с фоном не принимается, чтобы нельзя было обойти границы доски.
        val big = CanvasCodec.encode(CanvasDocument(id = "00000000-0000-0000-0000-000000000000", title = "o", width = 2000, height = 1500, objects = listOf(obj())))
        assertNull(CanvasSharedCodec.decodeObject(big))
        val twoObjects = CanvasCodec.encode(CanvasDocument(id = "00000000-0000-0000-0000-000000000000", title = "o", objects = listOf(obj(), obj())))
        assertNull(CanvasSharedCodec.decodeObject(twoObjects))
    }
    @Test fun documentIdIsStablePerChatAndValid() {
        val a = SharedBoardOps.documentId("chat-1"); val b = SharedBoardOps.documentId("chat-1"); val c = SharedBoardOps.documentId("chat-2")
        assertEquals(a, b); assertNotEquals(a, c); assertTrue(CanvasLimits.validId(a))
    }
    @Test fun deltaFindsOnlyAddedAndRemovedObjects() {
        val keep = obj(); val gone = obj(CanvasObjectKind.ELLIPSE); val fresh = obj(CanvasObjectKind.TRIANGLE)
        val d = SharedBoardOps.delta(listOf(keep, gone), listOf(keep, fresh))
        assertEquals(listOf(fresh), d.adds); assertEquals(listOf(gone), d.removes)
        assertTrue(SharedBoardOps.delta(listOf(keep), listOf(keep)).isEmpty)
    }
    @Test fun limitIsEnforcedWithRemovalsCounted() {
        val d = SharedBoardOps.Delta(adds = List(5) { obj() }, removes = emptyList())
        SharedBoardOps.checkLimit(CanvasLimits.MAX_OBJECTS - 5, d)
        try { SharedBoardOps.checkLimit(CanvasLimits.MAX_OBJECTS - 4, d); fail("Expected limit") } catch (_: IllegalArgumentException) {}
        SharedBoardOps.checkLimit(CanvasLimits.MAX_OBJECTS, SharedBoardOps.Delta(adds = listOf(obj()), removes = listOf(obj())))
    }
    @Test fun undoReversesOwnStrokeAndSkipsWhatOthersAlreadyChanged() {
        val mine = obj(); val erased = obj(CanvasObjectKind.DIAMOND)
        val step = SharedBoardOps.Delta(adds = listOf(mine), removes = listOf(erased))
        // Всё на месте: отмена убирает мой объект и возвращает стёртый.
        val plan = SharedBoardOps.reverse(setOf(mine.id), step)
        assertEquals(listOf(erased), plan.adds); assertEquals(listOf(mine), plan.removes)
        // Мой объект уже стёр собеседник, а стёртый кто-то вернул — делать нечего.
        assertTrue(SharedBoardOps.reverse(setOf(erased.id), step).isEmpty)
    }
    @Test fun redoIsTheReverseOfUndo() {
        val mine = obj(); val step = SharedBoardOps.Delta(adds = listOf(mine), removes = emptyList())
        val undo = SharedBoardOps.reverse(setOf(mine.id), step)           // убрать мой объект
        val redo = SharedBoardOps.reverse(emptySet(), undo)               // вернуть его
        assertEquals(listOf(mine), redo.adds); assertTrue(redo.removes.isEmpty())
    }

    @Test fun defaultSettingsMatchTheDocumentedDefaults() {
        val s = BoardSettings.from(prefs())
        assertEquals(BoardSettings(), s)
        assertTrue(s.showInChat); assertFalse(s.collapsedInChat); assertEquals(BoardPreviewHeight.MEDIUM, s.previewHeight)
        assertTrue(s.shapeAssist); assertFalse(s.snapToGrid); assertTrue(s.haptics); assertEquals(CanvasGrid.SQUARES, s.defaultGrid)
    }
    @Test fun validSettingsArePreserved() {
        val color = BoardColors.palette[2].second
        val s = BoardSettings.from(prefs(show = false, height = "LARGE", collapsed = true, color = color, width = 8, assist = false, snap = true, grid = "DOTS", haptics = false))
        assertFalse(s.showInChat); assertEquals(BoardPreviewHeight.LARGE, s.previewHeight); assertTrue(s.collapsedInChat)
        assertEquals(color, s.inkColor); assertEquals(8f, s.inkWidth); assertFalse(s.shapeAssist); assertTrue(s.snapToGrid)
        assertEquals(CanvasGrid.DOTS, s.defaultGrid); assertFalse(s.haptics)
    }
    @Test fun corruptedSettingsFallBackToDefaults() {
        val s = BoardSettings.from(prefs(height = "HUGE", color = 12345, width = 7, grid = "HEXAGONS"))
        assertEquals(BoardPreviewHeight.MEDIUM, s.previewHeight); assertEquals(BoardColors.DEFAULT, s.inkColor)
        assertEquals(4f, s.inkWidth); assertEquals(CanvasGrid.SQUARES, s.defaultGrid)
    }
    @Test fun previewHeightsGrowAndFitAPhone() {
        val h = BoardPreviewHeight.entries.map { it.dp }
        assertEquals(h.sorted(), h); assertTrue(h.all { it in 100..360 })
    }
    @Test fun sharedBoardDocumentUsesFixedSheet() {
        val doc = SharedBoard("c", "Общая доска", CanvasGrid.DOTS, listOf(obj()), null, false).toDocument()
        assertEquals(1200, doc.width); assertEquals(900, doc.height); assertEquals(CanvasGrid.DOTS, doc.grid); assertNull(doc.backgroundBase64)
        CanvasLimits.validate(doc)
    }
}
