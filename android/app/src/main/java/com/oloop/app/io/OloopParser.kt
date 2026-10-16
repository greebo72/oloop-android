package com.oloop.app.io

import com.oloop.app.model.*
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.Tag

object OloopParser {

    const val CURRENT_FORMAT_VERSION = 1

    private val KNOWN_TASK_KEYS = setOf(
        "formatVersion", "id", "projectId", "type", "title", "status",
        "labels", "imagePath", "createdAt", "createdBy", "updatedAt",
        "updatedBy", "comments"
    )

    private val KNOWN_PROJECT_KEYS = setOf(
        "formatVersion", "id", "name", "createdAt", "updatedAt"
    )

    /**
     * SnakeYAML configured to NEVER convert ISO timestamps to java.util.Date.
     * All timestamps remain raw String representations.
     */
    private val stringPreservingYaml: Yaml by lazy {
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
        }
        val constructor = object : SafeConstructor(options) {
            init {
                // Override TIMESTAMP tag to treat scalars as plain Strings
                yamlConstructors[Tag.TIMESTAMP] = object : ConstructScalar() {
                    override fun construct(node: Node): Any {
                        return (node as? org.yaml.snakeyaml.nodes.ScalarNode)?.value ?: ""
                    }
                }
            }
        }
        Yaml(constructor)
    }

    data class ParseResult<T>(
        val data: T? = null,
        val error: String? = null
    )

    private fun splitFrontmatter(content: String): Pair<String, String> {
        val normalized = content.replace("\r\n", "\n")
        val regex = Regex("^---\\n([\\s\\S]*?)\\n---\\n?([\\s\\S]*)$")
        val match = regex.find(normalized)
            ?: throw IllegalArgumentException("Missing or malformed YAML frontmatter (expected '---' delimiters)")
        val frontmatter = match.groupValues[1]
        val body = match.groupValues[2].trim()
        return Pair(frontmatter, body)
    }

    fun parseProject(content: String, folderName: String): ParseResult<Project> {
        return try {
            val (frontmatter, body) = splitFrontmatter(content)
            @Suppress("UNCHECKED_CAST")
            val rawMap = stringPreservingYaml.load<Map<String, Any?>>(frontmatter)
                ?: return ParseResult(error = "project.md frontmatter is empty")

            // Check unknown keys
            for (key in rawMap.keys) {
                if (!KNOWN_PROJECT_KEYS.contains(key)) {
                    return ParseResult(error = "Unknown frontmatter key in project.md: '$key'")
                }
            }

            val formatVersion = (rawMap["formatVersion"] as? Number)?.toInt() ?: 1
            if (formatVersion > CURRENT_FORMAT_VERSION) {
                return ParseResult(error = "Unsupported formatVersion $formatVersion (app supports up to $CURRENT_FORMAT_VERSION)")
            }

            val id = rawMap["id"] as? String
                ?: return ParseResult(error = "project.md missing 'id'")
            val name = rawMap["name"] as? String
                ?: return ParseResult(error = "project.md missing 'name'")

            ParseResult(
                data = Project(
                    formatVersion = formatVersion,
                    id = id,
                    name = name,
                    folderName = folderName,
                    description = body,
                    createdAt = rawMap["createdAt"]?.toString(),
                    updatedAt = rawMap["updatedAt"]?.toString()
                )
            )
        } catch (e: Exception) {
            ParseResult(error = "Failed to parse project.md: ${e.message}")
        }
    }

    fun parseTask(content: String, fileName: String, expectedProjectId: String? = null): ParseResult<Task> {
        return try {
            val (frontmatter, body) = splitFrontmatter(content)
            @Suppress("UNCHECKED_CAST")
            val rawMap = stringPreservingYaml.load<Map<String, Any?>>(frontmatter)
                ?: return ParseResult(error = "Task frontmatter is empty")

            // 1. Unknown keys
            for (key in rawMap.keys) {
                if (!KNOWN_TASK_KEYS.contains(key)) {
                    return ParseResult(error = "Unknown frontmatter key: '$key'")
                }
            }

            // 2. formatVersion
            val formatVersion = (rawMap["formatVersion"] as? Number)?.toInt() ?: 1
            if (formatVersion > CURRENT_FORMAT_VERSION) {
                return ParseResult(error = "Unsupported formatVersion $formatVersion (app supports up to $CURRENT_FORMAT_VERSION)")
            }

            // 3. File name vs ID check
            val id = rawMap["id"] as? String
                ?: return ParseResult(error = "Missing required 'id'")
            val expectedFileName = "$id.md"
            if (fileName != expectedFileName) {
                return ParseResult(error = "File name mismatch: file is '$fileName', but id is '$id'")
            }

            // 4. Project ID check
            val projectId = rawMap["projectId"] as? String
                ?: return ParseResult(error = "Missing required 'projectId'")
            if (expectedProjectId != null && projectId != expectedProjectId) {
                return ParseResult(error = "Project ID mismatch: task projectId '$projectId' != parent project '$expectedProjectId'")
            }

            // 5. Type
            val typeStr = rawMap["type"] as? String
                ?: return ParseResult(error = "Missing required 'type'")
            val type = TaskType.fromValue(typeStr)
                ?: return ParseResult(error = "Invalid task type '$typeStr'")

            // 6. Title
            val title = rawMap["title"] as? String
            if (title.isNullOrBlank()) {
                return ParseResult(error = "Missing or empty required 'title'")
            }

            // 7. Status
            val statusStr = rawMap["status"] as? String
                ?: return ParseResult(error = "Missing required 'status'")
            val status = TaskStatus.fromValue(statusStr)
                ?: return ParseResult(error = "Invalid status '$statusStr'")

            // 8. Labels
            @Suppress("UNCHECKED_CAST")
            val rawLabels = (rawMap["labels"] as? List<*>) ?: emptyList<Any>()
            val labels = rawLabels.mapNotNull { it?.toString()?.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            // 9. imagePath
            val imagePath = rawMap["imagePath"] as? String

            // 10. Timestamps & Actors
            val createdAt = rawMap["createdAt"]?.toString()
                ?: return ParseResult(error = "Missing 'createdAt'")
            val createdBy = rawMap["createdBy"]?.toString()
                ?: return ParseResult(error = "Missing 'createdBy'")
            val updatedAt = rawMap["updatedAt"]?.toString()
                ?: return ParseResult(error = "Missing 'updatedAt'")
            val updatedBy = rawMap["updatedBy"]?.toString()
                ?: return ParseResult(error = "Missing 'updatedBy'")

            // 11. Comments
            @Suppress("UNCHECKED_CAST")
            val rawComments = (rawMap["comments"] as? List<*>) ?: emptyList<Any>()
            val comments = rawComments.mapNotNull { item ->
                (item as? Map<*, *>)?.let { map ->
                    val commentBody = map["body"]?.toString() ?: ""
                    val commentCreated = map["createdAt"]?.toString() ?: ""
                    if (commentBody.isNotBlank() && commentCreated.isNotBlank()) {
                        Comment(commentBody, commentCreated)
                    } else null
                }
            }

            ParseResult(
                data = Task(
                    formatVersion = formatVersion,
                    id = id,
                    projectId = projectId,
                    type = type,
                    title = title,
                    status = status,
                    labels = labels,
                    imagePath = imagePath,
                    createdAt = createdAt,
                    createdBy = createdBy,
                    updatedAt = updatedAt,
                    updatedBy = updatedBy,
                    comments = comments,
                    description = body
                )
            )
        } catch (e: Exception) {
            ParseResult(error = "Failed to parse task file: ${e.message}")
        }
    }
}
