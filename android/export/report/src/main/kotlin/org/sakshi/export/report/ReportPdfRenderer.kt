package org.sakshi.export.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import java.io.OutputStream

/** What a render produced. */
public data class RenderSummary(val pageCount: Int)

/** Turns a [ReportModel] into a document. Behind an interface so JVM tests can stand in for the platform. */
public interface ReportRenderer {
    /**
     * Writes the document to [output] without closing it.
     *
     * @throws IllegalArgumentException if the model has more than [ReportPdfRenderer.MAX_EVENT_BLOCKS] event blocks.
     */
    public fun render(model: ReportModel, output: OutputStream): RenderSummary
}

/**
 * A4 PDF through `android.graphics.pdf.PdfDocument`. Text is laid out with `StaticLayout`, so the platform
 * shapes complex scripts. Pages carry no images, links or embedded files; the status of each part is a word in
 * capitals and quotes have a left rule, so nothing depends on colour.
 *
 * Deviation from megaplan 20.2: this slice uses the platform default typeface with system fallback fonts for
 * Devanagari and Malayalam. Bundling Noto Sans, Noto Sans Devanagari and Noto Sans Malayalam is a follow-up,
 * and shaping of each script still needs a native-speaker visual check.
 */
public class ReportPdfRenderer : ReportRenderer {
    override fun render(model: ReportModel, output: OutputStream): RenderSummary {
        require(model.events.size <= MAX_EVENT_BLOCKS) { "A report holds at most $MAX_EVENT_BLOCKS event blocks" }
        val paginator = PdfPaginator()
        val pages = paginator.paginate(paginator.measure(ReportParagraphs.of(model)))
        val document = PdfDocument()
        try {
            val rule = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
            val footer = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = FOOTER_SIZE
                typeface = Typeface.DEFAULT
                color = Color.BLACK
            }
            pages.forEachIndexed { index, slices ->
                val info = PdfDocument.PageInfo.Builder(PageGeometry.WIDTH, PageGeometry.HEIGHT, index + 1).create()
                val page = document.startPage(info)
                slices.forEach { draw(page.canvas, it, rule) }
                val label = ReportText.fill(ReportText.PAGE_FOOTER, index + 1, pages.size, model.reportVersion)
                page.canvas.drawText(label, PageGeometry.MARGIN, PageGeometry.FOOTER_BASELINE, footer)
                document.finishPage(page)
            }
            document.writeTo(output)
            output.flush()
        } finally {
            document.close()
        }
        return RenderSummary(pages.size)
    }

    private fun draw(canvas: Canvas, slice: Slice, rule: Paint) {
        val layout = slice.item.layout
        val top = layout.getLineTop(slice.first).toFloat()
        val bottom = layout.getLineBottom(slice.end - 1).toFloat()
        val x = PageGeometry.MARGIN + slice.item.indent
        if (slice.item.paragraph.style == ParagraphStyle.QUOTE) {
            canvas.drawRect(x - RULE_GAP - RULE_WIDTH, slice.top, x - RULE_GAP, slice.top + (bottom - top), rule)
        }
        canvas.save()
        canvas.translate(x, slice.top - top)
        canvas.clipRect(0f, top, layout.width.toFloat(), bottom)
        layout.draw(canvas)
        canvas.restore()
    }

    public companion object {
        /** Upper bound on event blocks per report. */
        public const val MAX_EVENT_BLOCKS: Int = 2000

        private const val FOOTER_SIZE = 8f
        private const val RULE_WIDTH = 1.5f
        private const val RULE_GAP = 5f
    }
}
