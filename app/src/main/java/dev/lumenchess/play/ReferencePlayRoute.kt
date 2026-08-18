package dev.lumenchess.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** P5 reference-fidelity presentation only. Runtime ownership stays in [PlayViewModel]. */
@Composable
fun ReferencePlayRoute(modifier: Modifier = Modifier, viewModel: PlayViewModel) {
    val ui by viewModel.uiState
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenStarted()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onScreenStarted()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenStopped()
        }
    }

    when (ui.mode) {
        PlayScreenMode.SETUP -> ReferenceSetupScreen(ui, viewModel, modifier)
        PlayScreenMode.LIVE -> ReferenceLiveScreen(ui, viewModel, modifier)
    }
}
