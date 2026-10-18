package io.github.greebo72.oloop.core

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

/**
 * Wraps a slow [OloopFileSystem] (the app's SAF layer, where every access is a binder call and
 * a cloud provider answers over the network) and mirrors every file it passes through into a
 * local directory.
 *
 * The mirror is a plain oloop data root: [JavaFileSystem] over [cacheRoot] serves a full
 * `loadAll()` from local disk, which lets the app paint the last known state instantly on a
 * cold start while the live load runs in the background.
 *
 * The mirror is best-effort: a failing cache write never fails a live operation. Callers keep
 * it honest by [clearCache] before a full refresh, so a task deleted on another device does not
 * linger as a ghost. A refresh that dies halfway leaves a partial mirror — the next cold start
 * shows that, then the next refresh fixes it.
 */
class CachingFileSystem(
    private val delegate: OloopFileSystem,
    private val cacheRoot: Path,
) : OloopFileSystem {

    override fun exists(path: String) = delegate.exists(path)

    override fun listDirectories(path: String) = delegate.listDirectories(path)

    override fun listFiles(path: String) = delegate.listFiles(path)

    override fun read(path: String): String =
        delegate.read(path).also { mirror(path, it) }

    override fun write(path: String, content: String) {
        delegate.write(path, content)
        mirror(path, content)
    }

    override fun createDirectory(path: String) {
        delegate.createDirectory(path)
        try {
            Files.createDirectories(cacheRoot.resolve(path))
        } catch (e: IOException) {
            // Best effort: a live createDirectory succeeded, the mirror may lag.
        }
    }

    /** Removes the whole mirror. Called before a full refresh, after any snapshot was read. */
    fun clearCache() {
        if (!Files.exists(cacheRoot)) return
        Files.walk(cacheRoot).use { s ->
            s.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
        Files.createDirectories(cacheRoot)
    }

    private fun mirror(path: String, content: String) {
        try {
            val target = cacheRoot.resolve(path)
            Files.createDirectories(target.parent)
            Files.write(target, content.toByteArray(StandardCharsets.UTF_8))
        } catch (e: IOException) {
            // Best effort: the live read or write is what counts.
        }
    }
}

/** True when the mirror holds at least one project folder. */
fun hasUsableCache(cacheRoot: Path): Boolean =
    Files.isDirectory(cacheRoot) && JavaFileSystem(cacheRoot).listDirectories("").isNotEmpty()
