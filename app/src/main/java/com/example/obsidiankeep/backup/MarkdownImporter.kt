package com.example.obsidiankeep.backup

object MarkdownImporter {

    data class ParsedNote(val title: String, val content: String, val color: Int?)

    private val FRONTMATTER_REGEX = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n?(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val TITLE_REGEX = Regex("""(?m)^title:\s*"?([^"\n]+)"?""")
    private val COLOR_REGEX = Regex("""(?m)^color:\s*"?(-?\d+)"?""")
    private val HEADING_REGEX = Regex("""(?m)^#\s+(.+)""")

    fun parse(markdown: String, fallbackTitle: String = "Import"): ParsedNote {
        val match = FRONTMATTER_REGEX.find(markdown)
        if (match != null) {
            val frontmatter = match.groupValues[1]
            val body = match.groupValues[2]
            val fmTitle = TITLE_REGEX.find(frontmatter)
                ?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() }
            val color = COLOR_REGEX.find(frontmatter)?.groupValues?.get(1)?.toIntOrNull()
            val title = fmTitle
                ?: HEADING_REGEX.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                ?: fallbackTitle
            return ParsedNote(title, body.trimEnd(), color)
        }
        val headingTitle = HEADING_REGEX.find(markdown)?.groupValues?.get(1)?.trim()
        val title = headingTitle ?: fallbackTitle
        return ParsedNote(title, markdown, null)
    }
}
