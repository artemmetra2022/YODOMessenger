package app.yodo.messenger.features.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasDrawingViewClippingTest {
    @Test
    fun loadingBackgroundStaysInsideView() = assertClipped(null)

    @Test
    fun loadedBoardStaysInsideView() = assertClipped(CanvasDocument(title = "Test"))

    private fun assertClipped(document: CanvasDocument?) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = CanvasDrawingView(InstrumentationRegistry.getInstrumentation().targetContext)
            view.document = document
            view.measure(
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 100, 80)
            val bitmap = Bitmap.createBitmap(180, 200, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.MAGENTA)
                val canvas = Canvas(bitmap)
                canvas.translate(40f, 60f)
                val clipBefore = canvas.clipBounds
                val saveCountBefore = canvas.saveCount
                view.draw(canvas)
                assertEquals(clipBefore, canvas.clipBounds)
                assertEquals(saveCountBefore, canvas.saveCount)
                // Sibling toolbars above/below and margins beside the board must survive.
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        if (x !in 40 until 140 || y !in 60 until 140) {
                            assertEquals("Paint leaked at ($x, $y)", Color.MAGENTA, bitmap.getPixel(x, y))
                        }
                    }
                }
                assertEquals(0xffe9edf3.toInt(), bitmap.getPixel(40, 60))
                canvas.drawColor(Color.GREEN)
                assertEquals(Color.GREEN, bitmap.getPixel(0, 0))
            } finally {
                bitmap.recycle()
            }
        }
    }
}
