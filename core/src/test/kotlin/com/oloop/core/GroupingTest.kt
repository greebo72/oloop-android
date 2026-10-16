package com.oloop.core

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupingTest {
    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val utc = ZoneOffset.UTC

    private fun task(
        id: String,
        created: String,
        updated: String,
        type: TaskType = TaskType.TODO,
        status: TaskStatus = TaskStatus.OPEN,
    ) = Task(1, id, "p", type, id, status, null, emptyList(), created, "a", updated, "a", emptyList(), "")

    private val tasks = listOf(
        task("today", "2026-10-16T01:00:00Z", "2026-10-16T09:00:00Z"),
        task("yesterday", "2026-10-15T23:59:59Z", "2026-10-15T23:59:59Z", TaskType.BUG),
        task("week", "2026-10-12T10:00:00Z", "2026-10-12T10:00:00Z", TaskType.BUG, TaskStatus.CLOSED),
        task("month", "2026-10-01T10:00:00Z", "2026-09-20T10:00:00Z", TaskType.QUESTION),
        task("old", "2025-01-01T10:00:00Z", "2025-01-01T10:00:00Z", TaskType.DECISION, TaskStatus.CLOSED),
    )

    private fun titles(mode: GroupingMode) =
        Grouping.group(tasks, mode, now, utc).map { it.title to it.tasks.map(Task::id) }

    @Test
    fun noneIsOneListNewestFirst() {
        assertEquals(listOf(null to listOf("today", "yesterday", "week", "month", "old")), titles(GroupingMode.NONE))
    }

    @Test
    fun creationDateUsesCreatedAt() {
        assertEquals(
            listOf(
                "Today" to listOf("today"),
                "Yesterday" to listOf("yesterday"),
                "This week" to listOf("week"),
                "This month" to listOf("month"),
                "Older" to listOf("old"),
            ),
            titles(GroupingMode.CREATION_DATE),
        )
    }

    @Test
    fun modificationDateUsesUpdatedAtAndLeavesOutEmptyGroups() {
        val groups = Grouping.group(tasks.filter { it.id != "yesterday" }, GroupingMode.MODIFICATION_DATE, now, utc)
        assertEquals(listOf("Today", "This week", "This month", "Older"), groups.map { it.title })
    }

    @Test
    fun theLocalTimeZoneDecidesWhereMidnightIs() {
        val tokyo = ZoneOffset.ofHours(9)
        // 23:59 UTC on the 15th is already the 16th in Tokyo, so it counts as today there.
        val g = Grouping.group(listOf(tasks[1]), GroupingMode.MODIFICATION_DATE, now, tokyo)
        assertEquals("Today", g.single().title)
    }

    @Test
    fun typeGroupsFollowTheFixedOrder() {
        assertEquals(
            listOf("Todo", "Question", "Decision", "Bug"),
            Grouping.group(tasks, GroupingMode.TASK_TYPE, now, utc).map { it.title },
        )
    }

    @Test
    fun statusPutsOpenFirst() {
        assertEquals(
            listOf("Open" to listOf("today", "yesterday", "month"), "Closed" to listOf("week", "old")),
            titles(GroupingMode.TASK_STATUS),
        )
    }

    @Test
    fun ageIsReadable() {
        fun age(ts: String) = Grouping.age(ts, now)
        assertEquals("just now", age("2026-10-16T11:59:30Z"))
        assertEquals("5 min", age("2026-10-16T11:55:00Z"))
        assertEquals("3 h", age("2026-10-16T09:00:00Z"))
        assertEquals("1 day", age("2026-10-15T11:00:00Z"))
        assertEquals("12 days", age("2026-10-04T11:00:00Z"))
        assertEquals("4 months", age("2026-06-16T11:00:00Z"))
        assertEquals("2 years", age("2024-10-01T11:00:00Z"))
    }
}
