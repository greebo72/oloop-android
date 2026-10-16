package com.oloop.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oloop.app.io.OloopWriter
import com.oloop.app.model.*
import com.oloop.app.saf.SafStorageManager
import com.oloop.app.saf.SaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

data class UiState(
    val rootFolderUri: String? = null,
    val userActor: String = "",
    val projects: List<Project> = emptyList(),
    val unreadableFiles: List<String> = emptyList(),
    val selectedProject: Project? = null,
    val tasks: List<Task> = emptyList(),
    val selectedTask: Task? = null,
    val showClosedTasks: Boolean = false,
    val groupingMode: GroupingMode = GroupingMode.NONE,
    val isEditingTask: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val activeConflict: ConflictState? = null,
    val promptActorDialog: Boolean = false,
    val messageBanner: String? = null
)

class OloopViewModel(private val storage: SafStorageManager) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val actor = storage.getUserActor()
        val uri = storage.getPersistedRootUri()?.toString()
        _uiState.value = _uiState.value.copy(
            userActor = actor,
            rootFolderUri = uri
        )
        if (uri != null) {
            refreshProjects()
        }
    }

    fun setActor(name: String) {
        storage.setUserActor(name)
        _uiState.value = _uiState.value.copy(
            userActor = name,
            promptActorDialog = false
        )
    }

    fun refreshProjects() {
        viewModelScope.launch {
            val (projects, unreadable) = storage.loadProjects()
            _uiState.value = _uiState.value.copy(
                projects = projects,
                unreadableFiles = unreadable
            )
        }
    }

    fun selectProject(project: Project) {
        _uiState.value = _uiState.value.copy(
            selectedProject = project,
            selectedTask = null,
            isEditingTask = false,
            hasUnsavedChanges = false
        )
        refreshTasks()
    }

    fun refreshTasks() {
        val proj = _uiState.value.selectedProject ?: return
        viewModelScope.launch {
            val (tasks, unreadable) = storage.loadTasks(proj.id, proj.folderName)
            _uiState.value = _uiState.value.copy(
                tasks = tasks,
                unreadableFiles = unreadable
            )
        }
    }

    fun selectTask(task: Task) {
        _uiState.value = _uiState.value.copy(
            selectedTask = task,
            isEditingTask = false,
            hasUnsavedChanges = false
        )
    }

    fun toggleShowClosed() {
        _uiState.value = _uiState.value.copy(
            showClosedTasks = !_uiState.value.showClosedTasks
        )
    }

    fun setGroupingMode(mode: GroupingMode) {
        _uiState.value = _uiState.value.copy(groupingMode = mode)
    }

    fun toggleTaskStatus(task: Task) {
        val actor = getOrPromptActor() ?: return
        val newStatus = if (task.status == TaskStatus.OPEN) TaskStatus.CLOSED else TaskStatus.OPEN
        val updated = task.copy(
            status = newStatus,
            updatedAt = OloopWriter.nowUtcIso(),
            updatedBy = actor
        )
        saveTaskInternal(updated, task.updatedAt)
    }

    fun addComment(task: Task, commentBody: String) {
        if (commentBody.isBlank()) return
        val actor = getOrPromptActor() ?: return
        val now = OloopWriter.nowUtcIso()
        val newComment = Comment(body = commentBody.trim(), createdAt = now)
        val updated = task.copy(
            comments = task.comments + newComment,
            updatedAt = now,
            updatedBy = actor
        )
        saveTaskInternal(updated, task.updatedAt)
    }

    fun createTask(type: TaskType, title: String, description: String, labels: List<String>) {
        val proj = _uiState.value.selectedProject ?: return
        val actor = getOrPromptActor() ?: return
        val now = OloopWriter.nowUtcIso()

        val cleanLabels = labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val newTask = Task(
            formatVersion = 1,
            projectId = proj.id,
            type = type,
            title = title.trim(),
            status = TaskStatus.OPEN,
            labels = cleanLabels,
            imagePath = null,
            createdAt = now,
            createdBy = actor,
            updatedAt = now,
            updatedBy = actor,
            comments = emptyList(),
            description = description.trim()
        )

        saveTaskInternal(newTask, null)
    }

    fun saveTaskEdits(title: String, description: String, labels: List<String>) {
        val current = _uiState.value.selectedTask ?: return
        val actor = getOrPromptActor() ?: return
        val now = OloopWriter.nowUtcIso()
        val cleanLabels = labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        val updated = current.copy(
            title = title.trim(),
            description = description.trim(),
            labels = cleanLabels,
            updatedAt = now,
            updatedBy = actor
        )

        saveTaskInternal(updated, current.updatedAt)
    }

    private fun getOrPromptActor(): String? {
        val current = _uiState.value.userActor
        if (current.isBlank()) {
            _uiState.value = _uiState.value.copy(promptActorDialog = true)
            return null
        }
        return current
    }

    private fun saveTaskInternal(task: Task, lastKnownUpdatedAt: String?, forceOverwrite: Boolean = false) {
        val proj = _uiState.value.selectedProject ?: return
        viewModelScope.launch {
            when (val res = storage.saveTask(proj.folderName, task, lastKnownUpdatedAt, forceOverwrite)) {
                is SaveResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedTask = task,
                        hasUnsavedChanges = false,
                        activeConflict = null,
                        messageBanner = "Saved successfully"
                    )
                    refreshTasks()
                    refreshProjects()
                }
                is SaveResult.Conflict -> {
                    _uiState.value = _uiState.value.copy(
                        activeConflict = ConflictState(
                            task = task,
                            diskTask = res.diskTask,
                            folderName = proj.folderName
                        )
                    )
                }
                is SaveResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        messageBanner = "Error saving task: ${res.message}"
                    )
                }
            }
        }
    }

    fun resolveConflictReload() {
        val conflict = _uiState.value.activeConflict ?: return
        _uiState.value = _uiState.value.copy(
            selectedTask = conflict.diskTask,
            activeConflict = null,
            messageBanner = "Reloaded latest version from disk"
        )
        refreshTasks()
    }

    fun resolveConflictOverwrite() {
        val conflict = _uiState.value.activeConflict ?: return
        saveTaskInternal(conflict.task, null, forceOverwrite = true)
    }

    fun dismissConflict() {
        _uiState.value = _uiState.value.copy(activeConflict = null)
    }

    fun clearBanner() {
        _uiState.value = _uiState.value.copy(messageBanner = null)
    }

    companion object {
        fun formatRelativeAge(isoTimestamp: String): String {
            return try {
                val then = Instant.parse(isoTimestamp)
                val now = Instant.now()
                val seconds = ChronoUnit.SECONDS.between(then, now)
                val minutes = ChronoUnit.MINUTES.between(then, now)
                val hours = ChronoUnit.HOURS.between(then, now)
                val days = ChronoUnit.DAYS.between(then, now)

                when {
                    seconds < 60 -> "just now"
                    minutes < 60 -> "${minutes}m ago"
                    hours < 24 -> "${hours}h ago"
                    days == 1L -> "1 day ago"
                    days < 30 -> "$days days ago"
                    days < 365 -> "${days / 30}mo ago"
                    else -> "${days / 365}y ago"
                }
            } catch (e: Exception) {
                isoTimestamp
            }
        }
    }
}
