package com.oloop.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.oloop.core.OloopFileSystem
import java.io.IOException

/**
 * [OloopFileSystem] on top of the Storage Access Framework, for a folder the user picked.
 * Android gives no file paths for such folders, so every access goes through [DocumentFile].
 */
class SafFileSystem(private val context: Context, treeUri: Uri) : OloopFileSystem {
    private val root: DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("Cannot open the data folder")

    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }

    private fun find(path: String): DocumentFile? {
        var current: DocumentFile = root
        for (name in segments(path)) current = current.findFile(name) ?: return null
        return current
    }

    override fun exists(path: String) = find(path)?.exists() == true

    override fun listDirectories(path: String): List<String> =
        find(path)?.takeIf { it.isDirectory }?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.sorted()
            ?: emptyList()

    override fun listFiles(path: String): List<String> =
        find(path)?.takeIf { it.isDirectory }?.listFiles()?.filter { it.isFile }?.mapNotNull { it.name }?.sorted()
            ?: emptyList()

    override fun read(path: String): String {
        val file = find(path) ?: throw IOException("File not found: $path")
        val stream = context.contentResolver.openInputStream(file.uri) ?: throw IOException("Cannot open $path")
        return stream.use { String(it.readBytes(), Charsets.UTF_8) }
    }

    /**
     * Not atomic: the Storage Access Framework has no replace-by-rename. An existing file is
     * truncated and rewritten. The repository copies the old content to `.backup/` first.
     */
    override fun write(path: String, content: String) {
        val parts = segments(path)
        var dir = root
        for (name in parts.dropLast(1)) {
            dir = dir.findFile(name) ?: dir.createDirectory(name) ?: throw IOException("Cannot create folder $name")
        }
        val name = parts.last()
        val file = dir.findFile(name) ?: createFile(dir, name)
        val stream = context.contentResolver.openOutputStream(file.uri, "wt") ?: throw IOException("Cannot write $path")
        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
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
}
