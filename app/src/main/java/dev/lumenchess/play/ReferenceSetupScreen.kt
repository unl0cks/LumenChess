package dev.lumenchess.play

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.design.LumenP5IdentityPalette
import dev.lumenchess.design.lumenP5IdentityPalette
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import kotlin.math.roundToInt

/** Native translation of the visually approved P5 New Game refined 390x844 proof. */
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
    val palette = lumenP5IdentityPalette()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .newGameBackground(palette)
            .testTag(PLAY_SETUP_TEST_TAG),
    ) {
        val ref = NewGameReferenceScale(
            horizontalFactor = maxWidth.value / 390f,
            verticalFactor = maxHeight.value / 844f,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ref.dp(16f))
                .padding(top = ref.vdp(20f), bottom = ref.vdp(22f)),
        ) {
            NewGameTopBar(ref, onBack, Modifier.fillMaxWidth().height(ref.vdp(48f)))
            Spacer(Modifier.height(ref.vdp(8f)))

            NewGameSetupPlane(
                ref = ref,
                palette = palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ref.vdp(692f))
                    .testTag("p5-setup-shell"),
            ) {
                ui.restorableGame?.let { restored ->
                    NewGameSelector(
                        ref = ref,
                        palette = palette,
                        title = "Continue game · ${if (restored.setup.variant == Variant.STANDARD) "Standard" else "Chess960"} · ${restored.setup.engine.displayName}",
                        expanded = false,
                        onClick = viewModel::resumeLastGame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ref.vdp(52f))
                            .testTag(PLAY_RESUME_TEST_TAG),
                    )
                    Spacer(Modifier.height(ref.vdp(8f)))
                }

                NewGameModeSection(ref, palette, ui.setup, viewModel::updateVariant, viewModel::updateChess960Index)
                NewGameOpponentSection(
                    ref = ref,
                    palette = palette,
                    engine = ui.setup.engine,
                    expanded = engineExpanded,
                    onToggle = { engineExpanded = !engineExpanded },
                    onEngine = {
                        viewModel.updateEngine(it)
                        engineExpanded = false
                    },
                )
                NewGameStrengthSection(ref, palette, ui.setup.strengthTarget, viewModel::updateStrengthTarget)
                NewGameStrengthModelSection(ref, palette, ui.setup.strengthModel, viewModel::updateStrengthModel)
                NewGameSideSection(ref, palette, ui.setup.side, viewModel::updateSide)
                NewGameTimeSection(
                    ref = ref,
                    palette = palette,
                    control = ui.setup.timeControl,
                    expanded = timeExpanded,
                    onToggle = { timeExpanded = !timeExpanded },
                    onControl = {
                        viewModel.updateTimeControl(it.copy(incrementMillis = ui.setup.timeControl.incrementMillis))
                        timeExpanded = false
                    },
                )
                NewGameIncrementSection(
                    ref = ref,
                    palette = palette,
                    control = ui.setup.timeControl,
                    expanded = incrementExpanded,
                    onToggle = { incrementExpanded = !incrementExpanded },
                    onIncrement = {
                        viewModel.updateTimeControl(ui.setup.timeControl.copy(incrementMillis = it * 1_000L))
                        incrementExpanded = false
                    },
                )

                val validationMessage = when (val validation = ui.setupValidation) {
                    PlaySetupValidation.Valid -> null
                    is PlaySetupValidation.Invalid -> validation.reason
                    is PlaySetupValidation.UnsupportedStrength -> validation.reason
                }
                (validationMessage ?: ui.message)?.let {
                    NewGameValidationMessage(ref, it, Modifier.fillMaxWidth().padding(bottom = ref.vdp(5f)))
                }

                // Frozen proof: exactly 5 reference units between Inc / Delay and the CTA.
                Spacer(Modifier.height(ref.vdp(5f)))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ref.vdp(52f))
                        .testTag("p5-setup-start"),
                ) {
                    NewGamePrimaryButton(
                        ref = ref,
                        palette = palette,
                        enabled = ui.setupValidation is PlaySetupValidation.Valid,
                        onClick = viewModel::startNewGame,
                        modifier = Modifier.fillMaxSize().testTag("p5-setup-start-button"),
                    )
                }
            }

            Spacer(Modifier.height(ref.vdp(17f)))
            NewGameNotes(ref, palette, Modifier.fillMaxWidth().padding(horizontal = ref.dp(5f)))
        }
    }
}

private data class NewGameReferenceScale(
    val horizontalFactor: Float,
    val verticalFactor: Float,
) {
    fun dp(referencePx: Float): Dp = (referencePx * horizontalFactor).dp
    fun vdp(referencePx: Float): Dp = (referencePx * verticalFactor).dp
    fun sp(referencePx: Float) = (referencePx * minOf(horizontalFactor, verticalFactor)).sp
}

private enum class NewGameGlyph { BOARD, CHESS960, ENGINE, WHITE, BLACK, RANDOM, CLOCK, TARGET, INFO, CHECK }

private fun Modifier.newGameBackground(palette: LumenP5IdentityPalette): Modifier = drawWithCache {
    val base = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to palette.appBackgroundLift,
            .28f to palette.appBackground,
            1f to Color(0xFF070A0C),
        ),
    )
    onDrawBehind {
        drawRect(base)
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF38687A).copy(alpha = .045f), Color.Transparent),
                center = Offset(size.width * .15f, -size.height * .04f),
                radius = size.width * .92f,
            ),
        )
    }
}

@Composable
private fun NewGameTopBar(ref: NewGameReferenceScale, onBack: () -> Unit, modifier: Modifier) {
    val palette = lumenP5IdentityPalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .92f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "new-game-back-scale",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            "New Game",
            color = palette.text,
            fontSize = ref.sp(21f),
            lineHeight = ref.sp(25f),
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(ref.dp(48f))
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onBack,
                )
                .semantics { contentDescription = "Navigate back" }
                .testTag("p5-setup-back"),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(ref.dp(22f))) {
                val stroke = ref.dp(1.8f).toPx()
                val tint = Color(0xFF99A4A9)
                drawLine(tint, Offset(size.width * .64f, size.height * .19f), Offset(size.width * .35f, size.height * .50f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .35f, size.height * .50f), Offset(size.width * .64f, size.height * .81f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun NewGameSetupPlane(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(ref.dp(14f))
    Box(
        modifier
            .shadow(
                ref.dp(3.5f),
                shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = .20f),
                spotColor = Color.Black.copy(alpha = .28f),
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .drawWithCache {
                    val corner = CornerRadius(ref.dp(14f).toPx())
                    onDrawBehind {
                        drawRoundRect(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF12171A).copy(alpha = .72f),
                                    Color(0xFF0A0E10).copy(alpha = .66f),
                                ),
                            ),
                            cornerRadius = corner,
                        )
                        drawLine(
                            Brush.horizontalGradient(listOf(Color.Transparent, palette.text.copy(alpha = .045f), Color.Transparent)),
                            Offset(ref.dp(16f).toPx(), ref.vdp(1f).toPx()),
                            Offset(size.width - ref.dp(16f).toPx(), ref.vdp(1f).toPx()),
                            ref.dp(1f).toPx().coerceAtLeast(1f),
                        )
                        drawRoundRect(
                            Color(0xFF8FAAB5).copy(alpha = .055f),
                            cornerRadius = corner,
                            style = Stroke(ref.dp(1f).toPx().coerceAtLeast(1f)),
                        )
                    }
                }
                .testTag("p5-setup-plane"),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = ref.dp(11f), end = ref.dp(11f), top = ref.vdp(11f), bottom = ref.vdp(5f)),
            ) { content() }
        }
    }
}

@Composable
private fun NewGameModeSection(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    setup: PlaySetupConfig,
    onVariant: (Variant) -> Unit,
    onChess960Index: (Int) -> Unit,
) {
    val chess960 = setup.variant == Variant.CHESS960
    Column(Modifier.fillMaxWidth().heightIn(min = ref.vdp(80f)).testTag("p5-setup-game-mode")) {
        NewGameSectionHeader(ref, "Game Mode")
        NewGameChoiceBed(ref, palette, Modifier.fillMaxWidth().height(ref.vdp(56f))) {
            NewGameChoiceFace(
                ref,
                palette,
                "Standard",
                NewGameGlyph.BOARD,
                !chess960,
                { onVariant(Variant.STANDARD) },
                Modifier.weight(1f).testTag("p5-setup-standard"),
            )
            Spacer(Modifier.width(ref.dp(4f)))
            NewGameChoiceFace(
                ref,
                palette,
                "Chess960",
                NewGameGlyph.CHESS960,
                chess960,
                { onVariant(Variant.CHESS960) },
                Modifier.weight(1f),
            )
        }
        if (chess960) {
            val index = setup.chess960Index ?: 518
            Spacer(Modifier.height(ref.vdp(7f)))
            Row(
                Modifier.fillMaxWidth().testTag("p5-setup-chess960-position"),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Starting position", color = palette.muted, fontSize = ref.sp(10f), lineHeight = ref.sp(13f))
                Text("#$index", color = palette.cyan, fontSize = ref.sp(10f), lineHeight = ref.sp(13f), fontWeight = FontWeight.SemiBold)
            }
            NewGameSlider(
                ref = ref,
                palette = palette,
                value = index.toFloat(),
                valueRange = 0f..959f,
                semanticLabel = "Chess960 starting position",
                onValueChange = { onChess960Index(it.roundToInt().coerceIn(0, 959)) },
                modifier = Modifier.fillMaxWidth().height(ref.vdp(28f)),
            )
            Spacer(Modifier.height(ref.vdp(6f)))
        }
    }
}

@Composable
private fun NewGameOpponentSection(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    engine: PlayEngine,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEngine: (PlayEngine) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = ref.vdp(81f))) {
        // Engine Info intentionally remains informational text only; production has no destination.
        NewGameSectionHeader(ref, "Opponent", trailing = "Engine Info")
        NewGameSelector(
            ref = ref,
            palette = palette,
            title = engine.displayName,
            expanded = expanded,
            leading = NewGameGlyph.ENGINE,
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().height(ref.vdp(55f)).testTag("p5-setup-opponent"),
        )
        if (expanded) {
            Spacer(Modifier.height(ref.vdp(5f)))
            NewGameMenu(
                ref,
                palette,
                PlayEngine.entries.map { it.displayName to (it == engine) },
            ) { label -> onEngine(PlayEngine.entries.first { it.displayName == label }) }
            Spacer(Modifier.height(ref.vdp(6f)))
        }
    }
}

@Composable
private fun NewGameStrengthSection(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    target: EngineStrengthTarget,
    onStrength: (EngineStrengthTarget) -> Unit,
) {
    val elo = (target as? EngineStrengthTarget.Elo)?.value ?: 3000
    Column(Modifier.fillMaxWidth().height(ref.vdp(130f))) {
        NewGameSectionHeader(ref, "Strength (Elo)", info = true)
        Box(Modifier.fillMaxWidth().height(ref.vdp(19f)), contentAlignment = Alignment.CenterStart) {
            Text(
                if (target is EngineStrengthTarget.Elo) elo.toString() else "Maximum",
                color = palette.text,
                fontSize = ref.sp(14f),
                lineHeight = ref.sp(17f),
                fontWeight = FontWeight.SemiBold,
            )
        }
        NewGameSlider(
            ref = ref,
            palette = palette,
            value = elo.toFloat(),
            valueRange = 400f..3000f,
            semanticLabel = "Strength Elo",
            onValueChange = {
                val snapped = ((it / 50f).roundToInt() * 50).coerceIn(400, 3000)
                onStrength(EngineStrengthTarget.Elo(snapped))
            },
            modifier = Modifier.fillMaxWidth().height(ref.vdp(25f)).testTag("p5-setup-strength-slider"),
        )
        Row(Modifier.fillMaxWidth().height(ref.vdp(16f)), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("400", color = Color(0xFF8A959A), fontSize = ref.sp(10f), lineHeight = ref.sp(12f))
            Text("3000", color = Color(0xFF8A959A), fontSize = ref.sp(10f), lineHeight = ref.sp(12f))
        }
        NewGameDisabledAction(ref, Modifier.fillMaxWidth().height(ref.vdp(48f)).testTag("p5-match-my-elo"))
    }
}

@Composable
private fun NewGameStrengthModelSection(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    selectedModel: EngineStrengthModel,
    onModel: (EngineStrengthModel) -> Unit,
) {
    Column(Modifier.fillMaxWidth().height(ref.vdp(100f))) {
        NewGameSectionHeader(ref, "Strength Model", info = true)
        NewGameChoiceBed(
            ref,
            palette,
            Modifier.fillMaxWidth().height(ref.vdp(56f)).testTag("p5-setup-strength-model"),
        ) {
            NewGameSegment(ref, palette, "Hybrid", selectedModel == EngineStrengthModel.HYBRID, { onModel(EngineStrengthModel.HYBRID) }, Modifier.weight(1f))
            Spacer(Modifier.width(ref.dp(4f)))
            NewGameSegment(ref, palette, "Engine Native", selectedModel == EngineStrengthModel.ENGINE_NATIVE, { onModel(EngineStrengthModel.ENGINE_NATIVE) }, Modifier.weight(1.22f))
            Spacer(Modifier.width(ref.dp(4f)))
            NewGameSegment(ref, palette, "Humanized", selectedModel == EngineStrengthModel.HUMANIZED, { onModel(EngineStrengthModel.HUMANIZED) }, Modifier.weight(1.08f))
        }
        Text(
            when (selectedModel) {
                EngineStrengthModel.HYBRID -> "Hybrid (default): Engine limits + humanization layer."
                EngineStrengthModel.ENGINE_NATIVE -> "Use only the engine's native strength controls."
                EngineStrengthModel.HUMANIZED -> "Increase Lumen's human-like move selection."
            },
            modifier = Modifier.padding(start = ref.dp(1f), top = ref.vdp(5f)),
            color = Color(0xFF8D989D),
            fontSize = ref.sp(9.7f),
            lineHeight = ref.sp(14f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NewGameSideSection(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    selectedSide: PlaySide,
    onSide: (PlaySide) -> Unit,
) {
    Column(Modifier.fillMaxWidth().height(ref.vdp(76f)).testTag("p5-setup-side")) {
        NewGameSectionHeader(ref, "Side")
        NewGameChoiceBed(ref, palette, Modifier.fillMaxWidth().height(ref.vdp(56f))) {
            PlaySide.entries.forEachIndexed { index, side ->
                NewGameChoiceFace(
                    ref = ref,
                    palette = palette,
                    label = side.name.lowercase().replaceFirstChar { it.uppercase() },
                    glyph = when (side) {
                        PlaySide.WHITE -> NewGameGlyph.WHITE
                        PlaySide.BLACK -> NewGameGlyph.BLACK
                        PlaySide.RANDOM -> NewGameGlyph.RANDOM
                    },
                    selected = side == selectedSide,
                    onClick = { onSide(side) },
                    modifier = Modifier
                        .weight(1f)
                        .then(if (side == selectedSide) Modifier.testTag("p5-setup-side-selected") else Modifier),
                )
                if (index != PlaySide.entries.lastIndex) Spacer(Modifier.width(ref.dp(4f)))
            }
        }
    }
}

@Composable
private fun NewGameTimeSection(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    control: PlayTimeControl,
    expanded: Boolean,
    onToggle: () -> Unit,
    onControl: (PlayTimeControl) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = ref.vdp(76f))) {
        NewGameSectionHeader(ref, "Time Control")
        NewGameSelector(
            ref,
            palette,
            referenceTimeCategory(control),
            expanded,
            Modifier.fillMaxWidth().height(ref.vdp(52f)).testTag("p5-setup-time"),
            NewGameGlyph.CLOCK,
            true,
            onToggle,
        )
        if (expanded) {
            Spacer(Modifier.height(ref.vdp(5f)))
            NewGameMenu(
                ref,
                palette,
                REFERENCE_TIME_CONTROLS.map { it.label to (it.control.initialMillis == control.initialMillis) },
            ) { label -> onControl(REFERENCE_TIME_CONTROLS.first { it.label == label }.control) }
            Spacer(Modifier.height(ref.vdp(6f)))
        }
    }
}

@Composable
private fun NewGameIncrementSection(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    control: PlayTimeControl,
    expanded: Boolean,
    onToggle: () -> Unit,
    onIncrement: (Long) -> Unit,
) {
    val seconds = control.incrementMillis / 1_000L
    val choices = listOf(0L, 1L, 2L, 5L, 10L)
    Column(Modifier.fillMaxWidth().heightIn(min = ref.vdp(76f))) {
        NewGameSectionHeader(ref, "Inc / Delay")
        NewGameSelector(
            ref,
            palette,
            "$seconds sec",
            expanded,
            Modifier.fillMaxWidth().height(ref.vdp(52f)).testTag("p5-inc-delay"),
            null,
            true,
            onToggle,
        )
        if (expanded) {
            Spacer(Modifier.height(ref.vdp(5f)))
            NewGameIncrementMenu(
                ref = ref,
                palette = palette,
                choices = choices,
                selectedSeconds = seconds,
                onSelect = onIncrement,
            )
            Spacer(Modifier.height(ref.vdp(6f)))
        }
    }
}

@Composable
private fun NewGameSectionHeader(
    ref: NewGameReferenceScale,
    label: String,
    info: Boolean = false,
    trailing: String? = null,
) {
    val palette = lumenP5IdentityPalette()
    Row(
        Modifier.fillMaxWidth().height(ref.vdp(20f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ref.dp(6f)),
    ) {
        Text(label, color = palette.text, fontSize = ref.sp(13f), lineHeight = ref.sp(16f), fontWeight = FontWeight.SemiBold)
        if (info) NewGameIcon(NewGameGlyph.INFO, Color(0xFF879298), Modifier.size(ref.dp(13f)), ref)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, color = palette.cyan.copy(alpha = .76f), fontSize = ref.sp(10f), lineHeight = ref.sp(16f), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NewGameChoiceBed(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(ref.dp(11f))
    Row(
        modifier
            .clip(shape)
            .drawWithCache {
                val cornerPx = ref.dp(11f).toPx()
                val corner = CornerRadius(cornerPx)
                val inset = ref.dp(1.25f).toPx()
                val insetSize = Size(
                    (size.width - inset * 2f).coerceAtLeast(0f),
                    (size.height - inset * 2f).coerceAtLeast(0f),
                )
                val insetCorner = CornerRadius((cornerPx - inset).coerceAtLeast(0f))
                val innerShadow = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = .38f),
                        .10f to Color.Black.copy(alpha = .24f),
                        .28f to Color.Black.copy(alpha = .07f),
                        .62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .05f),
                    ),
                )
                onDrawBehind {
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF0B1013), Color(0xFF0F1519))), cornerRadius = corner)
                    drawRoundRect(innerShadow, cornerRadius = corner)
                    drawRoundRect(Color(0xFF77939D).copy(alpha = .07f), cornerRadius = corner, style = Stroke(ref.dp(1f).toPx().coerceAtLeast(1f)))
                    drawRoundRect(
                        Color.Black.copy(alpha = .22f),
                        topLeft = Offset(inset, inset),
                        size = insetSize,
                        cornerRadius = insetCorner,
                        style = Stroke(ref.dp(1.1f).toPx().coerceAtLeast(1f)),
                    )
                    drawRoundRect(
                        Color.White.copy(alpha = .012f),
                        topLeft = Offset(inset * 2f, inset * 2f),
                        size = Size(
                            (size.width - inset * 4f).coerceAtLeast(0f),
                            (size.height - inset * 4f).coerceAtLeast(0f),
                        ),
                        cornerRadius = CornerRadius((cornerPx - inset * 2f).coerceAtLeast(0f)),
                        style = Stroke(ref.dp(.65f).toPx().coerceAtLeast(.75f)),
                    )
                }
            }
            .padding(horizontal = ref.dp(3f), vertical = ref.vdp(3f)),
        content = content,
    )
}

@Composable
private fun NewGameChoiceFace(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    label: String,
    glyph: NewGameGlyph,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .986f else 1f, if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "choice-$label-scale")
    val offset by animateDpAsState(if (pressed) ref.vdp(1.5f) else 0.dp, if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "choice-$label-y")
    val elevation by animateDpAsState(if (pressed || !selected) 0.dp else ref.dp(2.5f), if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "choice-$label-shadow")
    val shape = RoundedCornerShape(ref.dp(8f))
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offset.toPx()
                shadowElevation = elevation.toPx()
                this.shape = shape
                clip = true
            }
            .selectedFace(ref, palette, selected, pressed)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ref.dp(7f))) {
            NewGameIcon(glyph, if (selected) palette.text else Color(0xFF8F999E), Modifier.size(ref.dp(19f)), ref)
            Text(
                label,
                color = if (selected) Color(0xFFE6F6FB) else Color(0xFF8F999E),
                fontSize = ref.sp(11.5f),
                lineHeight = ref.sp(14f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NewGameSegment(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .986f else 1f, if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "segment-$label-scale")
    val offset by animateDpAsState(if (pressed) ref.vdp(1.5f) else 0.dp, if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "segment-$label-y")
    val elevation by animateDpAsState(if (pressed || !selected) 0.dp else ref.dp(2.5f), if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "segment-$label-shadow")
    val shape = RoundedCornerShape(ref.dp(8f))
    Box(
        modifier
            .fillMaxSize()
            .then(if (selected) Modifier.testTag("p5-setup-strength-model-selected") else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offset.toPx()
                shadowElevation = elevation.toPx()
                this.shape = shape
                clip = true
            }
            .selectedFace(ref, palette, selected, pressed)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color(0xFFDDF6FF) else Color(0xFF879196),
            fontSize = ref.sp(10.5f),
            lineHeight = ref.sp(13f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.selectedFace(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    selected: Boolean,
    pressed: Boolean,
): Modifier = drawWithCache {
    val corner = CornerRadius(ref.dp(8f).toPx())
    val fill = if (pressed) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color(0xFF1C343E),
                .25f to Color(0xFF1B323A),
                .50f to Color(0xFF192F38),
                .75f to Color(0xFF172D36),
                1f to Color(0xFF162A32),
            ),
        )
    } else {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color(0xFF22424E),
                .25f to Color(0xFF1F3D48),
                .50f to Color(0xFF1D3843),
                .75f to Color(0xFF1B343E),
                1f to Color(0xFF182F39),
            ),
        )
    }
    onDrawBehind {
        if (selected) {
            drawRoundRect(fill, cornerRadius = corner)
            drawLine(Color(0xFFB0E5F6).copy(alpha = if (pressed) .05f else .11f), Offset(ref.dp(8f).toPx(), ref.vdp(1f).toPx()), Offset(size.width - ref.dp(8f).toPx(), ref.vdp(1f).toPx()), ref.dp(.7f).toPx(), StrokeCap.Round)
            drawRoundRect(palette.cyan.copy(alpha = .20f), cornerRadius = corner, style = Stroke(ref.dp(1f).toPx().coerceAtLeast(1f)))
        }
    }
}

@Composable
private fun NewGameSelector(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    title: String,
    expanded: Boolean,
    modifier: Modifier,
    leading: NewGameGlyph? = null,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .994f else 1f, if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "selector-$title-scale")
    val offset by animateDpAsState(if (pressed) ref.vdp(2f) else 0.dp, if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "selector-$title-y")
    val elevation by animateDpAsState(if (pressed) ref.dp(2f) else ref.dp(6f), if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "selector-$title-shadow")
    val shape = RoundedCornerShape(ref.dp(10f))
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offset.toPx()
                shadowElevation = elevation.toPx()
                ambientShadowColor = Color.Black.copy(alpha = if (pressed) .20f else .28f)
                spotShadowColor = Color.Black.copy(alpha = if (pressed) .24f else .32f)
                this.shape = shape
                clip = true
            }
            .drawWithCache {
                val corner = CornerRadius(ref.dp(10f).toPx())
                val face = Brush.verticalGradient(
                    if (pressed) listOf(Color(0xFF141A1D), Color(0xFF111619)) else listOf(Color(0xFF171D21), Color(0xFF12181B)),
                )
                val edgeDepth = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = if (pressed) .012f else .026f),
                        .20f to Color.Transparent,
                        .72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (pressed) .025f else .045f),
                    ),
                )
                onDrawBehind {
                    drawRoundRect(face, cornerRadius = corner)
                    drawRoundRect(edgeDepth, cornerRadius = corner)
                    if (expanded) drawRect(palette.cyan.copy(alpha = .48f), Offset(0f, ref.vdp(8f).toPx()), Size(ref.dp(2f).toPx(), size.height - ref.vdp(16f).toPx()))
                    drawRoundRect(Color(0xFF879FA8).copy(alpha = if (expanded) .14f else .12f), cornerRadius = corner, style = Stroke(ref.dp(1f).toPx().coerceAtLeast(1f)))
                }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = ref.dp(if (compact) 11f else 12f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ref.dp(if (compact) 8f else 10f)),
    ) {
        if (leading == NewGameGlyph.ENGINE) NewGameEngineWell(ref, palette)
        else if (leading != null) NewGameIcon(leading, Color(0xFF93A0A5), Modifier.size(ref.dp(19f)), ref)
        Text(
            title,
            Modifier.weight(1f),
            color = palette.text,
            fontSize = ref.sp(if (compact) 12.8f else 13f),
            lineHeight = ref.sp(16f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NewGameChevron(ref, Color(0xFF89959A), expanded)
    }
}

@Composable
private fun NewGameEngineWell(ref: NewGameReferenceScale, palette: LumenP5IdentityPalette) {
    val shape = RoundedCornerShape(ref.dp(9f))
    Box(
        Modifier
            .size(ref.dp(36f))
            .graphicsLayer {
                shadowElevation = ref.dp(2.5f).toPx()
                this.shape = shape
                clip = true
            }
            .drawWithCache {
                val corner = CornerRadius(ref.dp(9f).toPx())
                onDrawBehind {
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF18323C), Color(0xFF12262E))), cornerRadius = corner)
                    drawRoundRect(Brush.radialGradient(listOf(palette.cyan.copy(alpha = .13f), Color.Transparent), center = Offset(size.width * .30f, size.height * .18f), radius = size.width * .72f), cornerRadius = corner)
                    drawRoundRect(palette.cyan.copy(alpha = .18f), cornerRadius = corner, style = Stroke(ref.dp(1f).toPx().coerceAtLeast(1f)))
                }
            },
        contentAlignment = Alignment.Center,
    ) { NewGameIcon(NewGameGlyph.ENGINE, palette.cyan, Modifier.size(ref.dp(20f)), ref) }
}

@Composable
private fun NewGameSlider(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    semanticLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(if (dragging) 1.08f else 1f, if (dragging) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "new-game-slider-thumb")
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    Canvas(
        modifier
            .pointerInput(valueRange) {
                fun update(x: Float) {
                    val f = (x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + (valueRange.endInclusive - valueRange.start) * f)
                }
                detectDragGestures(
                    onDragStart = { dragging = true; update(it.x) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    update(change.position.x)
                }
            }
            .semantics { contentDescription = "$semanticLabel. $value from ${valueRange.start} to ${valueRange.endInclusive}" },
    ) {
        val y = size.height * .5f
        val trackWidth = ref.dp(3f).toPx()
        val thumbX = size.width * fraction
        drawLine(Color(0xFF1D2428), Offset(0f, y), Offset(size.width, y), trackWidth, StrokeCap.Round)
        drawLine(Brush.horizontalGradient(listOf(Color(0xFF4D91A8), palette.cyan)), Offset(0f, y), Offset(thumbX, y), trackWidth, StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = .38f), ref.dp(8f).toPx() * thumbScale, Offset(thumbX, y))
        drawCircle(Color(0xFF17333D), ref.dp(7f).toPx() * thumbScale, Offset(thumbX, y))
        drawCircle(palette.cyan, ref.dp(5f).toPx() * thumbScale, Offset(thumbX, y))
        drawCircle(Color.White.copy(alpha = .18f), ref.dp(3.1f).toPx() * thumbScale, Offset(thumbX, y))
    }
}

@Composable
private fun NewGameDisabledAction(ref: NewGameReferenceScale, modifier: Modifier) {
    val shape = RoundedCornerShape(ref.dp(9f))
    Row(
        modifier
            .graphicsLayer {
                shadowElevation = ref.dp(3f).toPx()
                ambientShadowColor = Color.Black.copy(alpha = .14f)
                spotShadowColor = Color.Black.copy(alpha = .19f)
                this.shape = shape
                clip = true
            }
            .drawWithCache {
                val corner = CornerRadius(ref.dp(9f).toPx())
                val edgeDepth = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = .018f),
                        .24f to Color.Transparent,
                        .76f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .035f),
                    ),
                )
                onDrawBehind {
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF151B1E), Color(0xFF111619))), cornerRadius = corner)
                    drawRoundRect(edgeDepth, cornerRadius = corner)
                    drawRoundRect(Color(0xFF879FA8).copy(alpha = .08f), cornerRadius = corner, style = Stroke(ref.dp(1f).toPx().coerceAtLeast(1f)))
                }
            }
            .clickable(enabled = false, onClick = {}),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        NewGameIcon(NewGameGlyph.TARGET, Color(0xFF69757A), Modifier.size(ref.dp(18f)), ref)
        Spacer(Modifier.width(ref.dp(8f)))
        Text("Match My Elo", color = Color(0xFF69757A), fontSize = ref.sp(12f), lineHeight = ref.sp(15f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NewGamePrimaryButton(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val faceOffset by animateDpAsState(if (pressed) ref.vdp(4f) else 0.dp, if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "new-game-cta-face-y")
    val baseOffset by animateDpAsState(if (pressed) ref.vdp(5f) else ref.vdp(4f), if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "new-game-cta-base-y")
    val contactElevation by animateDpAsState(if (pressed) ref.dp(3f) else ref.dp(7f), if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(), label = "new-game-cta-contact-shadow")
    val shape = RoundedCornerShape(ref.dp(10f))
    val enabledAlpha = if (enabled) 1f else .50f
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = baseOffset.toPx()
                    alpha = enabledAlpha
                    shadowElevation = contactElevation.toPx()
                    ambientShadowColor = Color.Black.copy(alpha = if (pressed) .22f else .31f)
                    spotShadowColor = Color.Black.copy(alpha = if (pressed) .26f else .34f)
                    this.shape = shape
                    clip = true
                }
                .drawWithCache {
                    val corner = CornerRadius(ref.dp(10f).toPx())
                    onDrawBehind { drawRoundRect(Color(0xFF244A58), cornerRadius = corner) }
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = faceOffset.toPx()
                    alpha = enabledAlpha
                    this.shape = shape
                    clip = true
                }
                .drawWithCache {
                    val corner = CornerRadius(ref.dp(10f).toPx())
                    val face = if (pressed) {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color(0xFF4E93AB),
                                .24f to Color(0xFF4A8EA6),
                                .52f to Color(0xFF4689A0),
                                .78f to Color(0xFF42839B),
                                1f to Color(0xFF3F7F96),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color(0xFF589FB7),
                                .22f to Color(0xFF5094AC),
                                .46f to Color(0xFF4789A1),
                                .73f to Color(0xFF407F96),
                                1f to Color(0xFF39758B),
                            ),
                        )
                    }
                    onDrawBehind {
                        drawRoundRect(face, cornerRadius = corner)
                        drawRoundRect(palette.cyan.copy(alpha = .18f), cornerRadius = corner, style = Stroke(ref.dp(1f).toPx().coerceAtLeast(1f)))
                    }
                }
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag(PLAY_START_TEST_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Text("Start Game", color = Color(0xFFF6FBFD), fontSize = ref.sp(13.5f), lineHeight = ref.sp(17f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NewGameValidationMessage(ref: NewGameReferenceScale, message: String, modifier: Modifier) {
    val shape = RoundedCornerShape(ref.dp(8f))
    Box(modifier.clip(shape).drawWithCache {
        val corner = CornerRadius(ref.dp(8f).toPx())
        onDrawBehind {
            drawRoundRect(Color(0xFF2B1518), cornerRadius = corner)
            drawRoundRect(Color(0xFFC96B72).copy(alpha = .42f), cornerRadius = corner, style = Stroke(ref.dp(1f).toPx()))
        }
    }.padding(horizontal = ref.dp(10f), vertical = ref.vdp(7f))) {
        Text(message, color = Color(0xFFE18A90), fontSize = ref.sp(10f), lineHeight = ref.sp(13f))
    }
}

@Composable
private fun NewGameNotes(ref: NewGameReferenceScale, palette: LumenP5IdentityPalette, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ref.vdp(7f))) {
        NewGameNote(ref, palette, "Match My Elo is preview-only in this build.", "p5-setup-note-1")
        NewGameNote(ref, palette, "Your selected strength, side and clock apply when the game starts.", "p5-setup-note-2")
    }
}

@Composable
private fun NewGameNote(ref: NewGameReferenceScale, palette: LumenP5IdentityPalette, text: String, tag: String) {
    Row(
        Modifier.fillMaxWidth().testTag(tag),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ref.dp(8f)),
    ) {
        NewGameIcon(NewGameGlyph.CHECK, palette.cyan.copy(alpha = .72f), Modifier.size(ref.dp(15f)), ref)
        Text(text, color = Color(0xFF8C979C), fontSize = ref.sp(9.7f), lineHeight = ref.sp(14f))
    }
}

@Composable
private fun NewGameMenu(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    items: List<Pair<String, Boolean>>,
    onSelect: (String) -> Unit,
) {
    NewGameMenuSurface(ref, palette) {
        items.forEach { (label, selected) ->
            NewGameMenuRow(
                ref = ref,
                palette = palette,
                label = label,
                selected = selected,
                onClick = { onSelect(label) },
            )
        }
    }
}

@Composable
private fun NewGameIncrementMenu(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    choices: List<Long>,
    selectedSeconds: Long,
    onSelect: (Long) -> Unit,
) {
    NewGameMenuSurface(ref, palette) {
        choices.forEach { seconds ->
            NewGameMenuRow(
                ref = ref,
                palette = palette,
                label = "$seconds sec",
                selected = seconds == selectedSeconds,
                onClick = { onSelect(seconds) },
            )
        }
    }
}

@Composable
private fun NewGameMenuSurface(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(ref.dp(9f))
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(ref.dp(3f), shape, clip = false)
            .clip(shape)
            .drawWithCache {
                val corner = CornerRadius(ref.dp(9f).toPx())
                onDrawBehind {
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF151B1E), Color(0xFF101518))), cornerRadius = corner)
                    drawRoundRect(Color(0xFF879FA8).copy(alpha = .10f), cornerRadius = corner, style = Stroke(ref.dp(1f).toPx()))
                }
            },
    ) { content() }
}

@Composable
private fun NewGameMenuRow(
    ref: NewGameReferenceScale,
    palette: LumenP5IdentityPalette,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember(label) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(ref.vdp(44f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(horizontal = ref.dp(12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            color = if (selected) palette.text else palette.muted,
            fontSize = ref.sp(12f),
            lineHeight = ref.sp(15f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        if (selected) NewGameIcon(NewGameGlyph.CHECK, palette.cyan, Modifier.size(ref.dp(15f)), ref)
    }
}

@Composable
private fun NewGameChevron(ref: NewGameReferenceScale, color: Color, expanded: Boolean) {
    Canvas(Modifier.size(ref.dp(17f)).graphicsLayer { rotationZ = if (expanded) 180f else 0f }) {
        val stroke = ref.dp(1.7f).toPx()
        drawLine(color, Offset(size.width * .30f, size.height * .42f), Offset(size.width * .50f, size.height * .62f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .50f, size.height * .62f), Offset(size.width * .70f, size.height * .42f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun NewGameIcon(glyph: NewGameGlyph, tint: Color, modifier: Modifier, ref: NewGameReferenceScale) {
    Canvas(modifier) {
        val stroke = ref.dp(1.45f).toPx().coerceAtLeast(1f)
        when (glyph) {
            NewGameGlyph.BOARD, NewGameGlyph.CHESS960 -> {
                val unit = size.minDimension / 24f
                val origin = Offset((size.width - 24f * unit) / 2f, (size.height - 24f * unit) / 2f)
                fun point(x: Float, y: Float) = origin + Offset(x * unit, y * unit)
                val boardStroke = (1.6f * unit).coerceAtLeast(1f)
                drawRoundRect(
                    color = tint,
                    topLeft = point(4.3f, 4.3f),
                    size = Size(15.4f * unit, 15.4f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = Stroke(boardStroke),
                )
                drawLine(tint, point(12f, 4.6f), point(12f, 19.6f), boardStroke, StrokeCap.Round)
                drawLine(tint, point(4.6f, 12f), point(19.6f, 12f), boardStroke, StrokeCap.Round)
                if (glyph == NewGameGlyph.BOARD) {
                    drawRect(tint.copy(alpha = .28f), point(4.6f, 4.6f), Size(7.2f * unit, 7.2f * unit))
                    drawRect(tint.copy(alpha = .28f), point(12.2f, 12.2f), Size(7.2f * unit, 7.2f * unit))
                } else {
                    drawLine(tint, point(6.6f, 16.8f), point(17.4f, 7.2f), boardStroke, StrokeCap.Round)
                    drawLine(tint, point(14.9f, 7.2f), point(17.5f, 7.1f), boardStroke, StrokeCap.Round)
                    drawLine(tint, point(17.5f, 7.1f), point(17.4f, 9.7f), boardStroke, StrokeCap.Round)
                }
            }
            NewGameGlyph.ENGINE -> {
                val unit = size.minDimension / 24f
                val origin = Offset((size.width - 24f * unit) / 2f, (size.height - 24f * unit) / 2f)
                fun point(x: Float, y: Float) = origin + Offset(x * unit, y * unit)
                val iconStroke = (1.55f * unit).coerceAtLeast(1f)
                val outline = Stroke(iconStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawRoundRect(
                    color = tint,
                    topLeft = point(5.4f, 6.6f),
                    size = Size(13.2f * unit, 10.8f * unit),
                    cornerRadius = CornerRadius(2.7f * unit),
                    style = outline,
                )
                listOf(
                    9f to (3.9f to 6.6f),
                    15f to (3.9f to 6.6f),
                    9f to (17.4f to 20.1f),
                    15f to (17.4f to 20.1f),
                ).forEach { (x, ys) -> drawLine(tint, point(x, ys.first), point(x, ys.second), iconStroke, StrokeCap.Round) }
                listOf(
                    10f to (3.1f to 5.4f),
                    14f to (3.1f to 5.4f),
                    10f to (18.6f to 20.9f),
                    14f to (18.6f to 20.9f),
                ).forEach { (y, xs) -> drawLine(tint, point(xs.first, y), point(xs.second, y), iconStroke, StrokeCap.Round) }
                val star = Path().apply {
                    moveTo(point(12f, 9.2f).x, point(12f, 9.2f).y)
                    lineTo(point(12.7f, 10.7f).x, point(12.7f, 10.7f).y)
                    lineTo(point(14.4f, 10.9f).x, point(14.4f, 10.9f).y)
                    lineTo(point(13.2f, 12.1f).x, point(13.2f, 12.1f).y)
                    lineTo(point(13.5f, 13.8f).x, point(13.5f, 13.8f).y)
                    lineTo(point(12f, 13f).x, point(12f, 13f).y)
                    lineTo(point(10.5f, 13.8f).x, point(10.5f, 13.8f).y)
                    lineTo(point(10.8f, 12.1f).x, point(10.8f, 12.1f).y)
                    lineTo(point(9.6f, 10.9f).x, point(9.6f, 10.9f).y)
                    lineTo(point(11.3f, 10.7f).x, point(11.3f, 10.7f).y)
                    close()
                }
                drawPath(star, tint, style = outline)
            }
            NewGameGlyph.WHITE, NewGameGlyph.BLACK -> {
                val unit = size.minDimension / 24f
                val origin = Offset((size.width - 24f * unit) / 2f, (size.height - 24f * unit) / 2f)
                fun point(x: Float, y: Float) = origin + Offset(x * unit, y * unit)
                val pieceStroke = (1.6f * unit).coerceAtLeast(1f)
                val outline = Stroke(pieceStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawCircle(tint, 3f * unit, point(12f, 7f), style = outline)
                val piece = Path().apply {
                    moveTo(point(9.5f, 10.4f).x, point(9.5f, 10.4f).y)
                    lineTo(point(14.5f, 10.4f).x, point(14.5f, 10.4f).y)
                    lineTo(point(15.3f, 14.4f).x, point(15.3f, 14.4f).y)
                    lineTo(point(17.3f, 16.6f).x, point(17.3f, 16.6f).y)
                    lineTo(point(6.7f, 16.6f).x, point(6.7f, 16.6f).y)
                    lineTo(point(8.7f, 14.4f).x, point(8.7f, 14.4f).y)
                    close()
                }
                drawPath(piece, tint, style = outline)
                drawLine(tint, point(6.1f, 19f), point(17.9f, 19f), pieceStroke, StrokeCap.Round)
            }
            NewGameGlyph.RANDOM -> {
                drawRoundRect(tint.copy(alpha = .40f), Offset(size.width * .20f, size.height * .30f), Size(size.width * .45f, size.height * .52f), CornerRadius(ref.dp(1.8f).toPx()), style = Stroke(stroke))
                drawRoundRect(tint, Offset(size.width * .36f, size.height * .18f), Size(size.width * .45f, size.height * .52f), CornerRadius(ref.dp(1.8f).toPx()), style = Stroke(stroke))
                drawLine(tint, Offset(size.width * .47f, size.height * .48f), Offset(size.width * .69f, size.height * .48f), stroke, StrokeCap.Round)
            }
            NewGameGlyph.CLOCK -> {
                drawCircle(tint, size.minDimension * .35f, style = Stroke(stroke))
                drawLine(tint, center, Offset(center.x, size.height * .28f), stroke, StrokeCap.Round)
                drawLine(tint, center, Offset(size.width * .65f, size.height * .56f), stroke, StrokeCap.Round)
            }
            NewGameGlyph.TARGET -> {
                drawCircle(tint, size.minDimension * .34f, style = Stroke(stroke))
                drawCircle(tint, size.minDimension * .15f, style = Stroke(stroke * .85f))
                drawCircle(tint, ref.dp(.9f).toPx())
            }
            NewGameGlyph.INFO -> {
                drawCircle(tint, size.minDimension * .43f, style = Stroke(ref.dp(1f).toPx()))
                drawCircle(tint, ref.dp(.7f).toPx(), Offset(center.x, size.height * .32f))
                drawLine(tint, Offset(center.x, size.height * .46f), Offset(center.x, size.height * .70f), ref.dp(1f).toPx(), StrokeCap.Round)
            }
            NewGameGlyph.CHECK -> {
                drawCircle(tint.copy(alpha = .11f), size.minDimension * .46f)
                drawCircle(tint.copy(alpha = .76f), size.minDimension * .43f, style = Stroke(ref.dp(.8f).toPx()))
                drawLine(tint, Offset(size.width * .25f, size.height * .53f), Offset(size.width * .43f, size.height * .70f), ref.dp(.9f).toPx(), StrokeCap.Round)
                drawLine(tint, Offset(size.width * .43f, size.height * .70f), Offset(size.width * .76f, size.height * .33f), ref.dp(.9f).toPx(), StrokeCap.Round)
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
