package app.yodo.messenger.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMediaCodecTest {

    @Test
    fun `album survives chunking and out of order assembly`() {
        val original = OfflineMediaPayload(
            type = OfflineMediaType.ALBUM,
            itemsBase64 = listOf("a".repeat(20_000), "b".repeat(20_000))
        )
        val encoded = OfflineMediaCodec.encode(original)
        val chunks = OfflineMediaCodec.chunks(encoded)
        val assembler = OfflineMediaAssembler()
        val transferId = "transfer"

        chunks.indices.reversed().forEach { index ->
            assertNull(assembler.addChunk(transferId, index, chunks[index]))
        }
        val assembled = assembler.addMeta(
            OfflineMediaMeta(transferId, OfflineMediaType.ALBUM, chunks.size, durationMs = 0L)
        )

        assertEquals(original, assembled)
        assertTrue(chunks.all { it.length <= 16_000 })
    }

    @Test
    fun `audio metadata and chunks round trip`() {
        val payload = OfflineMediaPayload(
            type = OfflineMediaType.AUDIO,
            itemsBase64 = listOf("audio-data"),
            durationMs = 12_345L
        )
        val encoded = OfflineMediaCodec.encode(payload)

        assertEquals(payload, OfflineMediaCodec.decode(encoded))
        assertEquals(3 to "chunk", OfflineMediaCodec.parseChunk(OfflineMediaCodec.chunkText(3, "chunk")))
    }

    @Test
    fun `invalid chunk is rejected`() {
        assertNull(OfflineMediaCodec.parseChunk("missing-index"))
        assertNull(OfflineMediaCodec.parseChunk(":data"))
    }
}
