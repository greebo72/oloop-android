package com.oloop.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oloop.app.model.TaskType
import com.oloop.app.saf.SafStorageManager
import com.oloop.app.ui.screens.*

class MainActivity : ComponentActivity() {

    private lateinit var storageManager: SafStorageManager
    private lateinit var viewModel: OloopViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageManager = SafStorageManager(applicationContext)
        viewModel = OloopViewModel(storageManager)

        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsState()
                var currentScreen by remember { mutableStateOf("projects") }
                var showCreateDialog by remember { mutableStateOf(false) }

                // Storage Access Framework Directory Picker Launcher
                val folderPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null) {
                        storageManager.persistRootUri(uri)
                        viewModel.refreshProjects()
                    }
                }

                // Conflict Resolution Dialog
                state.activeConflict?.let { conflict ->
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissConflict() },
                        title = { Text("Concurrent Change Conflict") },
                        text = {
                            Text(
                                "This task was modified on disk by another process after you loaded it.\n\n" +
                                        "Disk updatedAt: ${conflict.diskTask.updatedAt}\n" +
                                        "Your updatedAt: ${conflict.task.updatedAt}\n\n" +
                                        "Do you want to reload the latest version from disk or overwrite it?"
                            )
                        },
                        confirmButton = {
                            Button(onClick = { viewModel.resolveConflictOverwrite() }) {
                                Text("Overwrite")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { viewModel.resolveConflictReload() }) {
                                Text("Reload from disk")
                            }
                        }
                    )
                }

                // Actor Prompt Dialog
                if (state.promptActorDialog) {
                    var actorInput by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Enter Your Name (Actor)") },
                        text = {
                            Column {
                                Text("A user name is required before saving tasks. It will be recorded in createdBy and updatedBy.")
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = actorInput,
                                    onValueChange = { actorInput = it },
                                    label = { Text("User Name") },
                                    singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.setActor(actorInput) },
                                enabled = actorInput.trim().isNotEmpty()
                            ) {
                                Text("Continue")
                            }
                        }
                    )
                }

                // New Task Dialog
                if (showCreateDialog && state.selectedProject != null) {
                    var newType by remember { mutableStateOf(TaskType.TODO) }
                    var newTitle by remember { mutableStateOf("") }
                    var newDesc by remember { mutableStateOf("") }
                    var newLabels by remember { mutableStateOf("") }

                    AlertDialog(
                        onDismissRequest = { showCreateDialog = false },
                        title = { Text("New Task in ${state.selectedProject?.name}") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Type:", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TaskType.entries.forEach { t ->
                                        FilterChip(
                                            selected = newType == t,
                                            onClick = { newType = t },
                                            label = { Text(t.value) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = newTitle,
                                    onValueChange = { newTitle = it },
                                    label = { Text("Title *") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = newLabels,
                                    onValueChange = { newLabels = it },
                                    label = { Text("Labels (comma separated)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = newDesc,
                                    onValueChange = { newDesc = it },
                                    label = { Text("Description (Markdown)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val labelList = newLabels.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                    viewModel.createTask(newType, newTitle, newDesc, labelList)
                                    showCreateDialog = false
                                },
                                enabled = newTitle.trim().isNotEmpty()
                            ) {
                                Text("Create Task")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreateDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Navigation Flow
                when (currentScreen) {
                    "settings" -> {
                        SettingsScreen(
                            currentActor = state.userActor,
                            currentFolderUri = state.rootFolderUri,
                            onBack = { currentScreen = "projects" },
                            onSaveActor = { viewModel.setActor(it) },
                            onPickFolderClick = { folderPickerLauncher.launch(null) }
                        )
                    }
                    "detail" -> {
                        state.selectedTask?.let { task ->
                            TaskDetailScreen(
                                task = task,
                                onBack = { currentScreen = "tasks" },
                                onSaveEdits = { t, d, l -> viewModel.saveTaskEdits(t, d, l) },
                                onToggleStatus = { viewModel.toggleTaskStatus(task) },
                                onAddComment = { viewModel.addComment(task, it) }
                            )
                        } ?: run {
                            currentScreen = "tasks"
                        }
                    }
                    "tasks" -> {
                        state.selectedProject?.let { proj ->
                            TaskListScreen(
                                project = proj,
                                tasks = state.tasks,
                                showClosed = state.showClosedTasks,
                                groupingMode = state.groupingMode,
                                onBack = { currentScreen = "projects" },
                                onSelectTask = {
                                    viewModel.selectTask(it)
                                    currentScreen = "detail"
                                },
                                onToggleShowClosed = { viewModel.toggleShowClosed() },
                                onSetGroupingMode = { viewModel.setGroupingMode(it) },
                                onCreateTaskClick = { showCreateDialog = true }
                            )
                        } ?: run {
                            currentScreen = "projects"
                        }
                    }
                    else -> {
                        ProjectListScreen(
                            projects = state.projects,
                            unreadableFiles = state.unreadableFiles,
                            onSelectProject = {
                                viewModel.selectProject(it)
                                currentScreen = "tasks"
                            },
                            onOpenSettings = { currentScreen = "settings" }
                        )
                    }
                }
            }
        }
    }
}
