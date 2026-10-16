package com.oloop.app

import com.oloop.app.io.OloopParser
import com.oloop.app.io.OloopWriter
import com.oloop.app.model.Comment
import com.oloop.app.model.Task
import com.oloop.app.model.TaskStatus
import com.oloop.app.model.TaskType
import org.junit.Assert.*
import org.junit.Test

class OloopParserTest {

    @Test
    fun testParseAllValidTypes() {
        for (type in TaskType.entries) {
            val uuid = "11111111-2222-3333-4444-555555555555"
            val raw = """
                ---
                formatVersion: 1
                id: "$uuid"
                projectId: "proj-1"
                type: ${type.value}
                title: "Valid ${type.value}"
                status: open
                labels:
                  - "test"
                imagePath: null
                createdAt: "2026-10-16T12:00:00Z"
                createdBy: "Tester"
                updatedAt: "2026-10-16T12:00:00Z"
                updatedBy: "Tester"
                comments: []
                ---

                Description for ${type.value}
            """.trimIndent()

            val res = OloopParser.parseTask(raw, "$uuid.md", "proj-1")
            assertNotNull("Failed to parse valid ${type.value}: ${res.error}", res.data)
            assertEquals(type, res.data?.type)
            assertEquals("2026-10-16T12:00:00Z", res.data?.createdAt)
        }
    }

    @Test
    fun testRoundTripUnchanged() {
        val original = Task(
            formatVersion = 1,
            id = "22222222-3333-4444-8888-999999999999",
            projectId = "proj-roundtrip",
            type = TaskType.FEATURE,
            title = "Roundtrip Test",
            status = TaskStatus.OPEN,
            labels = listOf("alpha", "beta"),
            imagePath = null,
            createdAt = "2026-10-16T10:00:00Z",
            createdBy = "Alice",
            updatedAt = "2026-10-16T11:00:00Z",
            updatedBy = "Bob",
            comments = listOf(Comment("Test comment", "2026-10-16T11:30:00Z")),
            description = "Detailed markdown body content."
        )

        val serialized = OloopWriter.serializeTask(original)
        val res = OloopParser.parseTask(serialized, "${original.id}.md", original.projectId)

        assertNotNull("Re-parsing serialized task failed: ${res.error}", res.data)
        val parsed = res.data!!

        assertEquals(original.id, parsed.id)
        assertEquals(original.projectId, parsed.projectId)
        assertEquals(original.type, parsed.type)
        assertEquals(original.title, parsed.title)
        assertEquals(original.status, parsed.status)
        assertEquals(original.labels, parsed.labels)
        assertEquals(original.createdAt, parsed.createdAt)
        assertEquals(original.updatedAt, parsed.updatedAt)
        assertEquals(original.comments.size, parsed.comments.size)
        assertEquals(original.description, parsed.description)
    }

    @Test
    fun testRefuseHigherFormatVersion() {
        val raw = """
            ---
            formatVersion: 2
            id: "33333333-3333-3333-3333-333333333333"
            projectId: "proj-1"
            type: todo
            title: "Future Format"
            status: open
            createdAt: "2026-10-16T12:00:00Z"
            createdBy: "Tester"
            updatedAt: "2026-10-16T12:00:00Z"
            updatedBy: "Tester"
            ---
        """.trimIndent()

        val res = OloopParser.parseTask(raw, "33333333-3333-3333-3333-333333333333.md", "proj-1")
        assertNull("Should reject formatVersion 2", res.data)
        assertTrue(res.error?.contains("Unsupported formatVersion") == true)
    }

    @Test
    fun testRefuseFilenameIdMismatch() {
        val raw = """
            ---
            formatVersion: 1
            id: "correct-id"
            projectId: "proj-1"
            type: todo
            title: "Mismatch test"
            status: open
            createdAt: "2026-10-16T12:00:00Z"
            createdBy: "Tester"
            updatedAt: "2026-10-16T12:00:00Z"
            updatedBy: "Tester"
            ---
        """.trimIndent()

        val res = OloopParser.parseTask(raw, "wrong-file-name.md", "proj-1")
        assertNull("Should reject filename mismatch", res.data)
        assertTrue(res.error?.contains("File name mismatch") == true)
    }

    @Test
    fun testRefuseUnknownKeys() {
        val raw = """
            ---
            formatVersion: 1
            id: "44444444-4444-4444-4444-444444444444"
            projectId: "proj-1"
            type: todo
            title: "Unknown Key"
            status: open
            priority: "high"
            createdAt: "2026-10-16T12:00:00Z"
            createdBy: "Tester"
            updatedAt: "2026-10-16T12:00:00Z"
            updatedBy: "Tester"
            ---
        """.trimIndent()

        val res = OloopParser.parseTask(raw, "44444444-4444-4444-4444-444444444444.md", "proj-1")
        assertNull("Should reject unknown frontmatter key", res.data)
        assertTrue(res.error?.contains("Unknown frontmatter key") == true)
    }
}
