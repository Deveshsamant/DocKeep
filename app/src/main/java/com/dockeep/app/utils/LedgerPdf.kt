package com.dockeep.app.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.dockeep.app.R
import com.dockeep.app.database.DocumentImage
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Writes a document out as a PDF.
 *
 * Uses the platform's own [PdfDocument] rather than iText: it is present on
 * every device from API 19, adds nothing to the APK, and — unlike the iText 7
 * desktop build the project was pulling in — is actually supported on Android.
 *
 * Blocks are laid out in the order the user arranged them: each scan gets a
 * page sized to fit it with a margin, and each note is typeset on its own page
 * in the same Archivo the app uses on screen.
 */
object LedgerPdf {

    /** A4 at 72dpi, the unit PdfDocument works in. */
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 36

    /**
     * Renders [blocks] into [target].
     *
     * @return the file written, or null if there was nothing renderable.
     */
    fun write(
        context: Context,
        blocks: List<DocumentImage>,
        title: String,
        target: File
    ): File? {
        val renderable = blocks.filter { it.isText || File(it.imagePath).exists() }
        if (renderable.isEmpty()) return null

        val pdf = PdfDocument()
        try {
            var pageNumber = 1
            for (block in renderable) {
                val page = pdf.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber++).create()
                )
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                if (block.isText) {
                    drawTextPage(context, canvas, title, block.text.orEmpty())
                } else {
                    drawImagePage(canvas, File(block.imagePath))
                }
                pdf.finishPage(page)
            }

            target.parentFile?.let { if (!it.exists()) it.mkdirs() }
            FileOutputStream(target).use { pdf.writeTo(it) }
            return target
        } finally {
            pdf.close()
        }
    }

    /** Convenience for the single-scan export from a scan's overflow menu. */
    fun writeSingle(
        context: Context,
        block: DocumentImage,
        title: String,
        target: File
    ): File? = write(context, listOf(block), title, target)

    /**
     * Draws one scan centred on the page, scaled down to fit inside the
     * margins but never scaled up past its own resolution.
     */
    private fun drawImagePage(canvas: Canvas, file: File) {
        // Measure first so a large scan is subsampled instead of decoded whole.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return

        val availW = PAGE_W - MARGIN * 2
        val availH = PAGE_H - MARGIN * 2

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= availW * 3 &&
            bounds.outHeight / (sample * 2) >= availH * 3
        ) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return

        try {
            val scale = min(availW.toFloat() / bitmap.width, availH.toFloat() / bitmap.height)
            val drawW = (bitmap.width * scale).toInt()
            val drawH = (bitmap.height * scale).toInt()
            val left = (PAGE_W - drawW) / 2
            val top = (PAGE_H - drawH) / 2

            canvas.drawBitmap(
                bitmap,
                null,
                Rect(left, top, left + drawW, top + drawH),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** Typesets a note: the document name as a tracked kicker, then the body. */
    private fun drawTextPage(context: Context, canvas: Canvas, title: String, body: String) {
        val kicker = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.archivo_bold)
            textSize = 9f
            letterSpacing = 0.14f
            color = Color.parseColor("#7D7979")
        }
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.archivo_regular)
            textSize = 12f
            color = Color.parseColor("#201E1D")
        }
        val rule = Paint().apply { color = Color.parseColor("#201E1D") }

        var y = MARGIN.toFloat() + 10f
        canvas.drawText(title.uppercase(), MARGIN.toFloat(), y, kicker)

        y += 10f
        canvas.drawRect(MARGIN.toFloat(), y, (PAGE_W - MARGIN).toFloat(), y + 2f, rule)

        y += 22f
        val width = PAGE_W - MARGIN * 2
        val layout = buildLayout(body, text, width)
        canvas.save()
        canvas.translate(MARGIN.toFloat(), y)
        layout.draw(canvas)
        canvas.restore()
    }

    @Suppress("DEPRECATION")
    private fun buildLayout(body: String, paint: TextPaint, width: Int): StaticLayout =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(body, 0, body.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1.35f)
                .build()
        } else {
            StaticLayout(body, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.35f, 4f, false)
        }
}
