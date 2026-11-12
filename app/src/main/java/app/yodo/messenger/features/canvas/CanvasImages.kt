package app.yodo.messenger.features.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

object CanvasImages {
    private const val MAX_INPUT = 20 * 1024 * 1024
    private fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong()*bounds.outHeight <= 100_000_000L) { "Некорректное или слишком большое изображение" }
        var sample = 1
        while (max(bounds.outWidth,bounds.outHeight)/sample > 1600) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample })) { "Не удалось открыть изображение" }
        val orientation = runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1)
        val matrix=Matrix()
        when(orientation) {
            2 -> matrix.setScale(-1f,1f); 3 -> matrix.setRotate(180f); 4 -> matrix.setScale(1f,-1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f,1f) }; 6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(270f); matrix.postScale(-1f,1f) }; 8 -> matrix.setRotate(270f)
        }
        return if(matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also { if(it!==bitmap) bitmap.recycle() }
    }
    fun fromBase64(value: String): Bitmap {
        require(value.length <= MAX_INPUT*4/3+8)
        return decode(Base64.decode(value,Base64.DEFAULT))
    }
    fun background(bitmap: Bitmap): String = boundedEncode(bitmap, Bitmap.CompressFormat.JPEG, CanvasLimits.MAX_BACKGROUND)
    fun importUri(context: Context, uri: Uri): String {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytesBounded() }
        val original = decode(bytes)
        return try { boundedEncode(original, Bitmap.CompressFormat.JPEG, CanvasLimits.MAX_BACKGROUND) } finally { original.recycle() }
    }
    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output=ByteArrayOutputStream(); val buffer=ByteArray(8192)
        while(true) { val n=read(buffer); if(n<0) break; require(output.size()+n <= MAX_INPUT) { "Файл больше 20 МБ" }; output.write(buffer,0,n) }
        return output.toByteArray()
    }
    fun png(doc: CanvasDocument, background: Bitmap?, bounded: Boolean): ByteArray {
        val bitmap=CanvasRenderer.bitmap(doc,background)
        return try {
            if(bounded) Base64.decode(boundedEncode(bitmap,Bitmap.CompressFormat.PNG,650000),Base64.NO_WRAP)
            else ByteArrayOutputStream().use { require(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)); it.toByteArray() }
        } finally { bitmap.recycle() }
    }
    private fun boundedEncode(original: Bitmap, format: Bitmap.CompressFormat, limit: Int): String {
        var bitmap=original
        try {
            repeat(9) {
                val output=ByteArrayOutputStream(); require(bitmap.compress(format,if(format==Bitmap.CompressFormat.PNG)100 else 85,output))
                val encoded=Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP)
                if(encoded.length <= limit) return encoded
                require(bitmap.width > 256 && bitmap.height > 192) { "Изображение слишком сложное для отправки" }
                val smaller=Bitmap.createScaledBitmap(bitmap,(bitmap.width*.75f).toInt(),(bitmap.height*.75f).toInt(),true)
                if(bitmap!==original) bitmap.recycle(); bitmap=smaller
            }
            error("Изображение слишком большое")
        } finally { if(bitmap!==original) bitmap.recycle() }
    }
}
