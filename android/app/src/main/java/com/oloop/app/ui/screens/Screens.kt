package com.oloop.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oloop.app.model.*
import com.oloop.app.ui.OloopViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    projects: List<Project>,
    unreadableFiles: List<String>,
    onSelectProject: (Project) -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("oloop", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (unreadableFiles.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Unreadable files (${unreadableFiles.size}):",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            unreadableFiles.forEach { err ->
                                Text("• $err", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            if (projects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No oloop projects found in root folder.\nEnsure subfolders contain a valid project.md.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(projects) { project ->
                ListItem(
                    modifier = Modifier.clickable { onSelectProject(project) },
                    headlineContent = {
                        Text(project.name, fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text(
                            "${project.openTasksCount} open tasks • ${project.totalTasksCount} total",
                            fontSize = 13.sp
                        )
                    },
                    trailingContent = {
                        project.latestTaskUpdatedAt?.let {
                            Text(
                                OloopViewModel.formatRelativeAge(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    project: Project,
    tasks: List<Task>,
    showClosed: Boolean,
    groupingMode: GroupingMode,
    onBack: () -> Unit,
    onSelectTask: (Task) -> Unit,
    onToggleShowClosed: () -> Unit,
    onSetGroupingMode: (GroupingMode) -> Unit,
    onCreateTaskClick: () -> Unit
) {
    var groupingMenuOpen by remember { mutableStateOf(false) }

    // Filter tasks based on showClosed
    val visibleTasks = remember(tasks, showClosed) {
        if (showClosed) tasks else tasks.filter { it.status == TaskStatus.OPEN }
    }

    // Sort tasks within groups by updatedAt descending
    val sortedTasks = remember(visibleTasks) {
        visibleTasks.sortedByDescending { it.updatedAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${visibleTasks.size} tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Grouping selector button
                    Box {
                        IconButton(onClick = { groupingMenuOpen = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Grouping")
                        }
                        DropdownMenu(
                            expanded = groupingMenuOpen,
                            onDismissRequest = { groupingMenuOpen = false }
                        ) {
                            GroupingMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    trailingIcon = {
                                        if (mode == groupingMode) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        onSetGroupingMode(mode)
                                        groupingMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // Show closed toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertidally,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        FilterChip(
                            selected = showClosed,
                            onClick = onToggleShowClosed,
                            label = { Text("Closed") },
                            leadingIcon = {
                                if (showClosed) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTaskClick) {
                Icon(Icons.Default.Add, contentDescription = "New task")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (groupingMode) {
                GroupingMode.NONE -> {
                    items(sortedTasks) { task ->
                        TaskRowItem(task = task, onClick = { onSelectTask(task) })
                        HorizontalDivider()
                    }
                }
                GroupingMode.TASK_TYPE -> {
                    TaskType.entries.forEach { type ->
                        val groupTasks = sortedTasks.filter { it.type == type }
                        if (groupTasks.isNotEmpty()) {
                            item {
                                GroupHeader(title = "${type.value.uppercase()} (${groupTasks.size})")
                            }
                            items(groupTasks) { task ->
                                TaskRowItem(task = task, onClick = { onSelectTask(task) })
                                HorizontalDivider()
                            }
                        }
                    }
                }
                GroupingMode.TASK_STATUS -> {
                    val openTasks = sortedTasks.filter { it.status == TaskStatus.OPEN }
                    val closedTasks = sortedTasks.filter { it.status == TaskStatus.CLOSED }

                    if (openTasks.isNotEmpty()) {
                        item { GroupHeader(title = "OPEN (${openTasks.size})") }
                        items(openTasks) { task ->
                            TaskRowItem(task = task, onClick = { onSelectTask(task) })
                            HorizontalDivider()
                        }
                    }
                    if (closedTasks.isNotEmpty()) {
                        item { GroupHeader(title = "CLOSED (${closedTasks.size})") }
                        items(closedTasks) { task ->
                            TaskRowItem(task = task, onClick = { onSelectTask(task) })
                            HorizontalDivider()
                        }
                    }
                }
                GroupingMode.CREATION_DATE, GroupingMode.MODIFICATION_DATE -> {
                    // Buckets: Today, Yesterday, This week, This month, Older
                    items(sortedTasks) { task ->
                        TaskRowItem(task = task, onClick = { onSelectTask(task) }, showAge = true)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun GroupHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun TaskRowItem(
    task: Task,
    onClick: () -> Unit,
    showAge: Boolean = true
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(type = task.type)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        supportingContent = {
            if (task.labels.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    task.labels.take(4).forEach { label ->
                        Text(
                            "#$label",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                if (task.status == TaskStatus.CLOSED) {
                    Text(
                        task.type.closedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (showAge) {
                    Text(
                        OloopViewModel.formatRelativeAge(task.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    )
}

@Composable
fun TypeBadge(type: TaskType) {
    val (bg, fg) = when (type) {
        TaskType.TODO -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        TaskType.QUESTION -> Pair(Color(0xFFFFF8E1), Color(0xFFF57F17))
        TaskType.DECISION -> Pair(Color(0xFFEDE7F6), Color(0xFF512DA8))
        TaskType.FEATURE -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        TaskType.BUG -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
    }

    Box(
        modifier = Modifier
            .background(bg, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = type.value,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
