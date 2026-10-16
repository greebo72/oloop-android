package com.oloop.core

data class ProjectData(val project: Project, val tasks: List<Task>)

/** A file that could not be read, shown to the user and never written to. */
data class Unreadable(val path: String, val reason: String)

data class LoadResult(val projects: List<ProjectData>, val unreadable: List<Unreadable>)

sealed class SaveResult {
    object Saved : SaveResult()
    /** The file on disk changed since it was loaded. Nothing was written. */
    data class Conflict(val diskTask: Task) : SaveResult()
    /** Nothing was written. */
    data class Refused(val reason: String) : SaveResult()
}

/** Loads and saves projects and tasks in the oloop folder layout. */
class OloopRepository(private val fs: OloopFileSystem) {

    fun loadAll(): LoadResult {
        val projects = mutableListOf<ProjectData>()
        val unreadable = mutableListOf<Unreadable>()
        for (folder in fs.listDirectories("")) {
            if (folder.startsWith(".")) continue
            val projectFile = "$folder/project.md"
            if (!fs.exists(projectFile)) continue
            val project = try {
                OloopParser.parseProject(fs.read(projectFile), folder)
            } catch (e: Exception) {
                unreadable += Unreadable(projectFile, e.message ?: e.toString())
                continue
            }
            val tasks = mutableListOf<Task>()
            for (name in fs.listFiles("$folder/tasks")) {
                if (!name.endsWith(".md") || name.startsWith(".")) continue
                val path = "$folder/tasks/$name"
                try {
                    tasks += OloopParser.parseTask(fs.read(path), name, project.id)
                } catch (e: Exception) {
                    unreadable += Unreadable(path, e.message ?: e.toString())
                }
            }
            projects += ProjectData(project, tasks)
        }
        return LoadResult(projects.sortedBy { it.project.name.lowercase() }, unreadable)
    }

    /**
     * Saves [task] to `<project>/tasks/<id>.md`.
     *
     * [expectedUpdatedAt] is the `updatedAt` the caller loaded. If the file on disk has another
     * value, nothing is written and [SaveResult.Conflict] is returned, unless [force] is set.
     * A file on disk that cannot be parsed is never overwritten. Before an existing file is
     * replaced, its content is copied to `<project>/.backup/<id>.md.bak` (one generation).
     */
    fun saveTask(
        project: Project,
        task: Task,
        expectedUpdatedAt: String?,
        force: Boolean = false,
    ): SaveResult {
        if (task.projectId != project.id) return SaveResult.Refused("task belongs to another project")
        val fileName = "${task.id}.md"
        val text = OloopWriter.serializeTask(task)
        // Never write something this library would refuse to read back.
        try {
            OloopParser.parseTask(text, fileName, project.id)
        } catch (e: FormatException) {
            return SaveResult.Refused("task is invalid: ${e.message}")
        }
        val path = "${project.folder}/tasks/$fileName"
        if (fs.exists(path)) {
            val diskText = fs.read(path)
            val disk = try {
                OloopParser.parseTask(diskText, fileName, project.id)
            } catch (e: FormatException) {
                return SaveResult.Refused("file on disk is unreadable and is left untouched: ${e.message}")
            }
            if (!force && expectedUpdatedAt != null && disk.updatedAt != expectedUpdatedAt) {
                return SaveResult.Conflict(disk)
            }
            fs.write("${project.folder}/.backup/$fileName.bak", diskText)
        }
        fs.write(path, text)
        return SaveResult.Saved
    }
}
