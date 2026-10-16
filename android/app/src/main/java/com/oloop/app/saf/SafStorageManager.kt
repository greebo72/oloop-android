package com.oloop.app.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.oloop.app.io.OloopParser
import com.oloop.app.io.OloopWriter
import com.oloop.app.model.Project
import com.oloop.app.model.Task
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

sealed class SaveResult {
    object Success : SaveResult()
    data class Conflict(val diskTask: Task, val memoryTask: Task) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

class SafStorageManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "oloop_prefs"
        private const val KEY_ROOT_URI = "root_tree_uri"
        private const val KEY_USER_ACTOR = "user_actor"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getUserActor(): String {
        return prefs.getString(KEY_USER_ACTOR, "") ?: ""
    }

    fun setUserActor(name: String) {
        prefs.edit().putString(KEY_USER_ACTOR, name.trim()).apply()
    }

    fun getPersistedRootUri(): Uri? {
        val uriStr = prefs.getString(KEY_ROOT_URI, null) ?: return null
        return Uri.parse(uriStr)
    }

    fun persistRootUri(uri: Uri) {
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            // Logged or handled
        }
        prefs.edit().putString(KEY_ROOT_URI, uri.toString()).apply()
    }

    private fun getRootDocument(): DocumentFile? {
        val uri = getPersistedRootUri() ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    private fun readFileContent(file: DocumentFile): String {
        context.contentResolver.openInputStream(file.uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                return reader.readText()
            }
        } ?: throw IllegalStateException("Could not open input stream for ${file.name}")
    }

    private fun writeDirect(file: DocumentFile, content: String) {
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                writer.write(content)
                writer.flush()
            }
        } ?: throw IllegalStateException("Could not open output stream for ${file.name}")
    }

    fun loadProjects(): Pair<List<Project>, List<String>> {
        val root = getRootDocument() ?: return Pair(emptyList(), listOf("No root folder selected"))
        val projects = mutableListOf<Project>()
        val unreadable = mutableListOf<String>()

        for (file in root.listFiles()) {
            if (file.isDirectory && !file.name.orEmpty().startsWith(".")) {
                val folderName = file.name.orEmpty()
                val projectMd = file.findFile("project.md")
                if (projectMd != null && projectMd.isFile) {
                    try {
                        val text = readFileContent(projectMd)
                        val parseResult = OloopParser.parseProject(text, folderName)
                        if (parseResult.data != null) {
                            val (tasks, _) = loadTasks(parseResult.data.id, folderName)
                            val openCount = tasks.count { it.status == com.oloop.app.model.TaskStatus.OPEN }
                            val latestUpdate = tasks.map { it.updatedAt }.maxOrNull() ?: parseResult.data.updatedAt

                            projects.add(
                                parseResult.data.copy(
                                    totalTasksCount = tasks.size,
                                    openTasksCount = openCount,
                                    latestTaskUpdatedAt = latestUpdate
                                )
                            )
                        } else {
                            unreadable.add("$folderName/project.md: ${parseResult.error}")
                        }
                    } catch (e: Exception) {
                        unreadable.add("$folderName/project.md: ${e.message}")
                    }
                }
            }
        }

        projects.sortByDescending { it.latestTaskUpdatedAt ?: "" }
        return Pair(projects, unreadable)
    }

    fun loadTasks(projectId: String, projectFolderName: String): Pair<List<Task>, List<String>> {
        val root = getRootDocument() ?: return Pair(emptyList(), listOf("No root folder selected"))
        val projectFolder = root.findFile(projectFolderName)
            ?: return Pair(emptyList(), listOf("Project folder '$projectFolderName' not found"))

        val tasks = mutableListOf<Task>()
        val unreadable = mutableListOf<String>()

        for (file in projectFolder.listFiles()) {
            val name = file.name.orEmpty()
            if (file.isFile && name.endsWith(".md") && name != "project.md") {
                try {
                    val content = readFileContent(file)
                    val res = OloopParser.parseTask(content, name, projectId)
                    if (res.data != null) {
                        tasks.add(res.data)
                    } else {
                        unreadable.add("$projectFolderName/$name: ${res.error}")
                    }
                } catch (e: Exception) {
                    unreadable.add("$projectFolderName/$name: ${e.message}")
                }
            }
        }

        return Pair(tasks, unreadable)
    }

    /**
     * Saves task with atomic backup and concurrent change detection.
     */
    fun saveTask(
        projectFolderName: String,
        task: Task,
        lastKnownUpdatedAt: String?,
        forceOverwrite: Boolean = false
    ): SaveResult {
        val root = getRootDocument() ?: return SaveResult.Error("No root folder selected")
        val projectFolder = root.findFile(projectFolderName)
            ?: return SaveResult.Error("Project folder '$projectFolderName' not found")

        val targetFileName = "${task.id}.md"
        val existingFile = projectFolder.findFile(targetFileName)

        // 1. Re-read disk file for concurrent modification detection
        if (existingFile != null && existingFile.isFile) {
            val diskContent = readFileContent(existingFile)
            val parsedDisk = OloopParser.parseTask(diskContent, targetFileName, task.projectId)
            if (parsedDisk.data != null) {
                if (!forceOverwrite && lastKnownUpdatedAt != null && parsedDisk.data.updatedAt != lastKnownUpdatedAt) {
                    return SaveResult.Conflict(
                        diskTask = parsedDisk.data,
                        memoryTask = task
                    )
                }
            }

            // 2. Before overwriting, copy to <project>/.backup/<fileName>.bak
            val backupFolder = projectFolder.findFile(".backup") ?: projectFolder.createDirectory(".backup")
            if (backupFolder != null) {
                val backupFileName = "$targetFileName.bak"
                val existingBackup = backupFolder.findFile(backupFileName)
                val backupFile = existingBackup ?: backupFolder.createFile("text/markdown", backupFileName)
                if (backupFile != null) {
                    writeDirect(backupFile, diskContent)
                }
            }
        }

        // 3. Write target file safely
        val serialized = OloopWriter.serializeTask(task)
        val fileToWrite = existingFile ?: projectFolder.createFile("text/markdown", targetFileName)
            ?: return SaveResult.Error("Failed to create file $targetFileName")

        writeDirect(fileToWrite, serialized)
        return SaveResult.Success
    }
}
