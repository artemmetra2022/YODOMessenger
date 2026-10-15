package app.yodo.messenger.features.canvas

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Executors

/** Actual controller + wire codec/framing; in-memory transport, NOT a Nearby radio/device test. */
class CanvasSessionIntegrationTest {
    private class MemoryTransport:CanvasTransport {
        val link=MutableStateFlow(CanvasLinkState())
        override val state=link.asStateFlow()
        val packets=Channel<ByteArray>(16)
        override val incoming=packets.receiveAsFlow()
        override var epoch=0L
        lateinit var peer:MemoryTransport
        var sendGate:CompletableDeferred<Unit>?=null
        override fun host(){};override fun discover(){};override fun connect(peer:CanvasPeer){};override fun verify(matches:Boolean){}
        override suspend fun send(bytes:ByteArray){val startedEpoch=epoch;sendGate?.await();check(startedEpoch==epoch){"Old send failed"};check(link.value.phase==CanvasLinkPhase.CONNECTED)
            val receiver=CanvasFrames.Assembler();var assembled:ByteArray?=null
            CanvasFrames.split(bytes).forEach{receiver.accept(it,0)?.let{value->assembled=value}}
            peer.packets.send(requireNotNull(assembled));yield()
        }
        override suspend fun cursor(bytes:ByteArray)=send(bytes)
        override fun stop(error:String?){epoch++;link.value=CanvasLinkState(error=error)
            if(peer.link.value.phase!=CanvasLinkPhase.IDLE){peer.epoch++;peer.link.value=CanvasLinkState(error="Disconnected")}}
    }
    private class PairSession(val scope:CoroutineScope) {
        val room=UUID.randomUUID().toString()
        val h=MemoryTransport();val g=MemoryTransport()
        var hostDoc=CanvasDocument(title="Векторы ведущего",objects=listOf(shape()))
        val guestOriginal=CanvasDocument(title="Личная доска участника")
        var guestDoc=guestOriginal
        val errors=mutableListOf<String>()
        init{h.peer=g;g.peer=h}
        val host=CanvasCollaboration(h,scope,{hostDoc},{hostDoc=it},{errors.add(it)},{})
        val guest=CanvasCollaboration(g,scope,{guestDoc},{guestDoc=it},{errors.add(it)},{})
        suspend fun connect(){
            yield()
            g.link.value=CanvasLinkState(CanvasLinkPhase.CONNECTED,false,room);yield()
            h.link.value=CanvasLinkState(CanvasLinkPhase.CONNECTED,true,room)
            withTimeout(5000){host.state.first{it.active};guest.state.first{it.active}}
        }
        suspend fun settled(revision:Long){withTimeout(5000){host.state.first{it.revision==revision&&!it.pending};guest.state.first{it.revision==revision&&!it.pending}}}
        fun assertSame(){assertEquals(hostDoc,guestDoc.copy(id=hostDoc.id))}
    }
    private fun session(initial:CanvasDocument?=null,test:suspend PairSession.()->Unit){
        val dispatcher=Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope=CoroutineScope(SupervisorJob()+dispatcher)
        try{runBlocking(dispatcher){val pair=PairSession(scope);if(initial!=null)pair.hostDoc=initial;pair.connect();pair.test()}}finally{scope.cancel();dispatcher.close()}
    }
    companion object {private fun shape()=CanvasObject(kind=CanvasObjectKind.LINE,points=listOf(CanvasPoint(5f,10f),CanvasPoint(50f,100f)))}
    @Test fun initialSnapshotCreatesSeparateLocalCopy()=session{assertSame();assertNotEquals(hostDoc.id,guestDoc.id);assertNotEquals(guestOriginal.id,guestDoc.id);assertEquals("Личная доска участника",guestOriginal.title)}
    @Test fun guestProposalIsAcknowledgedAndAppliedToBoth()=session{val added=shape();guest.edit(guestDoc.copy(objects=guestDoc.objects+added));settled(1);assertSame();assertTrue(hostDoc.objects.contains(added))}
    @Test fun simultaneousAddsAreSerializedWithoutLoss()=session{val a=shape();val b=shape();host.edit(hostDoc.copy(objects=hostDoc.objects+a));guest.edit(guestDoc.copy(objects=guestDoc.objects+b));settled(2);assertSame();assertTrue(hostDoc.objects.containsAll(listOf(a,b)))}
    @Test fun personalUndoAndRedoKeepPeerDrawing()=session{val a=shape();val b=shape();host.edit(hostDoc.copy(objects=hostDoc.objects+a));settled(1);guest.edit(guestDoc.copy(objects=guestDoc.objects+b));settled(2);host.undo(false);settled(3);assertSame();assertFalse(hostDoc.objects.contains(a));assertTrue(hostDoc.objects.contains(b));host.undo(true);settled(4);assertSame();assertTrue(hostDoc.objects.containsAll(listOf(a,b)))}
    @Test fun staleViewGestureDoesNotRemovePeerNewObject()=session{val previous=guestDoc;val extra=shape();host.edit(hostDoc.copy(objects=hostDoc.objects+extra));settled(1);guest.editGesture(previous,previous.copy(objects=emptyList()),0);settled(2);assertSame();assertEquals(listOf(extra),hostDoc.objects)}
    @Test fun staleEraseIsRejectedAndNextCommandStillWorks()=session{val previous=guestDoc;val original=previous.objects.single();host.edit(hostDoc.copy(objects=emptyList()));settled(1);host.undo(false);settled(2);guest.editGesture(previous,previous.copy(objects=emptyList()),0);withTimeout(5000){guest.state.first{!it.pending}};assertEquals(2L,guest.state.value.revision);assertTrue(hostDoc.objects.contains(original));val next=shape();guest.edit(guestDoc.copy(objects=guestDoc.objects+next));settled(3);assertSame();assertTrue(errors.any{it.contains("изменился")})}
    @Test fun cursorAppearsAndClearsWithoutDocumentMutation()=session{val prior=hostDoc;host.cursor(CanvasPoint(21f,34f),0xff2563eb.toInt());withTimeout(5000){guest.state.first{it.cursor!=null}};assertEquals(CanvasPoint(21f,34f),guest.state.value.cursor!!.point);host.cursor(null,0);withTimeout(5000){guest.state.first{it.cursor==null}};assertEquals(prior,hostDoc);assertEquals(0L,guest.state.value.revision)}
    @Test fun remoteCursorExpires()=session{host.cursor(CanvasPoint(1f,2f),0);withTimeout(5000){guest.state.first{it.cursor!=null}};withTimeout(5000){guest.state.first{it.cursor==null}}}
    @Test fun disconnectPreservesConfirmedCopyAndDropsLiveState()=session{guest.edit(guestDoc.copy(objects=guestDoc.objects+shape()));settled(1);val prior=guestDoc;h.stop(null);withTimeout(5000){guest.state.first{it.link.phase==CanvasLinkPhase.IDLE}};assertEquals(prior,guestDoc);assertFalse(guest.state.value.active);assertFalse(guest.state.value.pending)}
    @Test fun revisionGapStopsSessionWithoutReplacingConfirmedBoard()=session{val prior=guestDoc;val forged=CanvasCommit(UUID.randomUUID().toString(),99,CanvasDelta(adds=listOf(shape())),false,false,false,false);h.send(CanvasCollabWire.encode(CanvasWire.Committed(room,forged)));withTimeout(5000){guest.state.first{it.link.phase==CanvasLinkPhase.IDLE}};assertEquals(prior,guestDoc);assertTrue(errors.isNotEmpty())}
    @Test fun wrongSessionCannotMutateBoard()=session{val prior=guestDoc;h.send(CanvasCollabWire.encode(CanvasWire.Cursor(UUID.randomUUID().toString(),1,CanvasPoint(1f,1f),0)));withTimeout(5000){guest.state.first{it.link.phase==CanvasLinkPhase.IDLE}};assertEquals(prior,guestDoc)}
    @Test fun oldSendFailureCannotCloseNewSession()=session{
        val gate=CompletableDeferred<Unit>();h.sendGate=gate
        val added=shape();host.edit(hostDoc.copy(objects=hostDoc.objects+added));yield()
        withTimeout(5000){while(!hostDoc.objects.contains(added))yield()}
        h.stop(null)
        withTimeout(5000){host.state.first{it.link.phase==CanvasLinkPhase.IDLE};guest.state.first{it.link.phase==CanvasLinkPhase.IDLE}}
        h.sendGate=null;val newRoom=UUID.randomUUID().toString()
        g.link.value=CanvasLinkState(CanvasLinkPhase.CONNECTED,false,newRoom);yield()
        h.link.value=CanvasLinkState(CanvasLinkPhase.CONNECTED,true,newRoom);yield()
        gate.complete(Unit)
        withTimeout(5000){host.state.first{it.active&&it.link.room==newRoom};guest.state.first{it.active&&it.link.room==newRoom}}
        assertSame();assertTrue(guestDoc.objects.contains(added));assertTrue(host.state.value.active)
    }

    @Test fun initialFormulaSnapshotKeepsSourceAndBounds()=session(CanvasDocument(title="Math",objects=listOf(CanvasObject(kind=CanvasObjectKind.FORMULA,points=listOf(CanvasPoint(20f,30f),CanvasPoint(200f,160f)),text="\\frac{1}{2}")))){
        assertSame();assertEquals("\\frac{1}{2}",guestDoc.objects.single().text);assertEquals(hostDoc.objects.single().points,guestDoc.objects.single().points)
    }
    @Test fun formulaReplacementAndPersonalUndoConverge()=session{
        val first=CanvasObject(kind=CanvasObjectKind.FORMULA,points=listOf(CanvasPoint(20f,30f),CanvasPoint(200f,160f)),text="x^2")
        guest.edit(guestDoc.copy(objects=guestDoc.objects+first));settled(1)
        val second=first.copy(id=UUID.randomUUID().toString(),text="\\sqrt{x}")
        guest.edit(guestDoc.copy(objects=guestDoc.objects.filterNot{it.id==first.id}+second));settled(2);assertSame();assertTrue(hostDoc.objects.contains(second))
        guest.undo(false);settled(3);assertSame();assertTrue(hostDoc.objects.contains(first));assertFalse(hostDoc.objects.contains(second))
        guest.undo(true);settled(4);assertSame();assertTrue(hostDoc.objects.contains(second))
    }

}
