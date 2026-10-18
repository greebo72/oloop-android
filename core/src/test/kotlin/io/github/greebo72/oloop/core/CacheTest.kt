package io.github.greebo72.oloop.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The local mirror behind the SAF layer: instant cold-start paint, refreshed as one snapshot. */
class CacheTest {

    private fun seed(root: java.nio.file.Path): Pair<OloopRepository, Project> {
        val repo = OloopRepository(JavaFileSystem(root))
        val created = repo.createProject("cached project")
        assertTrue(created is ProjectCreateResult.Created)
        val project = (created as ProjectCreateResult.Created).project
        val task = TaskOps.create(project, TaskType.TODO, "a task", "body", listOf("label"), "tester")
        assertTrue(repo.saveTask(project, task, null) is SaveResult.Saved)
        return repo to project
    }

    @Test
    fun `reads and writes mirror into the cache`() {
        val liveRoot = Files.createTempDirectory("oloop-live")
        val cacheRoot = Files.createTempDirectory("oloop-cache")
        seed(liveRoot)
        val caching = CachingFileSystem(JavaFileSystem(liveRoot), cacheRoot)
        assertFalse(hasUsableCache(cacheRoot))

        OloopRepository(caching).loadAll()
        assertTrue(hasUsableCache(cacheRoot))

        val snapshot = OloopRepository(JavaFileSystem(cacheRoot)).loadAll()
        assertEquals(1, snapshot.projects.size)
        assertEquals(listOf("a task"), snapshot.projects[0].tasks.map { it.title })

        val project = snapshot.projects[0].project
        val task = TaskOps.create(project, TaskType.BUG, "written through", "", emptyList(), "tester")
        assertTrue(OloopRepository(caching).saveTask(project, task, null) is SaveResult.Saved)
        val snapshot2 = OloopRepository(JavaFileSystem(cacheRoot)).loadAll()
        assertEquals(2, snapshot2.projects[0].tasks.size)
    }

    @Test
    fun `a live read error does not create a cache file`() {
        val liveRoot = Files.createTempDirectory("oloop-live")
        val cacheRoot = Files.createTempDirectory("oloop-cache")
        seed(liveRoot)
        OloopRepository(JavaFileSystem(liveRoot)).createProject("no tasks here")
        val caching = CachingFileSystem(JavaFileSystem(liveRoot), cacheRoot)
        OloopRepository(caching).loadAll()
        assertFailsWith<Exception> { caching.read("no-tasks-here/tasks/missing.md") }
        assertFalse(Files.exists(cacheRoot.resolve("no-tasks-here/tasks/missing.md")))
    }

    @Test
    fun `clearCache wipes the mirror`() {
        val liveRoot = Files.createTempDirectory("oloop-live")
        val cacheRoot = Files.createTempDirectory("oloop-cache")
        seed(liveRoot)
        val caching = CachingFileSystem(JavaFileSystem(liveRoot), cacheRoot)
        OloopRepository(caching).loadAll()
        assertTrue(hasUsableCache(cacheRoot))
        caching.clearCache()
        assertFalse(hasUsableCache(cacheRoot))
        assertTrue(Files.isDirectory(cacheRoot))
    }

    @Test
    fun `loadAll with parallelism returns the same result as serial`() {
        val liveRoot = Files.createTempDirectory("oloop-live")
        val cacheRoot = Files.createTempDirectory("oloop-cache")
        val (repo, project) = seed(liveRoot)
        repeat(10) { i ->
            val task = TaskOps.create(project, TaskType.TODO, "task $i", "", emptyList(), "tester")
            assertTrue(repo.saveTask(project, task, null) is SaveResult.Saved)
        }
        val caching = CachingFileSystem(JavaFileSystem(liveRoot), cacheRoot)
        val serial = OloopRepository(caching).loadAll()
        val parallel = OloopRepository(JavaFileSystem(liveRoot)).loadAll(parallelism = 4)
        assertEquals(
            serial.projects.map { p -> p.project.name to p.tasks.map { it.title } },
            parallel.projects.map { p -> p.project.name to p.tasks.map { it.title } },
        )
    }

    @Test
    fun `parallel loadAll keeps unreadable files out and reported`() {
        val liveRoot = Files.createTempDirectory("oloop-live")
        val (repo, project) = seed(liveRoot)
        JavaFileSystem(liveRoot).write("${project.folder}/tasks/broken.md", "not a task file")
        val result = OloopRepository(JavaFileSystem(liveRoot)).loadAll(parallelism = 4)
        assertEquals(1, result.projects.size)
        assertEquals(1, result.projects[0].tasks.size)
        assertEquals(1, result.unreadable.size)
        assertTrue(result.unreadable[0].path.endsWith("tasks/broken.md"))
    }
}
