package dev.lumenchess.play

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenEngineBadge
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import kotlin.math.roundToInt

private enum class SetupGlyph { BOARD, CHESS960, WHITE, BLACK, RANDOM, CLOCK, TARGET }
private enum class SetupSurfaceMode { NORMAL, SELECTED, PRIMARY, DISABLED }

private val SetupHeaderStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(
        fontSize = 19.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )
private val SetupSectionStyle
    @Composable get() = MaterialTheme.typography.labelLarge.copy(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
private val SetupControlStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(
        fontSize = 13.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
private val SetupSupportingStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
    )

@Composable
internal fun ReferenceSetupScreen(
    ui: PlayUiState,
    viewModel: PlayViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    var engineExpanded by remember { mutableStateOf(false) }
    var timeExpanded by remember { mutableStateOf(false) }
    var incrementExpanded by remember { mutableStateOf(false) }
    val shellShape = RoundedCornerShape(12.dp)
    val pageGlow = LumenColors.AccentBlueBright

    Column(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(LumenColors.Background, Color.Black, .08f),
                        LumenColors.Background,
                    ),
                ),
            )
            .drawBehind {
                val center = Offset(size.width * .22f, size.height * .21f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            pageGlow.copy(alpha = .018f),
                            pageGlow.copy(alpha = .005f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.width * .72f,
                    ),
                    center = center,
                    radius = size.width * .72f,
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag(PLAY_SETUP_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 3.5.dp,
                    shape = shellShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = .42f),
                    spotColor = Color.Black.copy(alpha = .56f),
                )
                .clip(shellShape)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to lerp(LumenColors.Surface, LumenColors.SurfaceRaised, .34f),
                            .12f to LumenColors.Surface,
                            1f to lerp(LumenColors.Surface, Color.Black, .07f),
                        ),
                    ),
                )
                .drawBehind {
                    drawLine(
                        color = LumenColors.OnSurface.copy(alpha = .055f),
                        start = Offset(13.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 13.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = .65.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                .border(1.dp, LumenColors.OutlineStrong.copy(alpha = .92f), shellShape)
                .padding(horizontal = 11.dp, vertical = 7.dp)
                .testTag("p5-setup-shell"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SetupHeader(onBack)

            ui.restorableGame?.let { restored ->
                SetupTactileSurface(
                    modifier = Modifier.fillMaxWidth().height(42.dp).testTag(PLAY_RESUME_TEST_TAG),
                    onClick = viewModel::resumeLastGame,
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Continue game", style = SetupControlStyle, color = LumenColors.OnSurface)
                            Text(
                                "${if (restored.setup.variant == Variant.STANDARD) "Standard" else "Chess960"} · ${restored.setup.engine.displayName}",
                                style = SetupSupportingStyle,
                                color = LumenColors.OnSurfaceMuted,
                            )
                        }
                        SetupChevron(expanded = false)
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().testTag("p5-setup-content"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SetupSection("Game Mode", Modifier.testTag("p5-setup-game-mode")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SetupChoiceTile(
                            label = "Standard",
                            glyph = SetupGlyph.BOARD,
                            selected = ui.setup.variant == Variant.STANDARD,
                            modifier = Modifier.weight(1f).testTag("p5-setup-standard"),
                            onClick = { viewModel.updateVariant(Variant.STANDARD) },
                        )
                        SetupChoiceTile(
                            label = "Chess960",
                            glyph = SetupGlyph.CHESS960,
                            selected = ui.setup.variant == Variant.CHESS960,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.updateVariant(Variant.CHESS960) },
                        )
                    }
                    if (ui.setup.variant == Variant.CHESS960) {
                        val index = ui.setup.chess960Index ?: 518
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Starting position", style = SetupSupportingStyle, color = LumenColors.OnSurfaceMuted)
                            Text("#$index", style = SetupSupportingStyle, color = LumenColors.AccentBlueBright)
                        }
                        SetupSlider(
                            value = index.toFloat(),
                            valueRange = 0f..959f,
                            modifier = Modifier.fillMaxWidth(),
                            onValueChange = {
                                viewModel.updateChess960Index(it.roundToInt().coerceIn(0, 959))
                            },
                        )
                    }
                }

                SetupSection("Opponent") {
                    SetupSelector(
                        title = ui.setup.engine.displayName,
                        expanded = engineExpanded,
                        modifier = Modifier.fillMaxWidth().testTag("p5-setup-opponent"),
                        leading = { LumenEngineBadge(ui.setup.engine.displayName) },
                        onClick = { engineExpanded = !engineExpanded },
                    )
                    Text(
                        "Engine Info",
                        modifier = Modifier.align(Alignment.End),
                        style = SetupSupportingStyle,
                        color = LumenColors.AccentBlueBright.copy(alpha = .72f),
                    )
                    if (engineExpanded) {
                        SetupDropMenu {
                            PlayEngine.entries.forEach { engine ->
                                SetupMenuRow(
                                    label = engine.displayName,
                                    selected = ui.setup.engine == engine,
                                    onClick = {
                                        viewModel.updateEngine(engine)
                                        engineExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                SetupSection("Strength (Elo)", info = true) {
                    val target = ui.setup.strengthTarget
                    val elo = (target as? EngineStrengthTarget.Elo)?.value ?: 3000
                    Text(
                        if (target is EngineStrengthTarget.Elo) elo.toString() else "Maximum",
                        style = SetupSupportingStyle.copy(fontWeight = FontWeight.Medium),
                        color = LumenColors.OnSurface.copy(alpha = .94f),
                    )
                    SetupSlider(
                        value = elo.toFloat(),
                        valueRange = 400f..3000f,
                        modifier = Modifier.fillMaxWidth().testTag("p5-setup-strength-slider"),
                        onValueChange = {
                            val snapped = ((it / 50f).roundToInt() * 50).coerceIn(400, 3000)
                            viewModel.updateStrengthTarget(EngineStrengthTarget.Elo(snapped))
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("400", style = SetupSupportingStyle, color = LumenColors.OnSurfaceMuted)
                        Text("3000", style = SetupSupportingStyle, color = LumenColors.OnSurfaceMuted)
                    }
                    SetupTactileSurface(
                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("p5-match-my-elo"),
                        mode = SetupSurfaceMode.DISABLED,
                        enabled = false,
                        onClick = {},
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            SetupIcon(SetupGlyph.TARGET, LumenColors.OnSurfaceMuted, Modifier.size(15.dp))
                            Spacer(Modifier.size(7.dp))
                            Text("Match My Elo", style = SetupControlStyle, color = LumenColors.OnSurfaceMuted)
                        }
                    }
                }

                SetupSection("Strength Model", info = true) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("p5-setup-strength-model"),
                    ) {
                        SetupSegment(
                            "Hybrid",
                            ui.setup.strengthModel == EngineStrengthModel.HYBRID,
                            Modifier.weight(1f),
                            first = true,
                        ) { viewModel.updateStrengthModel(EngineStrengthModel.HYBRID) }
                        SetupSegment(
                            "Engine Native",
                            ui.setup.strengthModel == EngineStrengthModel.ENGINE_NATIVE,
                            Modifier.weight(1.25f),
                        ) { viewModel.updateStrengthModel(EngineStrengthModel.ENGINE_NATIVE) }
                        SetupSegment(
                            "Humanized",
                            ui.setup.strengthModel == EngineStrengthModel.HUMANIZED,
                            Modifier.weight(1.12f),
                            last = true,
                        ) { viewModel.updateStrengthModel(EngineStrengthModel.HUMANIZED) }
                    }
                    Text(
                        when (ui.setup.strengthModel) {
                            EngineStrengthModel.HYBRID -> "Hybrid (default): Engine limits + humanization layer."
                            EngineStrengthModel.ENGINE_NATIVE -> "Use only the engine's native strength controls."
                            EngineStrengthModel.HUMANIZED -> "Increase Lumen's human-like move selection."
                        },
                        style = SetupSupportingStyle,
                        color = LumenColors.OnSurfaceMuted,
                    )
                }

                SetupSection("Side", Modifier.testTag("p5-setup-side")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlaySide.entries.forEach { side ->
                            SetupChoiceTile(
                                label = side.name.lowercase().replaceFirstChar { it.uppercase() },
                                glyph = when (side) {
                                    PlaySide.WHITE -> SetupGlyph.WHITE
                                    PlaySide.BLACK -> SetupGlyph.BLACK
                                    PlaySide.RANDOM -> SetupGlyph.RANDOM
                                },
                                selected = ui.setup.side == side,
                                modifier = Modifier.weight(1f),
                                compact = true,
                                onClick = { viewModel.updateSide(side) },
                            )
                        }
                    }
                }

                SetupSection("Time Control") {
                    SetupSelector(
                        title = referenceTimeCategory(ui.setup.timeControl),
                        expanded = timeExpanded,
                        modifier = Modifier.fillMaxWidth().testTag("p5-setup-time"),
                        leading = {
                            SetupIcon(SetupGlyph.CLOCK, LumenColors.OnSurfaceMuted, Modifier.size(16.dp))
                        },
                        onClick = { timeExpanded = !timeExpanded },
                    )
                    if (timeExpanded) {
                        SetupDropMenu {
                            REFERENCE_TIME_CONTROLS.forEach { option ->
                                SetupMenuRow(
                                    label = option.label,
                                    selected = ui.setup.timeControl.initialMillis == option.control.initialMillis,
                                    onClick = {
                                        viewModel.updateTimeControl(
                                            option.control.copy(incrementMillis = ui.setup.timeControl.incrementMillis),
                                        )
                                        timeExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                SetupSection("Inc / Delay") {
                    SetupSelector(
                        title = "${ui.setup.timeControl.incrementMillis / 1_000L} sec",
                        expanded = incrementExpanded,
                        modifier = Modifier.fillMaxWidth().testTag("p5-inc-delay"),
                        onClick = { incrementExpanded = !incrementExpanded },
                    )
                    if (incrementExpanded) {
                        SetupDropMenu {
                            listOf(0L, 1L, 2L, 5L, 10L).forEach { seconds ->
                                SetupMenuRow(
                                    label = "$seconds sec",
                                    selected = ui.setup.timeControl.incrementMillis == seconds * 1_000L,
                                    onClick = {
                                        viewModel.updateTimeControl(
                                            ui.setup.timeControl.copy(incrementMillis = seconds * 1_000L),
                                        )
                                        incrementExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                val validationMessage = when (val validation = ui.setupValidation) {
                    PlaySetupValidation.Valid -> null
                    is PlaySetupValidation.Invalid -> validation.reason
                    is PlaySetupValidation.UnsupportedStrength -> validation.reason
                }
                (validationMessage ?: ui.message)?.let { message ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(LumenColors.DestructiveSoft, RoundedCornerShape(6.dp))
                            .border(1.dp, LumenColors.Destructive.copy(alpha = .42f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    ) {
                        Text(message, style = SetupSupportingStyle, color = LumenColors.Destructive)
                    }
                }

                Box(Modifier.fillMaxWidth().testTag("p5-setup-start")) {
                    SetupTactileSurface(
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag(PLAY_START_TEST_TAG),
                        mode = if (ui.setupValidation is PlaySetupValidation.Valid) {
                            SetupSurfaceMode.PRIMARY
                        } else {
                            SetupSurfaceMode.DISABLED
                        },
                        enabled = ui.setupValidation is PlaySetupValidation.Valid,
                        onClick = viewModel::startNewGame,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Start Game",
                                style = SetupControlStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                color = if (ui.setupValidation is PlaySetupValidation.Valid) {
                                    Color(0xFFF7FBFD)
                                } else {
                                    LumenColors.OnSurfaceFaint
                                },
                            )
                        }
                    }
                }
            }
        }

        SetupNote(
            text = "Match My Elo is preview-only in this build.",
            modifier = Modifier.testTag("p5-setup-note-1"),
        )
        SetupNote(
            text = "Your selected strength, side and clock apply when the game starts.",
            modifier = Modifier.testTag("p5-setup-note-2"),
        )
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
private fun SetupHeader(onBack: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .90f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "p5-setup-back-scale",
    )
    Box(Modifier.fillMaxWidth().height(36.dp).testTag("p5-setup-header")) {
        Text(
            "New Game",
            modifier = Modifier.align(Alignment.Center),
            style = SetupHeaderStyle,
            color = LumenColors.OnSurface.copy(alpha = .99f),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(34.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onBack,
                )
                .testTag("p5-setup-back"),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.size(17.dp)) {
                val stroke = 1.45.dp.toPx()
                val tint = LumenColors.OnSurfaceMuted.copy(alpha = .94f)
                drawLine(
                    tint,
                    Offset(size.width * .70f, size.height * .16f),
                    Offset(size.width * .31f, size.height * .50f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * .31f, size.height * .50f),
                    Offset(size.width * .70f, size.height * .84f),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun SetupSection(
    label: String,
    modifier: Modifier = Modifier,
    info: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = SetupSectionStyle, color = LumenColors.OnSurface.copy(alpha = .98f))
            if (info) SetupInfoIcon()
        }
        content()
    }
}

@Composable
private fun SetupChoiceTile(
    label: String,
    glyph: SetupGlyph,
    selected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    SetupTactileSurface(
        modifier = modifier.height(44.dp),
        mode = if (selected) SetupSurfaceMode.SELECTED else SetupSurfaceMode.NORMAL,
        role = Role.RadioButton,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
        ) {
            SetupIcon(
                glyph,
                if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
                Modifier.size(if (compact) 16.dp else 18.dp),
            )
            Spacer(Modifier.size(if (compact) 6.dp else 7.dp))
            Text(
                label,
                style = SetupControlStyle,
                color = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SetupSelector(
    title: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    SetupTactileSurface(
        modifier = modifier.height(44.dp),
        mode = if (expanded) SetupSurfaceMode.SELECTED else SetupSurfaceMode.NORMAL,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Text(
                title,
                Modifier.weight(1f),
                style = SetupControlStyle,
                color = LumenColors.OnSurface.copy(alpha = .97f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SetupChevron(expanded)
        }
    }
}

@Composable
private fun SetupTactileSurface(
    modifier: Modifier,
    mode: SetupSurfaceMode = SetupSurfaceMode.NORMAL,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val primary = mode == SetupSurfaceMode.PRIMARY
    val selected = mode == SetupSurfaceMode.SELECTED
    val disabled = mode == SetupSurfaceMode.DISABLED || !enabled
    val shape = RoundedCornerShape(if (primary) 7.dp else 6.dp)

    val scale by animateFloatAsState(
        targetValue = if (pressed) .982f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-surface-scale-$mode",
    )
    val pressOffset by animateDpAsState(
        targetValue = if (pressed) .65.dp else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-surface-offset-$mode",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) .45.dp else if (primary) 3.dp else 2.2.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-surface-shadow-$mode",
    )
    val lowerEdge by animateDpAsState(
        targetValue = if (pressed) .45.dp else if (primary) 2.2.dp else 1.55.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-surface-edge-$mode",
    )
    val border by animateColorAsState(
        targetValue = when {
            disabled -> LumenColors.Outline.copy(alpha = .48f)
            pressed && (selected || primary) -> LumenColors.AccentBlueBright.copy(alpha = .98f)
            selected -> LumenColors.AccentBlueBright.copy(alpha = .92f)
            primary -> LumenColors.AccentBlueBright.copy(alpha = .72f)
            pressed -> LumenColors.OutlineStrong.copy(alpha = .96f)
            else -> LumenColors.Outline.copy(alpha = .82f)
        },
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-surface-border-$mode",
    )

    val baseLeft = when {
        primary -> lerp(LumenColors.AccentBlue, Color.White, .18f)
        selected -> lerp(LumenColors.SurfaceRaised, LumenColors.AccentBlue, .13f)
        disabled -> lerp(LumenColors.Surface, Color.Black, .02f)
        else -> lerp(LumenColors.Surface, LumenColors.SurfaceRaised, .32f)
    }
    val baseRight = when {
        primary -> lerp(LumenColors.AccentBlue, Color.Black, .14f)
        selected -> lerp(LumenColors.Surface, LumenColors.AccentBlue, .055f)
        else -> lerp(LumenColors.Surface, Color.Black, .055f)
    }

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = pressOffset.toPx()
                alpha = if (disabled) .72f else 1f
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (pressed) .22f else .34f),
                spotColor = Color.Black.copy(alpha = if (pressed) .28f else .46f),
            )
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(baseLeft, baseRight)))
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (primary) .105f else .035f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * .48f,
                    ),
                )
                if (selected) {
                    drawRect(
                        brush = Brush.radialGradient(
                            listOf(
                                LumenColors.AccentBlueBright.copy(alpha = if (pressed) .10f else .065f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * .28f, size.height * .45f),
                            radius = size.width * .62f,
                        ),
                    )
                }
                if (pressed) {
                    drawRect(Color.Black.copy(alpha = if (primary) .045f else .035f))
                }
                val edge = lowerEdge.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = .05f),
                            Color.Black.copy(alpha = if (primary) .28f else .22f),
                        ),
                        startY = size.height - edge,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height - edge),
                    size = Size(size.width, edge),
                )
                drawLine(
                    color = Color.White.copy(alpha = if (pressed) .09f else if (primary) .13f else .055f),
                    start = Offset(8.dp.toPx(), 1.dp.toPx()),
                    end = Offset(size.width - 8.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = .6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val inset = 1.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = if (pressed) .055f else .027f),
                    topLeft = Offset(inset, inset),
                    size = Size(
                        (size.width - inset * 2f).coerceAtLeast(0f),
                        (size.height - inset * 2f).coerceAtLeast(0f),
                    ),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = Stroke(.5.dp.toPx()),
                )
            }
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !disabled,
                role = role,
                onClick = onClick,
            ),
    ) {
        content()
    }
}

@Composable
private fun SetupSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.10f else 1f,
        animationSpec = if (dragging) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-slider-thumb",
    )
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val accent = LumenColors.AccentBlueBright

    Canvas(
        modifier
            .height(18.dp)
            .pointerInput(valueRange) {
                fun update(x: Float) {
                    val f = (x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + (valueRange.endInclusive - valueRange.start) * f)
                }
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        update(it.x)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    update(change.position.x)
                }
            },
    ) {
        val y = size.height * .5f
        val track = 2.4.dp.toPx()
        val thumbX = size.width * fraction
        drawLine(
            color = LumenColors.SurfaceHighest.copy(alpha = .94f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = track,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent.copy(alpha = .94f),
            start = Offset(0f, y),
            end = Offset(thumbX, y),
            strokeWidth = track,
            cap = StrokeCap.Round,
        )
        drawCircle(Color.Black.copy(alpha = .36f), 6.3.dp.toPx() * thumbScale, Offset(thumbX, y))
        drawCircle(accent.copy(alpha = .98f), 5.1.dp.toPx() * thumbScale, Offset(thumbX, y))
        drawCircle(Color.White.copy(alpha = .12f), 3.6.dp.toPx() * thumbScale, Offset(thumbX, y))
    }
}

@Composable
private fun RowScope.SetupSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    first: Boolean = false,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(
        topStart = if (first) 6.dp else 0.dp,
        bottomStart = if (first) 6.dp else 0.dp,
        topEnd = if (last) 6.dp else 0.dp,
        bottomEnd = if (last) 6.dp else 0.dp,
    )
    val face = if (selected) {
        lerp(LumenColors.SurfaceRaised, LumenColors.AccentBlue, .12f)
    } else {
        LumenColors.SurfaceRaised
    }
    Box(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (pressed) lerp(face, Color.Black, .08f) else face)
            .border(
                1.dp,
                if (selected) LumenColors.AccentBlueBright.copy(alpha = .92f) else LumenColors.Outline.copy(alpha = .78f),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = SetupSupportingStyle.copy(fontSize = 10.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SetupDropMenu(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, shape, clip = false)
            .clip(shape)
            .background(LumenColors.Surface)
            .border(1.dp, LumenColors.OutlineStrong.copy(alpha = .78f), shape),
        content = content,
    )
}

@Composable
private fun SetupMenuRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(if (selected) LumenColors.AccentBlueGhost else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = SetupControlStyle.copy(fontSize = 12.5.sp),
            color = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
        )
        if (selected) SetupCheck(Modifier.size(13.dp))
    }
}

@Composable
private fun SetupChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = LumenMotion.normalTween(),
        label = "setup-chevron",
    )
    Canvas(Modifier.size(14.dp).graphicsLayer { rotationZ = rotation }) {
        val tint = LumenColors.OnSurfaceMuted.copy(alpha = .94f)
        val stroke = 1.35.dp.toPx()
        drawLine(
            tint,
            Offset(size.width * .24f, size.height * .40f),
            Offset(size.width * .50f, size.height * .65f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(size.width * .50f, size.height * .65f),
            Offset(size.width * .76f, size.height * .40f),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun SetupInfoIcon() {
    Canvas(Modifier.size(12.dp)) {
        val tint = LumenColors.OnSurfaceMuted.copy(alpha = .80f)
        drawCircle(tint, size.minDimension * .43f, style = Stroke(.9.dp.toPx()))
        drawCircle(tint, .75.dp.toPx(), Offset(center.x, size.height * .32f))
        drawLine(
            tint,
            Offset(center.x, size.height * .45f),
            Offset(center.x, size.height * .69f),
            .9.dp.toPx(),
            StrokeCap.Round,
        )
    }
}

@Composable
private fun SetupNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SetupCheck(Modifier.padding(top = 1.dp).size(12.dp))
        Text(text, style = SetupSupportingStyle, color = LumenColors.OnSurfaceMuted)
    }
}

@Composable
private fun SetupCheck(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tint = LumenColors.AccentBlueBright.copy(alpha = .88f)
        drawCircle(tint.copy(alpha = .10f), size.minDimension * .46f)
        drawCircle(tint.copy(alpha = .72f), size.minDimension * .43f, style = Stroke(.75.dp.toPx()))
        val stroke = .85.dp.toPx()
        drawLine(tint, Offset(size.width * .25f, size.height * .53f), Offset(size.width * .43f, size.height * .70f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * .43f, size.height * .70f), Offset(size.width * .76f, size.height * .33f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun SetupIcon(glyph: SetupGlyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.1.dp.toPx()
        when (glyph) {
            SetupGlyph.BOARD,
            SetupGlyph.CHESS960,
            -> {
                val boardSize = size.minDimension * .78f
                val left = (size.width - boardSize) / 2f
                val top = (size.height - boardSize) / 2f
                val cell = boardSize / 4f
                drawRoundRect(
                    color = tint.copy(alpha = .14f),
                    topLeft = Offset(left - 1.dp.toPx(), top - 1.dp.toPx()),
                    size = Size(boardSize + 2.dp.toPx(), boardSize + 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.4.dp.toPx()),
                )
                repeat(4) { row ->
                    repeat(4) { col ->
                        val bright = if (glyph == SetupGlyph.CHESS960) {
                            (row + col * 2) % 3 != 0
                        } else {
                            (row + col) % 2 == 0
                        }
                        drawRect(
                            color = if (bright) tint.copy(alpha = .92f) else Color.Black.copy(alpha = .42f),
                            topLeft = Offset(left + col * cell, top + row * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
                if (glyph == SetupGlyph.CHESS960) {
                    drawLine(
                        LumenColors.AccentBlueBright.copy(alpha = .90f),
                        Offset(left + cell * .35f, top + cell * 3.55f),
                        Offset(left + cell * 3.65f, top + cell * .45f),
                        .85.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
            SetupGlyph.WHITE,
            SetupGlyph.BLACK,
            -> {
                val pieceTint = if (glyph == SetupGlyph.WHITE) tint else tint.copy(alpha = .58f)
                drawCircle(pieceTint, size.minDimension * .15f, Offset(center.x, size.height * .28f))
                val body = Path().apply {
                    moveTo(size.width * .37f, size.height * .43f)
                    lineTo(size.width * .63f, size.height * .43f)
                    lineTo(size.width * .70f, size.height * .70f)
                    lineTo(size.width * .30f, size.height * .70f)
                    close()
                }
                drawPath(body, pieceTint)
                drawLine(pieceTint, Offset(size.width * .28f, size.height * .77f), Offset(size.width * .72f, size.height * .77f), stroke * 1.2f, StrokeCap.Round)
            }
            SetupGlyph.RANDOM -> {
                val cardShape = RoundedCornerShape(1.5.dp)
                drawRoundRect(
                    tint.copy(alpha = .24f),
                    Offset(size.width * .19f, size.height * .25f),
                    Size(size.width * .45f, size.height * .55f),
                    CornerRadius(1.5.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawRoundRect(
                    tint.copy(alpha = .82f),
                    Offset(size.width * .36f, size.height * .17f),
                    Size(size.width * .45f, size.height * .55f),
                    CornerRadius(1.5.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(tint, Offset(size.width * .43f, size.height * .47f), Offset(size.width * .68f, size.height * .47f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .62f, size.height * .39f), Offset(size.width * .70f, size.height * .47f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .70f, size.height * .47f), Offset(size.width * .62f, size.height * .55f), stroke, StrokeCap.Round)
            }
            SetupGlyph.CLOCK -> {
                drawCircle(tint.copy(alpha = .12f), size.minDimension * .37f)
                drawCircle(tint, size.minDimension * .34f, style = Stroke(stroke))
                drawLine(tint, center, Offset(center.x, size.height * .29f), stroke, StrokeCap.Round)
                drawLine(tint, center, Offset(size.width * .64f, size.height * .56f), stroke, StrokeCap.Round)
            }
            SetupGlyph.TARGET -> {
                drawCircle(tint, size.minDimension * .34f, style = Stroke(stroke))
                drawCircle(tint.copy(alpha = .84f), size.minDimension * .13f, style = Stroke(stroke * .9f))
                drawCircle(tint, size.minDimension * .045f)
                drawLine(tint, Offset(center.x, size.height * .08f), Offset(center.x, size.height * .24f), stroke * .8f, StrokeCap.Round)
                drawLine(tint, Offset(center.x, size.height * .76f), Offset(center.x, size.height * .92f), stroke * .8f, StrokeCap.Round)
            }
        }
    }
}

private data class ReferenceTimeOption(val label: String, val control: PlayTimeControl)

private val REFERENCE_TIME_CONTROLS = listOf(
    ReferenceTimeOption("Blitz", PlayTimeControl(initialMillis = 180_000L)),
    ReferenceTimeOption("Rapid", PlayTimeControl(initialMillis = 600_000L)),
    ReferenceTimeOption("Classical", PlayTimeControl(initialMillis = 1_800_000L)),
)

private fun referenceTimeCategory(control: PlayTimeControl): String {
    val minutes = control.initialMillis / 60_000L
    return when {
        minutes <= 3L -> "Blitz"
        minutes <= 15L -> "Rapid"
        else -> "Classical"
    }
}
