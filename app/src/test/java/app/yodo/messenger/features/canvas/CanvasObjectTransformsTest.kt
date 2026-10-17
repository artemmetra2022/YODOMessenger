package app.yodo.messenger.features.canvas

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CanvasObjectTransformsTest {
    private fun obj()=CanvasObject(kind=CanvasObjectKind.RECTANGLE,points=listOf(CanvasPoint(100f,100f),CanvasPoint(300f,200f)))
    private fun doc(o: CanvasObject=obj())=CanvasDocument(title="Объекты",objects=listOf(o))
    private fun reject(block: ()->Unit) { try { block();fail("Expected rejection") }catch(_: IllegalArgumentException){} }
    @Test fun moveKeepsGeometry() { val o=obj();val x=CanvasObjectTransforms.translate(o,30f,-20f);assertEquals(CanvasPoint(130f,80f),x.points.first());assertEquals(200f,x.points.last().x-x.points.first().x,0f) }
    @Test fun completeObjectClampedInsidePage() { val x=CanvasObjectTransforms.fit(CanvasObjectTransforms.translate(obj(),-400f,-400f),doc())!!;val b=canvasObjectBounds(x);assertEquals(0f,b.left,.001f);assertEquals(0f,b.top,.001f);assertEquals(204f,b.width,.001f) }
    @Test fun strokePaddingIncluded() {val b=canvasObjectBounds(obj());assertEquals(98f,b.left,0f);assertEquals(302f,b.right,0f)}
    @Test fun arrowHeadIncluded() {val o=CanvasObject(kind=CanvasObjectKind.ARROW,points=listOf(CanvasPoint(100f,100f),CanvasPoint(101f,100f)));val b=canvasObjectBounds(o);val legs=arrowLegs(o.points.first(),o.points.last(),o.width);assertTrue(b.contains(legs.first));assertTrue(b.contains(legs.second))}
    @Test fun resizeAroundCenter() { val o=CanvasObjectTransforms.translate(obj(),200f,100f);val x=CanvasObjectTransforms.scale(o,2f,doc())!!;assertEquals(canvasObjectBounds(o).center,canvasObjectBounds(x).center);assertEquals(400f,x.points.last().x-x.points.first().x,0f) }
    @Test fun rotationNormalized() {val o=obj();val x=CanvasObjectTransforms.rotate(o,-15f,doc())!!;assertEquals(345f,x.rotation,0f);CanvasLimits.validate(doc(x))}
    @Test fun rotatedObjectStaysInside() {val o=obj();val x=CanvasObjectTransforms.rotate(o,90f,doc())!!;val b=rotatedCanvasBounds(canvasObjectBounds(x),x.rotation);assertTrue(b.left>=0&&b.top>=0&&b.right<=1200&&b.bottom<=900)}
    @Test fun oversizedRejectedRatherThanDistorted() {val o=obj().copy(points=listOf(CanvasPoint(0f,0f),CanvasPoint(1200f,900f)));assertNull(CanvasObjectTransforms.rotate(o,45f,doc(o)))}
    @Test fun invalidScaleRejected() {assertNull(CanvasObjectTransforms.scale(obj(),Float.NaN,doc()));assertNull(CanvasObjectTransforms.scale(obj(),-1f,doc()))}
    @Test fun textScaleFreezesWrappingWidth() {val o=CanvasObject(kind=CanvasObjectKind.TEXT,points=listOf(CanvasPoint(100f,200f)),text="Text",fontSize=32f);val x=CanvasObjectTransforms.scale(o,.5f,doc(o))!!;assertEquals(544f,x.textBoxWidth,0f);assertEquals(16f,x.fontSize,0f)}
    @Test fun replacementUsesFreshImmutableId() {val o=obj();val d=doc(o);val x=CanvasObjectTransforms.replace(d,o,CanvasObjectTransforms.translate(o,10f,0f));assertEquals(1,x.objects.size);assertNotEquals(o.id,x.objects.single().id);assertEquals(d.id,x.id)}
    @Test fun copyKeepsOriginal() {val o=obj();val d=doc(o);val x=CanvasObjectTransforms.replace(d,o,o,true);assertEquals(2,x.objects.size);assertEquals(o,x.objects.first());assertNotEquals(o.id,x.objects.last().id)}
    @Test fun staleSelectionRejected() {val o=obj();reject{CanvasObjectTransforms.replace(doc().copy(objects=emptyList()),o,o)}}
    @Test fun objectBudgetEnforced() {val o=obj();val d=doc(o).copy(objects=listOf(o)+List(299){obj()});reject{CanvasObjectTransforms.replace(d,o,o,true)}}
    @Test fun transformIsOneUndoStep() {val o=obj();val d=doc(o);val h=CanvasHistory(d);h.commit(CanvasObjectTransforms.replace(d,o,CanvasObjectTransforms.translate(o,30f,0f)));assertEquals(d,h.undo());assertFalse(h.canUndo);assertTrue(h.canRedo)}
    @Test fun rotatedDocumentRoundTripV4() {val d=doc(obj().copy(rotation=45f));val json=CanvasCodec.encode(d);assertTrue(json.contains("\"version\":4"));assertEquals(d,CanvasCodec.decode(json))}
    @Test fun legacyUntransformedDocumentKeepsOldVersion() {val d=doc();assertTrue(CanvasCodec.encode(d).contains("\"version\":1"));assertEquals(d,CanvasCodec.decode(CanvasCodec.encode(d)))}
    @Test fun wrappingWidthRoundTrip() {val o=CanvasObject(kind=CanvasObjectKind.TEXT,points=listOf(CanvasPoint(10f,100f)),text="Text",textBoxWidth=200f);val d=doc(o);assertEquals(d,CanvasCodec.decode(CanvasCodec.encode(d)))}
    @Test fun nonfiniteRotationRejected() {reject{CanvasLimits.validate(doc(obj().copy(rotation=Float.NaN)))};reject{CanvasLimits.validate(doc(obj().copy(rotation=360f)))}}
    @Test fun legacyVersionCannotHideRotation() {val json=CanvasCodec.encode(doc(obj().copy(rotation=30f))).replace("\"version\":4","\"version\":3");reject{CanvasCodec.decode(json)}}
    @Test fun rotatedLineHitMatchesPivot() {val o=CanvasObject(kind=CanvasObjectKind.LINE,points=listOf(CanvasPoint(100f,100f),CanvasPoint(300f,100f)),rotation=90f);assertTrue(hitCanvasObject(o,CanvasPoint(200f,150f),1f));assertFalse(hitCanvasObject(o,CanvasPoint(270f,100f),1f))}
    @Test fun wirePreservesTransformedObjects() {val host=UUID.randomUUID().toString();val delta=CanvasDelta(listOf(obj().copy(rotation=45f)));val wire=CanvasWire.Propose(host,CanvasCommand(sequence=1,baseRevision=0,delta=delta));assertEquals(wire,CanvasCollabWire.decode(CanvasCollabWire.encode(wire)))}
    @Test fun oldWireProtocolRejected() {val host=UUID.randomUUID().toString();val wire=CanvasWire.Propose(host,CanvasCommand(sequence=1,baseRevision=0));reject{CanvasCollabWire.decode(CanvasCollabWire.encode(wire).toString(Charsets.UTF_8).replace("\"protocol\":5","\"protocol\":4").toByteArray())}}
    @Test fun peerMovePreventsResurrectionByUndo() {
        val h=UUID.randomUUID().toString();val g=UUID.randomUUID().toString();val o=obj();val d=doc(o);val a=CanvasAuthority(d,h,g)
        val moved=CanvasObjectTransforms.replace(d,o,CanvasObjectTransforms.translate(o,20f,0f));a.execute(h,CanvasCommand(sequence=1,baseRevision=0,delta=CanvasDelta.between(d,moved)))
        val next=CanvasObjectTransforms.replace(a.document,a.document.objects.single(),CanvasObjectTransforms.translate(a.document.objects.single(),20f,0f))
        a.execute(g,CanvasCommand(sequence=1,baseRevision=1,delta=CanvasDelta.between(a.document,next)))
        a.execute(h,CanvasCommand(sequence=2,baseRevision=2,kind=CanvasCommandKind.UNDO));assertEquals(next,a.document)
    }
    @Test fun sharedReplacementUndoSkipsMissingVersion() {val o=obj();val n=o.copy(id=UUID.randomUUID().toString());assertTrue(SharedBoardOps.reverse(emptySet(),SharedBoardOps.Delta(listOf(n),listOf(o))).isEmpty)}
}
