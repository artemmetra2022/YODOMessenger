package app.yodo.messenger.features.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

class CanvasDrawingView(context: Context) : View(context) {
    var document: CanvasDocument? = null
    var backgroundImage: Bitmap? = null
    var tool = CanvasTool.PEN
    var inkColor = 0xff1e293b.toInt()
    var inkWidth = 4f
    var fontSize = 32f
    var onCommit: (CanvasDocument) -> Unit = {}
    var onGesture: ((CanvasDocument,CanvasDocument,Long)->Unit)? = null
    var documentRevision=0L
    private var gestureRevision=0L
    private fun finishGesture(before:CanvasDocument,after:CanvasDocument){val callback=onGesture;if(callback!=null)callback(before,after,gestureRevision)else onCommit(after)}
    var onText: (CanvasPoint) -> Unit = {}
    var onFormula: (CanvasPoint,CanvasObject?) -> Unit = {_,_->}
    var onLimit: () -> Unit = {}
    var onCursor: (CanvasPoint?) -> Unit = {}
    var remoteCursor: CanvasRemoteCursor? = null
    private var zoom=1f; private var pan=CanvasPoint(0f,0f)
    private var draft: CanvasObject?=null
    private var erasedPreview: CanvasDocument?=null
    private val erasedIds=mutableSetOf<String>()
    private var lastFocus: CanvasPoint?=null
    private var eraserPoint: CanvasPoint?=null
    private var multiTouch=false
    private var gestureTooLong=false
    private var activeGesture=false
    private fun transform() = document?.let { CanvasTransform.fit(width.toFloat(),height.toFloat(),it,zoom,pan) }
    private val detector = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val doc=document?:return false;val before=transform()?:return false
            val anchor=before.world(CanvasPoint(detector.focusX,detector.focusY))
            zoom=(zoom*detector.scaleFactor).coerceIn(1f,6f)
            val newScale=CanvasTransform.fit(width.toFloat(),height.toFloat(),doc,zoom).scale
            pan=CanvasPoint(detector.focusX-width/2f+(doc.width/2f-anchor.x)*newScale,detector.focusY-height/2f+(doc.height/2f-anchor.y)*newScale)
            invalidate();return true
        }
    })
    fun cancelRemoteConflict(){cancelDraft();invalidate()}
    fun resetViewport(){zoom=1f;pan=CanvasPoint(0f,0f);cancelDraft();invalidate()}
    private fun cancelDraft(){activeGesture=false;draft=null;erasedIds.clear();erasedPreview=null;eraserPoint=null;gestureTooLong=false}
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas);canvas.drawColor(0xffe9edf3.toInt())
        val base=document?:return
        val doc=if(erasedPreview!=null)base.copy(objects=base.objects.filterNot{it.id in erasedIds})else base;val transform=transform()?:return
        canvas.save();canvas.translate(transform.offsetX,transform.offsetY);canvas.scale(transform.scale,transform.scale)
        CanvasRenderer.draw(canvas,doc,backgroundImage,draft)
        remoteCursor?.let { cursor ->
            val p=cursor.point;val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=cursor.color;style=Paint.Style.FILL}
            val size=14f*resources.displayMetrics.density/transform.scale
            val path=android.graphics.Path().apply{moveTo(p.x,p.y);lineTo(p.x+size*.3f,p.y+size);lineTo(p.x+size*.6f,p.y+size*.6f);lineTo(p.x+size,p.y+size*.3f);close()}
            canvas.drawPath(path,paint);paint.textSize=12f*resources.displayMetrics.scaledDensity/transform.scale;canvas.drawText("Собеседник",p.x+size,p.y+size,paint)
        }
        eraserPoint?.let { p -> val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xff2563eb.toInt();style=Paint.Style.STROKE;strokeWidth=2f/transform.scale};canvas.drawCircle(p.x,p.y,max(12f,18f/transform.scale),paint) }
        canvas.restore()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val doc=document?:return false
        if(!isEnabled){cancelDraft();return false}
        parent?.requestDisallowInterceptTouchEvent(true);detector.onTouchEvent(event)
        if(event.actionMasked==MotionEvent.ACTION_POINTER_DOWN){onCursor(null);multiTouch=true;cancelDraft();lastFocus=null}
        if(event.pointerCount>=2){
            val focus=CanvasPoint((event.getX(0)+event.getX(1))/2,(event.getY(0)+event.getY(1))/2)
            if(event.actionMasked==MotionEvent.ACTION_MOVE){lastFocus?.let{pan=CanvasPoint(pan.x+focus.x-it.x,pan.y+focus.y-it.y)}}
            lastFocus=focus;invalidate();return true
        }
        if(multiTouch){if(event.actionMasked==MotionEvent.ACTION_UP||event.actionMasked==MotionEvent.ACTION_CANCEL){multiTouch=false;lastFocus=null};return true}
        val transform=transform()?:return false
        val world=transform.world(CanvasPoint(event.x,event.y))
        val point=CanvasPoint(world.x.coerceIn(0f,doc.width.toFloat()),world.y.coerceIn(0f,doc.height.toFloat()))
        fun erase(){eraserPoint=point;val current=doc.copy(objects=doc.objects.filterNot{it.id in erasedIds});val radius=max(12f,18f/transform.scale)
            current.objects.filter{hitCanvasObject(it,point,radius)}.forEach{erasedIds.add(it.id)}
            erasedPreview=doc.copy(objects=doc.objects.filterNot{it.id in erasedIds})}
        if(event.actionMasked==MotionEvent.ACTION_MOVE && !activeGesture)return true
        if(event.actionMasked==MotionEvent.ACTION_UP && !activeGesture)return true
        if(event.actionMasked==MotionEvent.ACTION_DOWN||event.actionMasked==MotionEvent.ACTION_MOVE)onCursor(point)
        if(event.actionMasked==MotionEvent.ACTION_UP||event.actionMasked==MotionEvent.ACTION_CANCEL)onCursor(null)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN -> {
                cancelDraft()
                if(tool!=CanvasTool.HAND && (world.x !in 0f..doc.width.toFloat()||world.y !in 0f..doc.height.toFloat()))return true
                activeGesture=true;gestureRevision=documentRevision
                lastFocus=CanvasPoint(event.x,event.y)
                when(tool){
                    CanvasTool.HAND -> Unit
                    CanvasTool.TEXT -> {onText(point);performClick()}
                    CanvasTool.FORMULA -> {val existing=doc.objects.asReversed().firstOrNull{it.kind==CanvasObjectKind.FORMULA&&hitCanvasObject(it,point,0f)};onFormula(existing?.points?.first()?:point,existing);performClick()}
                    CanvasTool.ERASER -> erase()
                    else -> {val kind=CanvasObjectKind.valueOf(tool.name);draft=CanvasObject(kind=kind,points=if(kind==CanvasObjectKind.PEN)listOf(point)else listOf(point,point),color=inkColor,width=inkWidth,fontSize=fontSize)}
                }
            }
            MotionEvent.ACTION_MOVE -> when(tool){
                CanvasTool.HAND -> {lastFocus?.let{pan=CanvasPoint(pan.x+event.x-it.x,pan.y+event.y-it.y)};lastFocus=CanvasPoint(event.x,event.y)}
                CanvasTool.ERASER -> erase()
                CanvasTool.TEXT,CanvasTool.FORMULA -> Unit
                else -> draft?.let { obj ->
                    if(obj.kind==CanvasObjectKind.PEN){
                        if(hypot(point.x-obj.points.last().x,point.y-obj.points.last().y)>1.5f){
                            if(obj.points.size>=CanvasLimits.MAX_POINTS_PER_OBJECT)gestureTooLong=true
                            else draft=obj.copy(points=obj.points+point)
                        }
                    }else draft=obj.copy(points=listOf(obj.points.first(),point))
                }
            }
            MotionEvent.ACTION_UP -> {
                draft?.let { obj ->
                    if(obj.kind==CanvasObjectKind.PEN) {
                        if(hypot(point.x-obj.points.last().x,point.y-obj.points.last().y)>1.5f) {
                            if(obj.points.size>=CanvasLimits.MAX_POINTS_PER_OBJECT)gestureTooLong=true
                            else draft=obj.copy(points=obj.points+point)
                        }
                    } else draft=obj.copy(points=listOf(obj.points.first(),point))
                }
                if(gestureTooLong)onLimit()
                else if(erasedPreview!=null)finishGesture(doc,doc.copy(objects=doc.objects.filterNot{it.id in erasedIds}))
                else draft?.let{finishGesture(doc,doc.copy(objects=doc.objects+it))}
                cancelDraft();lastFocus=null;performClick()
            }
            MotionEvent.ACTION_CANCEL -> {cancelDraft();lastFocus=null}
        }
        invalidate();return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
