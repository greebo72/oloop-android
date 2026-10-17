package com.oloop.app

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import com.oloop.core.Task
import com.oloop.core.TaskOps
import com.oloop.core.TaskStatus

@Composable
fun DetailPane(state: UiState, vm: OloopViewModel, showBack: Boolean) {
    val task = state.selectedTask
    if (task == null) {
        Placeholder("Select a task")
        return
    }
    val projectName = state.selectedProject?.project?.name ?: ""
    // A fresh editor state whenever the task on disk changes.
    androidx.compose.runtime.key(task.id, task.updatedAt) {
        TaskDetail(task, projectName, vm, onBack = if (showBack) ({ vm.selectTask(null) }) else null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskDetail(task: Task, projectName: String, vm: OloopViewModel, onBack: (() -> Unit)?) {
    var editing by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(task.type) }
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var labels by remember { mutableStateOf(task.labels) }
    var commentDraft by remember { mutableStateOf("") }

    val dirty = editing && (
        type != task.type || title != task.title || description != task.description ||
            TaskOps.cleanLabels(labels) != task.labels
        )
    LaunchedEffect(dirty) { vm.setDirty(dirty) }

    Column(Modifier.fillMaxSize()) {
        if (editing) {
            // Edit (1d): close icon, "Edit task", filled Save, disabled until dirty.
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    editing = false
                    type = task.type; title = task.title; description = task.description; labels = task.labels
                }) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                Text("Edit task", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(
                    enabled = dirty && title.isNotBlank(),
                    shape = RoundedCornerShape(20.dp),
                    onClick = {
                        vm.saveEdits(task, type, title, description, labels)
                        editing = false
                    },
                ) { Text("Save") }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column {
                    Text(
                        "Type",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TypePicker(type, { type = it }, vm.state.value.recentTypes)
                }
                TonalTextField(title, { title = it }, "Title", modifier = Modifier.fillMaxWidth())
                Column {
                    LabelsField(labels, { labels = it }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Separate with commas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Column {
                    TonalTextField(
                        description, { description = it }, "Description",
                        modifier = Modifier.fillMaxWidth().height(160.dp), minLines = 5,
                    )
                    Row(
                        Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Markdown supported",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Created ${formatAge(task.createdAt)} by ${task.createdBy} · Updated ${formatAge(task.updatedAt)} by ${task.updatedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        } else {
            // View (1c): project name in the bar, edit + more_vert icons. No filled button.
            var menuOpen by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        projectName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (task.status == TaskStatus.OPEN) {
                            DropdownMenuItem(
                                text = { Text("Close task") },
                                leadingIcon = { Icon(Icons.Filled.TaskAlt, contentDescription = null) },
                                onClick = { menuOpen = false; vm.setStatus(task, TaskStatus.CLOSED) },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Reopen") },
                                onClick = { menuOpen = false; vm.setStatus(task, TaskStatus.OPEN) },
                            )
                        }
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                // 1. Type chip + status dot.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(typeContainer(task.type))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                taskTypeIcon(task.type),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = typeOnContainer(task.type),
                            )
                            Text(
                                task.type.value,
                                style = MaterialTheme.typography.labelMedium,
                                color = typeOnContainer(task.type),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (task.status == TaskStatus.OPEN) statusOpenColor()
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                        Text(
                            if (task.status == TaskStatus.OPEN) "Open" else "Closed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 2. Title.
                Text(
                    task.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                // 3. Labels as outlined chips.
                if (task.labels.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        task.labels.forEach { label ->
                            Box(
                                Modifier
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                // 4. Description.
                if (task.description.isNotBlank()) {
                    Box(Modifier.padding(top = 20.dp)) { MarkdownText(task.description) }
                }
                task.imagePath?.let {
                    Text(
                        "Image: $it",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                // 5. Meta.
                Text(
                    "Created ${formatAge(task.createdAt)} by ${task.createdBy} · Updated ${formatAge(task.updatedAt)} by ${task.updatedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                // 6. Close task as outlined button (Reopen when closed).
                if (task.status == TaskStatus.OPEN) {
                    OutlinedButton(
                        onClick = { vm.setStatus(task, TaskStatus.CLOSED) },
                        modifier = Modifier.padding(top = 20.dp).height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.Filled.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Close task")
                    }
                } else {
                    OutlinedButton(
                        onClick = { vm.setStatus(task, TaskStatus.OPEN) },
                        modifier = Modifier.padding(top = 20.dp).height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) { Text("Reopen") }
                }
            }
            // 7. Comments, behind a full-bleed divider.
            HorizontalDivider(Modifier.padding(top = 24.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Comments", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${task.comments.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (task.comments.isEmpty()) {
                    Text(
                        "No comments yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                task.comments.forEach {
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(
                            formatAge(it.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(it.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            }
            // Comment composer docked at the bottom, outside the scroll.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = commentDraft,
                        onValueChange = { commentDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (commentDraft.isEmpty()) {
                                    Text(
                                        "Add a comment",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
                IconButton(
                    onClick = {
                        if (commentDraft.isNotBlank()) {
                            vm.addComment(task, commentDraft)
                            commentDraft = ""
                        }
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = if (commentDraft.isBlank()) 0.38f else 1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    val context = LocalContext.current
    val codeBg = 0xFF1E293B.toInt()
    val codeFg = 0xFFE2E8F0.toInt()
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeBlockBackgroundColor(codeBg)
                        .codeBlockTextColor(codeFg)
                        .codeBackgroundColor(codeBg)
                        .codeTextColor(codeFg)
                }
            })
            .build()
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        factory = { TextView(it) },
        update = { view ->
            view.setTextColor(textColor)
            view.textSize = 16f
            markwon.setMarkdown(view, markdown)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
