package dev.lumenchess.play

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.lumenchess.design.LumenMotion

private enum class ReferencePlayPage { OVERVIEW, SETUP, ARENA_PREVIEW }

/** P5 reference-fidelity presentation only. Runtime ownership stays in [PlayViewModel]. */
@Composable
fun ReferencePlayRoute(modifier: Modifier = Modifier, viewModel: PlayViewModel) {
    val ui by viewModel.uiState
    var page by rememberSaveable { mutableStateOf(ReferencePlayPage.OVERVIEW) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val slideDistance = with(LocalDensity.current) { 10.dp.roundToPx() }

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
        PlayScreenMode.LIVE -> ReferenceLiveScreen(ui, viewModel, modifier)
        PlayScreenMode.SETUP -> AnimatedContent(
            targetState = page,
            modifier = modifier,
            transitionSpec = {
                val direction = targetState.ordinal.compareTo(initialState.ordinal)
                val sign = if (direction >= 0) 1 else -1
                (fadeIn(LumenMotion.normalTween()) +
                    slideInHorizontally(LumenMotion.normalTween()) { sign * slideDistance })
                    .togetherWith(
                        fadeOut(LumenMotion.fastTween()) +
                            slideOutHorizontally(LumenMotion.fastTween()) { -sign * slideDistance },
                    )
            },
            label = "reference-play-page",
        ) { targetPage ->
            when (targetPage) {
                ReferencePlayPage.OVERVIEW -> ReferencePlayOverviewScreen(
                    ui = ui,
                    onPlayVsEngine = { page = ReferencePlayPage.SETUP },
                    onArenaPreview = { page = ReferencePlayPage.ARENA_PREVIEW },
                    onBack = { backDispatcher?.onBackPressed() },
                    modifier = Modifier,
                )
                ReferencePlayPage.SETUP -> ReferenceSetupScreen(ui, viewModel, Modifier)
                ReferencePlayPage.ARENA_PREVIEW -> ReferenceArenaPreviewScreen(
                    onBack = { page = ReferencePlayPage.OVERVIEW },
                    modifier = Modifier,
                )
            }
        }
    }
}
