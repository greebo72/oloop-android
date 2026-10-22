package io.github.greebo72.oloop.core

/**
 * The media type a file is created with on a Storage Access Framework provider. It is only a
 * parameter of the create call, it is not part of the file: the name and the content are what
 * every other client sees.
 *
 * A provider may add an extension to match the type (a `.md` after a name that ends in `.bak`).
 * The file name is part of the format, so a file whose name must stay as it is gets a type that no
 * provider maps to an extension.
 */
object FileTypes {
    const val MARKDOWN = "text/markdown"
    const val BINARY = "application/octet-stream"

    /** Backup copies (`<id>.md.bak`) are no Markdown files, the others are. */
    fun mimeTypeFor(fileName: String): String =
        if (fileName.endsWith(".bak", ignoreCase = true)) BINARY else MARKDOWN
}
