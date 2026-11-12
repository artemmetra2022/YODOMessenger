package app.yodo.messenger.features.canvas

import org.junit.Assert.*
import org.junit.Test

class CanvasFoundationTest {
    private fun doc() = CanvasDocument(title="Геометрия · x² + y² = r²")
    private fun shape(kind:CanvasObjectKind=CanvasObjectKind.LINE)=CanvasObject(kind=kind,points=listOf(CanvasPoint(100f,100f),CanvasPoint(200f,200f)))
    private fun reject(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
    @Test fun emptyRoundTrip(){val d=doc();assertEquals(d,CanvasCodec.decode(CanvasCodec.encode(d)))}
    @Test fun allShapesRoundTrip(){val d=doc().copy(objects=CanvasObjectKind.entries.map{if(it==CanvasObjectKind.TEXT)shape(it).copy(text="√x ≤ π\n\"text\"") else if(it==CanvasObjectKind.FORMULA)shape(it).copy(text="\\frac{1}{2}") else shape(it)},backgroundBase64="aGVsbG8=",grid=CanvasGrid.DOTS,template=CanvasTemplate.AXES);assertEquals(d,CanvasCodec.decode(CanvasCodec.encode(d)))}
    @Test fun unknownVersionRejected(){reject{CanvasCodec.decode(CanvasCodec.encode(doc()).replace("\"version\":1","\"version\":99"))}}
    @Test fun unknownFormatRejected(){reject{CanvasCodec.decode(CanvasCodec.encode(doc()).replace("yodo.canvas","other"))}}
    @Test fun idValidation(){assertTrue(CanvasLimits.validId(doc().id));assertFalse(CanvasLimits.validId("../../other"));assertFalse(CanvasLimits.validId("1-1-1-1-1"))}
    @Test fun duplicateObjectRejected(){val o=shape();reject{CanvasLimits.validate(doc().copy(objects=listOf(o,o)))}}
    @Test fun dimensionsRejected(){reject{CanvasLimits.validate(doc().copy(width=0))};reject{CanvasLimits.validate(doc().copy(height=4097))}}
    @Test fun nonfiniteRejected(){reject{CanvasLimits.validate(doc().copy(objects=listOf(shape().copy(width=Float.NaN))))};reject{CanvasLimits.validate(doc().copy(objects=listOf(shape().copy(points=listOf(CanvasPoint(Float.POSITIVE_INFINITY,2f),CanvasPoint(3f,4f))))))}}
    @Test fun outOfBoundsRejected(){reject{CanvasLimits.validate(doc().copy(objects=listOf(shape().copy(points=listOf(CanvasPoint(-1f,2f),CanvasPoint(3f,4f))))))}}
    @Test fun emptyPointsRejected(){reject{CanvasLimits.validate(doc().copy(objects=listOf(shape().copy(points=emptyList()))))}}
    @Test fun shapePointCountRejected(){reject{CanvasLimits.validate(doc().copy(objects=listOf(shape().copy(points=listOf(CanvasPoint(1f,1f))))))}}
    @Test fun textLimitsRejected(){reject{CanvasLimits.validate(doc().copy(objects=listOf(shape(CanvasObjectKind.TEXT).copy(text=" "))))};reject{CanvasLimits.validate(doc().copy(objects=listOf(shape(CanvasObjectKind.TEXT).copy(text="x".repeat(501)))))}}
    @Test fun objectBudgetRejected(){reject{CanvasLimits.validate(doc().copy(objects=List(301){shape()}))}}
    @Test fun pointBudgetRejected(){reject{CanvasLimits.validate(doc().copy(objects=listOf(CanvasObject(kind=CanvasObjectKind.PEN,points=List(2049){CanvasPoint(1f,1f)}))))};reject{CanvasLimits.validate(doc().copy(objects=List(30){CanvasObject(kind=CanvasObjectKind.PEN,points=List(2048){CanvasPoint(1f,1f)})}))}}
    @Test fun backgroundBudgetRejected(){reject{CanvasLimits.validate(doc().copy(backgroundBase64="x".repeat(900001)))}}
    @Test fun jsonBudgetRejected(){reject{CanvasCodec.decode(" ".repeat(5000001))}}
    @Test fun historyUndoRedo(){val d=doc();val h=CanvasHistory(d);val next=d.copy(objects=listOf(shape()));h.commit(next);assertTrue(h.canUndo);assertEquals(d,h.undo());assertTrue(h.canRedo);assertEquals(next,h.redo())}
    @Test fun noOpPreservesHistory(){val d=doc();val h=CanvasHistory(d);h.commit(d);assertFalse(h.canUndo);h.commit(d.copy(title="A"));h.undo();h.commit(d);assertTrue(h.canRedo)}
    @Test fun freshEditDropsRedo(){val d=doc();val h=CanvasHistory(d);h.commit(d.copy(title="A"));h.undo();h.commit(d.copy(title="B"));assertFalse(h.canRedo)}
    @Test fun boundedHistory(){val h=CanvasHistory(doc(),3);repeat(10){h.commit(h.current.copy(title="Board $it"))};var n=0;while(h.canUndo){h.undo();n++};assertEquals(3,n)}
    @Test fun historyCannotSwitchId(){val h=CanvasHistory(doc());reject{h.commit(doc())}}
    @Test fun eraserBatchOneUndo(){val d=doc().copy(objects=listOf(shape(),shape()));val h=CanvasHistory(d);h.commit(d.copy(objects=emptyList()));assertEquals(d,h.undo());assertFalse(h.canUndo)}
    @Test fun backgroundUndo(){val d=doc();val h=CanvasHistory(d);h.commit(d.copy(backgroundBase64="image",grid=CanvasGrid.NONE));assertEquals(d,h.undo());assertEquals("image",h.redo().backgroundBase64)}
    @Test fun segmentDistanceHandlesZeroLength(){assertEquals(5f,segmentDistance(CanvasPoint(3f,4f),CanvasPoint(0f,0f),CanvasPoint(0f,0f)),.001f)}
    @Test fun lineHitAndMiss(){assertTrue(hitCanvasObject(shape(),CanvasPoint(150f,150f),1f));assertFalse(hitCanvasObject(shape(),CanvasPoint(100f,200f),1f))}
    @Test fun rectangleInteriorNotErased(){assertTrue(hitCanvasObject(shape(CanvasObjectKind.RECTANGLE),CanvasPoint(100f,150f),1f));assertFalse(hitCanvasObject(shape(CanvasObjectKind.RECTANGLE),CanvasPoint(150f,150f),1f))}
    @Test fun circleEdgeOnly(){val c=CanvasObject(kind=CanvasObjectKind.CIRCLE,points=listOf(CanvasPoint(100f,100f),CanvasPoint(150f,100f)));assertTrue(hitCanvasObject(c,CanvasPoint(100f,150f),1f));assertFalse(hitCanvasObject(c,CanvasPoint(100f,100f),1f))}
    @Test fun arrowHeadHit(){val o=shape(CanvasObjectKind.ARROW);val legs=arrowLegs(o.points.first(),o.points.last(),o.width);assertTrue(hitCanvasObject(o,legs.first,1f))}
    @Test fun transformInverse(){val t=CanvasTransform.fit(390f,430f,doc(),3f,CanvasPoint(20f,-15f));val p=CanvasPoint(174f,300f);val q=t.world(t.screen(p));assertEquals(p.x,q.x,.001f);assertEquals(p.y,q.y,.001f)}
    @Test fun fitStaysInsideViewport(){val d=doc();val t=CanvasTransform.fit(390f,430f,d);val a=t.screen(CanvasPoint(0f,0f));val b=t.screen(CanvasPoint(1200f,900f));assertTrue(a.x>=0&&a.y>=0&&b.x<=390&&b.y<=430)}
    @Test fun forbiddenMediaNotImported(){assertFalse(canImportCanvasImage(true,false,null,100));assertFalse(canImportCanvasImage(false,true,null,100));assertFalse(canImportCanvasImage(false,false,100,100));assertFalse(canImportCanvasImage(false,false,99,100))}
    @Test fun ordinaryMediaAllowed(){assertTrue(canImportCanvasImage(false,false,null,100));assertTrue(canImportCanvasImage(false,false,101,100))}
}
