package io.github.greebo72.oloop.core

/** File format version this library reads and writes. Newer files are refused. */
const val FORMAT_VERSION = 1

enum class TaskType(val value: String, val closedLabel: String) {
    TODO("todo", "Done"),
    QUESTION("question", "Answered"),
    DECISION("decision", "Decided"),
    FEATURE("feature", "Implemented"),
    BUG("bug", "Fixed");

    companion object {
        fun fromValue(value: String): TaskType? = entries.firstOrNull { it.value == value }
    }
}

enum class TaskStatus(val value: String) {
    OPEN("open"),
    CLOSED("closed");

    companion object {
        fun fromValue(value: String): TaskStatus? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Frontmatter fields this client does not know, in the order of the file. They are written back
 * unchanged, so an older client never erases a field a newer one added (SPEC.md, "Unknown fields").
 * The values are what the YAML parser produced: strings, numbers, booleans, null, lists, maps.
 */
typealias ExtraFields = Map<String, Any?>

data class Comment(
    val body: String,
    val createdAt: String,
    val extraFields: ExtraFields = emptyMap(),
)

/** A project. [folder] is the directory name below the data root, it is not stored in the file. */
data class Project(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val createdAt: String,
    val referencePath: String? = null,
    val referenceHost: String? = null,
    val folder: String,
    val extraFields: ExtraFields = emptyMap(),
)

data class Task(
    val formatVersion: Int,
    val id: String,
    val projectId: String,
    val type: TaskType,
    val title: String,
    val status: TaskStatus,
    val imagePath: String?,
    val labels: List<String>,
    val createdAt: String,
    val createdBy: String,
    val updatedAt: String,
    val updatedBy: String,
    val comments: List<Comment>,
    /** Markdown body of the file. */
    val description: String,
    /**
     * The calendar date the task should be done by, `YYYY-MM-DD`, no time and no time zone. Null when
     * there is none: the field is absent or null in the file. It does not depend on [status].
     */
    val dueDate: String? = null,
    val extraFields: ExtraFields = emptyMap(),
)

/** A file that violates the oloop format. The message names the field or rule. */
class FormatException(message: String) : RuntimeException(message)
