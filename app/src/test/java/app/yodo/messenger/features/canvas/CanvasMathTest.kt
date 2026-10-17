package app.yodo.messenger.features.canvas

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CanvasMathTest {
    private val measure=MathMeasure{text,size->MathMetrics(text.length*size*.6f,size*.8f,size*.2f)}
    private fun layout(source:String,size:Float=32f)=CanvasMathLayout.layout(CanvasMath.parse(source),size,measure)
    private fun reject(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    private fun formula(source:String="\\frac{a}{b}")=CanvasObject(kind=CanvasObjectKind.FORMULA,points=listOf(CanvasPoint(40f,50f),CanvasPoint(140f,150f)),text=source)
    private fun doc(source:String="\\frac{a}{b}")=CanvasDocument(title="Формулы",objects=listOf(formula(source)))
    @Test fun outerDollarsAreOptional(){assertEquals("x^2",CanvasMath.normalize("  $"+"x^2$  "));assertEquals("x",CanvasMath.normalize("$$"+"x$$"))}
    @Test fun whitespaceNormalized(){assertEquals("x + y",CanvasMath.normalize("x\n+\ty"))}
    @Test fun exampleTemplatesParseAndLayout(){CanvasMath.examples.values.forEach{val l=layout(it);assertTrue(it,l.width>0&&l.height>0)}}
    @Test fun fractionHasRule(){assertTrue(layout("\\frac{a}{b}").ink.any{it is MathInk.Line})}
    @Test fun fractionNumeratorAboveDenominator(){val l=layout("\\frac{a}{b}");val text=l.ink.filterIsInstance<MathInk.Text>();assertTrue(text.first().baseline<text.last().baseline)}
    @Test fun superscriptAboveBaseline(){val text=layout("x^2").ink.filterIsInstance<MathInk.Text>();assertTrue(text.last().baseline<text.first().baseline)}
    @Test fun subscriptBelowBaseline(){val text=layout("a_n").ink.filterIsInstance<MathInk.Text>();assertTrue(text.last().baseline>text.first().baseline)}
    @Test fun bothScriptsAreSeparated(){val text=layout("x_i^2").ink.filterIsInstance<MathInk.Text>();val upper=text.first{it.text=="2"};val lower=text.first{it.text=="i"};assertTrue(upper.baseline+upper.size*.2f<lower.baseline-lower.size*.8f)}
    @Test fun indexedRootHasIndex(){assertTrue(layout("\\sqrt[3]{x}").ink.filterIsInstance<MathInk.Text>().any{it.text=="3"})}
    @Test fun greekAndFunctions(){val text=layout("\\sin \\theta + \\pi").ink.filterIsInstance<MathInk.Text>().map{it.text};assertTrue(text.containsAll(listOf("sin","θ","π")))}
    @Test fun nativeMathSymbolsAccepted(){assertTrue(layout("α+β≤π").width>0)}
    @Test fun fencesGrowWithContent(){assertTrue(layout("\\left(\\frac{a}{b}\\right)").height>layout("a").height)}
    @Test fun arrowInsideLeftRightIsNotClosingCommand(){assertTrue(layout("\\left(x\\rightarrow y\\right)").width>0)}
    @Test fun curlyFencesHaveCurves(){assertTrue(layout("\\left\\{x\\right\\}").ink.any{it is MathInk.Curve})}
    @Test fun vectorAndHatHaveRules(){assertTrue(layout("\\vec{a}+\\hat{x}+\\overline{AB}").ink.count{it is MathInk.Line}>=6)}
    @Test fun gapOnlyRejected(){reject{CanvasMath.parse("\\,")};reject{CanvasMath.parse("\\left.\\,\\right.")}}
    @Test fun emptyRejected(){reject{CanvasMath.parse("")};reject{CanvasMath.parse(" ")}}
    @Test fun unknownCommandRejected(){reject{CanvasMath.parse("\\unknown{x}")}}
    @Test fun macrosRejected(){listOf("\\def\\a{a}","\\newcommand{a}{b}","\\gdef\\a{a}").forEach{reject{CanvasMath.parse(it)}}}
    @Test fun externalResourcesRejected(){listOf("\\input{secret}","\\includegraphics{x}","\\href{a}{b}","\\url{a}").forEach{reject{CanvasMath.parse(it)}}}
    @Test fun matrixAndMultilineRejected(){reject{CanvasMath.parse("\\begin{matrix}a&b\\end{matrix}")};reject{CanvasMath.parse("a\\\\b")}}
    @Test fun commentsAndControlCharsRejected(){listOf("x%comment","x#1","x\u0000","x&y").forEach{reject{CanvasMath.parse(it)}}}
    @Test fun cyrillicIsTextNotMath(){reject{CanvasMath.parse("привет")}}
    @Test fun sourceLimit(){reject{CanvasMath.parse("x".repeat(261))}}
    @Test fun nestingLimit(){reject{CanvasMath.parse("{".repeat(14)+"x"+"}".repeat(14))}}
    @Test fun nodeLimit(){reject{CanvasMath.parse("x".repeat(170))}}
    @Test fun commandLimit(){reject{CanvasMath.parse("\\pi ".repeat(49))}}
    @Test fun malformedGroupsRejected(){listOf("\\frac{a}","\\sqrt{x","x}","x_{","\\frac{}{2}","\\sqrt[]{x}").forEach{reject{CanvasMath.parse(it)}}}
    @Test fun duplicateScriptsRejected(){reject{CanvasMath.parse("x^2^3")};reject{CanvasMath.parse("x_i_j")}}
    @Test fun scriptWithoutBaseRejected(){reject{CanvasMath.parse("^2")};reject{CanvasMath.parse("_a")}}
    @Test fun unmatchedLeftRightRejected(){reject{CanvasMath.parse("\\left(x")};reject{CanvasMath.parse("\\right)")}}
    @Test fun fractionRequiresBraces(){reject{CanvasMath.parse("\\frac12")}}
    @Test fun v1DocumentsStillRoundTrip(){val d=CanvasDocument(title="Старый проект");val raw=CanvasCodec.encode(d);assertTrue(raw.contains("\"version\":1"));assertEquals(d,CanvasCodec.decode(raw))}
    @Test fun formulaRequiresDocumentV2(){val d=doc();val raw=CanvasCodec.encode(d);assertTrue(raw.contains("\"version\":2"));assertEquals(d,CanvasCodec.decode(raw));reject{CanvasCodec.decode(raw.replace("\"version\":2","\"version\":1"))}}
    @Test fun invalidStoredFormulaRejected(){reject{CanvasLimits.validate(doc("\\input{x}"))}}
    @Test fun invertedFormulaBoundsRejected(){reject{CanvasLimits.validate(doc().copy(objects=listOf(formula().copy(points=listOf(CanvasPoint(30f,30f),CanvasPoint(10f,10f))))))}}
    @Test fun thirtyFormulaLimit(){CanvasLimits.validate(doc().copy(objects=List(30){formula()}));reject{CanvasLimits.validate(doc().copy(objects=List(31){formula()}))}}
    @Test fun formulaHitBoxIsExact(){val o=formula();assertTrue(hitCanvasObject(o,CanvasPoint(60f,80f),0f));assertFalse(hitCanvasObject(o,CanvasPoint(39f,80f),0f))}
    @Test fun placementKeepsFormulaOnPage(){val d=doc();val bounds=CanvasMathLayout.bounds(CanvasPoint(1199f,899f),layout("\\frac{a}{b}"),d);assertTrue(bounds[1].x<=d.width&&bounds[1].y<=d.height);assertTrue(bounds[0].x>=0&&bounds[0].y>=0)}
    @Test fun tooLargePlacementRejected(){reject{CanvasMathLayout.bounds(CanvasPoint(0f,0f),MathLayout(100000f,100f,100f,emptyList()),doc())}}
    @Test fun replacementIsUndoableLocally(){val before=doc();val after=before.copy(objects=listOf(formula("x^2")));val h=CanvasHistory(before);h.commit(after);assertEquals(before,h.undo());assertEquals(after,h.redo())}
    @Test fun replacementSyncsAndUndoesAsOneOperation(){val initial=doc();val replacement=formula("x^2");val host=UUID.randomUUID().toString();val guest=UUID.randomUUID().toString();val a=CanvasAuthority(initial,host,guest);val delta=CanvasDelta.between(initial,initial.copy(objects=listOf(replacement)));val c=a.execute(guest,CanvasCommand(sequence=1,baseRevision=0,delta=delta));assertEquals(listOf(replacement),c.delta.apply(initial).objects);a.execute(guest,CanvasCommand(sequence=2,baseRevision=1,kind=CanvasCommandKind.UNDO));assertEquals(initial.objects,a.document.objects)}
    @Test fun formulaWireRoundTrip(){val d=doc("x=\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}");val s=UUID.randomUUID().toString();val h=UUID.randomUUID().toString();val g=UUID.randomUUID().toString();val message=CanvasWire.Snapshot(s,d,0,h,g);assertEquals(message,CanvasCollabWire.decode(CanvasCollabWire.encode(message)))}
    @Test fun v2PeersAreRejected(){val m=CanvasWire.Cursor(UUID.randomUUID().toString(),1,null,0);val raw=CanvasCollabWire.encode(m).toString(Charsets.UTF_8);assertTrue(raw.contains("\"protocol\":5"));reject{CanvasCollabWire.decode(raw.replace("\"protocol\":5","\"protocol\":3").toByteArray())}}
    @Test fun layoutsRemainFiniteAtEveryOfferedSize(){for(s in listOf(24f,32f,48f,64f))for(source in CanvasMath.examples.values){val l=layout(source,s);assertTrue(l.width.isFinite()&&l.height.isFinite());assertTrue(l.ink.size<=1024)}}
    @Test fun minimalSpaceArgumentsDoNotCreateNegativeExtents(){listOf("\\frac{\\,}{\\,}","\\sqrt{\\,}","\\vec{\\,}","\\hat{\\,}").forEach{val l=layout(it);assertTrue(l.width>0&&l.ascent>=0&&l.descent>=0)}}
}
