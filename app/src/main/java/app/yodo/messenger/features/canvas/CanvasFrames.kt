package app.yodo.messenger.features.canvas

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/** Bounded binary framing below Nearby's 32 KiB BYTES limit. No files or public identifiers. */
object CanvasFrames {
    const val CHUNK=24576; const val HEADER=64
    private const val MAGIC=0x59433201
    fun split(bytes:ByteArray,id:UUID=UUID.randomUUID()):List<ByteArray>{
        require(bytes.size in 1..CanvasCollabWire.MAX_BYTES)
        val count=(bytes.size+CHUNK-1)/CHUNK;val digest=MessageDigest.getInstance("SHA-256").digest(bytes)
        return (0 until count).map{index->val offset=index*CHUNK;val size=minOf(CHUNK,bytes.size-offset)
            ByteBuffer.allocate(HEADER+size).putInt(MAGIC).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits)
                .putInt(index).putInt(count).putInt(bytes.size).put(digest).put(bytes,offset,size).array()}
    }
    class Assembler {
        private data class Pending(val total:Int,val count:Int,val digest:ByteArray,val born:Long,val chunks:MutableMap<Int,ByteArray> = mutableMapOf())
        private val pending=mutableMapOf<UUID,Pending>()
        fun clear(){pending.clear()}
        fun accept(frame:ByteArray,now:Long):ByteArray? {
            require(frame.size in HEADER+1..HEADER+CHUNK)
            pending.entries.removeAll{now-it.value.born>20000}
            val b=ByteBuffer.wrap(frame);require(b.int==MAGIC);val id=UUID(b.long,b.long)
            val index=b.int;val count=b.int;val total=b.int;val digest=ByteArray(32).also{b.get(it)}
            require(total in 1..CanvasCollabWire.MAX_BYTES&&count==(total+CHUNK-1)/CHUNK&&index in 0 until count)
            require(b.remaining()==if(index==count-1)total-index*CHUNK else CHUNK)
            val item=pending[id]?:Pending(total,count,digest,now).also{require(pending.size<3);pending[id]=it}
            require(item.total==total&&item.count==count&&item.digest.contentEquals(digest))
            val chunk=ByteArray(b.remaining()).also{b.get(it)}
            item.chunks[index]?.let{require(it.contentEquals(chunk));return null}
            item.chunks[index]=chunk
            if(item.chunks.size!=count)return null
            val result=ByteArray(total);for(i in 0 until count)item.chunks.getValue(i).copyInto(result,i*CHUNK)
            pending.remove(id);require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(result),digest))
            return result
        }
    }
}
