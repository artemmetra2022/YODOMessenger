package app.yodo.messenger.features.canvas

import app.yodo.messenger.offline.NearbyRadioLease
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID

class CanvasCollaborationTest {
    private val host=UUID.randomUUID().toString();private val guest=UUID.randomUUID().toString()
    private fun doc()=CanvasDocument(title="Shared")
    private fun obj()=CanvasObject(kind=CanvasObjectKind.LINE,points=listOf(CanvasPoint(10f,20f),CanvasPoint(100f,150f)))
    private fun authority(d:CanvasDocument=doc())=CanvasAuthority(d,host,guest)
    private fun edit(seq:Long,base:Long=0,adds:List<CanvasObject> = emptyList(),removes:List<String> = emptyList())=CanvasCommand(sequence=seq,baseRevision=base,delta=CanvasDelta(adds,removes))
    private fun undo(seq:Long,base:Long,redo:Boolean=false)=CanvasCommand(sequence=seq,baseRevision=base,kind=if(redo)CanvasCommandKind.REDO else CanvasCommandKind.UNDO)
    private fun reject(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
    @Test fun concurrentAddsBothSurvive(){val a=authority();val x=obj();val y=obj();a.execute(host,edit(1,adds=listOf(x)));a.execute(guest,edit(1,adds=listOf(y)));assertEquals(listOf(x,y),a.document.objects);assertEquals(2,a.revision)}
    @Test fun hostSequencerAndReplicaConverge(){val d=doc();val a=authority(d);var replica=d;repeat(150){i->val actor=if(i%2==0)host else guest;val seq=(i/2+1).toLong();val c=a.execute(actor,edit(seq,base=a.revision,adds=listOf(obj())));val wire=CanvasCollabWire.decode(CanvasCollabWire.encode(CanvasWire.Committed(host,c))) as CanvasWire.Committed;replica=wire.commit.delta.apply(replica)};assertEquals(a.document,replica)}
    @Test fun duplicateSequenceRejected(){val a=authority();val c=edit(1,adds=listOf(obj()));a.execute(host,c);reject{a.execute(host,c)};assertEquals(1,a.document.objects.size)}
    @Test fun sequenceGapRejected(){val a=authority();reject{a.execute(host,edit(2,adds=listOf(obj())))};assertEquals(0,a.revision)}
    @Test fun unauthorizedActorRejected(){val a=authority();reject{a.execute(UUID.randomUUID().toString(),edit(1))}}
    @Test fun futureBaseRejected(){val a=authority();reject{a.execute(guest,edit(1,base=50))};assertEquals(0,a.revision);a.execute(guest,edit(2,adds=listOf(obj())));assertEquals(1,a.revision)}
    @Test fun undoDoesNotRemoveOtherPeerObjects(){val a=authority();val x=obj();val y=obj();a.execute(host,edit(1,adds=listOf(x)));a.execute(guest,edit(1,base=1,adds=listOf(y)));a.execute(host,undo(2,2));assertEquals(listOf(y),a.document.objects);a.execute(host,undo(3,3,true));assertEquals(setOf(x,y),a.document.objects.toSet())}
    @Test fun remoteErasePreventsResurrectionByOldUndo(){val a=authority();val x=obj();a.execute(host,edit(1,adds=listOf(x)));a.execute(guest,edit(1,base=1,removes=listOf(x.id)));a.execute(host,undo(2,2));assertTrue(a.document.objects.isEmpty());assertFalse(a.canRedo(host));a.execute(guest,undo(2,3));assertEquals(listOf(x),a.document.objects)}
    @Test fun staleEraseCannotDeleteRestoredObject(){val a=authority();val x=obj();a.execute(host,edit(1,adds=listOf(x)));a.execute(host,undo(2,1));a.execute(host,undo(3,2,true));reject{a.execute(guest,edit(1,base=1,removes=listOf(x.id)))};assertEquals(listOf(x),a.document.objects)}
    @Test fun tombstoneIdCannotBeReused(){val a=authority();val x=obj();a.execute(host,edit(1,adds=listOf(x)));a.execute(host,undo(2,1));reject{a.execute(guest,edit(1,base=2,adds=listOf(x)))}}
    @Test fun perActorRedoClearedOnOwnEditOnly(){val a=authority();a.execute(host,edit(1,adds=listOf(obj())));a.execute(host,undo(2,1));a.execute(guest,edit(1,base=2,adds=listOf(obj())));assertTrue(a.canRedo(host));a.execute(host,edit(3,base=3,adds=listOf(obj())));assertFalse(a.canRedo(host))}
    @Test fun eraseIsSingleUndoAction(){val d=doc().copy(objects=List(4){obj()});val a=authority(d);a.execute(guest,edit(1,removes=d.objects.map{it.id}));assertTrue(a.document.objects.isEmpty());a.execute(guest,undo(2,1));assertEquals(d.objects.toSet(),a.document.objects.toSet());assertFalse(a.canUndo(guest))}
    @Test fun invalidObjectDoesNotMutateDocument(){val a=authority();val original=a.document;reject{a.execute(host,edit(1,adds=listOf(obj().copy(width=Float.NaN))))};assertEquals(original,a.document);assertEquals(0,a.revision);assertFalse(a.canUndo(host))}
    @Test fun objectBudgetEnforced(){val d=doc().copy(objects=List(300){obj()});val a=authority(d);reject{a.execute(guest,edit(1,adds=listOf(obj())))};assertEquals(d,a.document)}
    @Test fun unknownRemovalRejected(){val a=authority();reject{a.execute(guest,edit(1,removes=listOf(obj().id)))}}
    @Test fun metadataCannotBeChangedThroughGesture(){val d=doc();reject{CanvasDelta.between(d,d.copy(title="fake"))};reject{CanvasDelta.between(d,d.copy(backgroundBase64="x"))}}
    @Test fun existingObjectCannotBeRewritten(){val d=doc().copy(objects=listOf(obj()));reject{CanvasDelta.between(d,d.copy(objects=d.objects.map{it.copy(color=0)}))}}
    @Test fun staleViewDeltaPreservesRemoteAddition(){val old=doc().copy(objects=listOf(obj()));val a=authority(old);val extra=obj();a.execute(host,edit(1,adds=listOf(extra)));val gesture=CanvasDelta.between(old,old.copy(objects=emptyList()));a.execute(guest,CanvasCommand(sequence=1,baseRevision=0,delta=gesture));assertEquals(listOf(extra),a.document.objects)}
    @Test fun hostUndoIsBounded(){val a=authority();repeat(65){a.execute(host,edit((it+1).toLong(),base=a.revision,adds=listOf(obj())))};repeat(60){a.execute(host,undo((66+it).toLong(),a.revision))};assertFalse(a.canUndo(host));assertEquals(5,a.document.objects.size)}
    @Test fun snapshotRoundTrip(){val m=CanvasWire.Snapshot(host,doc().copy(objects=listOf(obj()),backgroundBase64="YWJj"),0,host,guest);assertEquals(m,CanvasCollabWire.decode(CanvasCollabWire.encode(m)))}
    @Test fun proposalRoundTrip(){val m=CanvasWire.Propose(host,edit(1,adds=listOf(obj())));assertEquals(m,CanvasCollabWire.decode(CanvasCollabWire.encode(m)))}
    @Test fun rejectionRoundTrip(){val m=CanvasWire.Rejected(host,guest,3,"Отказ");assertEquals(m,CanvasCollabWire.decode(CanvasCollabWire.encode(m)))}
    @Test fun cursorRoundTrip(){for(p in listOf(CanvasPoint(24f,56f),null)){val m=CanvasWire.Cursor(host,10,p,-1);assertEquals(m,CanvasCollabWire.decode(CanvasCollabWire.encode(m)))}}
    @Test fun wrongProtocolRejected(){val raw=CanvasCollabWire.encode(CanvasWire.Cursor(host,1,null,0)).toString(Charsets.UTF_8);reject{CanvasCollabWire.decode(raw.replace("\"protocol\":3","\"protocol\":99").toByteArray())}}
    @Test fun invalidSessionRejected(){val raw=CanvasCollabWire.encode(CanvasWire.Cursor(host,1,null,0)).toString(Charsets.UTF_8);reject{CanvasCollabWire.decode(raw.replace(host,"../../path").toByteArray())}}
    @Test fun nonfiniteCursorRejected(){val raw=CanvasCollabWire.encode(CanvasWire.Cursor(host,1,CanvasPoint(2f,2f),0)).toString(Charsets.UTF_8);reject{CanvasCollabWire.decode(raw.replace("\"x\":2.0","\"x\":-1.0").toByteArray())}}
    @Test fun deeplyNestedPacketRejected(){reject{CanvasCollabWire.decode(("[".repeat(100)+"0"+"]".repeat(100)).toByteArray())}}
    @Test fun deeplyNestedEmbeddedDocumentRejected(){val raw=buildJsonObject{put("protocol",3);put("type","snapshot");put("session",host);put("host",host);put("guest",guest);put("revision",0);put("doc","[".repeat(100)+"0"+"]".repeat(100))};reject{CanvasCollabWire.decode(raw.toString().toByteArray())}}
    @Test fun oversizeWireRejected(){reject{CanvasCollabWire.decode(ByteArray(CanvasCollabWire.MAX_BYTES+1))}}
    @Test fun frameFitsNearbyBytesLimit(){CanvasFrames.split(ByteArray(100000)).forEach{assertTrue(it.size<32768)}}
    @Test fun frameRoundTripOutOfOrder(){val bytes=ByteArray(100001){(it%251).toByte()};val frames=CanvasFrames.split(bytes);val a=CanvasFrames.Assembler();var out:ByteArray?=null;frames.reversed().forEach{a.accept(it,100)?.let{b->out=b}};assertArrayEquals(bytes,out)}
    @Test fun duplicateChunkIsIdempotent(){val frames=CanvasFrames.split(ByteArray(50000){7});val a=CanvasFrames.Assembler();assertNull(a.accept(frames[0],1));assertNull(a.accept(frames[0],2));var out:ByteArray?=null;frames.drop(1).forEach{out=a.accept(it,3)};assertArrayEquals(ByteArray(50000){7},out)}
    @Test fun badDigestRejected(){val frame=CanvasFrames.split(byteArrayOf(1,2,3)).single().clone();frame[frame.lastIndex]=9;reject{CanvasFrames.Assembler().accept(frame,1)}}
    @Test fun impossibleFrameIndexRejected(){val frame=CanvasFrames.split(byteArrayOf(1)).single();ByteBuffer.wrap(frame).putInt(20,99);reject{CanvasFrames.Assembler().accept(frame,1)}}
    @Test fun oversizeFrameRejected(){reject{CanvasFrames.Assembler().accept(ByteArray(CanvasFrames.HEADER+CanvasFrames.CHUNK+1),1)}}
    @Test fun partialTransfersAreBounded(){val a=CanvasFrames.Assembler();repeat(3){a.accept(CanvasFrames.split(ByteArray(30000)).first(),1)};reject{a.accept(CanvasFrames.split(ByteArray(30000)).first(),1)}}
    @Test fun expiredTransferIsNotApplied(){val a=CanvasFrames.Assembler();val f=CanvasFrames.split(ByteArray(30000));assertNull(a.accept(f[0],0));assertNull(a.accept(f[1],20001))}
    @Test fun clearDiscardsPartialTransfer(){val a=CanvasFrames.Assembler();val f=CanvasFrames.split(ByteArray(30000));a.accept(f[0],0);a.clear();assertNull(a.accept(f[1],1))}
    @Test fun radioCannotBeStolen(){val a=UUID.randomUUID().toString();val b=UUID.randomUUID().toString();try{assertTrue(NearbyRadioLease.acquire(a));assertFalse(NearbyRadioLease.acquire(b));NearbyRadioLease.release(b);assertTrue(NearbyRadioLease.owns(a))}finally{NearbyRadioLease.release(a)}}
    @Test fun radioCanBeReusedAfterRelease(){val a=UUID.randomUUID().toString();val b=UUID.randomUUID().toString();try{assertTrue(NearbyRadioLease.acquire(a));NearbyRadioLease.release(a);assertTrue(NearbyRadioLease.acquire(b))}finally{NearbyRadioLease.release(a);NearbyRadioLease.release(b)}}
}
