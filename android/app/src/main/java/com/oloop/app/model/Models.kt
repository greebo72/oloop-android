package com.oloop.app.model

import java.util.UUID

enum class TaskType(val value: String, val closedLabel: String) {
    TODO("todo", "Done"),
    QUESTION("question", "Answered"),
    DECISION("decision", "Decided"),
    FEATURE("feature", "Implemented"),
    BUG("bug", "Fixed");

    companion object {
        fun fromValue(value: String): TaskType? = entries.find { it.value == value.lowercase() }
    }
}

enum class TaskStatus(val value: String) {
    OPEN("open"),
    CLOSED("closed");

    companion object {
        fun fromValue(value: String): TaskStatus? = entries.find { it.value == value.lowercase() }
    }
}

data class Comment(
    val body: String,
    val createdAt: String // Strictly ISO 8601 UTC string (e.g., "2026-10-16T12:00:00Z")
)

data class Task(
    val formatVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val type: TaskType,
    val title: String,
    val status: TaskStatus = TaskStatus.OPEN,
    val labels: List<String> = emptyList(),
    val imagePath: String? = null,
    val createdAt: String,
    val createdBy: String,
    val updatedAt: String,
    val updatedBy: String,
    val comments: List<Comment> = emptyList(),
    val description: String = ""
)

data class Project(
    val formatVersion: Int = 1,
    val id: String,
    val name: String,
    val folderName: String,
    val description: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val openTasksCount: Int = 0,
    val totalTasksCount: Int = 0,
    val latestTaskUpdatedAt: String? = null
)

enum class GroupingMode(val label: String) {
    NONE("None"),
    CREATION_DATE("Creation date"),
    MODIFICATION_DATE("Modification date"),
    TASK_TYPE("Task type"),
    TASK_STATUS("Task status")
}

data class ConflictState(
    val task: Task,
    val diskTask: Task,
    val folderName: String
)
