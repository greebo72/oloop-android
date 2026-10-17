package com.oloop.core

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

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

sealed class ProjectCreateResult {
    data class Created(val project: Project) : ProjectCreateResult()
    /** Nothing was written. */
    data class Refused(val reason: String) : ProjectCreateResult()
}

/** Loads and saves projects and tasks in the oloop folder layout. */
class OloopRepository(private val fs: OloopFileSystem) {

    fun loadAll(): LoadResult = loadAll(parallelism = 1)

    /**
     * [loadAll] with the task files read and parsed on [parallelism] threads.
     *
     * Over SAF every file read is a binder call, and a cloud provider adds network latency per
     * file, so serial reads of many tasks dominate the load time. Listing and `project.md` stay
     * serial: one query per folder, already cheap. Same results as the serial variant, same
     * order, same unreadable reporting. `parallelism <= 1` never touches a thread pool.
     */
    fun loadAll(parallelism: Int): LoadResult {
        val projects = mutableListOf<ProjectData>()
        val unreadable = mutableListOf<Unreadable>()
        val pending = mutableListOf<Pair<Project, List<Pair<String, String>>>>()
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
            val files = fs.listFiles("$folder/tasks")
                .filter { it.endsWith(".md") && !it.startsWith(".") }
                .map { "$folder/tasks/$it" to it }
            pending += project to files
        }
        val workers = if (parallelism > 1) minOf(parallelism, pending.sumOf { it.second.size }) else 1
        val pool = if (workers > 1) Executors.newFixedThreadPool(workers) else null
        try {
            val futures: List<List<Pair<String, Future<Result<Task>>>>>? = pool?.let { p ->
                pending.map { (project, files) ->
                    files.map { (path, name) ->
                        path to p.submit(
                            Callable<Result<Task>> {
                                runCatching { OloopParser.parseTask(fs.read(path), name, project.id) }
                            },
                        )
                    }
                }
            }
            pending.forEachIndexed { index, (project, files) ->
                val tasks = mutableListOf<Task>()
                if (futures == null) {
                    for ((path, name) in files) {
                        try {
                            tasks += OloopParser.parseTask(fs.read(path), name, project.id)
                        } catch (e: Exception) {
                            unreadable += Unreadable(path, e.message ?: e.toString())
                        }
                    }
                } else {
                    for ((path, future) in futures[index]) {
                        future.get().onSuccess { tasks += it }
                            .onFailure { e -> unreadable += Unreadable(path, e.message ?: e.toString()) }
                    }
                }
                projects += ProjectData(project, tasks)
            }
        } finally {
            pool?.shutdown()
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

    /**
     * Creates `<slug>/project.md` and the empty `<slug>/tasks/` folder, same as the CLI's
     * `project add`. Refuses when the name (case-insensitive) or the slug folder is already
     * taken, and when a `project.md` cannot be read, because then the name cannot be checked.
     * The `tasks/` folder is required: the CLI writes task files with a temp file in that
     * folder, so `task add` breaks without it.
     */
    fun createProject(name: String): ProjectCreateResult {
        val project = try {
            ProjectOps.create(name)
        } catch (e: FormatException) {
            return ProjectCreateResult.Refused(e.message ?: "invalid name")
        }
        for (folder in fs.listDirectories("")) {
            if (folder.startsWith(".")) continue
            val file = "$folder/project.md"
            if (!fs.exists(file)) continue
            val existing = try {
                OloopParser.parseProject(fs.read(file), folder)
            } catch (e: FormatException) {
                return ProjectCreateResult.Refused("$file cannot be read, so the name cannot be checked: ${e.message}")
            }
            if (existing.name.equals(project.name, ignoreCase = true)) {
                return ProjectCreateResult.Refused("project already exists: ${project.name}")
            }
        }
        if (fs.exists(project.folder)) {
            return ProjectCreateResult.Refused("folder ${project.folder} already exists")
        }
        val text = OloopWriter.serializeProject(project)
        // Never write something this library would refuse to read back.
        try {
            OloopParser.parseProject(text, project.folder)
        } catch (e: FormatException) {
            return ProjectCreateResult.Refused("project is invalid: ${e.message}")
        }
        fs.write("${project.folder}/project.md", text)
        fs.createDirectory("${project.folder}/tasks")
        return ProjectCreateResult.Created(project)
    }
}
