package com.example.obsidiankeep.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.obsidiankeep.R
import com.example.obsidiankeep.data.Note
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileMirrorManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getTreeUri(): Uri? = prefs.getString(KEY_TREE_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setTreeUri(uri: Uri?) {
        prefs.edit().apply {
            if (uri == null) remove(KEY_TREE_URI) else putString(KEY_TREE_URI, uri.toString())
        }.apply()
    }

    fun isConfigured(): Boolean = isEnabled() && getTreeUri() != null

    fun mirrorNote(note: Note) {
        if (!isConfigured()) return
        if (note.isEncrypted) return
        val treeUri = getTreeUri() ?: return
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return
        val safeName = sanitize(note.title.ifEmpty { context.getString(R.string.editor_no_title) }) + ".md"
        val content = buildString {
            append("---\n")
            append("title: \"${note.title.replace("\"", "\\\"")}\"\n")
            append("color: ${note.color}\n")
            append("updated: ${note.updatedAt}\n")
            if (note.reminderAt != null) append("reminder: ${note.reminderAt}\n")
            append("---\n\n")
            append(note.content)
        }
        val existing = tree.findFile(safeName)
        val file = existing ?: tree.createFile("text/markdown", safeName) ?: return
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    fun removeMirror(title: String) {
        if (!isConfigured()) return
        val treeUri = getTreeUri() ?: return
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return
        val safeName = sanitize(title.ifEmpty { context.getString(R.string.editor_no_title) }) + ".md"
        tree.findFile(safeName)?.delete()
    }

    private fun sanitize(name: String): String =
        name.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim().ifEmpty { context.getString(R.string.editor_no_title) }

    companion object {
        private const val PREFS_NAME = "mirror_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TREE_URI = "tree_uri"
    }
}
