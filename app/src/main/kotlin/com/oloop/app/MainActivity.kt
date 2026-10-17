package com.oloop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val viewModel: OloopViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OloopTheme { OloopApp(viewModel) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Another client may have changed the files while the app was in the background.
        if (!viewModel.state.value.dirty) viewModel.reload()
    }
}

@Composable
private fun OloopApp(vm: OloopViewModel) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setRoot(uri)
    }

    LaunchedEffect(state.banner) {
        state.banner?.let {
            snackbar.showSnackbar(it)
            vm.clearBanner()
        }
    }

    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val project = state.selectedProject
    val task = state.selectedTask

    BackHandler(enabled = state.showSettings && state.rootSelected) { vm.showSettings(false) }
    BackHandler(enabled = !wide && !state.showSettings && task != null) { vm.selectTask(null) }
    BackHandler(enabled = !state.showSettings && task == null && project != null) { vm.selectProject(null) }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when {
                !state.rootSelected || state.showSettings -> SettingsScreen(
                    state = state,
                    onPickFolder = { pickFolder.launch(null) },
                    onActorChange = vm::setActor,
                    onClose = if (state.rootSelected) ({ vm.showSettings(false) }) else null,
                )
                wide -> Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { ProjectsPane(state, vm) }
                    VerticalDivider()
                    Box(Modifier.weight(1.3f).fillMaxHeight()) { TasksPane(state, vm, showBack = false) }
                    VerticalDivider()
                    Box(Modifier.weight(1.7f).fillMaxHeight()) { DetailPane(state, vm, showBack = false) }
                }
                task != null -> DetailPane(state, vm, showBack = true)
                project != null -> TasksPane(state, vm, showBack = true)
                else -> ProjectsPane(state, vm)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    Dialogs(state, vm)
}

@Composable
private fun VerticalDivider() {
    androidx.compose.material3.VerticalDivider(Modifier.fillMaxHeight(), thickness = 1.dp)
}
