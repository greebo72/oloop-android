package io.github.greebo72.oloop.core

import java.io.File
import kotlin.test.Test

/** Checks that need real folders. Both are skipped unless their environment variable is set. */
class ManualChecks {
    /** Read-only check of a real data root: OLOOP_READ_ROOT=/path ./gradlew :core:test --tests '*ManualChecks*' */
    @Test
    fun readRealRoot() {
        val dir = System.getenv("OLOOP_READ_ROOT") ?: return
        val result = OloopRepository(JavaFileSystem(File(dir).toPath())).loadAll()
        println("REAL ROOT: ${result.projects.size} projects, ${result.projects.sumOf { it.tasks.size }} tasks, unreadable=${result.unreadable}")
        kotlin.test.assertEquals(emptyList(), result.unreadable)
    }

    @Test
    fun dump() {
        val out = System.getenv("OLOOP_INTEROP_OUT") ?: return
        val root = TestSupport.copyRoot(File(TestSupport.spec, "fixtures/valid/basic"))
        File(root.toString()).copyRecursively(File(out), overwrite = true)
        val repo = OloopRepository(JavaFileSystem(File(out).toPath()))
        val project = repo.loadAll().projects.single().project
        val t = TaskOps.create(project, TaskType.DECISION, "Written by Kotlin: \"quotes\" & äöü", "Line 1\n\nLine 3", listOf("android", "2026-01-01"), "kotlin-test")
        repo.saveTask(project, TaskOps.addComment(t, "a comment", "kotlin-test"), null)
    }
}
