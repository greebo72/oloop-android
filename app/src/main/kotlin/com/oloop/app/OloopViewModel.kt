package com.oloop.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oloop.core.GroupingMode
import com.oloop.core.OloopRepository
import com.oloop.core.ProjectCreateResult
import com.oloop.core.ProjectData
import com.oloop.core.SaveResult
import com.oloop.core.Task
import com.oloop.core.TaskOps
import com.oloop.core.TaskStatus
import com.oloop.core.TaskType
import com.oloop.core.Unreadable
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
    val grouping: GroupingMode = GroupingMode.NONE,
    val showSettings: Boolean = false,
    val showNewTask: Boolean = false,
    val showNewProject: Boolean = false,
    val askActor: Boolean = false,
    val dirty: Boolean = false,
    val conflict: ConflictState? = null,
    val banner: String? = null,
) {
    val selectedProject: ProjectData? get() = projects.firstOrNull { it.project.id == selectedProjectId }
    val selectedTask: Task? get() = selectedProject?.tasks?.firstOrNull { it.id == selectedTaskId }
}

class OloopViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val _state = MutableStateFlow(UiState(rootSelected = settings.rootUri != null, actor = settings.actor))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** What the user wanted to do when we had to ask for a name first. */
    private var afterActor: (() -> Unit)? = null

    init {
        reload()
    }

    private fun repository(): OloopRepository? =
        settings.rootUri?.let { OloopRepository(SafFileSystem(getApplication(), it)) }

    fun reload() {
        val repo = repository() ?: return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repo.loadAll()
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
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, banner = "Cannot read the folder: ${e.message}") }
            }
        }
    }

    // ---- navigation and view options ------------------------------------

    private fun leaveAllowed(): Boolean {
        if (!_state.value.dirty) return true
        _state.update { it.copy(banner = "Save or discard your changes first") }
        return false
    }

    fun selectProject(id: String?) {
        if (leaveAllowed()) _state.update { it.copy(selectedProjectId = id, selectedTaskId = null) }
    }

    fun selectTask(id: String?) {
        if (leaveAllowed()) _state.update { it.copy(selectedTaskId = id) }
    }

    fun setDirty(dirty: Boolean) = _state.update { it.copy(dirty = dirty) }
    fun setGrouping(mode: GroupingMode) = _state.update { it.copy(grouping = mode) }
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
        val repo = repository() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                repo.createProject(name)
            } catch (e: Exception) {
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

    fun createTask(type: TaskType, title: String, description: String, labels: List<String>) {
        val data = _state.value.selectedProject ?: return
        withActor { actor ->
            val task = TaskOps.create(data.project, type, title, description, labels, actor)
            save(data, task, expectedUpdatedAt = null, closeNewTaskDialog = true)
        }
    }

    fun saveEdits(task: Task, title: String, description: String, labels: List<String>) {
        val data = _state.value.selectedProject ?: return
        withActor { actor ->
            save(data, TaskOps.edit(task, title, description, labels, actor), task.updatedAt)
        }
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
        val repo = repository() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                repo.saveTask(data.project, task, expectedUpdatedAt, force)
            } catch (e: Exception) {
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
                is SaveResult.Refused -> _state.update { it.copy(banner = "Not saved: ${result.reason}") }
            }
        }
    }
}
