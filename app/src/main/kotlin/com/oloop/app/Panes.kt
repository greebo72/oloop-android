package com.oloop.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oloop.core.Grouping
import com.oloop.core.GroupingMode
import com.oloop.core.Task
import com.oloop.core.TaskStatus

@Composable
fun PaneHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        } else {
            Box(Modifier.padding(start = 12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
    HorizontalDivider()
}

@Composable
fun ProjectsPane(state: UiState, vm: OloopViewModel) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PaneHeader("oloop") {
                IconButton(onClick = vm::reload) { Icon(Icons.Default.Refresh, contentDescription = "Reload") }
                IconButton(onClick = { vm.showSettings(true) }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.unreadable.isNotEmpty()) {
                    item { UnreadableCard(state) }
                }
                if (state.projects.isEmpty() && !state.loading) {
                    item { Text("No projects found in this folder.", style = MaterialTheme.typography.bodyMedium) }
                }
                items(state.projects, key = { it.project.id }) { data ->
                    val open = data.tasks.count { it.status == TaskStatus.OPEN }
                    val latest = data.tasks.maxOfOrNull { Grouping.instant(it.updatedAt) }
                    Card(
                        Modifier.fillMaxWidth().clickable { vm.selectProject(data.project.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (data.project.id == state.selectedProjectId)
                                MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(data.project.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$open open of ${data.tasks.size}" +
                                    (latest?.let { " · updated ${Grouping.age(it.toString())} ago" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(76.dp)) }
            }
        }
        FloatingActionButton(
            onClick = { vm.showNewProject(true) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "New project") }
    }
}

@Composable
private fun UnreadableCard(state: UiState) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${state.unreadable.size} file(s) cannot be read and are left untouched",
                style = MaterialTheme.typography.titleSmall,
            )
            if (expanded) {
                state.unreadable.forEach {
                    Text("${it.path}: ${it.reason}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            } else {
                Text("Tap to show", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TasksPane(state: UiState, vm: OloopViewModel, showBack: Boolean) {
    val data = state.selectedProject
    if (data == null) {
        Placeholder("Select a project")
        return
    }
    val visible = data.tasks.filter { state.showClosed || it.status == TaskStatus.OPEN }
    val groups = Grouping.group(visible, state.grouping)
    var menuOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PaneHeader(data.project.name, onBack = if (showBack) ({ vm.selectProject(null) }) else null) {
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("Group: ${state.grouping.label}") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        GroupingMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = { vm.setGrouping(mode); menuOpen = false },
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show closed tasks", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.showClosed, onCheckedChange = { vm.toggleClosed() })
            }
            HorizontalDivider()
            if (visible.isEmpty()) {
                Placeholder(if (data.tasks.isEmpty()) "No tasks yet" else "No open tasks")
            } else LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                groups.forEach { group ->
                    group.title?.let { title ->
                        item(key = "header-$title") {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(group.tasks, key = { it.id }) { task ->
                        TaskRow(task, selected = task.id == state.selectedTaskId) { vm.selectTask(task.id) }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { vm.showNewTask(true) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "New task") }
    }
}

@Composable
private fun TaskRow(task: Task, selected: Boolean, onClick: () -> Unit) {
    val status = if (task.status == TaskStatus.CLOSED) task.type.closedLabel else task.type.value
    Column(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            task.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val labels = if (task.labels.isEmpty()) "" else " · " + task.labels.joinToString(", ")
        Text(
            "$status$labels · ${Grouping.age(task.updatedAt)} ago",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
