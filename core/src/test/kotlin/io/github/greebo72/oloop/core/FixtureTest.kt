package io.github.greebo72.oloop.core

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
        putAll(p.extraFields)
    }

    /** A task without a due date has no dueDate key, the unknown fields are keys like any other. */
    private fun taskMap(t: Task, projectName: String): Map<String, Any?> = buildMap {
        put("formatVersion", t.formatVersion)
        put("id", t.id)
        put("projectId", t.projectId)
        put("type", t.type.value)
        put("title", t.title)
        put("status", t.status.value)
        put("imagePath", t.imagePath)
        t.dueDate?.let { put("dueDate", it) }
        put("labels", t.labels)
        put("createdAt", t.createdAt)
        put("createdBy", t.createdBy)
        put("updatedAt", t.updatedAt)
        put("updatedBy", t.updatedBy)
        put("comments", t.comments.map { mapOf("body" to it.body, "createdAt" to it.createdAt) + it.extraFields })
        put("description", t.description)
        put("projectName", projectName)
        putAll(t.extraFields)
    }

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
            for (data in result.projects) {
                val text = OloopWriter.serializeProject(data.project)
                assertEquals(data.project, OloopParser.parseProject(text, data.project.folder), "round trip of ${data.project.id}")
            }
        }
    }

    /**
     * The round trip the spec asks for, through the files: read a fixture, write every task back with
     * the repository, read again, and the result equals expected.json (including dueDate and the
     * unknown fields of valid/due-date and valid/unknown-fields). The edit changes only the fields
     * the rules change.
     */
    @Test
    fun aFixtureWrittenBackThroughTheRepositoryReadsAsExpected() {
        for (name in listOf("due-date", "unknown-fields")) {
            val case = File(TestSupport.spec, "fixtures/valid/$name")
            val expected = Yaml().load<Map<String, Any?>>(File(case, "expected.json").readText())
            val root = TestSupport.copyRoot(case)
            val repo = OloopRepository(JavaFileSystem(root))
            for (data in repo.loadAll().projects) for (task in data.tasks) {
                val result = repo.saveTask(data.project, task.copy(), task.updatedAt)
                assertEquals(SaveResult.Saved, result, "$name: ${task.id}")
            }
            val (actual, unreadable) = readAll(root)
            assertEquals(emptyList(), unreadable, "$name: unreadable files")
            assertEquals(expected, actual, "valid/$name after a rewrite")
        }
    }
}
