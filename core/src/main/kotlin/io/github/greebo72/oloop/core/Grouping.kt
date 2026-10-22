package io.github.greebo72.oloop.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

enum class GroupingMode(val label: String) {
    NONE("None"),
    CREATION_DATE("Creation date"),
    MODIFICATION_DATE("Modification date"),
    TASK_TYPE("Task type"),
    TASK_STATUS("Task status"),
    DUE_DATE("Due date"),
}

data class TaskGroup(val title: String?, val tasks: List<Task>)

/** Grouping and age display for task lists. No UI code, so it is tested on the JVM. */
object Grouping {
    private val dateBuckets = listOf("Today", "Yesterday", "This week", "This month", "Older")

    /**
     * Groups [tasks] by [mode]. Inside a group the newest `updatedAt` comes first.
     * Date buckets count calendar days in [zone]: today, yesterday, 2 to 6 days ago (this week),
     * 7 to 30 days ago (this month), older. Empty groups are left out.
     */
    fun group(
        tasks: List<Task>,
        mode: GroupingMode,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<TaskGroup> {
        val sorted = tasks.sortedByDescending { instant(it.updatedAt) }
        return when (mode) {
            GroupingMode.NONE -> listOf(TaskGroup(null, sorted))
            GroupingMode.CREATION_DATE -> byDate(sorted, now, zone) { it.createdAt }
            GroupingMode.MODIFICATION_DATE -> byDate(sorted, now, zone) { it.updatedAt }
            GroupingMode.TASK_TYPE -> TaskType.entries.mapNotNull { type ->
                sorted.filter { it.type == type }.takeIf { it.isNotEmpty() }
                    ?.let { TaskGroup(type.value.replaceFirstChar(Char::uppercase), it) }
            }
            GroupingMode.DUE_DATE -> byDueDate(sorted, LocalDate.ofInstant(now, zone), locale)
            GroupingMode.TASK_STATUS -> listOf(
                TaskGroup("Open", sorted.filter { it.status == TaskStatus.OPEN }),
                TaskGroup("Closed", sorted.filter { it.status == TaskStatus.CLOSED }),
            ).filter { it.tasks.isNotEmpty() }
        }
    }

    private val dueBuckets = listOf("Overdue", "Today", "This week", "Later", "Earlier", "No due date")

    /**
     * Groups by due date: overdue, today, rest of this calendar week (first day of the week of
     * [locale]), later, and tasks without a date last. Inside a group the earliest date comes first.
     * A closed task with a past date is never overdue, it goes to "Earlier".
     */
    private fun byDueDate(sorted: List<Task>, today: LocalDate, locale: Locale): List<TaskGroup> {
        val endOfWeek = today.with(WeekFields.of(locale).dayOfWeek(), 7)
        val byBucket = sorted
            .sortedBy { DueDates.parse(it.dueDate) ?: LocalDate.MAX }
            .groupBy {
                val date = DueDates.parse(it.dueDate)
                when {
                    date == null -> "No due date"
                    date.isBefore(today) -> if (it.status == TaskStatus.OPEN) "Overdue" else "Earlier"
                    date == today -> "Today"
                    !date.isAfter(endOfWeek) -> "This week"
                    else -> "Later"
                }
            }
        return dueBuckets.mapNotNull { name -> byBucket[name]?.let { TaskGroup(name, it) } }
    }

    private fun byDate(
        sorted: List<Task>,
        now: Instant,
        zone: ZoneId,
        stamp: (Task) -> String,
    ): List<TaskGroup> {
        val today = LocalDate.ofInstant(now, zone)
        val byBucket = sorted.groupBy { bucket(ChronoUnit.DAYS.between(LocalDate.ofInstant(instant(stamp(it)), zone), today)) }
        return dateBuckets.mapNotNull { name -> byBucket[name]?.let { TaskGroup(name, it) } }
    }

    private fun bucket(daysAgo: Long): String = when {
        daysAgo <= 0 -> "Today"
        daysAgo == 1L -> "Yesterday"
        daysAgo <= 6 -> "This week"
        daysAgo <= 30 -> "This month"
        else -> "Older"
    }

    /** "just now", "5 min", "3 h", "1 day", "12 days", "4 months", "2 years". */
    fun age(timestamp: String, now: Instant = Instant.now()): String {
        val d = Duration.between(instant(timestamp), now)
        val days = d.toDays()
        return when {
            d.toMinutes() < 1 -> "just now"
            d.toHours() < 1 -> "${d.toMinutes()} min"
            days < 1 -> "${d.toHours()} h"
            days == 1L -> "1 day"
            days < 60 -> "$days days"
            days < 730 -> "${days / 30} months"
            else -> "${days / 365} years"
        }
    }

    fun instant(timestamp: String): Instant = OffsetDateTime.parse(timestamp.uppercase()).toInstant()
}
