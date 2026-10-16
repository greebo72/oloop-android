package com.oloop.app.io

import com.oloop.app.model.Project
import com.oloop.app.model.Task
import java.time.Instant
import java.time.format.DateTimeFormatter

object OloopWriter {

    fun nowUtcIso(): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(Regex("\\.\\d{3}Z$"), "Z")
    }

    private fun escapeYamlString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"$escaped\""
    }

    /**
     * Strictly writes frontmatter keys in the exact order specified by oloop:
     * formatVersion, id, projectId, type, title, status, labels, imagePath,
     * createdAt, createdBy, updatedAt, updatedBy, comments.
     */
    fun serializeTask(task: Task): String {
        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("formatVersion: ${task.formatVersion}\n")
        sb.append("id: ${escapeYamlString(task.id)}\n")
        sb.append("projectId: ${escapeYamlString(task.projectId)}\n")
        sb.append("type: ${task.type.value}\n")
        sb.append("title: ${escapeYamlString(task.title)}\n")
        sb.append("status: ${task.status.value}\n")

        // Labels
        if (task.labels.isEmpty()) {
            sb.append("labels: []\n")
        } else {
            sb.append("labels:\n")
            task.labels.forEach { label ->
                sb.append("  - ${escapeYamlString(label)}\n")
            }
        }

        // imagePath
        if (task.imagePath == null) {
            sb.append("imagePath: null\n")
        } else {
            sb.append("imagePath: ${escapeYamlString(task.imagePath)}\n")
        }

        // Timestamps & actors
        sb.append("createdAt: ${escapeYamlString(task.createdAt)}\n")
        sb.append("createdBy: ${escapeYamlString(task.createdBy)}\n")
        sb.append("updatedAt: ${escapeYamlString(task.updatedAt)}\n")
        sb.append("updatedBy: ${escapeYamlString(task.updatedBy)}\n")

        // Comments
        if (task.comments.isEmpty()) {
            sb.append("comments: []\n")
        } else {
            sb.append("comments:\n")
            task.comments.forEach { comment ->
                sb.append("  - body: ${escapeYamlString(comment.body)}\n")
                sb.append("    createdAt: ${escapeYamlString(comment.createdAt)}\n")
            }
        }

        sb.append("---\n")

        if (task.description.isNotBlank()) {
            sb.append("\n")
            sb.append(task.description.trim())
            sb.append("\n")
        }

        return sb.toString()
    }

    fun serializeProject(project: Project): String {
        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("formatVersion: ${project.formatVersion}\n")
        sb.append("id: ${escapeYamlString(project.id)}\n")
        sb.append("name: ${escapeYamlString(project.name)}\n")
        project.createdAt?.let { sb.append("createdAt: ${escapeYamlString(it)}\n") }
        project.updatedAt?.let { sb.append("updatedAt: ${escapeYamlString(it)}\n") }
        sb.append("---\n")

        if (project.description.isNotBlank()) {
            sb.append("\n")
            sb.append(project.description.trim())
            sb.append("\n")
        }

        return sb.toString()
    }
}
