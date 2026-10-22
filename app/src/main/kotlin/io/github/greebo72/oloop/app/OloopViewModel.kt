package io.github.greebo72.oloop.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.greebo72.oloop.core.CachingFileSystem
import io.github.greebo72.oloop.core.GroupingMode
import io.github.greebo72.oloop.core.JavaFileSystem
import io.github.greebo72.oloop.core.LoadResult
import io.github.greebo72.oloop.core.OloopRepository
import io.github.greebo72.oloop.core.ProjectCreateResult
import io.github.greebo72.oloop.core.ProjectData
import io.github.greebo72.oloop.core.SaveResult
import io.github.greebo72.oloop.core.hasUsableCache
import io.github.greebo72.oloop.core.Task
import io.github.greebo72.oloop.core.TaskOps
import io.github.greebo72.oloop.core.TaskStatus
import io.github.greebo72.oloop.core.TaskType
import io.github.greebo72.oloop.core.Unreadable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A save that found a newer version of the file on disk. */
data class ConflictState(val projectId: String, val mine: Task, val expectedUpdatedAt: String, val disk: Task)

data class UiState(
    val rootSelected: Boolean = false,
    val actor: String = "",
    val loading: Boolean = false,
    val projects: List<ProjectData> = emptyList(),
    val unreadable: List<Unreadable> = emptyList(),
    val selectedProjectId: String? = null,
    val selectedTaskId: String? = null,
    val showClosed: Boolean = false,
    /** Only the overdue tasks of the open project. Reset when another project is opened. */
    val onlyOverdue: Boolean = false,
    val grouping: GroupingMode = GroupingMode.NONE,
    val showSettings: Boolean = false,
    val showNewTask: Boolean = false,
    val showNewProject: Boolean = false,
    val askActor: Boolean = false,
    val dirty: Boolean = false,
    val conflict: ConflictState? = null,
    val banner: String? = null,
    val recentTypes: List<TaskType> = listOf(TaskType.TODO, TaskType.QUESTION),
) {
    val selectedProject: ProjectData? get() = projects.firstOrNull { it.project.id == selectedProjectId }
    val selectedTask: Task? get() = selectedProject?.tasks?.firstOrNull { it.id == selectedTaskId }
}

/** Log tag of the app. Errors the user sees as a banner are logged under it, with the exception. */
private const val TAG = "oloop"

class OloopViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val _state = MutableStateFlow(
        UiState(
            rootSelected = settings.rootUri != null,
            actor = settings.actor,
            recentTypes = settings.recentTypes.mapNotNull(TaskType::fromValue).ifEmpty { listOf(TaskType.TODO, TaskType.QUESTION) },
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** What the user wanted to do when we had to ask for a name first. */
    private var afterActor: (() -> Unit)? = null

    init {
        reload()
    }

    /**
     * Local mirror of the data folder, keyed by the chosen root so a different folder never
     * paints another folder's snapshot. The UI shows it instantly on cold start while the
     * live load runs.
     */
    private fun cacheRoot(): java.nio.file.Path? =
        settings.rootUri?.let {
            getApplication<Application>().filesDir.toPath().resolve("oloop-cache-${it.toString().hashCode()}")
        }

    /**
     * Builds the file stack: SAF wrapped in the cache mirror. Constructing it runs a binder
     * call ([DocumentFile.fromTreeUri]), so callers do that on [Dispatchers.IO], never on the
     * main thread.
     */
    private fun cachingFileSystem(): CachingFileSystem? =
        settings.rootUri?.let { CachingFileSystem(SafFileSystem(getApplication(), it), cacheRoot() ?: return null) }

    private fun repository(): OloopRepository? =
        cachingFileSystem()?.let { OloopRepository(it) }

    /**
     * VM init and activity ON_RESUME (MainActivity) both fire a reload on a cold start. The
     * second one is redundant and races the first over the shared cache mirror, so only one
     * load runs at a time.
     */
    private var loadJob: kotlinx.coroutines.Job? = null

    fun reload() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            // Snapshot first: building the SAF stack costs a binder call and must not delay
            // the cached list reaching the screen.
            paintSnapshot()
            val fs = cachingFileSystem() ?: run { _state.update { it.copy(loading = false) }; return@launch }
            fs.clearCache()
            try {
                // Binder round-trips per file dominate over SAF and Drive; parse is CPU-cheap.
                paint(OloopRepository(fs).loadAll(parallelism = 4))
            } catch (e: Exception) {
                Log.w(TAG, "Reading the data folder failed", e)
                _state.update { it.copy(loading = false, banner = "Cannot read the folder: ${e.message}") }
            }
        }
    }

    /** Paints the last successful load from the local mirror, before the live round-trip ends. */
    private fun paintSnapshot() {
        val cache = cacheRoot() ?: return
        if (!hasUsableCache(cache)) return
        val result = try {
            OloopRepository(JavaFileSystem(cache)).loadAll()
        } catch (e: Exception) {
            return
        }
        if (result.projects.isEmpty()) return
        _state.update { s ->
            val project = result.projects.firstOrNull { it.project.id == s.selectedProjectId }
            s.copy(
                projects = result.projects,
                unreadable = result.unreadable,
                selectedProjectId = project?.project?.id,
                selectedTaskId = project?.tasks?.firstOrNull { it.id == s.selectedTaskId }?.id,
            )
        }
    }

    private fun paint(result: LoadResult) {
        _state.update { s ->
            // Keep the selection only if it still exists.
            val project = result.projects.firstOrNull { it.project.id == s.selectedProjectId }
            s.copy(
                loading = false,
                projects = result.projects,
                unreadable = result.unreadable,
                selectedProjectId = project?.project?.id,
                selectedTaskId = project?.tasks?.firstOrNull { it.id == s.selectedTaskId }?.id,
            )
        }
    }

    // ---- navigation and view options ------------------------------------

    private fun leaveAllowed(): Boolean {
        if (!_state.value.dirty) return true
        _state.update { it.copy(banner = "Save or discard your changes first") }
        return false
    }

    fun selectProject(id: String?) {
        if (leaveAllowed()) _state.update { it.copy(selectedProjectId = id, selectedTaskId = null, onlyOverdue = false) }
    }

    fun selectTask(id: String?) {
        if (leaveAllowed()) _state.update { it.copy(selectedTaskId = id) }
    }

    fun setDirty(dirty: Boolean) = _state.update { it.copy(dirty = dirty) }
    fun setGrouping(mode: GroupingMode) = _state.update { it.copy(grouping = mode) }
    fun toggleOverdue() = _state.update { it.copy(onlyOverdue = !it.onlyOverdue) }
    fun toggleClosed() = _state.update { it.copy(showClosed = !it.showClosed) }
    fun showSettings(show: Boolean) = _state.update { it.copy(showSettings = show) }
    fun showNewTask(show: Boolean) = _state.update { it.copy(showNewTask = show) }
    fun showNewProject(show: Boolean) = _state.update { it.copy(showNewProject = show) }
    fun clearBanner() = _state.update { it.copy(banner = null) }
    fun dismissConflict() = _state.update { it.copy(conflict = null) }

    // ---- settings --------------------------------------------------------

    fun setRoot(uri: android.net.Uri) {
        settings.setRoot(uri)
        _state.update {
            it.copy(rootSelected = true, showSettings = false, projects = emptyList(), selectedProjectId = null, selectedTaskId = null)
        }
        reload()
    }

    fun setActor(name: String) {
        settings.actor = name
        _state.update { it.copy(actor = settings.actor, askActor = false) }
        if (settings.actor.isNotBlank()) afterActor?.invoke()
        afterActor = null
    }

    fun cancelActorPrompt() {
        afterActor = null
        _state.update { it.copy(askActor = false) }
    }

    /** Runs [action] with the user name, or asks for it first. Nothing is written without a name. */
    private fun withActor(action: (String) -> Unit) {
        val actor = _state.value.actor
        if (actor.isBlank()) {
            afterActor = { withActor(action) }
            _state.update { it.copy(askActor = true) }
        } else {
            action(actor)
        }
    }

    // ---- changes ---------------------------------------------------------

    /** No [withActor]: project.md has no createdBy/updatedBy fields. */
    fun createProject(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = repository() ?: return@launch
            val result = try {
                repo.createProject(name)
            } catch (e: Exception) {
                Log.w(TAG, "Creating project failed", e)
                ProjectCreateResult.Refused("write failed: ${e.message}")
            }
            when (result) {
                is ProjectCreateResult.Created -> {
                    _state.update { it.copy(showNewProject = false, banner = "Project created") }
                    reload()
                    selectProject(result.project.id)
                }
                // Dialog stays open so the user can fix the name.
                is ProjectCreateResult.Refused -> _state.update { it.copy(banner = "Not created: ${result.reason}") }
            }
        }
    }

    fun createTask(type: TaskType, title: String, description: String, labels: List<String>, dueDate: String? = null) {
        val data = _state.value.selectedProject ?: return
        withActor { actor ->
            rememberTypeUsed(type)
            val task = TaskOps.create(data.project, type, title, description, labels, actor, dueDate = dueDate)
            save(data, task, expectedUpdatedAt = null, closeNewTaskDialog = true)
        }
    }

    fun saveEdits(task: Task, type: TaskType, title: String, description: String, labels: List<String>, dueDate: String?) {
        val data = _state.value.selectedProject ?: return
        withActor { actor ->
            rememberTypeUsed(type)
            save(data, TaskOps.edit(task, type, title, description, labels, actor, dueDate = dueDate), task.updatedAt)
        }
    }

    /** Moves [type] to the front of the recent-types list and persists it. */
    private fun rememberTypeUsed(type: TaskType) {
        val recents = (listOf(type) + _state.value.recentTypes.filter { it != type }).take(6)
        settings.recentTypes = recents.map { it.value }
        _state.update { it.copy(recentTypes = recents) }
    }

    fun setStatus(task: Task, status: TaskStatus) {
        val data = _state.value.selectedProject ?: return
        withActor { actor -> save(data, TaskOps.setStatus(task, status, actor), task.updatedAt) }
    }

    fun addComment(task: Task, body: String) {
        val data = _state.value.selectedProject ?: return
        withActor { actor -> save(data, TaskOps.addComment(task, body, actor), task.updatedAt) }
    }

    fun keepMyVersion() {
        val c = _state.value.conflict ?: return
        val data = _state.value.projects.firstOrNull { it.project.id == c.projectId } ?: return
        _state.update { it.copy(conflict = null) }
        save(data, c.mine, c.expectedUpdatedAt, force = true)
    }

    fun useDiskVersion() {
        _state.update { it.copy(conflict = null, dirty = false) }
        reload()
    }

    private fun save(
        data: ProjectData,
        task: Task,
        expectedUpdatedAt: String?,
        force: Boolean = false,
        closeNewTaskDialog: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = repository() ?: return@launch
            val result = try {
                repo.saveTask(data.project, task, expectedUpdatedAt, force)
            } catch (e: Exception) {
                // The banner shows the message; the log keeps the whole exception for a later look.
                Log.w(TAG, "Saving task ${task.id} failed", e)
                SaveResult.Refused("write failed: ${e.message}")
            }
            when (result) {
                SaveResult.Saved -> {
                    _state.update {
                        it.copy(
                            banner = "Saved",
                            dirty = false,
                            showNewTask = it.showNewTask && !closeNewTaskDialog,
                            selectedTaskId = if (closeNewTaskDialog) task.id else it.selectedTaskId,
                        )
                    }
                    reload()
                }
                is SaveResult.Conflict -> _state.update {
                    it.copy(conflict = ConflictState(data.project.id, task, expectedUpdatedAt ?: "", result.diskTask))
                }
                is SaveResult.Refused -> {
                    Log.w(TAG, "Task ${task.id} not saved: ${result.reason}")
                    _state.update { it.copy(banner = "Not saved: ${result.reason}") }
                }
            }
        }
    }
}
