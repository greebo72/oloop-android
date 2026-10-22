package io.github.greebo72.oloop.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** dueDate and the unknown fields: read, write, keep. */
class DueDateAndUnknownFieldsTest {
    private val project = "11111111-1111-4111-8111-111111111111"
    private val id = "33333333-3333-4333-8333-333333333333"
    private val name = "$id.md"
    private val head = "---\nformatVersion: 1\nid: $id\nprojectId: $project\ntype: todo\ntitle: t\nstatus: open\nimagePath: null\n"
    private val tail = "labels: []\ncreatedAt: 2026-03-01T08:00:00Z\ncreatedBy: a\nupdatedAt: 2026-03-01T08:00:00Z\nupdatedBy: a\n"

    private fun task(extra: String = "", dueDate: String? = null, comments: String = "") =
        OloopParser.parseTask(
            head + (dueDate?.let { "dueDate: $it\n" } ?: "") + tail + comments + extra + "---\nBody\n",
            name,
        )

    // ---------------- dueDate ----------------

    @Test
    fun aDueDateIsReadAsAPlainDate() {
        assertEquals("2026-11-01", task(dueDate = "2026-11-01").dueDate)
        assertEquals("2026-11-01", task(dueDate = "\"2026-11-01\"").dueDate)
    }

    @Test
    fun noDueDateIsAbsentOrNull() {
        assertNull(task().dueDate)
        assertNull(task(dueDate = "null").dueDate)
    }

    @Test
    fun anInvalidDueDateMakesTheFileUnreadable() {
        for (bad in listOf("2026-02-30", "2026-13-01", "01.11.2026", "2026-1-1", "\"2026-11-01T10:00:00Z\"", "5", "[2026-11-01]")) {
            val e = assertFailsWith<FormatException>(bad) { task(dueDate = bad) }
            assertTrue("dueDate" in e.message!!, bad)
        }
    }

    @Test
    fun theDueDateIsWrittenPlainAndOmittedWhenThereIsNone() {
        val with = OloopWriter.serializeTask(task(dueDate = "2026-11-01"))
        assertTrue("\ndueDate: 2026-11-01\n" in with)
        assertEquals("2026-11-01", OloopParser.parseTask(with, name).dueDate)

        val without = OloopWriter.serializeTask(task(dueDate = "null"))
        assertFalse("dueDate" in without)
    }

    @Test
    fun aClosedTaskKeepsItsDueDateAndEditsDoNotTouchIt() {
        val t = task(dueDate = "2026-11-01")
        val closed = TaskOps.setStatus(t, TaskStatus.CLOSED, "bob", "2026-03-02T08:00:00Z")
        assertEquals("2026-11-01", closed.dueDate)
        val edited = TaskOps.edit(t, TaskType.BUG, "x", "", emptyList(), "bob", "2026-03-02T08:00:00Z")
        assertEquals("2026-11-01", edited.dueDate)
    }

    // ---------------- unknown fields ----------------

    @Test
    fun unknownFieldsOfATaskKeepTheirTypeAndOrderThroughAWrite() {
        val extra = "priority: high\nscore: 42\nratio: 1.5\nflag: true\nnothing: null\n" +
            "text: \"42\"\ntags:\n- a\n- 2\nnested:\n  inner: x\n  list:\n  - 1\n  - k: v\nempty: []\nnone: {}\n"
        val t = task(extra)
        assertEquals(
            listOf("priority", "score", "ratio", "flag", "nothing", "text", "tags", "nested", "empty", "none"),
            t.extraFields.keys.toList(),
        )

        val again = OloopParser.parseTask(OloopWriter.serializeTask(t), name)
        assertEquals(t, again)
        assertEquals(42, again.extraFields["score"])
        assertEquals("42", again.extraFields["text"])
        assertEquals(1.5, again.extraFields["ratio"])
        assertEquals(true, again.extraFields["flag"])
    }

    @Test
    fun unknownFieldsOfACommentAreKept() {
        val comments = "comments:\n- body: hi\n  createdAt: 2026-03-01T09:00:00Z\n  reaction: thumbs\n  meta:\n    by: x\n"
        val t = task(comments = comments)
        assertEquals(mapOf("reaction" to "thumbs", "meta" to mapOf("by" to "x")), t.comments.single().extraFields)

        assertEquals(t, OloopParser.parseTask(OloopWriter.serializeTask(t), name))
    }

    @Test
    fun unknownFieldsOfAProjectAreKept() {
        val text = "---\nformatVersion: 1\nid: $project\nname: P\ncreatedAt: 2026-01-01T00:00:00Z\ncolor: red\norder: 3\n---\n"
        val p = OloopParser.parseProject(text, "p")
        assertEquals(mapOf("color" to "red", "order" to 3), p.extraFields)
        assertEquals(p, OloopParser.parseProject(OloopWriter.serializeProject(p), "p"))
    }

    @Test
    fun everyChangeOfTheRulesKeepsTheUnknownFields() {
        val t = task("priority: high\n")
        val now = "2026-03-02T08:00:00Z"
        for (changed in listOf(
            TaskOps.edit(t, TaskType.TODO, "n", "d", listOf("x"), "bob", now),
            TaskOps.setStatus(t, TaskStatus.CLOSED, "bob", now),
            TaskOps.addComment(t, "hi", "bob", now),
        )) {
            assertEquals(mapOf("priority" to "high"), changed.extraFields)
            val back = OloopParser.parseTask(OloopWriter.serializeTask(changed), name)
            assertEquals(mapOf("priority" to "high"), back.extraFields)
        }
    }

    @Test
    fun aKeyThatIsNotAPlainWordIsQuoted() {
        val t = task("\"two words\": 1\n\"a:b\": 2\n")
        val text = OloopWriter.serializeTask(t)
        assertEquals(t, OloopParser.parseTask(text, name))
    }
}
