package io.github.greebo72.oloop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.greebo72.oloop.core.TaskOps
import io.github.greebo72.oloop.core.TaskType

/** Type icon per the design spec (Material Symbols Rounded, outlined). */
fun taskTypeIcon(type: TaskType): ImageVector = when (type) {
    TaskType.TODO -> Icons.Outlined.Checklist
    TaskType.QUESTION -> Icons.Outlined.Help
    TaskType.DECISION -> Icons.Outlined.Gavel
    TaskType.FEATURE -> Icons.Outlined.Build
    TaskType.BUG -> Icons.Outlined.BugReport
}

/** Tonal container per type: todo = primary, feature = tertiary, the rest = secondary. */
@Composable
fun typeContainer(type: TaskType): Color = when (type) {
    TaskType.TODO -> MaterialTheme.colorScheme.primaryContainer
    TaskType.FEATURE -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
fun typeOnContainer(type: TaskType): Color = when (type) {
    TaskType.TODO -> MaterialTheme.colorScheme.onPrimaryContainer
    TaskType.FEATURE -> MaterialTheme.colorScheme.onTertiaryContainer
    else -> MaterialTheme.colorScheme.onSecondaryContainer
}

/** Borderless filled field: tonal background, radius 10, label at the top, value below. */
@Composable
fun TonalField(
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** A TonalField wrapping a plain text value. */
@Composable
fun TonalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surfaceContainer,
    minLines: Int = 1,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    TonalField(label, focused, modifier, fill) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = minLines == 1,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                letterSpacing = MaterialTheme.typography.bodyLarge.letterSpacing,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = (24 * minLines).dp)
                .onFocusChanged { focused = it.isFocused }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
    }
}

/** Labels field: input chips inside the field, comma or Enter commits a chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelsField(
    labels: List<String>,
    onLabelsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surfaceContainer,
    chipFill: Color = MaterialTheme.colorScheme.surface,
) {
    var draft by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }

    fun commit(text: String) {
        val added = TaskOps.cleanLabels(listOf(text.trim()))
        if (added.isNotEmpty()) onLabelsChange(TaskOps.cleanLabels(labels + added))
    }

    TonalField("Labels", focused, modifier, fill) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            labels.forEach { label ->
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(chipFill)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove $label",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onLabelsChange(labels.filter { it != label }) },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            BasicTextField(
                value = draft,
                onValueChange = {
                    if (it.endsWith(",")) {
                        commit(it.removeSuffix(","))
                        draft = ""
                    } else {
                        draft = it
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(draft); draft = "" }),
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .height(32.dp)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

/**
 * Type picker (design 2c): two recent types plus a "+N" chip that opens a searchable
 * bottom sheet. The current type replaces the oldest recent chip when needed.
 */
@Composable
fun TypePicker(
    type: TaskType,
    onTypeChange: (TaskType) -> Unit,
    recent: List<TaskType>,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val recents = if (recent.size >= 2) recent.take(2) else (recent + TaskType.entries.filter { it !in recent }).take(2)
    val shown = remember(type, recents) {
        if (type in recents) recents else listOf(type) + recents.take(1)
    }
    val remaining = TaskType.entries.size - shown.size

    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { t ->
            FilterChip(
                selected = t == type,
                onClick = { onTypeChange(t) },
                leadingIcon = if (t == type) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                label = { Text(t.value) },
            )
        }
        if (remaining > 0) {
            FilterChip(
                selected = false,
                onClick = { sheetOpen = true },
                trailingIcon = {
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                label = { Text("+$remaining", color = MaterialTheme.colorScheme.primary) },
            )
        }
    }

    if (sheetOpen) TypeSheet(current = type, onDismiss = { sheetOpen = false }, onPick = { onTypeChange(it); sheetOpen = false })
}

/** Modal bottom sheet with search and a 3-column type grid (design 3g). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSheet(current: TaskType, onDismiss: () -> Unit, onPick: (TaskType) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matching = TaskType.entries.filter { it.value.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Task type", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    "Search types",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            matching.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { t ->
                        TypeTile(t, selected = t == current, onClick = { onPick(t) }, modifier = Modifier.weight(1f))
                    }
                    // Keep the last row's tiles the same width when it is not full.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun TypeTile(type: TaskType, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .height(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            taskTypeIcon(type),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            type.value,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}
