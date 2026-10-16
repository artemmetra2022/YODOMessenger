package app.yodo.messenger.features.canvas

import android.graphics.*
import kotlin.math.*

/** One renderer is used by the editor and PNG publication/export. */
object CanvasRenderer {
    fun draw(canvas: Canvas, doc: CanvasDocument, background: Bitmap?, draft: CanvasObject? = null) {
        canvas.save(); canvas.clipRect(0f, 0f, doc.width.toFloat(), doc.height.toFloat()); canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        if (background != null && !background.isRecycled) {
            val ratio = min(doc.width.toFloat() / background.width, doc.height.toFloat() / background.height)
            val w = background.width * ratio; val h = background.height * ratio
            paint.isFilterBitmap = true
            canvas.drawBitmap(background, null, RectF((doc.width - w) / 2, (doc.height - h) / 2, (doc.width + w) / 2, (doc.height + h) / 2), paint)
        }
        paint.color = Color.argb(if (background == null) 80 else 45, 148, 163, 184); paint.strokeWidth = 1f
        if (doc.grid == CanvasGrid.SQUARES) {
            var x = 0f; while (x <= doc.width) { canvas.drawLine(x, 0f, x, doc.height.toFloat(), paint); x += CanvasLimits.GRID_STEP }
            var y = 0f; while (y <= doc.height) { canvas.drawLine(0f, y, doc.width.toFloat(), y, paint); y += CanvasLimits.GRID_STEP }
        } else if (doc.grid == CanvasGrid.DOTS) {
            var x = CanvasLimits.GRID_STEP; while (x < doc.width) {
                var y = CanvasLimits.GRID_STEP; while (y < doc.height) { canvas.drawCircle(x, y, 1.8f, paint); y += CanvasLimits.GRID_STEP }; x += CanvasLimits.GRID_STEP
            }
        }
        drawTemplate(canvas, doc, paint)
        doc.objects.forEach { drawObject(canvas, it, doc.width, paint) }
        draft?.let { drawObject(canvas, it, doc.width, paint) }
        canvas.restore()
    }
    private fun drawObject(canvas: Canvas, obj: CanvasObject, pageWidth: Int, paint: Paint) {
        paint.color = obj.color; paint.strokeWidth = obj.width; paint.style = Paint.Style.STROKE
        val a = obj.points.first(); val b = obj.points.last()
        when (obj.kind) {
            CanvasObjectKind.PEN -> {
                if (obj.points.size == 1) { paint.style = Paint.Style.FILL; canvas.drawCircle(a.x, a.y, obj.width / 2, paint) }
                else {
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        for (i in 1 until obj.points.size) {
                            val previous = obj.points[i - 1]; val next = obj.points[i]
                            quadTo(previous.x, previous.y, (previous.x + next.x) / 2, (previous.y + next.y) / 2)
                        }
                        lineTo(b.x, b.y)
                    }; canvas.drawPath(path, paint)
                }
            }
            CanvasObjectKind.LINE -> canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            CanvasObjectKind.ARROW -> { canvas.drawLine(a.x, a.y, b.x, b.y, paint); val legs = arrowLegs(a, b, obj.width)
                canvas.drawLine(b.x, b.y, legs.first.x, legs.first.y, paint); canvas.drawLine(b.x, b.y, legs.second.x, legs.second.y, paint) }
            CanvasObjectKind.RECTANGLE -> canvas.drawRect(min(a.x,b.x), min(a.y,b.y), max(a.x,b.x), max(a.y,b.y), paint)
            CanvasObjectKind.CIRCLE -> canvas.drawCircle(a.x, a.y, hypot(b.x-a.x,b.y-a.y), paint)
            CanvasObjectKind.ELLIPSE -> canvas.drawOval(RectF(min(a.x,b.x), min(a.y,b.y), max(a.x,b.x), max(a.y,b.y)), paint)
            CanvasObjectKind.ROUNDED_RECT -> { val r = canvasCornerRadius(a, b)
                canvas.drawRoundRect(RectF(min(a.x,b.x), min(a.y,b.y), max(a.x,b.x), max(a.y,b.y)), r, r, paint) }
            CanvasObjectKind.TRIANGLE, CanvasObjectKind.DIAMOND -> { val polygon = canvasPolygon(obj.kind, a, b)
                val path = Path().apply { moveTo(polygon[0].x, polygon[0].y); for (i in 1 until polygon.size) lineTo(polygon[i].x, polygon[i].y); close() }
                canvas.drawPath(path, paint) }
            CanvasObjectKind.FORMULA -> CanvasMathNative.drawObject(canvas,obj)
            CanvasObjectKind.TEXT -> {
                paint.style = Paint.Style.FILL; paint.textSize = obj.fontSize; paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                var y = a.y; val available = (pageWidth - a.x - 12).coerceAtLeast(30f)
                obj.text.lines().forEach { line ->
                    var rest = line
                    if (rest.isEmpty()) y += obj.fontSize * 1.25f
                    while (rest.isNotEmpty()) {
                        val count = paint.breakText(rest, true, available, null).coerceAtLeast(1)
                        canvas.drawText(rest.take(count), a.x, y, paint); y += obj.fontSize * 1.25f; rest = rest.drop(count)
                    }
                }
            }
        }
    }
    private fun drawTemplate(canvas: Canvas, doc: CanvasDocument, paint: Paint) {
        val x = doc.width.toFloat(); val y = doc.height.toFloat()
        val color = 0xff64748b.toInt()
        fun line(a: CanvasPoint,b: CanvasPoint, arrow: Boolean=false) = drawObject(canvas, CanvasObject(kind=if(arrow) CanvasObjectKind.ARROW else CanvasObjectKind.LINE,points=listOf(a,b),color=color,width=2f),doc.width,paint)
        fun label(text:String,point:CanvasPoint) = drawObject(canvas, CanvasObject(kind=CanvasObjectKind.TEXT,points=listOf(point),text=text,color=color,fontSize=28f),doc.width,paint)
        when (doc.template) {
            CanvasTemplate.NONE -> Unit
            CanvasTemplate.AXES -> {
                val center=CanvasPoint(x/2,y/2); line(CanvasPoint(x*0.08f,center.y),CanvasPoint(x*0.92f,center.y),true)
                line(CanvasPoint(center.x,y*0.92f),CanvasPoint(center.x,y*0.08f),true)
                label("X",CanvasPoint(x*0.92f,center.y+38));label("Y",CanvasPoint(center.x+18,y*0.08f));label("0",CanvasPoint(center.x+8,center.y+30))
                var tick=CanvasLimits.GRID_STEP
                while(tick<x*0.4f){ line(CanvasPoint(center.x+tick,center.y-5),CanvasPoint(center.x+tick,center.y+5));line(CanvasPoint(center.x-tick,center.y-5),CanvasPoint(center.x-tick,center.y+5));tick+=CanvasLimits.GRID_STEP }
                tick=CanvasLimits.GRID_STEP
                while(tick<y*0.4f){ line(CanvasPoint(center.x-5,center.y+tick),CanvasPoint(center.x+5,center.y+tick));line(CanvasPoint(center.x-5,center.y-tick),CanvasPoint(center.x+5,center.y-tick));tick+=CanvasLimits.GRID_STEP }
            }
            CanvasTemplate.TRIANGLE,CanvasTemplate.RIGHT_TRIANGLE -> {
                val a=CanvasPoint(x*0.25f,y*0.75f);val b=if(doc.template==CanvasTemplate.RIGHT_TRIANGLE)CanvasPoint(x*0.25f,y*0.25f)else CanvasPoint(x*0.5f,y*0.25f);val c=CanvasPoint(x*0.75f,y*0.75f)
                line(a,b);line(b,c);line(c,a);label("A",CanvasPoint(a.x-30,a.y+35));label("B",CanvasPoint(b.x,b.y-18));label("C",CanvasPoint(c.x+12,c.y+35))
            }
        }
    }
    fun bitmap(doc: CanvasDocument, background: Bitmap?, maxWidth: Int = 1200): Bitmap {
        val width = min(doc.width,maxWidth); val height = (doc.height.toFloat()*width/doc.width).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas=Canvas(bitmap);canvas.scale(width.toFloat()/doc.width,height.toFloat()/doc.height);draw(canvas,doc,background)
        }
    }
}
