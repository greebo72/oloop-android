package com.oloop.core

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

data class Comment(val body: String, val createdAt: String)

/** A project. [folder] is the directory name below the data root, it is not stored in the file. */
data class Project(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val createdAt: String,
    val referencePath: String? = null,
    val referenceHost: String? = null,
    val folder: String,
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
)

/** A file that violates the oloop format. The message names the field or rule. */
class FormatException(message: String) : RuntimeException(message)
