package com.oloop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
            confirmButton = { TextButton(onClick = vm::keepMyVersion) { Text("Keep mine") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = vm::dismissConflict) { Text("Cancel") }
                    TextButton(onClick = vm::useDiskVersion) { Text("Reload") }
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
                TonalTextField(
                    name, { name = it }, "Name",
                    fill = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** Dialog shell per spec: surfaceContainerHigh, radius 28 (the M3 dialog defaults), 24 dp padding. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OloopDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}

@Composable
private fun NewProjectDialog(vm: OloopViewModel) {
    var name by remember { mutableStateOf("") }
    val slug = remember(name) { runCatching { ProjectOps.slugify(name.trim()) }.getOrNull() }
    val focus = remember { FocusRequester() }
    OloopDialog(onDismiss = { vm.showNewProject(false) }) {
        Text("New project", style = MaterialTheme.typography.headlineSmall)
        Text(
            when {
                name.isBlank() -> "The project gets its own folder in the data folder."
                slug == null -> "This name yields no valid folder name."
                else -> "Folder: $slug"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TonalTextField(
            name, { name = it }, "Name",
            fill = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
            focusRequester = focus,
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { vm.showNewProject(false) }) { Text("Cancel") }
            TextButton(enabled = slug != null, onClick = { vm.createProject(name) }) { Text("Create") }
        }
    }
}

@Composable
private fun NewTaskDialog(vm: OloopViewModel) {
    var type by remember { mutableStateOf(vm.state.value.recentTypes.firstOrNull() ?: TaskType.TODO) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf(listOf<String>()) }
    val focus = remember { FocusRequester() }
    OloopDialog(onDismiss = { vm.showNewTask(false) }) {
        Text("New task", style = MaterialTheme.typography.headlineSmall)
        TypePicker(type, { type = it }, vm.state.value.recentTypes)
        TonalTextField(
            title, { title = it }, "Title",
            fill = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
            focusRequester = focus,
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
        TonalTextField(
            description, { description = it }, "Description",
            fill = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().height(96.dp),
            minLines = 3,
        )
        Column {
            LabelsField(labels, { labels = it }, fill = MaterialTheme.colorScheme.surface)
            Text(
                "Separate with commas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { vm.showNewTask(false) }) { Text("Cancel") }
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { vm.createTask(type, title, description, TaskOps.cleanLabels(labels)) },
            ) { Text("Create") }
        }
    }
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
        OloopTopBar("Settings", onBack = onClose)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Data folder", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.rootSelected) "A folder is selected. Pick another one to switch."
                else "Pick the folder that holds your oloop data, for example the one you also use with the oloop CLI.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onPickFolder) { Text(if (state.rootSelected) "Change folder" else "Choose folder") }

            Text("Your name", style = MaterialTheme.typography.titleMedium)
            Text("Written to createdBy and updatedBy when you change a task.", style = MaterialTheme.typography.bodyMedium)
            TonalTextField(name, { name = it }, "Name", modifier = Modifier.fillMaxWidth())
            TextButton(
                enabled = name.isNotBlank() && name.trim() != state.actor,
                onClick = { onActorChange(name) },
            ) { Text("Save name") }
        }
    }
}
