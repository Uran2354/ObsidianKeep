package com.example.obsidiankeep.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.obsidiankeep.R
import com.example.obsidiankeep.data.Note
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun export(note: Note, outputStream: OutputStream) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = 0xFF1A1A1A.toInt()
            textSize = 22f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = 0xFF333333.toInt()
            textSize = 14f
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
        val mutedPaint = Paint().apply {
            color = 0xFF888888.toInt()
            textSize = 11f
            isAntiAlias = true
        }

        val margin = 40f
        var y = 60f

        canvas.drawText(note.title.ifEmpty { context.getString(R.string.editor_no_title) }, margin, y, titlePaint)
        y += 16
        canvas.drawText("ObsidianKeep", margin, y, mutedPaint)
        y += 30

        val maxWidth = pageInfo.pageWidth - 2 * margin
        val lines = wrapText(note.content, bodyPaint, maxWidth)
        val pageHeight = pageInfo.pageHeight
        for (line in lines) {
            if (y > pageHeight - 60) {
                doc.finishPage(page)
                val nextInfo = PdfDocument.PageInfo.Builder(595, 842, doc.pages.size + 1).create()
                val nextPage = doc.startPage(nextInfo)
                drawContent(note, nextPage, lines, titlePaint, bodyPaint, mutedPaint, margin)
                doc.finishPage(nextPage)
                break
            }
            canvas.drawText(line, margin, y, bodyPaint)
            y += 20
        }

        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }

    private fun drawContent(
        note: Note,
        page: PdfDocument.Page,
        lines: List<String>,
        titlePaint: Paint,
        bodyPaint: Paint,
        mutedPaint: Paint,
        margin: Float
    ) {
        val canvas = page.canvas
        var y = 60f
        canvas.drawText(note.title.ifEmpty { context.getString(R.string.editor_no_title) } + context.getString(R.string.pdf_continuation), margin, y, mutedPaint)
        y += 30
        for (line in lines) {
            canvas.drawText(line, margin, y, bodyPaint)
            y += 20
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) { result.add(""); return@forEach }
            val words = paragraph.split(' ')
            val current = StringBuilder()
            for (word in words) {
                val test = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(test) > maxWidth) {
                    if (current.isNotEmpty()) result.add(current.toString())
                    current.clear()
                    current.append(word)
                } else {
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(word)
                }
            }
            if (current.isNotEmpty()) result.add(current.toString())
        }
        return result
    }
}
