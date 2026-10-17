package app.yodo.messenger.features.canvas

import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class CanvasSelectionGestureTest {
    @Test fun selectingDraggingCopyingAndRotatingDoNotDrawExtraStrokes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val o=CanvasObject(kind=CanvasObjectKind.RECTANGLE,points=listOf(CanvasPoint(300f,300f),CanvasPoint(500f,450f)))
            val initial=CanvasDocument(title="Жесты",objects=listOf(o))
            val view=CanvasDrawingView(context);view.layout(0,0,1200,900);view.document=initial;view.tool=CanvasTool.SELECT
            var commits=0
            view.onCommit={commits++;view.document=it}
            val t=CanvasTransform.fit(1200f,900f,initial)
            fun touch(action:Int,x:Float,y:Float,time:Long) {
                val p=t.screen(CanvasPoint(x,y));val e=MotionEvent.obtain(0,time,action,p.x,p.y,0)
                try { view.onTouchEvent(e) } finally { e.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN,400f,375f,0);touch(MotionEvent.ACTION_MOVE,450f,405f,20);touch(MotionEvent.ACTION_UP,450f,405f,40)
            assertEquals(1,commits);assertEquals(1,view.document!!.objects.size)
            assertEquals(CanvasPoint(350f,330f),view.document!!.objects.single().points.first())
            view.copySelection();assertEquals(2,view.document!!.objects.size)
            view.rotateSelection(15f);assertEquals(15f,view.document!!.objects.last().rotation,0f)
            assertEquals(2,view.document!!.objects.size)
        }
    }
}
