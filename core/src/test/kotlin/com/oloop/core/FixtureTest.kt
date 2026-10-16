package com.oloop.core

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Runs the shared conformance fixtures of oloop-spec. */
class FixtureTest {

    private fun projectMap(p: Project): Map<String, Any?> = buildMap {
        put("formatVersion", p.formatVersion)
        put("id", p.id)
        put("name", p.name)
        put("createdAt", p.createdAt)
        p.referencePath?.let { put("referencePath", it) }
        p.referenceHost?.let { put("referenceHost", it) }
    }

    private fun taskMap(t: Task, projectName: String): Map<String, Any?> = mapOf(
        "formatVersion" to t.formatVersion,
        "id" to t.id,
        "projectId" to t.projectId,
        "type" to t.type.value,
        "title" to t.title,
        "status" to t.status.value,
        "imagePath" to t.imagePath,
        "labels" to t.labels,
        "createdAt" to t.createdAt,
        "createdBy" to t.createdBy,
        "updatedAt" to t.updatedAt,
        "updatedBy" to t.updatedBy,
        "comments" to t.comments.map { mapOf("body" to it.body, "createdAt" to it.createdAt) },
        "description" to t.description,
        "projectName" to projectName,
    )

    private fun readAll(root: Path): Pair<Map<String, Any?>, List<Unreadable>> {
        val result = OloopRepository(JavaFileSystem(root)).loadAll()
        val tasks = result.projects.flatMap { d -> d.tasks.map { taskMap(it, d.project.name) } }
            .sortedBy { it["id"] as String }
        val actual = mapOf("projects" to result.projects.map { projectMap(it.project) }, "tasks" to tasks)
        return actual to result.unreadable
    }

    @Test
    fun validFixturesProduceTheExpectedResult() {
        for (case in TestSupport.fixtureCases("valid")) {
            val expected = Yaml().load<Map<String, Any?>>(File(case, "expected.json").readText())
            val (actual, unreadable) = readAll(File(case, "root").toPath())
            assertEquals(emptyList(), unreadable, "valid/${case.name}: unreadable files")
            assertEquals(expected, actual, "valid/${case.name}")
        }
    }

    @Test
    fun invalidFixturesFailForTheStatedReason() {
        for (case in TestSupport.fixtureCases("invalid")) {
            val needle = File(case, "expected_error.txt").readText().trim()
            val (_, unreadable) = readAll(File(case, "root").toPath())
            if (unreadable.isEmpty()) fail("invalid/${case.name}: file was accepted")
            assertTrue(
                unreadable.any { needle in it.reason },
                "invalid/${case.name}: expected '$needle' in ${unreadable.map { it.reason }}",
            )
        }
    }

    @Test
    fun readingAndWritingBackChangesNoValue() {
        for (case in TestSupport.fixtureCases("valid")) {
            val root = File(case, "root").toPath()
            val result = OloopRepository(JavaFileSystem(root)).loadAll()
            for (data in result.projects) for (task in data.tasks) {
                val text = OloopWriter.serializeTask(task)
                val again = OloopParser.parseTask(text, "${task.id}.md", data.project.id)
                assertEquals(task, again, "round trip of ${task.id}")
            }
        }
    }
}
