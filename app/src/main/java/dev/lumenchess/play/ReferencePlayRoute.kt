package dev.lumenchess.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.lumenchess.board.ChessboardHighlights
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.ChessboardOrientation
import dev.lumenchess.board.LumenChessboard
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Square
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.LumenClock
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenEngineBadge
import dev.lumenchess.design.LumenPrimaryButton
import dev.lumenchess.design.LumenSecondaryButton
import dev.lumenchess.design.LumenSlider
import dev.lumenchess.design.LumenTopBar
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeState
import dev.lumenchess.runtime.clock.ClockReading
import kotlin.math.floor
import kotlin.math.roundToInt

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
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.onScreenStarted()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.onScreenStopped() }
    }
    when (ui.mode) {
        PlayScreenMode.SETUP -> ReferenceSetupScreen(ui, viewModel, modifier)
        PlayScreenMode.LIVE -> ReferenceLiveScreen(ui, viewModel, modifier)
    }
}

private enum class RefGlyph { BOARD, SHUFFLE, WHITE, BLACK, RANDOM, CLOCK, TARGET }

@Composable
private fun ReferenceSetupScreen(ui: PlayUiState, viewModel: PlayViewModel, modifier: Modifier) {
    var engineExpanded by remember { mutableStateOf(false) }
    var timeExpanded by remember { mutableStateOf(false) }
    var incrementExpanded by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift,LumenColors.Background)))
            .verticalScroll(rememberScrollState()).padding(horizontal=14.dp,vertical=7.dp).testTag(PLAY_SETUP_TEST_TAG),
        verticalArrangement=Arrangement.spacedBy(8.dp),
    ) {
        val frameShape=RoundedCornerShape(10.dp)
        Column(
            Modifier.fillMaxWidth().background(LumenColors.Surface.copy(alpha=.56f),frameShape)
                .border(1.dp,LumenColors.OutlineStrong,frameShape).padding(horizontal=11.dp,vertical=3.dp).testTag("p5-setup-shell"),
        ) {
            LumenTopBar(title="New Game",onBack={ viewModel.backToSetup() })
            ui.restorableGame?.let { restored ->
                CompactSurface(Modifier.fillMaxWidth().padding(bottom=8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal=9.dp,vertical=7.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp),
                    ) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                            Text("Continue game",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
                            Text("${if(restored.setup.variant==Variant.STANDARD)"Standard" else "Chess960"} · ${restored.setup.engine.displayName}",style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted)
                        }
                        LumenSecondaryButton("Resume",viewModel::resumeLastGame,Modifier.height(38.dp).testTag(PLAY_RESUME_TEST_TAG))
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(bottom=10.dp).testTag("p5-setup-content"),
                verticalArrangement=Arrangement.spacedBy(9.dp),
            ) {
                ReferenceSection("Game Mode") {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        ReferenceChoice("Standard",RefGlyph.BOARD,ui.setup.variant==Variant.STANDARD,Modifier.weight(1f).testTag("p5-setup-standard")) { viewModel.updateVariant(Variant.STANDARD) }
                        ReferenceChoice("Chess960",RefGlyph.SHUFFLE,ui.setup.variant==Variant.CHESS960,Modifier.weight(1f)) { viewModel.updateVariant(Variant.CHESS960) }
                    }
                    if(ui.setup.variant==Variant.CHESS960) {
                        val index=ui.setup.chess960Index?:518
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("Starting position",style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted)
                            Text("#$index",style=MaterialTheme.typography.labelSmall,color=LumenColors.AccentBlueBright)
                        }
                        LumenSlider(index.toFloat(),{ viewModel.updateChess960Index(it.roundToInt().coerceIn(0,959)) },0f..959f,steps=958)
                    }
                }
                ReferenceSection("Opponent") {
                    ReferenceDropdown(
                        title=ui.setup.engine.displayName,trailing=if(engineExpanded)"⌃" else "⌄",
                        leading={ LumenEngineBadge(ui.setup.engine.displayName) },expanded=engineExpanded,
                    ) { engineExpanded=!engineExpanded }
                    Text("Engine Info",modifier=Modifier.align(Alignment.End),style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceFaint)
                    if(engineExpanded) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        PlayEngine.entries.forEach { engine ->
                            ReferenceChoiceRow(engine.displayName,ui.setup.engine==engine) { viewModel.updateEngine(engine); engineExpanded=false }
                        }
                    }
                }
                ReferenceSection("Strength (Elo)") {
                    when(val target=ui.setup.strengthTarget) {
                        is EngineStrengthTarget.Elo -> {
                            Text(target.value.toString(),style=MaterialTheme.typography.labelMedium,color=LumenColors.AccentBlueBright)
                            LumenSlider(target.value.toFloat(),{
                                val snapped=((it/50f).roundToInt()*50).coerceIn(400,3000)
                                viewModel.updateStrengthTarget(EngineStrengthTarget.Elo(snapped))
                            },400f..3000f)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("400",style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted)
                                Text("3000",style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted)
                            }
                        }
                        EngineStrengthTarget.FullStrength -> Text("Maximum",style=MaterialTheme.typography.labelMedium,color=LumenColors.AccentBlueBright)
                    }
                    ReferenceFlatButton("Match My Elo",RefGlyph.TARGET,Modifier.fillMaxWidth().testTag("p5-match-my-elo"),enabled=false) {}
                }
                ReferenceSection("Strength Model") {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        listOf(EngineStrengthModel.HYBRID to "Hybrid",EngineStrengthModel.ENGINE_NATIVE to "Engine Native",EngineStrengthModel.HUMANIZED to "Humanized").forEach { (model,label) ->
                            ReferenceSegment(label,ui.setup.strengthModel==model,Modifier.weight(1f)) { viewModel.updateStrengthModel(model) }
                        }
                    }
                    Text(
                        when(ui.setup.strengthModel) {
                            EngineStrengthModel.HYBRID -> "Hybrid (default): Engine limits + humanization layer."
                            EngineStrengthModel.ENGINE_NATIVE -> "Use only the engine's native strength controls."
                            EngineStrengthModel.HUMANIZED -> "Increase Lumen's human-like move selection."
                        },style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted,
                    )
                }
                ReferenceSection("Side") {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        PlaySide.entries.forEach { side ->
                            ReferenceChoice(
                                side.name.lowercase().replaceFirstChar{it.uppercase()},
                                when(side){ PlaySide.WHITE->RefGlyph.WHITE; PlaySide.BLACK->RefGlyph.BLACK; PlaySide.RANDOM->RefGlyph.RANDOM },
                                ui.setup.side==side,Modifier.weight(1f),
                            ) { viewModel.updateSide(side) }
                        }
                    }
                }
                ReferenceSection("Time Control") {
                    ReferenceDropdown(referenceTimeCategory(ui.setup.timeControl),if(timeExpanded)"⌃" else "⌄",timeExpanded,leading={ RefIcon(RefGlyph.CLOCK,LumenColors.OnSurfaceMuted,Modifier.size(16.dp)) }) { timeExpanded=!timeExpanded }
                    if(timeExpanded) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        REFERENCE_TIME_CONTROLS.forEach { option ->
                            ReferenceChoiceRow(option.label,ui.setup.timeControl.initialMillis==option.control.initialMillis) {
                                viewModel.updateTimeControl(option.control.copy(incrementMillis=ui.setup.timeControl.incrementMillis)); timeExpanded=false
                            }
                        }
                    }
                }
                ReferenceSection("Inc / Delay") {
                    ReferenceDropdown("${ui.setup.timeControl.incrementMillis/1_000L} sec",if(incrementExpanded)"⌃" else "⌄",incrementExpanded,Modifier.testTag("p5-inc-delay")) { incrementExpanded=!incrementExpanded }
                    if(incrementExpanded) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        listOf(0L,1L,2L,5L,10L).forEach { seconds ->
                            ReferenceChoiceRow("$seconds sec",ui.setup.timeControl.incrementMillis==seconds*1_000L) {
                                viewModel.updateTimeControl(ui.setup.timeControl.copy(incrementMillis=seconds*1_000L)); incrementExpanded=false
                            }
                        }
                    }
                }
                val validationMessage=when(val validation=ui.setupValidation) {
                    PlaySetupValidation.Valid->null
                    is PlaySetupValidation.Invalid->validation.reason
                    is PlaySetupValidation.UnsupportedStrength->validation.reason
                }
                (validationMessage?:ui.message)?.let { message ->
                    Box(Modifier.fillMaxWidth().background(LumenColors.DestructiveSoft,RoundedCornerShape(6.dp)).border(1.dp,LumenColors.Destructive.copy(alpha=.42f),RoundedCornerShape(6.dp)).padding(horizontal=9.dp,vertical=7.dp)) {
                        Text(message,style=MaterialTheme.typography.labelSmall,color=LumenColors.Destructive)
                    }
                }
                LumenPrimaryButton("Start Game",viewModel::startNewGame,Modifier.fillMaxWidth().height(46.dp),ui.setupValidation is PlaySetupValidation.Valid,PLAY_START_TEST_TAG)
            }
        }
        Text("●  Match My Elo uses the rating source and range from Settings.",style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted)
        Text("●  All options are remembered per time control.",style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted)
        Spacer(Modifier.height(3.dp))
    }
}

@Composable private fun ReferenceSection(label:String,content:@Composable ColumnScope.()->Unit) = Column(verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,color=LumenColors.OnSurface); content() }

@Composable
private fun ReferenceChoice(label:String,glyph:RefGlyph,selected:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    val shape=RoundedCornerShape(5.dp)
    Row(modifier.height(42.dp).background(if(selected)LumenColors.AccentBlueGhost else LumenColors.SurfaceRaised,shape).border(1.dp,if(selected)LumenColors.AccentBlueBright else LumenColors.Outline,shape).clickable(role=Role.RadioButton,onClick=onClick).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        RefIcon(glyph,if(selected)LumenColors.OnSurface else LumenColors.OnSurfaceMuted,Modifier.size(16.dp)); Text(label,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold,color=if(selected)LumenColors.OnSurface else LumenColors.OnSurfaceMuted,maxLines=1)
    }
}

@Composable
private fun ReferenceDropdown(title:String,trailing:String,expanded:Boolean,modifier:Modifier=Modifier,leading:(@Composable()->Unit)?=null,onClick:()->Unit) {
    val shape=RoundedCornerShape(5.dp)
    Row(modifier.fillMaxWidth().height(44.dp).background(LumenColors.SurfaceRaised,shape).border(1.dp,if(expanded)LumenColors.AccentBlueBright else LumenColors.Outline,shape).clickable(role=Role.Button,onClick=onClick).padding(horizontal=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        leading?.invoke(); Text(title,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis); Text(trailing,style=MaterialTheme.typography.labelMedium,color=LumenColors.OnSurfaceMuted)
    }
}

@Composable
private fun ReferenceChoiceRow(label:String,selected:Boolean,onClick:()->Unit) {
    val shape=RoundedCornerShape(5.dp)
    Row(Modifier.fillMaxWidth().height(36.dp).background(if(selected)LumenColors.AccentBlueGhost else LumenColors.Surface,shape).border(1.dp,if(selected)LumenColors.AccentBlue.copy(alpha=.7f) else LumenColors.Outline,shape).clickable(onClick=onClick).padding(horizontal=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=if(selected)LumenColors.AccentBlueBright else LumenColors.OnSurface); if(selected) Text("✓",style=MaterialTheme.typography.labelSmall,color=LumenColors.AccentBlueBright)
    }
}

@Composable
private fun RowScope.ReferenceSegment(label:String,selected:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    val shape=RoundedCornerShape(5.dp)
    Box(modifier.height(34.dp).background(if(selected)LumenColors.AccentBlueGhost else LumenColors.SurfaceRaised,shape).border(1.dp,if(selected)LumenColors.AccentBlueBright else LumenColors.Outline,shape).clickable(role=Role.RadioButton,onClick=onClick).padding(horizontal=4.dp),contentAlignment=Alignment.Center) {
        Text(label,style=MaterialTheme.typography.labelSmall,color=if(selected)LumenColors.OnSurface else LumenColors.OnSurfaceMuted,maxLines=1)
    }
}

@Composable
private fun ReferenceFlatButton(label:String,glyph:RefGlyph,modifier:Modifier=Modifier,enabled:Boolean,onClick:()->Unit) {
    val shape=RoundedCornerShape(5.dp)
    Row(modifier.height(38.dp).background(LumenColors.SurfaceRaised,shape).border(1.dp,LumenColors.Outline,shape).clickable(enabled=enabled,role=Role.Button,onClick=onClick),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        RefIcon(glyph,if(enabled)LumenColors.OnSurfaceMuted else LumenColors.OnSurfaceFaint,Modifier.size(15.dp)); Spacer(Modifier.size(5.dp)); Text(label,style=MaterialTheme.typography.labelMedium,color=if(enabled)LumenColors.OnSurface else LumenColors.OnSurfaceMuted)
    }
}

@Composable private fun CompactSurface(modifier:Modifier=Modifier,content:@Composable()->Unit) = Box(modifier.background(LumenColors.SurfaceRaised,RoundedCornerShape(6.dp)).border(1.dp,LumenColors.Outline,RoundedCornerShape(6.dp))) { content() }

@Composable
private fun RefIcon(glyph:RefGlyph,color:UiColor,modifier:Modifier=Modifier.size(16.dp)) {
    Canvas(modifier) {
        val w=size.width; val h=size.height; val s=size.minDimension*.075f
        when(glyph) {
            RefGlyph.BOARD -> repeat(2){r->repeat(2){f->drawRect(if((r+f)%2==0)color else color.copy(alpha=.45f),Offset(w*(.12f+f*.39f),h*(.12f+r*.39f)),androidx.compose.ui.geometry.Size(w*.36f,h*.36f))}}
            RefGlyph.SHUFFLE,RefGlyph.RANDOM -> { drawLine(color,Offset(w*.16f,h*.28f),Offset(w*.76f,h*.28f),s,StrokeCap.Round); drawLine(color,Offset(w*.66f,h*.18f),Offset(w*.78f,h*.28f),s,StrokeCap.Round); drawLine(color,Offset(w*.66f,h*.38f),Offset(w*.78f,h*.28f),s,StrokeCap.Round); drawLine(color,Offset(w*.16f,h*.72f),Offset(w*.76f,h*.72f),s,StrokeCap.Round) }
            RefGlyph.WHITE,RefGlyph.BLACK -> { val fill=if(glyph==RefGlyph.WHITE)UiColor(0xFFF3F0E5) else UiColor(0xFF474B4D); drawCircle(fill,w*.15f,Offset(w*.5f,h*.27f)); val pawn=Path().apply{moveTo(w*.40f,h*.42f);lineTo(w*.60f,h*.42f);lineTo(w*.66f,h*.67f);lineTo(w*.75f,h*.80f);lineTo(w*.25f,h*.80f);lineTo(w*.34f,h*.67f);close()}; drawPath(pawn,fill); drawPath(pawn,color.copy(alpha=.75f),style=androidx.compose.ui.graphics.drawscope.Stroke(s*.45f)) }
            RefGlyph.CLOCK -> { drawCircle(color,w*.35f,Offset(w*.5f,h*.5f),style=androidx.compose.ui.graphics.drawscope.Stroke(s)); drawLine(color,Offset(w*.5f,h*.5f),Offset(w*.5f,h*.29f),s,StrokeCap.Round); drawLine(color,Offset(w*.5f,h*.5f),Offset(w*.64f,h*.58f),s,StrokeCap.Round) }
            RefGlyph.TARGET -> { drawCircle(color,w*.34f,Offset(w*.5f,h*.5f),style=androidx.compose.ui.graphics.drawscope.Stroke(s)); drawCircle(color,w*.12f,Offset(w*.5f,h*.5f),style=androidx.compose.ui.graphics.drawscope.Stroke(s)); drawLine(color,Offset(w*.5f,h*.08f),Offset(w*.5f,h*.22f),s,StrokeCap.Round) }
        }
    }
}

@Composable
private fun ReferenceLiveScreen(ui:PlayUiState,viewModel:PlayViewModel,modifier:Modifier) {
    val runtime=ui.runtime?:return; val setup=ui.resolvedSetup?:return; val humanSide=setup.humanSide; val engineSide=humanSide.opposite
    val orientation=if(humanSide==Color.WHITE) ChessboardOrientation.WHITE else ChessboardOrientation.BLACK
    val humanTurn=runtime.position.sideToMove==humanSide&&runtime.controllers.forSide(humanSide)==RuntimeController.HUMAN
    val inputEnabled=humanTurn&&!runtime.paused&&runtime.terminal==null; val premoveEnabled=!humanTurn&&!runtime.paused&&runtime.terminal==null
    val lastMove=runtime.gameTree.mainline().lastOrNull()?.move; val queuedPremove=runtime.queuedPremove?.move
    val status=when { ui.message!=null->ui.message; runtime.terminal!=null->runtime.terminal.presentationLabel(); queuedPremove!=null->"Premove ${queuedPremove.uci} queued"; runtime.paused->"Game paused"; else->null }
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift,LumenColors.Background))).padding(horizontal=8.dp,vertical=6.dp).testTag(PLAY_LIVE_TEST_TAG),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        val shape=RoundedCornerShape(9.dp)
        Column(Modifier.fillMaxWidth().background(LumenColors.Surface,shape).border(1.dp,LumenColors.OutlineStrong,shape).padding(5.dp).testTag("p5-live-shell"),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            ReferenceParticipantRow(setup.engine.displayName,ui.engineStatus,engineSide,runtime.position.sideToMove,ui.clock,true,Modifier.testTag(PLAY_ENGINE_STATUS_TEST_TAG))
            Box(Modifier.fillMaxWidth().aspectRatio(1f).border(1.dp,LumenColors.OutlineStrong).testTag(PLAY_BOARD_STAGE_TEST_TAG)) {
                LumenChessboard(runtime.position,viewModel::onBoardMove,Modifier.fillMaxSize(),orientation,ChessboardInput(tapEnabled=inputEnabled,dragEnabled=inputEnabled),ChessboardHighlights(lastMove=lastMove,premoveSquares=queuedPremove?.let{setOf(it.from,it.to)}.orEmpty()))
                if(premoveEnabled) ReferencePremoveOverlay(runtime,humanSide,orientation,viewModel::queuePremove,Modifier.fillMaxSize())
            }
            ReferenceParticipantRow("You",humanSide.name.lowercase().replaceFirstChar{it.uppercase()},humanSide,runtime.position.sideToMove,ui.clock,false)
        }
        if(!status.isNullOrBlank()) Text(status,Modifier.fillMaxWidth().padding(horizontal=3.dp),style=MaterialTheme.typography.labelSmall,color=if(ui.message!=null)LumenColors.Destructive else LumenColors.OnSurfaceMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
        ReferenceActionStrip(runtime,queuedPremove!=null,viewModel,Modifier.fillMaxWidth().testTag("p5-live-action-strip"))
    }
}

@Composable
private fun ReferenceParticipantRow(name:String,detail:String,side:Color,activeSide:Color,clock:ClockReading?,engine:Boolean,modifier:Modifier=Modifier) {
    val millis=if(side==Color.WHITE)clock?.whiteRemainingMillis else clock?.blackRemainingMillis; val active=side==activeSide
    Row(modifier.fillMaxWidth().height(46.dp).background(if(active)LumenColors.SurfaceRaised else LumenColors.Surface).padding(horizontal=7.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        if(engine) LumenEngineBadge(name) else Box(Modifier.size(28.dp).background(LumenColors.SurfaceHighest,RoundedCornerShape(6.dp)),contentAlignment=Alignment.Center){ Text("♟",style=MaterialTheme.typography.labelLarge,color=LumenColors.OnSurface) }
        Column(Modifier.weight(1f)){ Text(name,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis); Text(detail,style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted,maxLines=1,overflow=TextOverflow.Ellipsis) }
        LumenClock(referenceClockText(millis),active=active,light=!engine,modifier=Modifier.semantics{contentDescription="$name clock ${referenceClockAccessibility(millis)}"})
    }
}

private enum class RefActionGlyph { PLAY,PAUSE,FLAG,MORE,CANCEL }
@Composable private fun ReferenceActionStrip(runtime:RuntimeState,hasPremove:Boolean,viewModel:PlayViewModel,modifier:Modifier) = Row(modifier.height(50.dp).background(LumenColors.Surface,RoundedCornerShape(8.dp)).border(1.dp,LumenColors.Outline,RoundedCornerShape(8.dp))) {
    if(hasPremove) ReferenceAction("Cancel",RefActionGlyph.CANCEL,onClick=viewModel::cancelPremove)
    if(runtime.terminal==null) { ReferenceAction(if(runtime.paused)"Resume" else "Pause",if(runtime.paused)RefActionGlyph.PLAY else RefActionGlyph.PAUSE,onClick=if(runtime.paused)viewModel::resume else viewModel::pause); ReferenceAction("Resign",RefActionGlyph.FLAG,true,viewModel::resign) }
    ReferenceAction("More",RefActionGlyph.MORE,onClick=viewModel::backToSetup)
}

@Composable
private fun RowScope.ReferenceAction(label:String,glyph:RefActionGlyph,destructive:Boolean=false,onClick:()->Unit) {
    val tint=if(destructive)LumenColors.Destructive else LumenColors.OnSurfaceMuted
    Column(Modifier.weight(1f).fillMaxSize().clickable(role=Role.Button,onClick=onClick),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Canvas(Modifier.size(15.dp)) { val s=size.minDimension*.10f; when(glyph) {
            RefActionGlyph.PLAY -> { val p=Path().apply{moveTo(size.width*.30f,size.height*.18f);lineTo(size.width*.78f,size.height*.50f);lineTo(size.width*.30f,size.height*.82f);close()};drawPath(p,tint) }
            RefActionGlyph.PAUSE -> { drawLine(tint,Offset(size.width*.36f,size.height*.2f),Offset(size.width*.36f,size.height*.8f),s*1.6f,StrokeCap.Round);drawLine(tint,Offset(size.width*.64f,size.height*.2f),Offset(size.width*.64f,size.height*.8f),s*1.6f,StrokeCap.Round) }
            RefActionGlyph.FLAG -> { drawLine(tint,Offset(size.width*.30f,size.height*.14f),Offset(size.width*.30f,size.height*.86f),s,StrokeCap.Round);val p=Path().apply{moveTo(size.width*.32f,size.height*.2f);lineTo(size.width*.78f,size.height*.32f);lineTo(size.width*.32f,size.height*.48f);close()};drawPath(p,tint) }
            RefActionGlyph.MORE -> repeat(3){i->drawCircle(tint,s,Offset(size.width*(.28f+i*.22f),size.height*.5f))}
            RefActionGlyph.CANCEL -> { drawLine(tint,Offset(size.width*.25f,size.height*.25f),Offset(size.width*.75f,size.height*.75f),s,StrokeCap.Round);drawLine(tint,Offset(size.width*.75f,size.height*.25f),Offset(size.width*.25f,size.height*.75f),s,StrokeCap.Round) }
        }}
        Spacer(Modifier.height(1.dp));Text(label,style=MaterialTheme.typography.labelSmall,color=if(destructive)LumenColors.Destructive else LumenColors.OnSurface)
    }
}

@Composable
private fun ReferencePremoveOverlay(runtime:RuntimeState,humanSide:Color,orientation:ChessboardOrientation,onPremove:(Move)->Unit,modifier:Modifier=Modifier) {
    var from by remember(runtime.positionRevision){mutableStateOf<Square?>(null)};LaunchedEffect(runtime.queuedPremove){if(runtime.queuedPremove==null)from=null}
    Box(modifier.semantics{contentDescription="Premove input board"}.testTag(PLAY_PREMOVE_OVERLAY_TEST_TAG).pointerInput(runtime.positionRevision,orientation,humanSide){detectTapGestures{offset->
        val square=referenceSquareFromOffset(offset,size,orientation)?:return@detectTapGestures;val selected=from
        if(selected==null){if(runtime.position[square]?.color==humanSide)from=square}else if(runtime.position[square]?.color==humanSide){from=square}else{val piece=runtime.position[selected];val promotion=if(piece?.type==PieceType.PAWN&&square.rank==if(humanSide==Color.WHITE)7 else 0)PieceType.QUEEN else null;onPremove(Move(selected,square,promotion));from=null}
    }})
}

private fun referenceSquareFromOffset(offset:Offset,size:IntSize,orientation:ChessboardOrientation):Square? {
    if(size.width<=0||size.height<=0||offset.x !in 0f..size.width.toFloat()||offset.y !in 0f..size.height.toFloat())return null
    val vf=floor(offset.x/(size.width/8f)).toInt().coerceIn(0,7);val vr=floor(offset.y/(size.height/8f)).toInt().coerceIn(0,7)
    return when(orientation){ChessboardOrientation.WHITE->Square.of(vf,7-vr);ChessboardOrientation.BLACK->Square.of(7-vf,vr)}
}

private data class RefTimeControlOption(val label:String,val control:PlayTimeControl)
private val REFERENCE_TIME_CONTROLS=listOf(RefTimeControlOption("1 min",PlayTimeControl(60_000L,0L)),RefTimeControlOption("3 min",PlayTimeControl(180_000L,0L)),RefTimeControlOption("5 min",PlayTimeControl(300_000L,0L)),RefTimeControlOption("10 min",PlayTimeControl(600_000L,0L)),RefTimeControlOption("15 min",PlayTimeControl(900_000L,0L)),RefTimeControlOption("30 min",PlayTimeControl(1_800_000L,0L)))
private fun referenceTimeCategory(value:PlayTimeControl)=when{value.initialMillis<=120_000L->"Bullet";value.initialMillis<=300_000L->"Blitz";else->"Rapid"}
private fun referenceClockText(millis:Long?):String{if(millis==null)return"--:--";val safe=millis.coerceAtLeast(0L);return"%d:%02d".format(safe/60_000L,(safe%60_000L)/1_000L)}
private fun referenceClockAccessibility(millis:Long?):String{if(millis==null)return"unavailable";val safe=millis.coerceAtLeast(0L);return"${safe/60_000L} minutes ${(safe%60_000L)/1_000L} seconds"}
