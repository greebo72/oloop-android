package com.oloop.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oloop.core.ProjectOps
import com.oloop.core.TaskOps
import com.oloop.core.TaskType

@Composable
fun Dialogs(state: UiState, vm: OloopViewModel) {
    if (state.askActor) ActorDialog(onSave = vm::setActor, onCancel = vm::cancelActorPrompt)
    if (state.showNewTask) NewTaskDialog(vm)
    if (state.showNewProject) NewProjectDialog(vm)
    state.conflict?.let { c ->
        AlertDialog(
            onDismissRequest = vm::dismissConflict,
            title = { Text("Changed elsewhere") },
            text = {
                Text(
                    "This task was changed on disk (${c.disk.updatedAt} by ${c.disk.updatedBy}) after you opened it. " +
                        "Reload to see that version and drop your change, or keep your version and replace the file. " +
                        "The replaced file is saved to .backup/.",
                )
            },
            confirmButton = { Button(onClick = vm::keepMyVersion) { Text("Keep mine") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = vm::dismissConflict) { Text("Cancel") }
                    OutlinedButton(onClick = vm::useDiskVersion) { Text("Reload") }
                }
            },
        )
    }
}

@Composable
private fun ActorDialog(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Your name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("oloop records who changed a task. Enter your name once, you can change it in the settings.")
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun NewProjectDialog(vm: OloopViewModel) {
    var name by remember { mutableStateOf("") }
    val slug = remember(name) { runCatching { ProjectOps.slugify(name.trim()) }.getOrNull() }
    AlertDialog(
        onDismissRequest = { vm.showNewProject(false) },
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    when {
                        name.isBlank() -> "The project gets its own folder in the data folder."
                        slug == null -> "This name yields no valid folder name."
                        else -> "Folder: $slug"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.typography.bodySmall.color,
                )
            }
        },
        confirmButton = {
            Button(enabled = slug != null, onClick = { vm.createProject(name) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { vm.showNewProject(false) }) { Text("Cancel") } },
    )
}

@Composable
private fun NewTaskDialog(vm: OloopViewModel) {
    var type by remember { mutableStateOf(TaskType.TODO) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.showNewTask(false) },
        title = { Text("New task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskType.entries.forEach {
                        FilterChip(selected = it == type, onClick = { type = it }, label = { Text(it.value) })
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    description, { description = it }, label = { Text("Description (Markdown)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                )
                OutlinedTextField(labels, { labels = it }, label = { Text("Labels, comma separated") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { vm.createTask(type, title, description, TaskOps.cleanLabels(labels.split(','))) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { vm.showNewTask(false) }) { Text("Cancel") } },
    )
}

@Composable
fun SettingsScreen(
    state: UiState,
    onPickFolder: () -> Unit,
    onActorChange: (String) -> Unit,
    onClose: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(state.actor) }
    Column(Modifier.fillMaxWidth()) {
        PaneHeader("Settings", onBack = onClose)
        Column(Modifier.padding16(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Data folder", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.rootSelected) "A folder is selected. Pick another one to switch."
                else "Pick the folder that holds your oloop data, for example the one you also use with the oloop CLI.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onPickFolder) { Text(if (state.rootSelected) "Change folder" else "Choose folder") }

            Text("Your name", style = MaterialTheme.typography.titleMedium)
            Text("Written to createdBy and updatedBy when you change a task.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = name.isNotBlank() && name.trim() != state.actor, onClick = { onActorChange(name) }) { Text("Save name") }
        }
    }
}

private fun Modifier.padding16() = this.then(Modifier.padding(16.dp))
