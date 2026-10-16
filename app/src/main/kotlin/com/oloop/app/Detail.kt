package com.oloop.app

import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import com.oloop.core.Grouping
import com.oloop.core.Task
import com.oloop.core.TaskStatus

@Composable
fun DetailPane(state: UiState, vm: OloopViewModel, showBack: Boolean) {
    val task = state.selectedTask
    if (task == null) {
        Placeholder("Select a task")
        return
    }
    // A fresh editor state whenever the task on disk changes.
    androidx.compose.runtime.key(task.id, task.updatedAt) {
        TaskDetail(task, vm, onBack = if (showBack) ({ vm.selectTask(null) }) else null)
    }
}

@Composable
private fun TaskDetail(task: Task, vm: OloopViewModel, onBack: (() -> Unit)?) {
    var editing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var labels by remember { mutableStateOf(task.labels.joinToString(", ")) }
    var comment by remember { mutableStateOf("") }

    fun parsedLabels() = labels.split(',')
    val dirty = editing && (title != task.title || description != task.description ||
        com.oloop.core.TaskOps.cleanLabels(parsedLabels()) != task.labels)
    LaunchedEffect(dirty) { vm.setDirty(dirty) }

    Column(Modifier.fillMaxSize()) {
        PaneHeader(if (editing) "Edit task" else "Task", onBack = onBack) {
            if (editing) {
                TextButton(onClick = {
                    editing = false
                    title = task.title; description = task.description; labels = task.labels.joinToString(", ")
                }) { Text("Discard") }
                TextButton(
                    enabled = dirty && title.isNotBlank(),
                    onClick = { vm.saveEdits(task, title, description, parsedLabels()) },
                ) { Text("Save") }
            } else {
                TextButton(onClick = { editing = true }) { Text("Edit") }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    labels, { labels = it }, label = { Text("Labels, comma separated") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    description, { description = it }, label = { Text("Description (Markdown)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 6,
                )
            } else {
                Text(task.title, style = MaterialTheme.typography.headlineSmall)
                val status = if (task.status == TaskStatus.CLOSED) "${task.type.value} · ${task.type.closedLabel}" else "${task.type.value} · open"
                Text(status, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (task.labels.isNotEmpty()) Text("Labels: " + task.labels.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                if (task.description.isNotBlank()) MarkdownText(task.description)
            }

            Text(
                "Created ${task.createdAt} by ${task.createdBy}\nUpdated ${task.updatedAt} by ${task.updatedBy} (${Grouping.age(task.updatedAt)} ago)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            task.imagePath?.let { Text("Image: $it", style = MaterialTheme.typography.bodySmall) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.status == TaskStatus.OPEN) {
                    Button(enabled = !editing, onClick = { vm.setStatus(task, TaskStatus.CLOSED) }) { Text("Close task") }
                } else {
                    OutlinedButton(enabled = !editing, onClick = { vm.setStatus(task, TaskStatus.OPEN) }) { Text("Reopen") }
                }
            }

            HorizontalDivider()
            Text("Comments (${task.comments.size})", style = MaterialTheme.typography.titleSmall)
            task.comments.forEach {
                Column {
                    Text(it.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedTextField(
                comment, { comment = it }, label = { Text("New comment") },
                modifier = Modifier.fillMaxWidth(), enabled = !editing,
            )
            Button(
                enabled = !editing && comment.isNotBlank(),
                onClick = { vm.addComment(task, comment); comment = "" },
            ) { Text("Add comment") }
        }
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        factory = { TextView(it) },
        update = { view ->
            view.setTextColor(color)
            view.textSize = 16f
            markwon.setMarkdown(view, markdown)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
