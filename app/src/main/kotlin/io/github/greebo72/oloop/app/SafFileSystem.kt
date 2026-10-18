package io.github.greebo72.oloop.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.github.greebo72.oloop.core.JavaFileSystem
import io.github.greebo72.oloop.core.OloopFileSystem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * [OloopFileSystem] on top of the Storage Access Framework, for a folder the user picked.
 * Android gives no file paths for such folders, so every access goes through document URIs.
 *
 * Every query to a document provider is a binder call, and cloud providers such as Google
 * Drive answer over the network, so listing a directory can take seconds. Two rules keep
 * that from dominating the load time and the UX:
 *
 * - The children of a directory are queried at most once per instance and cached. Loading
 *   all tasks of a project costs one directory query instead of one path walk per file.
 *   Instances are short-lived (one reload cycle creates one), and every write invalidates
 *   the cache, so a read never sees stale content.
 * - A query the provider does not answer throws [IOException] instead of reporting an
 *   empty folder. With Drive, a folder that is not synced yet and a folder that is truly
 *   empty must not look the same. A folder missing from the path is still an empty list,
 *   same as [JavaFileSystem]: a project without a `tasks/` folder has no tasks.
 */
class SafFileSystem(private val context: Context, private val treeUri: Uri) : OloopFileSystem {

    private val root: DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("Cannot open the data folder")

    private class Entry(val uri: Uri, val documentId: String, val isDirectory: Boolean)

    /**
     * A directory and its children. [documentId] is kept because deriving it from a URI only
     * works for document URIs, not for the tree URI of the root.
     */
    private class Dir(val file: DocumentFile, val documentId: String) {
        @Volatile var children: Map<String, Entry>? = null
    }

    private val cache = ConcurrentHashMap<String, Dir>()

    private fun rootDir(): Dir =
        cache.getOrPut("") { Dir(root, DocumentsContract.getTreeDocumentId(treeUri)) }

    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }

    /** The directory at [path], or null when it does not exist. */
    private fun resolveDir(path: String): Dir? {
        if (path.isEmpty()) return rootDir()
        cache[path]?.let { return it }
        val split = path.lastIndexOf('/')
        val parentPath = if (split < 0) "" else path.substring(0, split)
        val name = if (split < 0) path else path.substring(split + 1)
        val parent = resolveDir(parentPath) ?: return null
        val entry = parent.children()[name] ?: return null
        if (!entry.isDirectory) return null
        val file = DocumentFile.fromTreeUri(context, entry.uri) ?: throw IOException("Cannot open folder $name")
        return Dir(file, entry.documentId).also { cache[path] = it }
    }

    /** The file or directory at [path], or null when it does not exist. */
    private fun entry(path: String): Entry? {
        if (path.isEmpty()) return Entry(root.uri, DocumentsContract.getTreeDocumentId(treeUri), true)
        val split = path.lastIndexOf('/')
        val parentPath = if (split < 0) "" else path.substring(0, split)
        val name = if (split < 0) path else path.substring(split + 1)
        val parent = resolveDir(parentPath) ?: return null
        return parent.children()[name]
    }

    /** Children of this directory by display name, queried once. Throws on a silent provider. */
    private fun Dir.children(): Map<String, Entry> {
        children?.let { return it }
        synchronized(this) {
            children?.let { return it }
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val cursor = context.contentResolver.query(uri, columns, null, null, null)
                ?: throw IOException("The folder provider gave no answer for ${file.name ?: "a folder"}. Try again.")
            val result = mutableMapOf<String, Entry>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val isDir = DocumentsContract.Document.MIME_TYPE_DIR == c.getString(2)
                    result[name] = Entry(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), id, isDir)
                }
            }
            children = result
            return result
        }
    }

    private fun Entry.dir(pathHint: String): Dir {
        val file = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Cannot open $pathHint")
        return Dir(file, documentId)
    }

    override fun exists(path: String): Boolean = entry(path) != null

    override fun listDirectories(path: String): List<String> =
        resolveDir(path)?.children()?.filterValues { it.isDirectory }?.keys?.sorted() ?: emptyList()

    override fun listFiles(path: String): List<String> =
        resolveDir(path)?.children()?.filterValues { !it.isDirectory }?.keys?.sorted() ?: emptyList()

    override fun read(path: String): String {
        val file = entry(path)?.takeIf { !it.isDirectory } ?: throw IOException("File not found: $path")
        val stream = context.contentResolver.openInputStream(file.uri) ?: throw IOException("Cannot open $path")
        return stream.use { String(it.readBytes(), Charsets.UTF_8) }
    }

    /**
     * Not atomic: the Storage Access Framework has no replace-by-rename. An existing file is
     * truncated and rewritten. The repository copies the old content to `.backup/` first.
     */
    override fun write(path: String, content: String) {
        val parts = segments(path)
        val name = parts.last()
        val dir = createDirectories(parts.dropLast(1).joinToString("/"))
        val file = dir.children()[name]
            ?.let { DocumentFile.fromTreeUri(context, it.uri) ?: throw IOException("Cannot open $name") }
            ?: createFile(dir.file, name)
        val stream = context.contentResolver.openOutputStream(file.uri, "wt")
            ?: throw IOException("Cannot write $path")
        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        invalidate()
    }

    override fun createDirectory(path: String) {
        createDirectories(path)
        invalidate()
    }

    /** The directory at [path], creating every missing level. */
    private fun createDirectories(path: String): Dir {
        var current = rootDir()
        var currentPath = ""
        for (name in segments(path)) {
            currentPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
            val childPath = currentPath
            val existing = current.children()[name]
            current = if (existing?.isDirectory == true) {
                cache.getOrPut(childPath) { existing.dir(name) }
            } else {
                val created = current.file.createDirectory(name) ?: throw IOException("Cannot create folder $name")
                Dir(created, DocumentsContract.getDocumentId(created.uri)).also { cache[childPath] = it }
            }
        }
        return current
    }

    private fun createFile(dir: DocumentFile, name: String): DocumentFile {
        val created = dir.createFile("text/markdown", name) ?: throw IOException("Cannot create $name")
        // Some providers add or change an extension. The file name is part of the format.
        if (created.name != name && !created.renameTo(name)) {
            created.delete()
            throw IOException("The folder provider renamed $name to ${created.name}")
        }
        return dir.findFile(name) ?: throw IOException("Cannot find $name after creating it")
    }

    /** Folder content changed; every cached listing may be stale. */
    private fun invalidate() {
        cache.values.forEach { it.children = null }
    }
}
