package io.github.greebo72.oloop.core

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Reads project.md and task files and enforces the JSON Schemas of oloop-spec plus the rules a
 * schema cannot express (file name equals id, projectId matches, newer formatVersion refused).
 * Every violation throws [FormatException] whose message names the field.
 */
object OloopParser {
    private val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val dateTime =
        Regex("^\\d{4}-\\d{2}-\\d{2}[Tt]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([Zz]|[+-]\\d{2}:\\d{2})$")

    private val projectKeys = setOf("formatVersion", "id", "name", "createdAt", "referencePath", "referenceHost")
    private val projectRequired = listOf("id", "name", "createdAt")
    private val taskKeys = setOf(
        "formatVersion", "id", "projectId", "type", "title", "status", "imagePath", "dueDate", "labels",
        "createdAt", "createdBy", "updatedAt", "updatedBy", "comments",
    )
    private val commentKeys = setOf("body", "createdAt")
    private val datePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val taskRequired = listOf(
        "id", "projectId", "type", "title", "status", "createdAt", "createdBy", "updatedAt",
        "updatedBy", "labels",
    )

    fun parseProject(text: String, folder: String): Project {
        val (data, _) = Frontmatter.split(text)
        checkFormatVersion(data)
        checkRequired(data, projectRequired)
        return Project(
            formatVersion = formatVersion(data),
            id = uuid(data, "id"),
            name = string(data, "name", 1, 200),
            createdAt = timestamp(data, "createdAt"),
            referencePath = optionalString(data, "referencePath", 1),
            referenceHost = optionalString(data, "referenceHost", 1),
            folder = folder,
            extraFields = extraFields(data, projectKeys),
        )
    }

    fun parseTask(text: String, fileName: String, expectedProjectId: String? = null): Task {
        val (data, body) = Frontmatter.split(text)
        checkFormatVersion(data)
        checkRequired(data, taskRequired)
        val id = uuid(data, "id")
        val projectId = uuid(data, "projectId")
        val typeText = string(data, "type", 1, Int.MAX_VALUE)
        val statusText = string(data, "status", 1, Int.MAX_VALUE)
        val task = Task(
            formatVersion = formatVersion(data),
            id = id,
            projectId = projectId,
            type = TaskType.fromValue(typeText)
                ?: throw FormatException("type: must be one of ${TaskType.entries.joinToString { it.value }}, got '$typeText'"),
            title = string(data, "title", 1, 300),
            status = TaskStatus.fromValue(statusText)
                ?: throw FormatException("status: must be one of open, closed, got '$statusText'"),
            imagePath = if (data["imagePath"] == null) null else string(data, "imagePath", 0, Int.MAX_VALUE),
            labels = labels(data),
            createdAt = timestamp(data, "createdAt"),
            createdBy = string(data, "createdBy", 1, Int.MAX_VALUE),
            updatedAt = timestamp(data, "updatedAt"),
            updatedBy = string(data, "updatedBy", 1, Int.MAX_VALUE),
            comments = comments(data),
            description = body,
            dueDate = dueDate(data),
            extraFields = extraFields(data, taskKeys),
        )
        if (fileName != "$id.md") throw FormatException("id $id != file name $fileName")
        if (expectedProjectId != null && projectId != expectedProjectId) {
            throw FormatException("projectId does not match project $expectedProjectId")
        }
        return task
    }

    // ---- format version -------------------------------------------------

    /** Runs before any schema check so a newer file is reported as newer, not as invalid. */
    private fun checkFormatVersion(data: Map<String, Any?>) {
        val version = data["formatVersion"]
        if (version is Int && version > FORMAT_VERSION) {
            throw FormatException("file format version $version is newer than supported ($FORMAT_VERSION)")
        }
    }

    private fun formatVersion(data: Map<String, Any?>): Int {
        if (!data.containsKey("formatVersion")) return 1
        val v = data["formatVersion"]
        if (v !is Int || v < 1) throw FormatException("formatVersion: must be an integer >= 1")
        return v
    }

    // ---- field helpers --------------------------------------------------

    private fun checkRequired(data: Map<String, Any?>, required: List<String>) {
        for (key in required) if (!data.containsKey(key)) throw FormatException("$key: required property missing")
    }

    /**
     * The fields that are not part of the format, in file order. They are accepted and not checked:
     * a newer client may add fields without a formatVersion bump (SPEC.md, "Unknown fields").
     */
    private fun extraFields(data: Map<*, *>, known: Set<String>): ExtraFields {
        val extra = LinkedHashMap<String, Any?>()
        for ((key, value) in data) if (key is String && key !in known) extra[key] = value
        return extra
    }

    /** `dueDate` is absent, null or a real calendar day written `YYYY-MM-DD`. */
    private fun dueDate(data: Map<String, Any?>): String? {
        val value = data["dueDate"] ?: return null
        val text = value as? String ?: throw FormatException("dueDate: must be a date (YYYY-MM-DD) or null")
        if (!datePattern.matches(text)) throw FormatException("dueDate: not a date (YYYY-MM-DD): '$text'")
        try {
            LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            throw FormatException("dueDate: not a date (YYYY-MM-DD): '$text'")
        }
        return text
    }

    private fun string(data: Map<String, Any?>, key: String, min: Int, max: Int): String {
        val v = data[key] as? String ?: throw FormatException("$key: must be a string")
        // JSON Schema counts code points, not UTF-16 units.
        val length = v.codePointCount(0, v.length)
        if (length < min) throw FormatException("$key: must have at least $min character(s)")
        if (length > max) throw FormatException("$key: must have at most $max characters")
        return v
    }

    private fun optionalString(data: Map<String, Any?>, key: String, min: Int): String? =
        if (data[key] == null) null else string(data, key, min, Int.MAX_VALUE)

    private fun uuid(data: Map<String, Any?>, key: String): String {
        val v = string(data, key, 1, Int.MAX_VALUE)
        if (!uuid.matches(v)) throw FormatException("$key: not a valid UUID: '$v'")
        return v
    }

    private fun timestamp(data: Map<String, Any?>, key: String): String = checkTimestamp(data[key], key)

    private fun checkTimestamp(value: Any?, key: String): String {
        val v = value as? String ?: throw FormatException("$key: must be a date-time string")
        if (!dateTime.matches(v)) throw FormatException("$key: not an RFC 3339 date-time: '$v'")
        try {
            OffsetDateTime.parse(v.uppercase())
        } catch (e: DateTimeParseException) {
            throw FormatException("$key: not an RFC 3339 date-time: '$v'")
        }
        return v
    }

    private fun labels(data: Map<String, Any?>): List<String> {
        val raw = data["labels"] as? List<*> ?: throw FormatException("labels: must be a list")
        val out = raw.mapIndexed { i, item ->
            val label = item as? String ?: throw FormatException("labels/$i: must be a string")
            val length = label.codePointCount(0, label.length)
            if (length < 1 || length > 100) throw FormatException("labels/$i: must have 1 to 100 characters")
            label
        }
        if (out.toSet().size != out.size) throw FormatException("labels: items must be unique")
        return out
    }

    private fun comments(data: Map<String, Any?>): List<Comment> {
        if (!data.containsKey("comments")) return emptyList()
        val raw = data["comments"] as? List<*> ?: throw FormatException("comments: must be a list")
        return raw.mapIndexed { i, item ->
            val map = item as? Map<*, *> ?: throw FormatException("comments/$i: must be a mapping")
            if (!map.containsKey("body")) throw FormatException("comments/$i/body: required property missing")
            val body = map["body"] as? String ?: throw FormatException("comments/$i/body: must be a string")
            if (body.isEmpty()) throw FormatException("comments/$i/body: must not be empty")
            Comment(body, checkTimestamp(map["createdAt"], "comments/$i/createdAt"), extraFields(map, commentKeys))
        }
    }
}
