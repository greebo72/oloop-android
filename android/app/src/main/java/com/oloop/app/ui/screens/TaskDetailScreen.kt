package com.oloop.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oloop.app.model.Task
import com.oloop.app.model.TaskStatus
import com.oloop.app.ui.OloopViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: Task,
    onBack: () -> Unit,
    onSaveEdits: (title: String, description: String, labels: List<String>) -> Unit,
    onToggleStatus: () -> Unit,
    onAddComment: (String) -> Unit
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var labelsText by remember(task.id) { mutableStateOf(task.labels.joinToString(", ")) }
    var newCommentText by remember { mutableStateOf("") }

    val hasUnsavedChanges = remember(title, description, labelsText, task) {
        val currentLabels = labelsText.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        title.trim() != task.title || description.trim() != task.description || currentLabels != task.labels
    }

    var showLeaveWarning by remember { mutableStateOf(false) }

    if (showLeaveWarning) {
        AlertDialog(
            onDismissRequest = { showLeaveWarning = false },
            title = { Text("Unsaved changes") },
            text = { Text("You have unsaved changes in this task. Do you want to discard them?") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveWarning = false
                    onBack()
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveWarning = false }) {
                    Text("Keep editing")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Detail") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showLeaveWarning = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Status Toggle Button (Close / Reopen)
                    Button(
                        onClick = onToggleStatus,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (task.status == TaskStatus.OPEN)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (task.status == TaskStatus.OPEN)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (task.status == TaskStatus.OPEN) "Close task" else "Reopen task")
                    }

                    // Save Button
                    if (hasUnsavedChanges) {
                        FilledTonalButton(
                            onClick = {
                                val cleanLabels = labelsText.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .distinct()
                                onSaveEdits(title, description, cleanLabels)
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info: Type & Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeBadge(type = task.type)
                Surface(
                    color = if (task.status == TaskStatus.OPEN)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (task.status == TaskStatus.OPEN) "OPEN" else task.type.closedLabel.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Editable Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )

            // Editable Labels
            OutlinedTextField(
                value = labelsText,
                onValueChange = { labelsText = it },
                label = { Text("Labels (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Editable Markdown Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Markdown)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )

            // Image path display if set
            task.imagePath?.let { path ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Image: $path", fontSize = 13.sp)
                    }
                }
            }

            // Comments Section
            Text("Comments (${task.comments.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            task.comments.forEach { comment ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(comment.body, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            comment.createdAt,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Add Comment Field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    label = { Text("Add a comment...") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (newCommentText.isNotBlank()) {
                            onAddComment(newCommentText)
                            newCommentText = ""
                        }
                    },
                    enabled = newCommentText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }

            // Metadata Footer
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Created: ${task.createdAt} by ${task.createdBy}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    "Updated: ${task.updatedAt} by ${task.updatedBy}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Text("Task ID: ${task.id}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
