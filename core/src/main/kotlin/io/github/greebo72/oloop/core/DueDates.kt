package io.github.greebo72.oloop.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The due date of a task: a calendar date without a time zone, stored as `YYYY-MM-DD`. It is compared
 * with the local date of today and never converted. No UI code, so it is tested on the JVM.
 */
object DueDates {
    private val pattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** The date of a stored value, or null when [value] is null or not a real `YYYY-MM-DD` day. */
    fun parse(value: String?): LocalDate? {
        if (value == null || !pattern.matches(value)) return null
        return try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** The text that is stored: the date part only, ISO, no time and no zone. */
    fun format(date: LocalDate): String = date.toString()

    /** The date as the user reads it, in the format of the device (the [locale]). */
    fun display(value: String?, locale: Locale = Locale.getDefault()): String? =
        parse(value)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    /**
     * Whether the task is overdue: open, with a date before [today]. A closed task is never overdue,
     * it only keeps its date.
     */
    fun isOverdue(task: Task, today: LocalDate = LocalDate.now()): Boolean =
        task.status == TaskStatus.OPEN && parse(task.dueDate)?.isBefore(today) == true

    /** How many of [tasks] are overdue. */
    fun overdueCount(tasks: List<Task>, today: LocalDate = LocalDate.now()): Int =
        tasks.count { isOverdue(it, today) }

    /**
     * The date of a Material 3 date picker. The picker reports the chosen day as milliseconds at
     * midnight UTC, so the day is read in UTC. Reading it in the time zone of the device would move
     * it by a day west of Greenwich.
     */
    fun fromPickerMillis(millis: Long): String =
        format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())

    /** The value to hand to the date picker for a stored date: midnight UTC of that day. */
    fun toPickerMillis(value: String?): Long? =
        parse(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    /** Throws [FormatException] unless [value] is a real `YYYY-MM-DD` day. */
    fun require(value: String): String {
        if (parse(value) == null) throw FormatException("dueDate: not a date (YYYY-MM-DD): '$value'")
        return value
    }
}
