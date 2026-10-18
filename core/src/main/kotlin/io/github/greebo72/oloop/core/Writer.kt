package io.github.greebo72.oloop.core

/** Writes task files in the layout of the Python CLI: fixed key order, UTF-8, LF line endings. */
object OloopWriter {
    fun serializeProject(project: Project): String {
        val sb = StringBuilder("---\n")
        sb.append("formatVersion: ${project.formatVersion}\n")
        sb.append("id: ${quote(project.id)}\n")
        sb.append("name: ${quote(project.name)}\n")
        sb.append("createdAt: ${quote(project.createdAt)}\n")
        project.referencePath?.let { sb.append("referencePath: ${quote(it)}\n") }
        project.referenceHost?.let { sb.append("referenceHost: ${quote(it)}\n") }
        sb.append("---\n")
        return sb.toString()
    }

    fun serializeTask(task: Task): String {
        val sb = StringBuilder("---\n")
        sb.append("formatVersion: ${task.formatVersion}\n")
        sb.append("id: ${quote(task.id)}\n")
        sb.append("projectId: ${quote(task.projectId)}\n")
        sb.append("type: ${task.type.value}\n")
        sb.append("title: ${quote(task.title)}\n")
        sb.append("status: ${task.status.value}\n")
        sb.append("imagePath: ${task.imagePath?.let(::quote) ?: "null"}\n")
        if (task.labels.isEmpty()) {
            sb.append("labels: []\n")
        } else {
            sb.append("labels:\n")
            task.labels.forEach { sb.append("- ${quote(it)}\n") }
        }
        sb.append("createdAt: ${quote(task.createdAt)}\n")
        sb.append("createdBy: ${quote(task.createdBy)}\n")
        sb.append("updatedAt: ${quote(task.updatedAt)}\n")
        sb.append("updatedBy: ${quote(task.updatedBy)}\n")
        if (task.comments.isEmpty()) {
            sb.append("comments: []\n")
        } else {
            sb.append("comments:\n")
            task.comments.forEach {
                sb.append("- body: ${quote(it.body)}\n")
                sb.append("  createdAt: ${quote(it.createdAt)}\n")
            }
        }
        sb.append("---\n")
        val body = task.description.trim('\n')
        if (body.isNotBlank()) sb.append(body).append('\n')
        return sb.toString()
    }

    /** YAML double-quoted scalar. Always quoted, so no value is ever read as a number or date. */
    internal fun quote(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) {
            when {
                c == '\\' -> sb.append("\\\\")
                c == '"' -> sb.append("\\\"")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' || c == '\u007f' || c == '\u0085' || c == ' ' || c == ' ' ->
                    sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
