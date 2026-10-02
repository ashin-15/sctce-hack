package org.sakshi.export.report

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** Page geometry in PostScript points. */
internal object PageGeometry {
    const val WIDTH: Int = 595
    const val HEIGHT: Int = 842
    const val MARGIN: Float = 48f
    const val FOOTER_BASELINE: Float = HEIGHT - 24f
    const val CONTENT_WIDTH: Float = WIDTH - 2 * MARGIN
    const val CONTENT_TOP: Float = MARGIN
    const val CONTENT_BOTTOM: Float = HEIGHT - MARGIN - 8f
}

/** A paragraph that has been measured. */
internal class Measured(
    val paragraph: Paragraph,
    val layout: StaticLayout,
    val indent: Float,
    val before: Float,
    val after: Float,
)

/** The lines [first] until [end] of one measured paragraph, placed at [top] on a page. */
internal class Slice(val item: Measured, val first: Int, val end: Int, val top: Float) {
    val height: Float get() = (item.layout.getLineBottom(end - 1) - item.layout.getLineTop(first)).toFloat()
}

/** Measures paragraphs with the platform text layout and splits them across pages by line. */
internal class PdfPaginator {
    private val styles = mapOf(
        ParagraphStyle.TITLE to Style(paint(22f, Typeface.DEFAULT_BOLD), 0f, 0f, 8f),
        ParagraphStyle.HEADING to Style(paint(15f, Typeface.DEFAULT_BOLD), 0f, 18f, 6f),
        ParagraphStyle.SUBHEADING to Style(paint(12f, Typeface.DEFAULT_BOLD), 0f, 12f, 4f),
        ParagraphStyle.STATUS to Style(paint(8.5f, Typeface.DEFAULT_BOLD), 0f, 6f, 1f),
        ParagraphStyle.BODY to Style(paint(10f, Typeface.DEFAULT), 0f, 0f, 4f),
        ParagraphStyle.QUOTE to Style(paint(10.5f, Typeface.DEFAULT), QUOTE_INDENT, 0f, 3f),
        ParagraphStyle.MONO to Style(paint(7.5f, Typeface.MONOSPACE), 0f, 0f, 3f),
        ParagraphStyle.SMALL to Style(paint(9f, Typeface.DEFAULT), 0f, 2f, 2f),
    )

    fun measure(paragraphs: List<Paragraph>): List<Measured> = paragraphs.map { paragraph ->
        val style = styles.getValue(paragraph.style)
        val width = (PageGeometry.CONTENT_WIDTH - style.indent).toInt()
        val layout = StaticLayout.Builder.obtain(paragraph.text, 0, paragraph.text.length, style.paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        Measured(paragraph, layout, style.indent, style.before, style.after)
    }

    /** Page by page, the slices to draw. Never returns an empty list of pages. */
    fun paginate(items: List<Measured>): List<List<Slice>> {
        val pages = mutableListOf<MutableList<Slice>>(mutableListOf())
        var y = PageGeometry.CONTENT_TOP
        fun newPage() {
            pages += mutableListOf<Slice>()
            y = PageGeometry.CONTENT_TOP
        }
        items.forEachIndexed { index, item ->
            val atTop = y == PageGeometry.CONTENT_TOP
            if (!atTop) y += item.before
            if (item.paragraph.keepWithNext && !atTop) {
                val following = items.getOrNull(index + 1)
                val needed = item.layout.height + (following?.let { minOf(it.layout.height, FOLLOW_LINES * it.layout.getLineBottom(0)) } ?: 0)
                if (y + needed > PageGeometry.CONTENT_BOTTOM) {
                    newPage()
                }
            }
            var line = 0
            while (line < item.layout.lineCount) {
                val room = PageGeometry.CONTENT_BOTTOM - y
                var end = line
                while (end < item.layout.lineCount && item.layout.getLineBottom(end) - item.layout.getLineTop(line) <= room) end++
                if (end == line) {
                    if (y > PageGeometry.CONTENT_TOP) {
                        newPage()
                        continue
                    }
                    end = line + 1
                }
                val slice = Slice(item, line, end, y)
                pages.last() += slice
                y += slice.height
                line = end
                if (line < item.layout.lineCount) newPage()
            }
            y += item.after
        }
        return pages
    }

    private class Style(val paint: TextPaint, val indent: Float, val before: Float, val after: Float)

    private companion object {
        const val QUOTE_INDENT = 14f
        const val FOLLOW_LINES = 2

        fun paint(size: Float, typeface: Typeface): TextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.typeface = typeface
            color = android.graphics.Color.BLACK
        }
    }
}
