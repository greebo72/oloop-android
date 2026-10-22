package io.github.greebo72.oloop.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules behind the due date in the app: picker values, overdue, grouping, editing. */
class DueDateUiRulesTest {
    // 2026-03-11 is a Wednesday.
    private val now = Instant.parse("2026-03-11T12:00:00Z")
    private val today = LocalDate.of(2026, 3, 11)

    private fun task(id: String, due: String?, status: TaskStatus = TaskStatus.OPEN) = Task(
        1, id, "p", TaskType.TODO, id, status, null, emptyList(),
        "2026-01-01T00:00:00Z", "a", "2026-01-01T00:00:00Z", "a", emptyList(), "", dueDate = due,
    )

    @Test
    fun thePickerValueIsReadInUtcSoTheDayDoesNotMove() {
        // 2026-03-09 at midnight UTC. In a zone west of Greenwich the same instant is still 03-08.
        val millis = LocalDate.of(2026, 3, 9).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("2026-03-09", DueDates.fromPickerMillis(millis))
        assertEquals(millis, DueDates.toPickerMillis("2026-03-09"))
        assertEquals("2026-03-09", DueDates.fromPickerMillis(DueDates.toPickerMillis("2026-03-09")!!))
        // Late in the day still means the same calendar day.
        assertEquals("2026-03-09", DueDates.fromPickerMillis(millis + 23 * 3_600_000))
        assertNull(DueDates.toPickerMillis(null))
    }

    @Test
    fun overdueTasksAreCountedPerList() {
        val tasks = listOf(
            task("a", "2026-03-10"),
            task("b", "2020-01-01"),
            task("c", "2026-03-11"),
            task("d", null),
            task("e", "2020-01-01", TaskStatus.CLOSED),
        )
        assertEquals(2, DueDates.overdueCount(tasks, today))
        assertEquals(0, DueDates.overdueCount(emptyList(), today))
    }

    @Test
    fun theDateIsShownInTheFormatOfTheDevice() {
        assertEquals("Mar 9, 2026", DueDates.display("2026-03-09", Locale.US))
        assertEquals("09.03.2026", DueDates.display("2026-03-09", Locale.GERMANY))
        assertNull(DueDates.display(null))
        assertNull(DueDates.display("nonsense"))
    }

    @Test
    fun anOpenTaskBeforeTodayIsOverdueAndAClosedOneNever() {
        assertTrue(DueDates.isOverdue(task("a", "2026-03-10"), today))
        assertFalse(DueDates.isOverdue(task("b", "2026-03-11"), today))
        assertFalse(DueDates.isOverdue(task("c", "2026-03-12"), today))
        assertFalse(DueDates.isOverdue(task("d", null), today))
        assertFalse(DueDates.isOverdue(task("e", "2020-01-01", TaskStatus.CLOSED), today))
    }

    @Test
    fun theDueDateGroupingOrdersTheGroupsAndPutsTasksWithoutADateLast() {
        val tasks = listOf(
            task("none", null),
            task("later", "2026-12-01"),
            task("late-b", "2026-03-05"),
            task("late-a", "2026-03-01"),
            task("today", "2026-03-11"),
            task("friday", "2026-03-13"),
            task("old-closed", "2026-01-01", TaskStatus.CLOSED),
        )

        val groups = Grouping.group(tasks, GroupingMode.DUE_DATE, now, ZoneOffset.UTC, Locale.GERMANY)

        assertEquals(
            listOf(
                "Overdue" to listOf("late-a", "late-b"),
                "Today" to listOf("today"),
                "This week" to listOf("friday"),
                "Later" to listOf("later"),
                "Earlier" to listOf("old-closed"),
                "No due date" to listOf("none"),
            ),
            groups.map { it.title to it.tasks.map(Task::id) },
        )
    }

    @Test
    fun theWeekEndsOnTheLastDayOfTheWeekOfTheLocale() {
        val sunday = task("sunday", "2026-03-15")
        // Monday first (Germany): Sunday 15th is the last day of this week.
        assertEquals("This week", Grouping.group(listOf(sunday), GroupingMode.DUE_DATE, now, ZoneOffset.UTC, Locale.GERMANY).single().title)
        // Sunday first (US): the week ends on Saturday 14th, so Sunday 15th is later.
        assertEquals("Later", Grouping.group(listOf(sunday), GroupingMode.DUE_DATE, now, ZoneOffset.UTC, Locale.US).single().title)
    }

    @Test
    fun createAndEditSetChangeAndClearTheDate() {
        val project = Project(1, "11111111-1111-4111-8111-111111111111", "P", "2026-01-01T00:00:00Z", folder = "p")
        val t0 = "2026-03-01T08:00:00Z"
        val t1 = "2026-03-02T08:00:00Z"
        val created = TaskOps.create(project, TaskType.TODO, "x", "", emptyList(), "alice", t0, dueDate = "2026-05-17")
        assertEquals("2026-05-17", created.dueDate)

        // An edit that does not mention the date keeps it.
        val kept = TaskOps.edit(created, TaskType.TODO, "y", "", emptyList(), "bob", t1)
        assertEquals("2026-05-17", kept.dueDate)

        val changed = TaskOps.edit(created, TaskType.TODO, "x", "", emptyList(), "bob", t1, dueDate = "2026-06-01")
        assertEquals("2026-06-01", changed.dueDate)
        assertEquals("bob", changed.updatedBy)
        assertEquals(t1, changed.updatedAt)

        val cleared = TaskOps.edit(created, TaskType.TODO, "x", "", emptyList(), "bob", t1, dueDate = null)
        assertNull(cleared.dueDate)
        // The field is left out of the file, not written as null.
        assertFalse("dueDate" in OloopWriter.serializeTask(cleared))
    }

    @Test
    fun anInvalidDateIsRefused() {
        val project = Project(1, "11111111-1111-4111-8111-111111111111", "P", "2026-01-01T00:00:00Z", folder = "p")
        assertFailsWith<FormatException> {
            TaskOps.create(project, TaskType.TODO, "x", "", emptyList(), "alice", dueDate = "2026-02-30")
        }
    }
}
