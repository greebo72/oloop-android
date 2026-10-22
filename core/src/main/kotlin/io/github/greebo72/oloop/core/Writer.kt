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
        appendExtra(sb, project.extraFields, "")
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
        // Plain, like the CLI writes it. Left out when there is no date, never written as null.
        task.dueDate?.let { sb.append("dueDate: $it\n") }
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
                appendExtra(sb, it.extraFields, "  ")
            }
        }
        appendExtra(sb, task.extraFields, "")
        sb.append("---\n")
        val body = task.description.trim('\n')
        if (body.isNotBlank()) sb.append(body).append('\n')
        return sb.toString()
    }

    /** The unknown fields, after the known ones, in the order they were read. */
    private fun appendExtra(sb: StringBuilder, extra: ExtraFields, indent: String) {
        for ((key, value) in extra) appendEntry(sb, indent, key, value)
    }

    /**
     * One `key: value` entry of a mapping. Scalars keep their type (42 stays a number, "42" stays a
     * string), lists and mappings are written in block style.
     */
    private fun appendEntry(sb: StringBuilder, indent: String, key: String, value: Any?) {
        val name = if (plainKey.matches(key)) key else quote(key)
        when {
            value is Map<*, *> && value.isNotEmpty() -> {
                sb.append("$indent$name:\n")
                for ((k, v) in value) appendEntry(sb, "$indent  ", k.toString(), v)
            }
            value is Map<*, *> -> sb.append("$indent$name: {}\n")
            value is Collection<*> && value.isNotEmpty() -> {
                sb.append("$indent$name:\n")
                for (item in value) appendItem(sb, indent, item)
            }
            value is Collection<*> -> sb.append("$indent$name: []\n")
            else -> sb.append("$indent$name: ${scalar(value)}\n")
        }
    }

    /** One `- item` of a list. A mapping or list as item goes on the lines below the dash. */
    private fun appendItem(sb: StringBuilder, indent: String, item: Any?) {
        when {
            item is Map<*, *> && item.isNotEmpty() -> {
                sb.append("$indent-\n")
                for ((k, v) in item) appendEntry(sb, "$indent  ", k.toString(), v)
            }
            item is Map<*, *> -> sb.append("$indent- {}\n")
            item is Collection<*> && item.isNotEmpty() -> {
                sb.append("$indent-\n")
                for (nested in item) appendItem(sb, "$indent  ", nested)
            }
            item is Collection<*> -> sb.append("$indent- []\n")
            else -> sb.append("$indent- ${scalar(item)}\n")
        }
    }

    private val plainKey = Regex("^[A-Za-z_][A-Za-z0-9_-]*$")

    /** A scalar with its type: null, true/false and numbers plain, everything else quoted. */
    private fun scalar(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Int, is Long, is java.math.BigInteger -> value.toString()
        is Double, is Float -> {
            val d = (value as Number).toDouble()
            when {
                d.isNaN() -> ".nan"
                d.isInfinite() -> if (d > 0) ".inf" else "-.inf"
                else -> value.toString()
            }
        }
        is String -> quote(value)
        else -> quote(value.toString())
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
