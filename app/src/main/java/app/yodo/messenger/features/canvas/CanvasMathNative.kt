package app.yodo.messenger.features.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.LruCache
import android.view.View

/** Same immutable layout and drawing operations in preview, board and PNG. No TeX engine or assets. */
object CanvasMathNative {
    private data class Key(val source:String,val size:Float)
    private val cache=LruCache<Key,MathLayout>(64)
    private val font=Typeface.create("serif",Typeface.NORMAL)
    @Synchronized fun clearCache(){cache.evictAll()}
    @Synchronized fun layout(source:String,size:Float):MathLayout {
        val normalized=CanvasMath.normalize(source);val key=Key(normalized,size)
        cache.get(key)?.let{return it}
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=font}
        val result=CanvasMathLayout.layout(CanvasMath.parse(normalized),size,MathMeasure {text,fontSize->
            paint.textSize=fontSize;val metrics=paint.fontMetrics
            MathMetrics(paint.measureText(text),(-metrics.ascent).coerceAtLeast(0f),metrics.descent.coerceAtLeast(0f))
        })
        cache.put(key,result);return result
    }
    fun draw(canvas:Canvas,layout:MathLayout,color:Int){
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;typeface=font;strokeCap=Paint.Cap.ROUND}
        layout.ink.forEach{op->when(op){
            is MathInk.Text->{paint.style=Paint.Style.FILL;paint.textSize=op.size;canvas.drawText(op.text,op.x,op.baseline,paint)}
            is MathInk.Line->{paint.style=Paint.Style.STROKE;paint.strokeWidth=op.width;canvas.drawLine(op.x1,op.y1,op.x2,op.y2,paint)}
            is MathInk.Curve->{paint.style=Paint.Style.STROKE;paint.strokeWidth=op.width;val path=Path().apply{moveTo(op.x1,op.y1);quadTo(op.cx,op.cy,op.x2,op.y2)};canvas.drawPath(path,paint)}
        }}
    }
    fun drawObject(canvas:Canvas,obj:CanvasObject){
        val layout=layout(obj.text,obj.fontSize);val a=obj.points.first();val b=obj.points.last()
        val scale=minOf((b.x-a.x)/layout.width,(b.y-a.y)/layout.height)
        val saved=canvas.save()
        try{canvas.translate(a.x,a.y);canvas.scale(scale,scale);canvas.translate(0f,layout.ascent);draw(canvas,layout,obj.color)}finally{canvas.restoreToCount(saved)}
    }
}
class CanvasFormulaPreview(context:Context):View(context){
    var layout:MathLayout?=null
    var inkColor=0xff1e293b.toInt()
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);canvas.drawColor(android.graphics.Color.WHITE)
        val layout=layout?:return;val padding=12f*resources.displayMetrics.density
        val scale=minOf((width-padding*2)/layout.width,(height-padding*2)/layout.height).coerceAtLeast(.001f)
        val saved=canvas.save()
        try{canvas.translate((width-layout.width*scale)/2,(height-layout.height*scale)/2);canvas.scale(scale,scale);canvas.translate(0f,layout.ascent);CanvasMathNative.draw(canvas,layout,inkColor)}finally{canvas.restoreToCount(saved)}
    }
}
