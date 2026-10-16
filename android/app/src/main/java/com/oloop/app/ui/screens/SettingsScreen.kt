package com.oloop.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentActor: String,
    currentFolderUri: String?,
    onBack: () -> Unit,
    onSaveActor: (String) -> Unit,
    onPickFolderClick: () -> Unit
) {
    var actorInput by remember { mutableStateOf(currentActor) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Data Root Folder Setting
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data Root Folder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = currentFolderUri ?: "No folder selected yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Button(onClick = onPickFolderClick) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (currentFolderUri == null) "Choose Folder" else "Change Folder")
                    }
                }
            }

            // User Name (Actor) Setting
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("User Name (Actor)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Written to createdBy and updatedBy on every task write.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = actorInput,
                        onValueChange = { actorInput = it },
                        label = { Text("Your Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onSaveActor(actorInput) },
                        enabled = actorInput.trim().isNotEmpty() && actorInput.trim() != currentActor
                    ) {
                        Text("Save Name")
                    }
                }
            }
        }
    }
}
