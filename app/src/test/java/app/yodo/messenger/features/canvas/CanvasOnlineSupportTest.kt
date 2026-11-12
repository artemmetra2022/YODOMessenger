package app.yodo.messenger.features.canvas

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

/** Этап 5: логика онлайн-доски без Firebase — коды приглашений и порядок пакетов. */
class CanvasOnlineSupportTest {
    private fun bytes(n: Int) = byteArrayOf(n.toByte())
    private fun reject(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }

    @Test fun generatedCodesUseOnlyTheSafeAlphabet() {
        val random = SecureRandom()
        repeat(200) {
            val code = CanvasInviteCode.generate(random)
            assertEquals(CanvasInviteCode.LENGTH, code.length)
            assertTrue(code.all { it in CanvasInviteCode.ALPHABET })
            assertEquals(code, CanvasInviteCode.normalize(code))
        }
        assertTrue(CanvasInviteCode.ALPHABET.none { it in "01IO" })
    }
    @Test fun normalizeAcceptsHumanTyping() {
        assertEquals("ABCDEFGH", CanvasInviteCode.normalize("abcd-efgh"))
        assertEquals("ABCDEFGH", CanvasInviteCode.normalize("  ABCD EFGH "))
        assertEquals("ABCD2345", CanvasInviteCode.normalize("abcd-2345"))
    }
    @Test fun normalizeRejectsBadCodes() {
        assertNull(CanvasInviteCode.normalize(""))
        assertNull(CanvasInviteCode.normalize("ABCDEFG"))      // короткий
        assertNull(CanvasInviteCode.normalize("ABCDEFGHJ"))    // длинный
        assertNull(CanvasInviteCode.normalize("ABCD-EFG0"))    // 0 не бывает в коде
        assertNull(CanvasInviteCode.normalize("ABCD-EFGO"))    // O не бывает в коде
        assertNull(CanvasInviteCode.normalize("ABCD-EF/H"))
    }
    @Test fun displayGroupsByFour() = assertEquals("ABCD-EFGH", CanvasInviteCode.display("ABCDEFGH"))
    @Test fun packetIdsSortLikeNumbers() {
        assertEquals("0000000001", CanvasInviteCode.packetId(1))
        assertEquals("0000012345", CanvasInviteCode.packetId(12345))
        assertTrue(CanvasInviteCode.packetId(9) < CanvasInviteCode.packetId(10))
        assertEquals(42L, CanvasInviteCode.packetNumber("0000000042"))
        assertNull(CanvasInviteCode.packetNumber("42"))
        assertNull(CanvasInviteCode.packetNumber("00000000x2"))
        assertNull(CanvasInviteCode.packetNumber("0000000000"))
        reject { CanvasInviteCode.packetId(0) }
    }

    @Test fun inOrderPacketsPassStraightThrough() {
        val r = CanvasPacketReorder()
        assertEquals(listOf(1), r.accept(1, bytes(1), 0).map { it[0].toInt() })
        assertEquals(listOf(2), r.accept(2, bytes(2), 1).map { it[0].toInt() })
        assertEquals(3L, r.nextExpected)
    }
    @Test fun outOfOrderPacketsAreHeldAndReleasedInOrder() {
        val r = CanvasPacketReorder()
        assertTrue(r.accept(3, bytes(3), 0).isEmpty())
        assertTrue(r.accept(2, bytes(2), 1).isEmpty())
        assertEquals(listOf(1, 2, 3), r.accept(1, bytes(1), 2).map { it[0].toInt() })
        assertEquals(4L, r.nextExpected)
    }
    @Test fun duplicatesAndStalePacketsAreIgnored() {
        val r = CanvasPacketReorder()
        r.accept(1, bytes(1), 0)
        assertTrue(r.accept(1, bytes(9), 1).isEmpty())          // уже обработан
        assertTrue(r.accept(3, bytes(3), 2).isEmpty())
        assertTrue(r.accept(3, bytes(9), 3).isEmpty())          // дубликат ожидающего
        assertEquals(listOf(2, 3), r.accept(2, bytes(2), 4).map { it[0].toInt() }) // дубликат не подменил данные
    }
    @Test fun gapIsReportedOnlyAfterTheLimit() {
        val r = CanvasPacketReorder()
        assertFalse(r.gapExceeded(0, 20_000))
        r.accept(2, bytes(2), 1_000)                              // пакета 1 нет — «дыра»
        assertFalse(r.gapExceeded(20_999, 20_000))
        assertTrue(r.gapExceeded(21_001, 20_000))
        r.accept(1, bytes(1), 22_000)                             // дыра закрыта
        assertFalse(r.gapExceeded(99_000, 20_000))
    }
    @Test fun progressRestartsTheGapClock() {
        val r = CanvasPacketReorder()
        r.accept(3, bytes(3), 0)                                  // ждём 1 и 2
        r.accept(1, bytes(1), 15_000)                             // прогресс, ждём 2
        assertFalse(r.gapExceeded(34_000, 20_000))
        assertTrue(r.gapExceeded(35_001, 20_000))
    }
    @Test fun tooManyBufferedPacketsRejected() {
        val r = CanvasPacketReorder(maxBuffered = 5)
        for (n in 2..6) r.accept(n.toLong(), bytes(n), 0)
        reject { r.accept(7, bytes(7), 0) }
    }
    @Test fun invalidNumberRejected() = reject { CanvasPacketReorder().accept(0, bytes(0), 0) }

    @Test fun onlineStateFieldsDefaultToOffline() {
        val s = CanvasLinkState()
        assertFalse(s.online); assertNull(s.peerName)
        assertEquals(CanvasLinkPhase.IDLE, s.phase)
    }
    @Test fun framesRoundTripThroughReorderAndAssembler() {
        // Как на проводе: большое сообщение режется на кадры, приходит вразнобой, собирается обратно.
        val payload = ByteArray(80_000) { (it * 31).toByte() }
        val frames = CanvasFrames.split(payload)
        assertTrue(frames.size > 1)
        val reorder = CanvasPacketReorder(); val assembler = CanvasFrames.Assembler()
        var whole: ByteArray? = null
        frames.withIndex().toList().reversed().forEach { (index, frame) ->
            reorder.accept(index + 1L, frame, 0).forEach { assembler.accept(it, 0)?.let { done -> whole = done } }
        }
        assertArrayEquals(payload, whole)
    }
}
