package io.github.greebo72.oloop.core

import java.io.File
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RulesTest {
    private val t0 = "2026-02-01T10:00:00Z"
    private val t1 = "2026-02-02T11:00:00Z"

    private fun repo(): Triple<OloopRepository, Project, java.nio.file.Path> {
        val root = TestSupport.copyRoot(File(TestSupport.spec, "fixtures/valid/basic"))
        val repo = OloopRepository(JavaFileSystem(root))
        return Triple(repo, repo.loadAll().projects.single().project, root)
    }

    private fun newTask(project: Project) =
        TaskOps.create(project, TaskType.TODO, "  Title  ", "Body", listOf(" a ", "a", "", "b"), "alice", t0)

    @Test
    fun createSetsTheDocumentedFields() {
        val (_, project, _) = repo()
        val t = newTask(project)
        assertEquals("Title", t.title)
        assertEquals(listOf("a", "b"), t.labels)
        assertEquals(TaskStatus.OPEN, t.status)
        assertEquals(null, t.imagePath)
        assertEquals(t0, t.createdAt)
        assertEquals(t.createdAt, t.updatedAt)
        assertEquals("alice", t.createdBy)
        assertEquals(t.createdBy, t.updatedBy)
        assertEquals(FORMAT_VERSION, t.formatVersion)
    }

    @Test
    fun editKeepsIdentityAndMovesUpdatedFields() {
        val (_, project, _) = repo()
        val t = newTask(project)
        val e = TaskOps.edit(t, TaskType.FEATURE, "New", "New body", listOf("x"), "bob", t1)
        assertEquals(t.id, e.id)
        assertEquals(t.createdAt, e.createdAt)
        assertEquals(t.createdBy, e.createdBy)
        assertEquals(TaskType.FEATURE, e.type)
        assertEquals(t1, e.updatedAt)
        assertEquals("bob", e.updatedBy)
    }

    @Test
    fun closeReopenAndCommentUpdateTheTask() {
        val (_, project, _) = repo()
        val t = newTask(project)
        val closed = TaskOps.setStatus(t, TaskStatus.CLOSED, "bob", t1)
        assertEquals(TaskStatus.CLOSED, closed.status)
        assertEquals(t1, closed.updatedAt)
        val commented = TaskOps.addComment(closed, "  hi  ", "bob", t1)
        assertEquals(listOf(Comment("hi", t1)), commented.comments)
        assertFailsWith<IllegalArgumentException> { TaskOps.addComment(t, "   ", "bob", t1) }
    }

    @Test
    fun anActorIsRequired() {
        val (_, project, _) = repo()
        assertFailsWith<IllegalArgumentException> {
            TaskOps.create(project, TaskType.BUG, "x", "", emptyList(), "  ")
        }
    }

    @Test
    fun timestampsHaveWholeSecondsAndZ() {
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$").matches(TaskOps.nowUtc()))
    }

    @Test
    fun newTasksAreWrittenToTheTasksFolder() {
        val (repo, project, root) = repo()
        val t = newTask(project)
        assertEquals(SaveResult.Saved, repo.saveTask(project, t, null))
        val file = root.resolve("example-project/tasks/${t.id}.md")
        assertTrue(file.toFile().exists())
        assertTrue(file.readText().startsWith("---\nformatVersion: 1\n"))
        assertFalse(root.resolve("example-project/${t.id}.md").toFile().exists())
        val loaded = repo.loadAll().projects.single().tasks.single { it.id == t.id }
        assertEquals(t, loaded)
    }

    @Test
    fun savingAnExistingTaskBacksUpTheOldFile() {
        val (repo, project, root) = repo()
        val t = newTask(project)
        repo.saveTask(project, t, null)
        val before = root.resolve("example-project/tasks/${t.id}.md").readText()
        val closed = TaskOps.setStatus(t, TaskStatus.CLOSED, "bob", t1)
        assertEquals(SaveResult.Saved, repo.saveTask(project, closed, t.updatedAt))
        assertEquals(before, root.resolve("example-project/.backup/${t.id}.md.bak").readText())
    }

    @Test
    fun aChangeOnDiskIsReportedAndNotOverwritten() {
        val (repo, project, root) = repo()
        val t = newTask(project)
        repo.saveTask(project, t, null)
        // Another client changes the task.
        val other = TaskOps.addComment(t, "from elsewhere", "carol", t1)
        repo.saveTask(project, other, t.updatedAt)
        val onDisk = root.resolve("example-project/tasks/${t.id}.md").readText()

        val mine = TaskOps.setStatus(t, TaskStatus.CLOSED, "bob", "2026-02-03T00:00:00Z")
        val result = repo.saveTask(project, mine, t.updatedAt)
        assertIs<SaveResult.Conflict>(result)
        assertEquals(other, result.diskTask)
        assertEquals(onDisk, root.resolve("example-project/tasks/${t.id}.md").readText())

        assertEquals(SaveResult.Saved, repo.saveTask(project, mine, t.updatedAt, force = true))
    }

    @Test
    fun anUnreadableFileOnDiskIsNeverOverwritten() {
        val (repo, project, root) = repo()
        val t = newTask(project)
        val path = root.resolve("example-project/tasks/${t.id}.md")
        path.toFile().writeText("not a task file\n")
        val result = repo.saveTask(project, t, null, force = true)
        assertIs<SaveResult.Refused>(result)
        assertEquals("not a task file\n", path.readText())
    }

    @Test
    fun invalidTasksAreRefusedBeforeWriting() {
        val (repo, project, root) = repo()
        val t = newTask(project).copy(title = "x".repeat(301))
        assertIs<SaveResult.Refused>(repo.saveTask(project, t, null))
        assertFalse(root.resolve("example-project/tasks/${t.id}.md").toFile().exists())
    }

    @Test
    fun specialCharactersSurviveAWriteAndRead() {
        val (repo, project, _) = repo()
        val nasty = "Quote \" backslash \\ colon: # hash\ttab äöü 日本 😀 123"
        val t = TaskOps.create(project, TaskType.DECISION, nasty, "Line 1\n\nLine 3: x", listOf("true", "null", "2026-01-01"), "a b", t0)
        val withComment = TaskOps.addComment(t, "multi\nline \"comment\"", "a b", t1)
        repo.saveTask(project, withComment, null)
        val loaded = repo.loadAll().projects.single().tasks.single { it.id == t.id }
        assertEquals(withComment, loaded)
    }

    @Test
    fun timestampsStayStringsEvenWhenUnquoted() {
        val (_, project, _) = repo()
        val text = """
            ---
            id: 33333333-3333-4333-8333-333333333333
            projectId: ${project.id}
            type: todo
            title: unquoted
            status: open
            labels: []
            createdAt: 2026-03-01T08:00:00Z
            createdBy: a
            updatedAt: 2026-03-01T08:00:00Z
            updatedBy: a
            ---
        """.trimIndent() + "\n"
        val task = OloopParser.parseTask(text, "33333333-3333-4333-8333-333333333333.md", project.id)
        assertEquals("2026-03-01T08:00:00Z", task.createdAt)
        assertEquals(1, task.formatVersion)
    }

    @Test
    fun projectFilesWithReferenceFieldsAreAccepted() {
        val text = "---\nformatVersion: 1\nid: 11111111-1111-4111-8111-111111111111\nname: P\ncreatedAt: 2026-01-01T00:00:00Z\n" +
            "referencePath: /work/p\nreferenceHost: mac\n---\n"
        val p = OloopParser.parseProject(text, "p")
        assertEquals("/work/p", p.referencePath)
        assertEquals("mac", p.referenceHost)
    }

    @Test
    fun unknownKeysAndEmptyCommentsAreRefused() {
        val project = "11111111-1111-4111-8111-111111111111"
        val head = "---\nid: 33333333-3333-4333-8333-333333333333\nprojectId: $project\ntype: todo\ntitle: t\nstatus: open\n" +
            "labels: []\ncreatedAt: 2026-03-01T08:00:00Z\ncreatedBy: a\nupdatedAt: 2026-03-01T08:00:00Z\nupdatedBy: a\n"
        val name = "33333333-3333-4333-8333-333333333333.md"
        val unknown = assertFailsWith<FormatException> { OloopParser.parseTask(head + "extra: 1\n---\n", name) }
        assertTrue("extra" in unknown.message!!)
        val empty = assertFailsWith<FormatException> {
            OloopParser.parseTask(head + "comments:\n- body: \"\"\n  createdAt: 2026-03-01T08:00:00Z\n---\n", name)
        }
        assertTrue("comments/0/body" in empty.message!!)
        assertNotEquals("", empty.message)
    }
}
