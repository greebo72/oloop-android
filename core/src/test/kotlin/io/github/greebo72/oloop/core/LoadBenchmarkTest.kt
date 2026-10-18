package io.github.greebo72.oloop.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Measurement for the load-performance task: how much do parallel task reads help when every
 * read costs a round-trip, like over SAF with a cloud provider? Prints both times; the assert
 * only checks the direction, not a ratio, so CI machines never trip over scheduler noise.
 */
class LoadBenchmarkTest {

    /** Adds a fixed latency to [read], like a provider answering over the network. */
    private class SlowFileSystem(private val delegate: OloopFileSystem, private val readMillis: Long) : OloopFileSystem {
        override fun exists(path: String) = delegate.exists(path)
        override fun listDirectories(path: String) = delegate.listDirectories(path)
        override fun listFiles(path: String) = delegate.listFiles(path)
        override fun read(path: String): String {
            Thread.sleep(readMillis)
            return delegate.read(path)
        }
        override fun write(path: String, content: String) = delegate.write(path, content)
        override fun createDirectory(path: String) = delegate.createDirectory(path)
    }

    @Test
    fun `parallel loadAll is faster than serial when reads are slow`() {
        val root = Files.createTempDirectory("oloop-bench")
        val repo = OloopRepository(JavaFileSystem(root))
        val projects = (1..4).map { i ->
            when (val r = repo.createProject("bench project $i")) {
                is ProjectCreateResult.Created -> r.project
                is ProjectCreateResult.Refused -> error(r.reason)
            }
        }
        projects.forEach { project ->
            repeat(15) { i ->
                val task = TaskOps.create(project, TaskType.TODO, "bench task $i", "body", listOf("bench"), "tester")
                val saved = repo.saveTask(project, task, null)
                assertTrue(saved is SaveResult.Saved)
            }
        }
        val slow = SlowFileSystem(JavaFileSystem(root), 20)

        val serialStart = System.nanoTime()
        val serial = OloopRepository(slow).loadAll()
        val serialMillis = (System.nanoTime() - serialStart) / 1_000_000

        val parallelStart = System.nanoTime()
        val parallel = OloopRepository(slow).loadAll(parallelism = 4)
        val parallelMillis = (System.nanoTime() - parallelStart) / 1_000_000

        println("loadAll 60 tasks, 20ms per read: serial ${serialMillis}ms, parallel(4) ${parallelMillis}ms")
        assertEquals(serial.projects.map { it.tasks.map { t -> t.title } }, parallel.projects.map { it.tasks.map { t -> t.title } })
        assertEquals(serial.projects.map { it.project.name }, parallel.projects.map { it.project.name })
        assertTrue(parallelMillis < serialMillis, "parallel ($parallelMillis ms) should beat serial ($serialMillis ms)")
    }
}
