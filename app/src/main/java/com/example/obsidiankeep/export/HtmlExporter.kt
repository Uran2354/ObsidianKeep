package com.example.obsidiankeep.export

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.obsidiankeep.data.Note
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Конвертация заметки в standalone HTML-файл.
 *
 * Markdown конвертируется в HTML вручную (без сторонних библиотек):
 *  - заголовки # → <h1>...</h1>
 *  - жирный **text** → <strong>text</strong>
 *  - курсив *text* → <em>text</em>
 *  - инлайн-код `code` → <code>code</code>
 *  - блоки кода ``` ... ``` → <pre><code>...</code></pre>
 *  - списки - / * → <ul><li>...</li></ul>
 *  - нумерованные 1. → <ol><li>...</li></ol>
 *  - чек-листы [ ] / [x] → <input type="checkbox">
 *  - цитаты > → <blockquote>...</blockquote>
 *  - ссылки [[Title]] → <a href="#title">Title</a>
 *  - горизонтальная линия --- → <hr/>
 */
@Singleton
class HtmlExporter @Inject constructor() {

    fun export(note: Note): String {
        val titleHtml = escapeHtml(note.title.ifBlank { "Untitled" })
        val bodyHtml = markdownToHtml(note.content)
        return buildString {
            append("<!DOCTYPE html>")
            append("<html lang=\"ru\">")
            append("<head>")
            append("<meta charset=\"UTF-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            append("<title>").append(titleHtml).append("</title>")
            append("<style>")
            append(DEFAULT_CSS)
            append("</style>")
            append("</head>")
            append("<body>")
            append("<h1 class=\"note-title\">").append(titleHtml).append("</h1>")
            append(bodyHtml)
            append("</body></html>")
        }
    }

    private fun markdownToHtml(markdown: String): String {
        val lines = markdown.lines()
        val out = StringBuilder()
        var inCodeBlock = false
        val codeBuffer = StringBuilder()
        var inUl = false
        var inOl = false

        fun closeUl() { if (inUl) { out.append("</ul>"); inUl = false } }
        fun closeOl() { if (inOl) { out.append("</ol>"); inOl = false } }

        for (line in lines) {
            // Блок кода
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    out.append("<pre><code>").append(escapeHtml(codeBuffer.toString())).append("</code></pre>")
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    closeUl(); closeOl()
                    inCodeBlock = true
                }
                continue
            }
            if (inCodeBlock) {
                if (codeBuffer.isNotEmpty()) codeBuffer.append("\n")
                codeBuffer.append(line)
                continue
            }

            // Горизонтальная линия
            if (line.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))) {
                closeUl(); closeOl()
                out.append("<hr/>")
                continue
            }

            // Заголовки
            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
            if (headingMatch != null) {
                closeUl(); closeOl()
                val level = headingMatch.groupValues[1].length.coerceAtMost(6)
                val text = inlineMarkdown(headingMatch.groupValues[2])
                out.append("<h$level>").append(text).append("</h$level>")
                continue
            }

            // Чек-лист
            val checkboxMatch = Regex("^[-*]\\s+\\[([ xX])]\\s+(.+)$").matchEntire(line)
            if (checkboxMatch != null) {
                closeOl()
                if (!inUl) { out.append("<ul class=\"checkbox-list\">"); inUl = true }
                val checked = checkboxMatch.groupValues[1].equals("x", ignoreCase = true)
                val attr = if (checked) "checked disabled" else "disabled"
                val text = inlineMarkdown(checkboxMatch.groupValues[2])
                out.append("<li><input type=\"checkbox\" $attr/> ").append(text).append("</li>")
                continue
            }

            // Маркированный список
            val ulMatch = Regex("^[-*]\\s+(.+)$").matchEntire(line)
            if (ulMatch != null) {
                closeOl()
                if (!inUl) { out.append("<ul>"); inUl = true }
                out.append("<li>").append(inlineMarkdown(ulMatch.groupValues[1])).append("</li>")
                continue
            }

            // Нумерованный список
            val olMatch = Regex("^\\d+\\.\\s+(.+)$").matchEntire(line)
            if (olMatch != null) {
                closeUl()
                if (!inOl) { out.append("<ol>"); inOl = true }
                out.append("<li>").append(inlineMarkdown(olMatch.groupValues[1])).append("</li>")
                continue
            }

            // Цитата
            if (line.startsWith(">")) {
                closeUl(); closeOl()
                out.append("<blockquote>").append(inlineMarkdown(line.removePrefix(">").trim())).append("</blockquote>")
                continue
            }

            // Пустая строка
            if (line.isBlank()) {
                closeUl(); closeOl()
                continue
            }

            // Обычный параграф
            closeUl(); closeOl()
            out.append("<p>").append(inlineMarkdown(line)).append("</p>")
        }

        // Незакрытый блок кода
        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            out.append("<pre><code>").append(escapeHtml(codeBuffer.toString())).append("</code></pre>")
        }
        closeUl(); closeOl()
        return out.toString()
    }

    private fun inlineMarkdown(text: String): String {
        var result = escapeHtml(text)
        // [[Ссылки]]
        result = Regex("\\[\\[(.+?)]]").replace(result) { m ->
            val title = m.groupValues[1]
            "<a href=\"#${title.replace(" ", "-")}\">$title</a>"
        }
        // **жирный**
        result = Regex("\\*\\*(.+?)\\*\\*").replace(result) { "<strong>${it.groupValues[1]}</strong>" }
        // *курсив*
        result = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)").replace(result) { "<em>${it.groupValues[1]}</em>" }
        // `код`
        result = Regex("`(.+?)`").replace(result) { "<code>${it.groupValues[1]}</code>" }
        return result
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    companion object {
        private const val DEFAULT_CSS = """
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                   max-width: 760px; margin: 24px auto; padding: 0 16px;
                   color: #1a1a1a; line-height: 1.55; }
            .note-title { border-bottom: 2px solid #BB86FC; padding-bottom: 8px; color: #1a1a1a; }
            h1, h2, h3, h4 { color: #4a148c; margin-top: 24px; }
            h1 { font-size: 26px; } h2 { font-size: 22px; } h3 { font-size: 19px; } h4 { font-size: 17px; }
            a { color: #6200ee; text-decoration: none; }
            a:hover { text-decoration: underline; }
            code { background: #f4f4f4; padding: 2px 6px; border-radius: 4px; font-family: 'Consolas', 'Monaco', monospace; }
            pre { background: #f4f4f4; padding: 12px; border-radius: 6px; overflow-x: auto; }
            pre code { background: transparent; padding: 0; }
            blockquote { border-left: 3px solid #BB86FC; margin-left: 0; padding: 4px 16px; color: #555; font-style: italic; }
            hr { border: none; border-top: 1px solid #ddd; margin: 16px 0; }
            ul, ol { padding-left: 24px; }
            .checkbox-list { list-style: none; padding-left: 0; }
            .checkbox-list li { margin: 4px 0; }
            input[type="checkbox"] { margin-right: 8px; }
        """
    }
}

/**
 * Экспорт в plain text — Markdown-разметка вырезается.
 */
@Singleton
class TxtExporter @Inject constructor() {

    fun export(note: Note): String {
        val title = note.title.ifBlank { "Untitled" }
        val body = stripMarkdown(note.content)
        return "$title\n\n$body\n"
    }

    private fun stripMarkdown(text: String): String {
        var result = text
        // Убираем заголовки
        result = Regex("^(#{1,6})\\s+").replace(result, "")
        // [[Link]] → Link
        result = Regex("\\[\\[(.+?)]]").replace(result) { it.groupValues[1] }
        // **bold** → bold
        result = Regex("\\*\\*(.+?)\\*\\*").replace(result) { it.groupValues[1] }
        // *italic* → italic
        result = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)").replace(result) { it.groupValues[1] }
        // `code` → code
        result = Regex("`(.+?)`").replace(result) { it.groupValues[1] }
        // Блоки кода — оставляем содержимое, убираем ```
        result = result.replace("```", "")
        // Чек-листы
        result = Regex("^[-*]\\s+\\[[ xX]]\\s+", RegexOption.MULTILINE).replace(result) { "[ ] " }
        // Цитаты
        result = Regex("^>\\s*", RegexOption.MULTILINE).replace(result, "")
        // Списки
        result = Regex("^[-*]\\s+", RegexOption.MULTILINE).replace(result, "• ")
        result = Regex("^\\d+\\.\\s+", RegexOption.MULTILINE).replace(result, "• ")
        return result
    }
}

/**
 * Печать заметки через системный PrintManager.
 * Загружает HTML в WebView и печатает его.
 *
 * ВАЖНО: вызывать из Activity (нужен Context с доступом к системе печати).
 */
class NotePrinter(private val context: Context) {

    fun print(note: Note, htmlExporter: HtmlExporter) {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val jobName = note.title.ifBlank { "Untitled" } + " - ObsidianKeep"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("default", "default", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager?.print(jobName, printAdapter, attrs)
            }
        }
        val html = htmlExporter.export(note)
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }
}
