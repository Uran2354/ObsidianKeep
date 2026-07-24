package com.example.obsidiankeep.backup

import android.content.Context
import com.example.obsidiankeep.R
import com.example.obsidiankeep.data.Folder
import com.example.obsidiankeep.data.Note
import com.example.obsidiankeep.data.NoteDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val dao: NoteDao,
    @ApplicationContext private val context: Context
) {
    data class ImportResult(val foldersImported: Int, val notesImported: Int)

    suspend fun exportTo(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val folders = dao.getAllFoldersOnce().filter { !it.isProtected && it.deletedAt == null }
        val notes = dao.getAllNotesOnce().filter { !it.isEncrypted && it.deletedAt == null }

        ZipOutputStream(outputStream).use { zip ->
            val manifest = JSONObject()
            manifest.put("version", 1)
            manifest.put("exportedAt", System.currentTimeMillis())

            val foldersArr = JSONArray()
            folders.forEach { f ->
                foldersArr.put(JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("updatedAt", f.updatedAt)
                })
            }
            manifest.put("folders", foldersArr)

            val notesArr = JSONArray()
            notes.forEach { n ->
                val fileName = "notes/${n.id}.md"
                notesArr.put(JSONObject().apply {
                    put("id", n.id)
                    put("folderId", n.folderId ?: JSONObject.NULL)
                    put("title", n.title)
                    put("color", n.color)
                    put("updatedAt", n.updatedAt)
                    put("file", fileName)
                })
                zip.putNextEntry(ZipEntry(fileName))
                zip.write(buildMarkdown(n).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            manifest.put("notes", notesArr)

            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    suspend fun importFrom(inputStream: InputStream): ImportResult = withContext(Dispatchers.IO) {
        var manifest: JSONObject? = null
        val fileContents = mutableMapOf<String, ByteArray>()

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "manifest.json") {
                    manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                } else if (name.startsWith("notes/")) {
                    fileContents[name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val mf = manifest ?: return@withContext ImportResult(0, 0)
        val idMap = mutableMapOf<String, String>()
        var foldersImported = 0

        val foldersArr = mf.optJSONArray("folders") ?: JSONArray()
        for (i in 0 until foldersArr.length()) {
            val f = foldersArr.getJSONObject(i)
            val oldId = f.getString("id")
            val newId = UUID.randomUUID().toString()
            idMap[oldId] = newId
            dao.insertFolder(
                Folder(
                    id = newId,
                    name = f.getString("name"),
                    updatedAt = f.optLong("updatedAt", System.currentTimeMillis())
                )
            )
            foldersImported++
        }

        var notesImported = 0
        val notesArr = mf.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notesArr.length()) {
            val n = notesArr.getJSONObject(i)
            val file = n.getString("file")
            val bytes = fileContents[file] ?: continue
            val fallbackTitle = n.optString("title", context.getString(R.string.import_default_title))
            val parsed = MarkdownImporter.parse(String(bytes, Charsets.UTF_8), fallbackTitle)
            val oldFolderId = if (n.isNull("folderId")) null else n.optString("folderId", null)
            val newFolderId = oldFolderId?.let { idMap[it] }
            val newNoteId = UUID.randomUUID().toString()
            dao.saveNoteWithLinks(
                Note(
                    id = newNoteId,
                    folderId = newFolderId,
                    title = parsed.title.ifBlank { fallbackTitle },
                    content = parsed.content,
                    color = parsed.color ?: n.optInt("color", 0xFF2C2C2C.toInt()),
                    updatedAt = n.optLong("updatedAt", System.currentTimeMillis())
                )
            )
            notesImported++
        }

        ImportResult(foldersImported, notesImported)
    }

    private fun buildMarkdown(note: Note): String = buildString {
        append("---\n")
        append("title: \"${note.title.replace("\"", "\\\"")}\"\n")
        append("color: ${note.color}\n")
        append("updated: ${note.updatedAt}\n")
        append("---\n\n")
        append(note.content)
    }
}
