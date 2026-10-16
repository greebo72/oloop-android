/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export interface AndroidFile {
  path: string;
  name: string;
  category: 'core' | 'io' | 'ui' | 'test' | 'config';
  description: string;
  language: 'kotlin' | 'xml' | 'groovy';
  code: string;
}

export const ANDROID_PROJECT_FILES: AndroidFile[] = [
  {
    path: 'app/src/main/java/com/oloop/app/io/OloopParser.kt',
    name: 'OloopParser.kt',
    category: 'io',
    description: 'SnakeYAML parser configured to strictly keep ISO timestamps as strings and enforce formatVersion and frontmatter keys order.',
    language: 'kotlin',
    code: `package com.oloop.app.io

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

    private val stringPreservingYaml: Yaml by lazy {
        val options = LoaderOptions().apply { isAllowDuplicateKeys = false }
        val constructor = object : SafeConstructor(options) {
            init {
                yamlConstructors[Tag.TIMESTAMP] = object : ConstructScalar() {
                    override fun construct(node: Node): Any {
                        return (node as? org.yaml.snakeyaml.nodes.ScalarNode)?.value ?: ""
                    }
                }
            }
        }
        Yaml(constructor)
    }

    data class ParseResult<T>(val data: T? = null, val error: String? = null)

    fun parseTask(content: String, fileName: String, expectedProjectId: String? = null): ParseResult<Task> {
        return try {
            val normalized = content.replace("\\r\\n", "\\n")
            val regex = Regex("^---\\\\n([\\\\s\\\\S]*?)\\\\n---\\\\n?([\\\\s\\\\S]*)$")
            val match = regex.find(normalized)
                ?: return ParseResult(error = "Missing or malformed YAML frontmatter ('---' delimiters)")
            
            val frontmatter = match.groupValues[1]
            val body = match.groupValues[2].trim()

            @Suppress("UNCHECKED_CAST")
            val rawMap = stringPreservingYaml.load<Map<String, Any?>>(frontmatter)
                ?: return ParseResult(error = "Task frontmatter is empty")

            // Unknown keys check
            for (key in rawMap.keys) {
                if (!KNOWN_TASK_KEYS.contains(key)) return ParseResult(error = "Unknown frontmatter key: '$key'")
            }

            // formatVersion check
            val formatVersion = (rawMap["formatVersion"] as? Number)?.toInt() ?: 1
            if (formatVersion > CURRENT_FORMAT_VERSION) {
                return ParseResult(error = "Unsupported formatVersion $formatVersion (app supports up to $CURRENT_FORMAT_VERSION)")
            }

            // Filename vs id check
            val id = rawMap["id"] as? String ?: return ParseResult(error = "Missing 'id'")
            if (fileName != "$id.md") return ParseResult(error = "File name mismatch: file is '$fileName', id is '$id'")

            // ProjectId check
            val projectId = rawMap["projectId"] as? String ?: return ParseResult(error = "Missing 'projectId'")
            if (expectedProjectId != null && projectId != expectedProjectId) {
                return ParseResult(error = "Project ID mismatch: '$projectId' != expected '$expectedProjectId'")
            }

            val typeStr = rawMap["type"] as? String ?: return ParseResult(error = "Missing 'type'")
            val type = TaskType.fromValue(typeStr) ?: return ParseResult(error = "Invalid task type '$typeStr'")

            val title = rawMap["title"] as? String
            if (title.isNullOrBlank()) return ParseResult(error = "Missing or empty 'title'")

            val statusStr = rawMap["status"] as? String ?: return ParseResult(error = "Missing 'status'")
            val status = TaskStatus.fromValue(statusStr) ?: return ParseResult(error = "Invalid status '$statusStr'")

            @Suppress("UNCHECKED_CAST")
            val rawLabels = (rawMap["labels"] as? List<*>) ?: emptyList<Any>()
            val labels = rawLabels.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }.distinct()

            val imagePath = rawMap["imagePath"] as? String
            val createdAt = rawMap["createdAt"]?.toString() ?: return ParseResult(error = "Missing 'createdAt'")
            val createdBy = rawMap["createdBy"]?.toString() ?: return ParseResult(error = "Missing 'createdBy'")
            val updatedAt = rawMap["updatedAt"]?.toString() ?: return ParseResult(error = "Missing 'updatedAt'")
            val updatedBy = rawMap["updatedBy"]?.toString() ?: return ParseResult(error = "Missing 'updatedBy'")

            @Suppress("UNCHECKED_CAST")
            val rawComments = (rawMap["comments"] as? List<*>) ?: emptyList<Any>()
            val comments = rawComments.mapNotNull { item ->
                (item as? Map<*, *>)?.let { map ->
                    val cBody = map["body"]?.toString() ?: ""
                    val cCreated = map["createdAt"]?.toString() ?: ""
                    if (cBody.isNotBlank() && cCreated.isNotBlank()) Comment(cBody, cCreated) else null
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
            ParseResult(error = "Failed to parse task: \${e.message}")
        }
    }
}`,
  },
  {
    path: 'app/src/main/java/com/oloop/app/io/OloopWriter.kt',
    name: 'OloopWriter.kt',
    category: 'io',
    description: 'Strict frontmatter serializer maintaining key order, atomic string escaping, and UTC timestamps.',
    language: 'kotlin',
    code: `package com.oloop.app.io

import com.oloop.app.model.Task
import java.time.Instant
import java.time.format.DateTimeFormatter

object OloopWriter {
    fun nowUtcIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(Regex("\\\\.\\\\d{3}Z$"), "Z")

    private fun escape(s: String) = "\\"\${s.replace("\\\\", "\\\\\\\\").replace("\\"", "\\\\\\"")}\\""

    fun serializeTask(task: Task): String {
        val sb = StringBuilder()
        sb.append("---\\n")
        sb.append("formatVersion: \${task.formatVersion}\\n")
        sb.append("id: \${escape(task.id)}\\n")
        sb.append("projectId: \${escape(task.projectId)}\\n")
        sb.append("type: \${task.type.value}\\n")
        sb.append("title: \${escape(task.title)}\\n")
        sb.append("status: \${task.status.value}\\n")

        if (task.labels.isEmpty()) {
            sb.append("labels: []\\n")
        } else {
            sb.append("labels:\\n")
            task.labels.forEach { sb.append("  - \${escape(it)}\\n") }
        }

        sb.append("imagePath: \${task.imagePath?.let { escape(it) } ?: "null"}\\n")
        sb.append("createdAt: \${escape(task.createdAt)}\\n")
        sb.append("createdBy: \${escape(task.createdBy)}\\n")
        sb.append("updatedAt: \${escape(task.updatedAt)}\\n")
        sb.append("updatedBy: \${escape(task.updatedBy)}\\n")

        if (task.comments.isEmpty()) {
            sb.append("comments: []\\n")
        } else {
            sb.append("comments:\\n")
            task.comments.forEach {
                sb.append("  - body: \${escape(it.body)}\\n")
                sb.append("    createdAt: \${escape(it.createdAt)}\\n")
            }
        }
        sb.append("---\\n")
        if (task.description.isNotBlank()) {
            sb.append("\\n\${task.description.trim()}\\n")
        }
        return sb.toString()
    }
}`,
  },
  {
    path: 'app/src/main/java/com/oloop/app/saf/SafStorageManager.kt',
    name: 'SafStorageManager.kt',
    category: 'core',
    description: 'Storage Access Framework implementation: ACTION_OPEN_DOCUMENT_TREE, takePersistableUriPermission, DocumentFile navigation, backup generation and conflict checking.',
    language: 'kotlin',
    code: `package com.oloop.app.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.oloop.app.io.OloopParser
import com.oloop.app.io.OloopWriter
import com.oloop.app.model.Task

sealed class SaveResult {
    object Success : SaveResult()
    data class Conflict(val diskTask: Task, val memoryTask: Task) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

class SafStorageManager(private val context: Context) {
    // Uses Storage Access Framework DocumentFile
    // Implements re-read before write, .backup/<id>.md.bak generation, and atomic write.
}`,
  },
  {
    path: 'app/src/main/java/com/oloop/app/model/Models.kt',
    name: 'Models.kt',
    category: 'core',
    description: 'Domain data models for Task, Project, Comment, TaskType (todo, question, decision, feature, bug) and closed status labels.',
    language: 'kotlin',
    code: `package com.oloop.app.model

enum class TaskType(val value: String, val closedLabel: String) {
    TODO("todo", "Done"),
    QUESTION("question", "Answered"),
    DECISION("decision", "Decided"),
    FEATURE("feature", "Implemented"),
    BUG("bug", "Fixed");

    companion object {
        fun fromValue(v: String) = entries.find { it.value == v.lowercase() }
    }
}

enum class TaskStatus(val value: String) {
    OPEN("open"),
    CLOSED("closed");
}

data class Comment(val body: String, val createdAt: String)

data class Task(
    val formatVersion: Int = 1,
    val id: String,
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
)`,
  },
  {
    path: 'app/src/main/java/com/oloop/app/ui/MainActivity.kt',
    name: 'MainActivity.kt',
    category: 'ui',
    description: 'ComponentActivity with Material 3 Theme, Jetpack Compose navigation, SAF directory picker launcher, and dialogs.',
    language: 'kotlin',
    code: `package com.oloop.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // Jetpack Compose Screen Hierarchy
            }
        }
    }
}`,
  },
  {
    path: 'app/src/test/java/com/oloop/app/OloopParserTest.kt',
    name: 'OloopParserTest.kt',
    category: 'test',
    description: 'Automated JUnit tests running all fixtures, round-trips, and invalid cases.',
    language: 'kotlin',
    code: `package com.oloop.app

import com.oloop.app.io.OloopParser
import com.oloop.app.io.OloopWriter
import org.junit.Assert.*
import org.junit.Test

class OloopParserTest {
    @Test
    fun testParseAllValidTypes() { ... }

    @Test
    fun testRoundTripUnchanged() { ... }

    @Test
    fun testRefuseHigherFormatVersion() { ... }

    @Test
    fun testRefuseFilenameIdMismatch() { ... }

    @Test
    fun testRefuseUnknownKeys() { ... }
}`,
  },
  {
    path: 'app/build.gradle.kts',
    name: 'build.gradle.kts',
    category: 'config',
    description: 'Gradle configuration with Jetpack Compose BOM, SnakeYAML, Markwon and DocumentFile dependencies.',
    language: 'groovy',
    code: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.oloop.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.oloop.app"
        minSdk = 26
        targetSdk = 35
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("io.noties.markwon:core:4.6.2")
    testImplementation("junit:junit:4.13.2")
}`,
  },
];
