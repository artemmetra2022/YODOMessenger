package app.yodo.messenger.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Генерирует красивую QR-карточку в стиле YODO:
 *  - тёмный фон с градиентом
 *  - QR-код с круглыми точками и логотипом "Y" по центру
 *  - имя пользователя / @username под кодом
 *  - брендовые цвета переданы снаружи, чтобы карточка соответствовала выбранной теме
 */
object QrCardGenerator {

    private const val CARD_W = 900
    private const val CARD_H = 1160
    private const val QR_SIZE = 580
    private const val CORNER_RADIUS = 52f

    /**
     * @param content     строка, которую кодирует QR (например, "yodo://user/abc123")
     * @param displayName отображаемое имя пользователя
     * @param username    @username (может быть null)
     * @param primaryArgb брендовый цвет темы в формате ARGB (Int)
     */
    fun generate(
        content: String,
        displayName: String,
        username: String?,
        primaryArgb: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas, primaryArgb)
        drawTopBrand(canvas, primaryArgb)
        val qrBottom = drawQr(canvas, content, primaryArgb)
        drawUserInfo(canvas, displayName, username, qrBottom, primaryArgb)
        drawFooterHint(canvas)

        return bitmap
    }

    // ────────────────────────────────────────────────────────────────────
    // Фон: тёмная карточка с тонким градиентом сверху (бренд → тёмный)
    // ────────────────────────────────────────────────────────────────────

    private fun drawBackground(canvas: Canvas, primaryArgb: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Скруглённая карточка
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(
            RectF(0f, 0f, CARD_W.toFloat(), CARD_H.toFloat()),
            CORNER_RADIUS, CORNER_RADIUS, paint
        )

        // Полупрозрачный градиент вверху — намёк на цвет темы
        paint.shader = LinearGradient(
            0f, 0f, 0f, 260f,
            intArrayOf(blendAlpha(primaryArgb, 0.18f), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(0f, 0f, CARD_W.toFloat(), 260f),
            CORNER_RADIUS, CORNER_RADIUS, paint
        )
        paint.shader = null
    }

    // ────────────────────────────────────────────────────────────────────
    // Верхняя часть: кружок с "Y" + "YODO" + "Messenger"
    // ────────────────────────────────────────────────────────────────────

    private fun drawTopBrand(canvas: Canvas, primaryArgb: Int) {
        val cx = CARD_W / 2f
        val logoR = 38f
        val logoY = 70f

        // Кружок
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryArgb }
        canvas.drawCircle(cx, logoY, logoR, circlePaint)

        // "Y"
        val yPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = logoR * 1.25f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText("Y", cx, logoY + logoR * 0.44f, yPaint)

        // "YODO"
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText("YODO", cx, logoY + logoR + 52f, titlePaint)

        // "Messenger"
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Messenger", cx, logoY + logoR + 90f, subtitlePaint)
    }

    // ────────────────────────────────────────────────────────────────────
    // QR-код: белый прямоугольник, точечные модули, "Y" в центре
    // ────────────────────────────────────────────────────────────────────

    private fun drawQr(canvas: Canvas, content: String, primaryArgb: Int): Float {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.MARGIN, 1)
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val bitMatrix = try {
            MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)
        } catch (e: Exception) {
            return 200f + QR_SIZE + 32f
        }

        val pad = 28f
        val qrLeft = (CARD_W - QR_SIZE) / 2f
        val qrTop = 200f

        // Белый фон под QR
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(
            RectF(qrLeft - pad, qrTop - pad, qrLeft + QR_SIZE + pad, qrTop + QR_SIZE + pad),
            20f, 20f, whitePaint
        )

        // Тёмные точки
        val moduleSize = QR_SIZE.toFloat() / bitMatrix.width
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }

        for (y in 0 until bitMatrix.height) {
            for (x in 0 until bitMatrix.width) {
                if (bitMatrix[x, y]) {
                    val cx = qrLeft + x * moduleSize + moduleSize / 2f
                    val cy = qrTop + y * moduleSize + moduleSize / 2f
                    canvas.drawCircle(cx, cy, moduleSize * 0.44f, dotPaint)
                }
            }
        }

        // Центральный логотип (белый кружок → чистит модули под ним)
        val cx = CARD_W / 2f
        val cy = qrTop + QR_SIZE / 2f
        val logoR = moduleSize * 4.6f
        canvas.drawCircle(cx, cy, logoR + 3f, whitePaint)  // белое кольцо-зазор
        val logoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryArgb }
        canvas.drawCircle(cx, cy, logoR, logoBgPaint)
        val yPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = logoR * 1.2f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText("Y", cx, cy + logoR * 0.43f, yPaint)

        return qrTop + QR_SIZE + pad
    }

    // ────────────────────────────────────────────────────────────────────
    // Имя и @username
    // ────────────────────────────────────────────────────────────────────

    private fun drawUserInfo(
        canvas: Canvas,
        displayName: String,
        username: String?,
        qrBottom: Float,
        primaryArgb: Int
    ) {
        val cx = CARD_W / 2f
        val nameY = qrBottom + 72f

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val safeName = displayName.take(28)
        canvas.drawText(safeName, cx, nameY, namePaint)

        if (!username.isNullOrBlank()) {
            val usernamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = blendArgb(primaryArgb, Color.parseColor("#94A3B8"), 0.4f)
                textSize = 38f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("@${username.take(20)}", cx, nameY + 52f, usernamePaint)
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Подсказка внизу
    // ────────────────────────────────────────────────────────────────────

    private fun drawFooterHint(canvas: Canvas) {
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            strokeWidth = 1.5f
        }
        canvas.drawLine(60f, CARD_H - 110f, CARD_W - 60f, CARD_H - 110f, divPaint)

        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Отсканируйте, чтобы написать мне в YODO",
            CARD_W / 2f, CARD_H - 62f, hintPaint
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // Утилиты цвета
    // ────────────────────────────────────────────────────────────────────

    /** Цвет с прозрачностью alpha (0..1) поверх чёрного. */
    private fun blendAlpha(argb: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (a shl 24) or (argb and 0x00FFFFFF)
    }

    /** Линейная интерполяция двух цветов: t=0 → c1, t=1 → c2. */
    private fun blendArgb(c1: Int, c2: Int, t: Float): Int {
        fun ch(from: Int, to: Int) = (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt()
        val r = ch(c1, c2).coerceIn(0, 255)
        val g = ((Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()).coerceIn(0, 255)
        val b = ((Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()).coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
