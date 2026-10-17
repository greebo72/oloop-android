package com.oloop.core

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Pure functions for the behavior rules: each returns the changed task, nothing is written. */
object TaskOps {
    /** UTC, whole seconds, with Z. Example: 2026-10-16T12:00:00Z. */
    fun nowUtc(): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

    /** Trim, drop empty entries, remove duplicates, keep order. */
    fun cleanLabels(labels: List<String>): List<String> =
        labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun create(
        project: Project,
        type: TaskType,
        title: String,
        description: String,
        labels: List<String>,
        actor: String,
        now: String = nowUtc(),
        id: String = UUID.randomUUID().toString(),
    ): Task {
        requireActor(actor)
        return Task(
            formatVersion = FORMAT_VERSION,
            id = id,
            projectId = project.id,
            type = type,
            title = title.trim(),
            status = TaskStatus.OPEN,
            imagePath = null,
            labels = cleanLabels(labels),
            createdAt = now,
            createdBy = actor,
            updatedAt = now,
            updatedBy = actor,
            comments = emptyList(),
            description = description,
        )
    }

    /** Edits type, title, description and labels. Id, createdAt and createdBy never change. */
    fun edit(
        task: Task,
        type: TaskType,
        title: String,
        description: String,
        labels: List<String>,
        actor: String,
        now: String = nowUtc(),
    ): Task {
        requireActor(actor)
        return task.copy(
            type = type,
            title = title.trim(),
            description = description,
            labels = cleanLabels(labels),
            updatedAt = now,
            updatedBy = actor,
        )
    }

    fun setStatus(task: Task, status: TaskStatus, actor: String, now: String = nowUtc()): Task {
        requireActor(actor)
        return task.copy(status = status, updatedAt = now, updatedBy = actor)
    }

    fun addComment(task: Task, body: String, actor: String, now: String = nowUtc()): Task {
        requireActor(actor)
        require(body.isNotBlank()) { "comment must not be empty" }
        return task.copy(
            comments = task.comments + Comment(body.trim(), now),
            updatedAt = now,
            updatedBy = actor,
        )
    }

    private fun requireActor(actor: String) = require(actor.isNotBlank()) { "actor name is required" }
}
