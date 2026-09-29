package dev.lumenchess.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.design.LumenDerivativeTopBar
import dev.lumenchess.engine.api.EngineStrengthCapability
import dev.lumenchess.engine.host.Reckless09Engine
import dev.lumenchess.engine.host.Stockfish18Engine
import dev.lumenchess.play.PlayEngine

/** Facts about one bundled engine, taken from the same identity objects the engine host runs. */
internal data class InstalledEngineInfo(
    val name: String,
    val license: String,
    val details: List<Pair<String, String>>,
)

internal fun installedEngines(): List<InstalledEngineInfo> = PlayEngine.entries.map { engine ->
    val capabilities = engine.capabilities
    val variants = capabilities.variants.sortedBy { it.ordinal }
        .joinToString(" · ") { if (it == Variant.STANDARD) "Standard" else "Chess960" }
    val strength = when (val range = capabilities.strength) {
        is EngineStrengthCapability.EloRange ->
            "Native ${range.minElo}–${range.maxElo} Elo, plus Humanized and Hybrid"
        null -> "Humanized and Hybrid (no native Elo limit)"
    }
    val (license, identity) = when (engine) {
        PlayEngine.STOCKFISH_18 -> Stockfish18Engine.LICENSE_SPDX to listOf(
            "Version" to Stockfish18Engine.VERSION,
            "Source" to "${Stockfish18Engine.SOURCE_TAG} · ${Stockfish18Engine.SOURCE_COMMIT.take(9)}",
        )
        PlayEngine.RECKLESS_0_9_0 -> Reckless09Engine.LICENSE_SPDX to listOf(
            "Version" to Reckless09Engine.VERSION,
            "Source" to "${Reckless09Engine.SOURCE_TAG} · ${Reckless09Engine.SOURCE_COMMIT.take(9)}",
            "Network" to Reckless09Engine.NETWORK,
        )
    }
    InstalledEngineInfo(
        name = engine.displayName,
        license = license,
        details = identity + listOfNotNull(
            "Variants" to variants,
            "Strength" to strength,
            capabilities.multiPv?.let { "Analysis lines" to "up to ${it.maxLines}" },
        ),
    )
}

@Composable
fun EnginesSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LumenDerivativePage(
        modifier = modifier,
        testTag = "derivative-engines-settings",
        scrollable = true,
        spacing = 10,
    ) {
        LumenDerivativeTopBar(title = "Engines", onBack = onBack, backTestTag = "engines-settings-back")
        installedEngines().forEach { engine ->
            LumenDerivativeSurface(
                role = DerivativeSurfaceRole.PREVIEW_PANEL,
                modifier = Modifier.fillMaxWidth().testTag("engine-card-${engine.name}"),
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(engine.name, style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
                    Text(engine.license, style = MaterialTheme.typography.labelMedium, color = LumenColors.AccentBlueBright)
                    engine.details.forEach { (label, value) -> EngineDetailRow(label, value) }
                }
            }
        }
        Text(
            "Both engines are bundled with LumenChess and run in isolated processes. Every move an engine returns is validated by LumenChess before it is played.",
            style = MaterialTheme.typography.bodySmall,
            color = LumenColors.OnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EngineDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.padding(end = 4.dp), style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
        Text(
            value,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = LumenColors.OnSurface,
        )
    }
}
