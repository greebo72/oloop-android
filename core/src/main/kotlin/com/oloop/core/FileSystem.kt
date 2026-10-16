package com.oloop.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The folder access the library needs. Paths are relative to the data root and use "/".
 * Android implements this on top of the Storage Access Framework, tests use [JavaFileSystem].
 */
interface OloopFileSystem {
    fun exists(path: String): Boolean
    /** Names of the sub-directories of [path]. "" is the root. Empty if [path] does not exist. */
    fun listDirectories(path: String): List<String>
    /** Names of the files in [path]. Empty if [path] does not exist. */
    fun listFiles(path: String): List<String>
    fun read(path: String): String
    /** Creates missing directories. Replaces an existing file. */
    fun write(path: String, content: String)
}

class JavaFileSystem(private val root: Path) : OloopFileSystem {
    private fun resolve(path: String): Path = root.resolve(path)

    override fun exists(path: String) = Files.exists(resolve(path))

    override fun listDirectories(path: String): List<String> = list(path) { Files.isDirectory(it) }

    override fun listFiles(path: String): List<String> = list(path) { Files.isRegularFile(it) }

    private fun list(path: String, keep: (Path) -> Boolean): List<String> {
        val dir = resolve(path)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { s -> s.filter(keep).map { it.fileName.toString() }.sorted().toList() }
    }

    override fun read(path: String): String = String(Files.readAllBytes(resolve(path)), StandardCharsets.UTF_8)

    /** Temp file in the same directory, then an atomic move. A crash never leaves half a file. */
    override fun write(path: String, content: String) {
        val target = resolve(path)
        Files.createDirectories(target.parent)
        val tmp = Files.createTempFile(target.parent, ".${target.fileName}.", ".tmp")
        try {
            Files.write(tmp, content.toByteArray(StandardCharsets.UTF_8))
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }
}
