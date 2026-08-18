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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    )
private val SetupSectionStyle
    @Composable get() = MaterialTheme.typography.labelLarge.copy(
        fontSize = 14.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )
private val SetupControlStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(
        fontSize = 14.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
private val SetupSupportingStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Normal,
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
    val shellTopHighlight = LumenColors.OnSurface.copy(alpha = .07f)

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
                    elevation = 4.dp,
                    shape = shellShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = .44f),
                    spotColor = Color.Black.copy(alpha = .58f),
                )
                .clip(shellShape)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to lerp(LumenColors.Surface, LumenColors.SurfaceRaised, .42f),
                            .12f to LumenColors.Surface,
                            1f to lerp(LumenColors.Surface, Color.Black, .08f),
                        ),
                    ),
                )
                .drawBehind {
                    drawLine(
                        color = shellTopHighlight,
                        start = Offset(13.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 13.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = .7.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                .border(1.dp, LumenColors.OutlineStrong.copy(alpha = .92f), shellShape)
                .padding(horizontal = 11.dp, vertical = 7.dp)
                .testTag("p5-setup-shell"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SetupHeader(onBack)

            ui.restorableGame?.let { restored ->
                SetupTactileSurface(
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag(PLAY_RESUME_TEST_TAG),
                    onClick = viewModel::resumeLastGame,
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SetupSection("Game Mode", Modifier.testTag("p5-setup-game-mode")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        height = 60.dp,
                        modifier = Modifier.fillMaxWidth().testTag("p5-setup-opponent"),
                        leading = {
                            LumenEngineBadge(ui.setup.engine.displayName, Modifier.size(32.dp))
                        },
                        onClick = { engineExpanded = !engineExpanded },
                    )
                    Text(
                        "Engine Info",
                        modifier = Modifier.align(Alignment.End),
                        style = SetupSupportingStyle,
                        color = LumenColors.AccentBlueBright.copy(alpha = .78f),
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
                        style = SetupControlStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        color = LumenColors.OnSurface.copy(alpha = .97f),
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
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("p5-match-my-elo"),
                        mode = SetupSurfaceMode.DISABLED,
                        enabled = false,
                        onClick = {},
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            SetupIcon(SetupGlyph.TARGET, LumenColors.OnSurfaceMuted, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Match My Elo", style = SetupControlStyle, color = LumenColors.OnSurfaceMuted)
                        }
                    }
                }

                SetupSection("Strength Model", info = true) {
                    SetupStrengthModelControl(
                        selected = ui.setup.strengthModel,
                        onSelect = viewModel::updateStrengthModel,
                    )
                    Text(
                        when (ui.setup.strengthModel) {
                            EngineStrengthModel.HYBRID -> "Hybrid (default): Engine limits + humanization layer."
                            EngineStrengthModel.ENGINE_NATIVE -> "Use only the engine's native strength controls."
                            EngineStrengthModel.HUMANIZED -> "Increase Lumen's human-like move selection."
                        },
                        style = SetupSupportingStyle,
                        color = LumenColors.OnSurfaceMuted.copy(alpha = .96f),
                    )
                }

                SetupSection("Side", Modifier.testTag("p5-setup-side")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
                            SetupIcon(SetupGlyph.CLOCK, LumenColors.OnSurfaceMuted, Modifier.size(20.dp))
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
                            .background(LumenColors.DestructiveSoft, RoundedCornerShape(7.dp))
                            .border(1.dp, LumenColors.Destructive.copy(alpha = .42f), RoundedCornerShape(7.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(message, style = SetupSupportingStyle, color = LumenColors.Destructive)
                    }
                }

                Box(Modifier.fillMaxWidth().testTag("p5-setup-start")) {
                    SetupTactileSurface(
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag(PLAY_START_TEST_TAG),
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
                                style = SetupControlStyle.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Bold),
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
    val backTint = LumenColors.OnSurfaceMuted.copy(alpha = .94f)
    val scale by animateFloatAsState(
        targetValue = if (pressed) .90f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "p5-setup-back-scale",
    )
    Box(Modifier.fillMaxWidth().height(40.dp).testTag("p5-setup-header")) {
        Text(
            "New Game",
            modifier = Modifier.align(Alignment.Center),
            style = SetupHeaderStyle,
            color = LumenColors.OnSurface.copy(alpha = .99f),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(38.dp)
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
            Canvas(Modifier.size(18.dp)) {
                val stroke = 1.55.dp.toPx()
                drawLine(
                    backTint,
                    Offset(size.width * .70f, size.height * .16f),
                    Offset(size.width * .31f, size.height * .50f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    backTint,
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        modifier = modifier.height(if (compact) 56.dp else 60.dp),
        mode = if (selected) SetupSurfaceMode.SELECTED else SetupSurfaceMode.NORMAL,
        role = Role.RadioButton,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 9.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
        ) {
            SetupIcon(
                glyph,
                if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
                Modifier.size(if (compact) 20.dp else 23.dp),
            )
            Spacer(Modifier.size(if (compact) 7.dp else 9.dp))
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
    height: Dp = 56.dp,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    SetupTactileSurface(
        modifier = modifier.height(height),
        mode = if (expanded) SetupSurfaceMode.SELECTED else SetupSurfaceMode.NORMAL,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading?.invoke()
            Text(
                title,
                Modifier.weight(1f),
                style = SetupControlStyle,
                color = LumenColors.OnSurface.copy(alpha = .98f),
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
    val shape = RoundedCornerShape(if (primary) 7.dp else 8.dp)

    val scale by animateFloatAsState(
        targetValue = if (pressed) .98f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-surface-scale-$mode",
    )
    val pressOffset by animateDpAsState(
        targetValue = if (pressed) 1.15.dp else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-surface-offset-$mode",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) .35.dp else if (primary) 4.dp else 3.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-surface-shadow-$mode",
    )
    val lowerEdge by animateDpAsState(
        targetValue = if (pressed) .3.dp else if (primary) 3.1.dp else 2.5.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-surface-edge-$mode",
    )
    val border by animateColorAsState(
        targetValue = when {
            disabled -> LumenColors.OutlineStrong.copy(alpha = .58f)
            pressed && (selected || primary) -> LumenColors.AccentBlueBright.copy(alpha = 1f)
            selected -> LumenColors.AccentBlueBright.copy(alpha = .92f)
            primary -> LumenColors.AccentBlueBright.copy(alpha = .82f)
            pressed -> LumenColors.OutlineStrong.copy(alpha = 1f)
            else -> LumenColors.OutlineStrong.copy(alpha = .78f)
        },
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-surface-border-$mode",
    )
    val selectedGlow = LumenColors.AccentBlueBright

    val baseLeft = when {
        primary -> lerp(LumenColors.AccentBlue, Color.White, .23f)
        selected -> lerp(LumenColors.SurfaceRaised, LumenColors.AccentBlue, .16f)
        disabled -> lerp(LumenColors.SurfaceRaised, Color.Black, .05f)
        else -> lerp(LumenColors.Surface, LumenColors.SurfaceRaised, .46f)
    }
    val baseRight = when {
        primary -> lerp(LumenColors.AccentBlue, Color.Black, .12f)
        selected -> lerp(LumenColors.Surface, LumenColors.AccentBlue, .07f)
        disabled -> lerp(LumenColors.Surface, Color.Black, .08f)
        else -> lerp(LumenColors.Surface, Color.Black, .06f)
    }

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = pressOffset.toPx()
                alpha = if (disabled) .82f else 1f
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (pressed) .16f else .38f),
                spotColor = Color.Black.copy(alpha = if (pressed) .20f else .52f),
            )
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(baseLeft, baseRight)))
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (primary) .14f else if (selected) .075f else .055f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * .52f,
                    ),
                )
                if (selected) {
                    drawRect(
                        brush = Brush.radialGradient(
                            listOf(
                                selectedGlow.copy(alpha = if (pressed) .13f else .085f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * .28f, size.height * .38f),
                            radius = size.width * .58f,
                        ),
                    )
                }
                if (pressed) {
                    drawRect(Color.Black.copy(alpha = if (primary) .075f else .065f))
                }
                val edge = lowerEdge.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = .06f),
                            Color.Black.copy(alpha = if (primary) .34f else .28f),
                        ),
                        startY = size.height - edge,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height - edge),
                    size = Size(size.width, edge),
                )
                drawLine(
                    color = Color.White.copy(alpha = if (pressed) .13f else if (primary) .16f else .08f),
                    start = Offset(9.dp.toPx(), 1.dp.toPx()),
                    end = Offset(size.width - 9.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = .75.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val inset = 1.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = if (pressed) .075f else .04f),
                    topLeft = Offset(inset, inset),
                    size = Size(
                        (size.width - inset * 2f).coerceAtLeast(0f),
                        (size.height - inset * 2f).coerceAtLeast(0f),
                    ),
                    cornerRadius = CornerRadius(7.dp.toPx()),
                    style = Stroke(.6.dp.toPx()),
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
        targetValue = if (dragging) 1.08f else 1f,
        animationSpec = if (dragging) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-slider-thumb",
    )
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val accent = LumenColors.AccentBlueBright
    val trackColor = LumenColors.SurfaceHighest.copy(alpha = .98f)

    Canvas(
        modifier
            .height(26.dp)
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
        val track = 2.8.dp.toPx()
        val thumbX = size.width * fraction
        drawLine(
            color = trackColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = track,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent.copy(alpha = .96f),
            start = Offset(0f, y),
            end = Offset(thumbX, y),
            strokeWidth = track,
            cap = StrokeCap.Round,
        )
        drawCircle(Color.Black.copy(alpha = .42f), 7.2.dp.toPx() * thumbScale, Offset(thumbX, y))
        drawCircle(accent.copy(alpha = .99f), 5.8.dp.toPx() * thumbScale, Offset(thumbX, y))
        drawCircle(Color.White.copy(alpha = .15f), 4.0.dp.toPx() * thumbScale, Offset(thumbX, y))
    }
}

@Composable
private fun SetupStrengthModelControl(
    selected: EngineStrengthModel,
    onSelect: (EngineStrengthModel) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val topHighlight = LumenColors.OnSurface.copy(alpha = .07f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(
                elevation = 2.8.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = .32f),
                spotColor = Color.Black.copy(alpha = .44f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(LumenColors.SurfaceRaised, Color.White, .025f),
                        LumenColors.Surface,
                    ),
                ),
            )
            .drawBehind {
                drawRect(
                    Color.Black.copy(alpha = .24f),
                    topLeft = Offset(0f, size.height - 2.2.dp.toPx()),
                    size = Size(size.width, 2.2.dp.toPx()),
                )
                drawLine(
                    topHighlight,
                    Offset(8.dp.toPx(), 1.dp.toPx()),
                    Offset(size.width - 8.dp.toPx(), 1.dp.toPx()),
                    .65.dp.toPx(),
                    StrokeCap.Round,
                )
            }
            .border(1.dp, LumenColors.OutlineStrong.copy(alpha = .82f), shape)
            .testTag("p5-setup-strength-model"),
    ) {
        SetupSegment(
            label = "Hybrid",
            selected = selected == EngineStrengthModel.HYBRID,
            modifier = Modifier.weight(1f),
            first = true,
            onClick = { onSelect(EngineStrengthModel.HYBRID) },
        )
        SetupSegment(
            label = "Engine Native",
            selected = selected == EngineStrengthModel.ENGINE_NATIVE,
            modifier = Modifier.weight(1.25f),
            onClick = { onSelect(EngineStrengthModel.ENGINE_NATIVE) },
        )
        SetupSegment(
            label = "Humanized",
            selected = selected == EngineStrengthModel.HUMANIZED,
            modifier = Modifier.weight(1.12f),
            last = true,
            onClick = { onSelect(EngineStrengthModel.HUMANIZED) },
        )
    }
}

@Composable
private fun SetupSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    first: Boolean = false,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressOffset by animateDpAsState(
        targetValue = if (pressed) .8.dp else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "setup-segment-offset-$label",
    )
    val shape = RoundedCornerShape(
        topStart = if (first) 7.dp else 0.dp,
        bottomStart = if (first) 7.dp else 0.dp,
        topEnd = if (last) 7.dp else 0.dp,
        bottomEnd = if (last) 7.dp else 0.dp,
    )
    val baseFace = if (selected) {
        lerp(LumenColors.SurfaceRaised, LumenColors.AccentBlue, .17f)
    } else {
        lerp(LumenColors.Surface, LumenColors.SurfaceRaised, .25f)
    }
    val face = if (pressed) lerp(baseFace, Color.Black, .10f) else baseFace
    val segmentAccent = LumenColors.AccentBlueBright
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { translationY = pressOffset.toPx() }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(face, Color.White, if (selected) .025f else .012f),
                        face,
                    ),
                ),
            )
            .drawBehind {
                if (selected) {
                    drawRect(
                        brush = Brush.radialGradient(
                            listOf(
                                segmentAccent.copy(alpha = if (pressed) .11f else .07f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * .45f, size.height * .35f),
                            radius = size.width * .8f,
                        ),
                    )
                }
                drawRect(
                    Color.Black.copy(alpha = if (pressed) .10f else .20f),
                    topLeft = Offset(0f, size.height - if (pressed) .35.dp.toPx() else 2.dp.toPx()),
                    size = Size(size.width, if (pressed) .35.dp.toPx() else 2.dp.toPx()),
                )
            }
            .border(
                1.dp,
                if (selected) segmentAccent.copy(alpha = if (pressed) 1f else .90f)
                else LumenColors.Outline.copy(alpha = .72f),
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
            style = SetupSupportingStyle.copy(
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SetupDropMenu(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, shape, clip = false)
            .clip(shape)
            .background(LumenColors.Surface)
            .border(1.dp, LumenColors.OutlineStrong.copy(alpha = .80f), shape),
        content = content,
    )
}

@Composable
private fun SetupMenuRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(if (selected) LumenColors.AccentBlueGhost else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = SetupControlStyle.copy(fontSize = 13.5.sp),
            color = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
        )
        if (selected) SetupCheck(Modifier.size(14.dp))
    }
}

@Composable
private fun SetupChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = LumenMotion.normalTween(),
        label = "setup-chevron",
    )
    val tint = LumenColors.OnSurfaceMuted.copy(alpha = .94f)
    Canvas(Modifier.size(15.dp).graphicsLayer { rotationZ = rotation }) {
        val stroke = 1.45.dp.toPx()
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
    val tint = LumenColors.OnSurfaceMuted.copy(alpha = .84f)
    Canvas(Modifier.size(13.dp)) {
        drawCircle(tint, size.minDimension * .43f, style = Stroke(1.dp.toPx()))
        drawCircle(tint, .8.dp.toPx(), Offset(center.x, size.height * .32f))
        drawLine(
            tint,
            Offset(center.x, size.height * .45f),
            Offset(center.x, size.height * .69f),
            1.dp.toPx(),
            StrokeCap.Round,
        )
    }
}

@Composable
private fun SetupNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SetupCheck(Modifier.size(14.dp))
        Text(
            text,
            style = SetupSupportingStyle,
            color = LumenColors.OnSurfaceMuted.copy(alpha = .96f),
        )
    }
}

@Composable
private fun SetupCheck(modifier: Modifier = Modifier) {
    val tint = LumenColors.AccentBlueBright.copy(alpha = .90f)
    Canvas(modifier) {
        drawCircle(tint.copy(alpha = .11f), size.minDimension * .46f)
        drawCircle(tint.copy(alpha = .76f), size.minDimension * .43f, style = Stroke(.8.dp.toPx()))
        val stroke = .9.dp.toPx()
        drawLine(
            tint,
            Offset(size.width * .25f, size.height * .53f),
            Offset(size.width * .43f, size.height * .70f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(size.width * .43f, size.height * .70f),
            Offset(size.width * .76f, size.height * .33f),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun SetupIcon(glyph: SetupGlyph, tint: Color, modifier: Modifier = Modifier) {
    val accent = LumenColors.AccentBlueBright.copy(alpha = .92f)
    Canvas(modifier) {
        val stroke = 1.2.dp.toPx()
        when (glyph) {
            SetupGlyph.BOARD,
            SetupGlyph.CHESS960,
            -> {
                val boardSize = size.minDimension * .80f
                val left = (size.width - boardSize) / 2f
                val top = (size.height - boardSize) / 2f
                val cell = boardSize / 4f
                drawRoundRect(
                    color = tint.copy(alpha = .15f),
                    topLeft = Offset(left - 1.dp.toPx(), top - 1.dp.toPx()),
                    size = Size(boardSize + 2.dp.toPx(), boardSize + 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.6.dp.toPx()),
                )
                repeat(4) { row ->
                    repeat(4) { col ->
                        val bright = if (glyph == SetupGlyph.CHESS960) {
                            (row + col * 2) % 3 != 0
                        } else {
                            (row + col) % 2 == 0
                        }
                        drawRect(
                            color = if (bright) tint.copy(alpha = .94f) else Color.Black.copy(alpha = .44f),
                            topLeft = Offset(left + col * cell, top + row * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
                if (glyph == SetupGlyph.CHESS960) {
                    drawLine(
                        accent,
                        Offset(left + cell * .35f, top + cell * 3.55f),
                        Offset(left + cell * 3.65f, top + cell * .45f),
                        .95.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
            SetupGlyph.WHITE,
            SetupGlyph.BLACK,
            -> {
                val pieceTint = if (glyph == SetupGlyph.WHITE) tint else tint.copy(alpha = .60f)
                drawCircle(pieceTint, size.minDimension * .15f, Offset(center.x, size.height * .28f))
                val body = Path().apply {
                    moveTo(size.width * .37f, size.height * .43f)
                    lineTo(size.width * .63f, size.height * .43f)
                    lineTo(size.width * .70f, size.height * .70f)
                    lineTo(size.width * .30f, size.height * .70f)
                    close()
                }
                drawPath(body, pieceTint)
                drawLine(
                    pieceTint,
                    Offset(size.width * .28f, size.height * .77f),
                    Offset(size.width * .72f, size.height * .77f),
                    stroke * 1.25f,
                    StrokeCap.Round,
                )
            }
            SetupGlyph.RANDOM -> {
                drawRoundRect(
                    tint.copy(alpha = .26f),
                    Offset(size.width * .19f, size.height * .25f),
                    Size(size.width * .45f, size.height * .55f),
                    CornerRadius(1.7.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawRoundRect(
                    tint.copy(alpha = .84f),
                    Offset(size.width * .36f, size.height * .17f),
                    Size(size.width * .45f, size.height * .55f),
                    CornerRadius(1.7.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(tint, Offset(size.width * .43f, size.height * .47f), Offset(size.width * .68f, size.height * .47f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .62f, size.height * .39f), Offset(size.width * .70f, size.height * .47f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .70f, size.height * .47f), Offset(size.width * .62f, size.height * .55f), stroke, StrokeCap.Round)
            }
            SetupGlyph.CLOCK -> {
                drawCircle(tint.copy(alpha = .14f), size.minDimension * .38f)
                drawCircle(tint, size.minDimension * .35f, style = Stroke(stroke))
                drawLine(tint, center, Offset(center.x, size.height * .28f), stroke, StrokeCap.Round)
                drawLine(tint, center, Offset(size.width * .65f, size.height * .56f), stroke, StrokeCap.Round)
            }
            SetupGlyph.TARGET -> {
                drawCircle(tint, size.minDimension * .35f, style = Stroke(stroke))
                drawCircle(tint.copy(alpha = .86f), size.minDimension * .14f, style = Stroke(stroke * .9f))
                drawCircle(tint, size.minDimension * .05f)
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
