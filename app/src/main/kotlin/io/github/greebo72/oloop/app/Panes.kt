package io.github.greebo72.oloop.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.greebo72.oloop.core.DueDates
import io.github.greebo72.oloop.core.Grouping
import io.github.greebo72.oloop.core.GroupingMode
import io.github.greebo72.oloop.core.Task
import io.github.greebo72.oloop.core.TaskStatus

/** Formats [Grouping.age] for display: "just now" or "5 min ago". */
fun formatAge(timestamp: String): String =
    Grouping.age(timestamp).let { if (it == "just now") it else "$it ago" }

/** Small top app bar, 64 dp: leading icon, title with optional subtitle, trailing actions. */
@Composable
fun OloopTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backIcon: @Composable (() -> Unit)? = null,
    logo: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                if (backIcon != null) backIcon()
                else Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else if (logo) {
            Image(
                painter = painterResource(R.drawable.oloop_logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 12.dp, end = 8.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

@Composable
fun ProjectsPane(state: UiState, vm: OloopViewModel) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OloopTopBar("oloop", logo = true) {
                IconButton(onClick = vm::reload) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") }
                IconButton(onClick = { vm.showSettings(true) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
                if (state.unreadable.isNotEmpty()) {
                    item { UnreadableRow(state) }
                }
                if (state.projects.isEmpty() && !state.loading) {
                    item {
                        Text(
                            "No projects found in this folder.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                items(state.projects, key = { it.project.id }) { data ->
                    val open = data.tasks.count { it.status == TaskStatus.OPEN }
                    val latest = data.tasks.maxOfOrNull { Grouping.instant(it.updatedAt) }
                    ProjectRow(
                        name = data.project.name,
                        updated = latest?.let { "Updated ${formatAge(it.toString())}" } ?: "No tasks yet",
                        counts = "$open / ${data.tasks.size} open",
                        overdue = DueDates.overdueCount(data.tasks),
                        onClick = { vm.selectProject(data.project.id) },
                    )
                }
            }
        }
        NewFab("New project", Modifier.align(Alignment.BottomEnd).padding(16.dp)) { vm.showNewProject(true) }
    }
}

/** Flat project row: 40 dp folder icon on primaryContainer, name, updated, open count. */
@Composable
private fun ProjectRow(name: String, updated: String, counts: String, overdue: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                updated,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(counts, style = MaterialTheme.typography.labelMedium)
            // Where something is late, the project says so without being opened.
            if (overdue > 0) OverdueBadge(Modifier.padding(top = 4.dp), text = "$overdue overdue")
        }
    }
}

@Composable
private fun UnreadableRow(state: UiState) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "${state.unreadable.size} file(s) cannot be read and are left untouched",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        if (expanded) {
            state.unreadable.forEach {
                Text(
                    "${it.path}: ${it.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
    val openCount = data.tasks.count { it.status == TaskStatus.OPEN }
    val overdueCount = DueDates.overdueCount(data.tasks)
    val visible = data.tasks.filter {
        (state.showClosed || it.status == TaskStatus.OPEN) && (!state.onlyOverdue || DueDates.isOverdue(it))
    }
    val grouped = state.grouping != GroupingMode.NONE
    var menuOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // The three filters sit in the top bar, right of the project name: icons only, 52 dp each.
            OloopTopBar(
                title = data.project.name,
                subtitle = "$openCount open",
                onBack = if (showBack) ({ vm.selectProject(null) }) else null,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Only offered where something is overdue, or while it is on, so it can be turned off.
                if (overdueCount > 0 || state.onlyOverdue) {
                    FilterButton(checked = state.onlyOverdue, onCheckedChange = { vm.toggleOverdue() }) {
                        // The number says how many; the badge sits on the icon.
                        BadgedBox(badge = { Badge { Text("$overdueCount") } }) {
                            Icon(
                                Icons.Outlined.EventBusy,
                                contentDescription = "Only overdue tasks ($overdueCount)",
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                FilterButton(checked = state.showClosed, onCheckedChange = { vm.toggleClosed() }) {
                    Icon(
                        if (state.showClosed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (state.showClosed) "Hide closed tasks" else "Show closed tasks",
                        modifier = Modifier.size(28.dp),
                    )
                }
                Box {
                    FilterButton(checked = state.grouping != GroupingMode.NONE, onCheckedChange = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "Group: ${state.grouping.label}",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        GroupingMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                trailingIcon = if (mode == state.grouping) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else {
                                    null
                                },
                                onClick = { vm.setGrouping(mode); menuOpen = false },
                            )
                        }
                    }
                }
                }
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            if (visible.isEmpty()) {
                Placeholder(
                    when {
                        data.tasks.isEmpty() -> "No tasks yet"
                        state.onlyOverdue -> "No overdue tasks"
                        else -> "No open tasks"
                    },
                )
            } else LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                if (grouped) {
                    Grouping.group(visible, state.grouping).forEach { group ->
                        group.title?.let { title ->
                            item(key = "header-$title") { SectionHeader(title) }
                        }
                        items(group.tasks, key = { it.id }) { task ->
                            TaskRow(task) { vm.selectTask(task.id) }
                        }
                    }
                } else {
                    items(visible.filter { it.status == TaskStatus.OPEN }, key = { it.id }) { task ->
                        TaskRow(task) { vm.selectTask(task.id) }
                    }
                    if (state.showClosed && visible.any { it.status == TaskStatus.CLOSED }) {
                        item(key = "header-Closed") { SectionHeader("Closed") }
                        items(visible.filter { it.status == TaskStatus.CLOSED }, key = { it.id }) { task ->
                            TaskRow(task) { vm.selectTask(task.id) }
                        }
                    }
                }
            }
        }
        // The FAB is hidden while the new-task dialog is open.
        if (!state.showNewTask) {
            NewFab("New task", Modifier.align(Alignment.BottomEnd).padding(16.dp)) { vm.showNewTask(true) }
        }
    }
}

/**
 * A 52 dp icon toggle for the filters in the top bar: primary container when on, plain when off.
 * The colors are the ones of the app's own containers, so the icon reads on them in dark mode too.
 */
@Composable
private fun FilterButton(checked: Boolean, onCheckedChange: (Boolean) -> Unit, content: @Composable () -> Unit) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        content = content,
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun NewFab(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(16.dp),
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(text) },
    )
}

/** Flat list row: tonal type icon, title, "type · labels", trailing age. */
@Composable
private fun TaskRow(task: Task, onClick: () -> Unit) {
    val isClosed = task.status == TaskStatus.CLOSED
    val age = Grouping.age(task.updatedAt)
    val metaLine = buildString {
        append(task.type.value)
        if (!isClosed && task.labels.any()) append(" · ").append(task.labels.joinToString(", "))
        if (isClosed) append(" · closed by ").append(task.updatedBy)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isClosed) MaterialTheme.colorScheme.surfaceContainer
                    else typeContainer(task.type),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isClosed) {
                Icon(
                    Icons.Filled.TaskAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            } else {
                Icon(
                    taskTypeIcon(task.type),
                    contentDescription = task.type.value,
                    modifier = Modifier.size(20.dp),
                    tint = typeOnContainer(task.type),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isClosed) androidx.compose.ui.text.font.FontWeight.Normal else androidx.compose.ui.text.font.FontWeight.Medium,
                ),
                color = if (isClosed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (isClosed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                metaLine,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isClosed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The due date on its own line, so it does not push the labels out of the meta line.
            // A closed task shows its date but is never overdue.
            DueDates.display(task.dueDate)?.let { shown ->
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (DueDates.isOverdue(task)) OverdueBadge()
                    Text(
                        shown,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isClosed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            age,
            style = MaterialTheme.typography.labelMedium,
            color = if (isClosed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
