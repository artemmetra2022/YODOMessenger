package app.yodo.messenger.features.canvas

import java.util.UUID
import kotlin.math.*

/** Platform-independent geometry. Native text measurement may supply tighter bounds. */
data class CanvasBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val center get() = CanvasPoint((left+right)/2f,(top+bottom)/2f)
    val width get() = right-left
    val height get() = bottom-top
    fun contains(p: CanvasPoint, margin: Float = 0f) = p.x>=left-margin&&p.x<=right+margin&&p.y>=top-margin&&p.y<=bottom+margin
    fun corners() = listOf(CanvasPoint(left,top),CanvasPoint(right,top),CanvasPoint(right,bottom),CanvasPoint(left,bottom))
}
fun rotateCanvasPoint(p: CanvasPoint, center: CanvasPoint, degrees: Float): CanvasPoint {
    val angle=degrees*PI.toFloat()/180f;val x=p.x-center.x;val y=p.y-center.y
    return CanvasPoint(center.x+x*cos(angle)-y*sin(angle),center.y+x*sin(angle)+y*cos(angle))
}
fun canvasObjectBounds(o: CanvasObject): CanvasBounds {
    val a=o.points.first(); val b=o.points.last()
    val raw=when(o.kind) {
        CanvasObjectKind.CIRCLE -> { val r=hypot(b.x-a.x,b.y-a.y);CanvasBounds(a.x-r,a.y-r,a.x+r,a.y+r) }
        CanvasObjectKind.TEXT -> {
            val width=if(o.textBoxWidth>0f)o.textBoxWidth else o.text.lines().maxOf{it.length}.coerceAtLeast(1)*o.fontSize*.65f
            CanvasBounds(a.x,a.y-o.fontSize,a.x+width,a.y+o.fontSize*(o.text.lines().size-1)*1.25f+o.fontSize*.3f)
        }
        CanvasObjectKind.ARROW -> {
            val legs=arrowLegs(a,b,o.width);val points=o.points+listOf(legs.first,legs.second)
            CanvasBounds(points.minOf{it.x},points.minOf{it.y},points.maxOf{it.x},points.maxOf{it.y})
        }
        else -> CanvasBounds(o.points.minOf{it.x},o.points.minOf{it.y},o.points.maxOf{it.x},o.points.maxOf{it.y})
    }
    val pad=if(o.kind==CanvasObjectKind.TEXT || o.kind==CanvasObjectKind.FORMULA)0f else o.width/2f
    return CanvasBounds(raw.left-pad,raw.top-pad,raw.right+pad,raw.bottom+pad)
}
fun rotatedCanvasBounds(bounds: CanvasBounds, rotation: Float): CanvasBounds {
    val ps=bounds.corners().map{rotateCanvasPoint(it,bounds.center,rotation)}
    return CanvasBounds(ps.minOf{it.x},ps.minOf{it.y},ps.maxOf{it.x},ps.maxOf{it.y})
}

object CanvasObjectTransforms {
    fun translate(o: CanvasObject, dx: Float, dy: Float): CanvasObject =
        o.copy(points=o.points.map{CanvasPoint(it.x+dx,it.y+dy)})

    /** Clamp the complete rotated object, not each point independently (which would distort it). */
    fun fit(o: CanvasObject, doc: CanvasDocument, measure: (CanvasObject)->CanvasBounds = ::canvasObjectBounds): CanvasObject? {
        val box=rotatedCanvasBounds(measure(o),o.rotation)
        if(box.width>doc.width+.01f||box.height>doc.height+.01f)return null
        val dx=when{box.left<0f->-box.left;box.right>doc.width->doc.width-box.right;else->0f}
        val dy=when{box.top<0f->-box.top;box.bottom>doc.height->doc.height-box.bottom;else->0f}
        val moved=translate(o,dx,dy)
        // CIRCLE control point and oversized imported text can lie outside their visible bounds.
        if(moved.points.any{it.x !in -.001f..doc.width+.001f||it.y !in -.001f..doc.height+.001f})return null
        return moved.copy(points=moved.points.map{CanvasPoint(it.x.coerceIn(0f,doc.width.toFloat()),it.y.coerceIn(0f,doc.height.toFloat()))})
    }
    fun scale(o: CanvasObject, factor: Float, doc: CanvasDocument, measure: (CanvasObject)->CanvasBounds = ::canvasObjectBounds): CanvasObject? {
        if(!factor.isFinite()||factor<=0f)return null
        val font=o.kind==CanvasObjectKind.TEXT||o.kind==CanvasObjectKind.FORMULA
        val low=max(1f/o.width,if(font)16f/o.fontSize else .01f)
        val high=min(32f/o.width,if(font)96f/o.fontSize else 100f)
        val k=factor.coerceIn(low,high);val c=measure(o).center
        val textWidth=if(o.kind==CanvasObjectKind.TEXT) (if(o.textBoxWidth>0f)o.textBoxWidth else (doc.width-o.points.first().x-12f).coerceAtLeast(30f))*k else o.textBoxWidth
        if(textWidth>4096f)return null
        val next=o.copy(points=o.points.map{CanvasPoint(c.x+(it.x-c.x)*k,c.y+(it.y-c.y)*k)},
            width=(o.width*k).coerceIn(1f,32f),fontSize=if(font)(o.fontSize*k).coerceIn(16f,96f)else o.fontSize,textBoxWidth=textWidth)
        return fit(next,doc,measure)
    }
    fun rotate(o: CanvasObject, degrees: Float, doc: CanvasDocument, measure: (CanvasObject)->CanvasBounds = ::canvasObjectBounds): CanvasObject? {
        if(!degrees.isFinite())return null
        return fit(o.copy(rotation=((degrees%360f)+360f)%360f),doc,measure)
    }
    /** Every edit gets a new ID: compatible with immutable shared-board objects and conflict-aware undo. */
    fun replace(doc: CanvasDocument, expected: CanvasObject, transformed: CanvasObject, copy: Boolean = false): CanvasDocument {
        require(doc.objects.any{it==expected}){"Объект уже изменён или удалён"}
        require(!copy || doc.objects.size<CanvasLimits.MAX_OBJECTS) { "На доске уже 300 объектов. Удалите лишние перед копированием." }
        val replacement=transformed.copy(id=UUID.randomUUID().toString())
        val next=doc.copy(objects=if(copy)doc.objects+replacement else doc.objects.filterNot{it.id==expected.id}+replacement)
        CanvasLimits.validate(next);return next
    }
}
