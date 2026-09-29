package dev.lumenchess.settings

import android.os.Build
import android.system.Os
import android.system.OsConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lumenchess.R
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeRow
import dev.lumenchess.design.LumenDerivativeSectionLabel
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.design.LumenDerivativeTopBar
import dev.lumenchess.engine.host.Reckless09Engine
import dev.lumenchess.engine.host.Stockfish18Engine

/** Version, bundled licences and the device facts that matter for a chess engine. */
@Composable
fun AboutSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        }.getOrDefault("unknown")
    }
    val pageSizeKiB = remember { runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) / 1024 }.getOrNull() }
    var showFontLicense by remember { mutableStateOf(false) }
    val fontLicense = remember(context) {
        runCatching {
            context.resources.openRawResource(R.raw.inter_tight_ofl).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    LumenDerivativePage(
        modifier = modifier,
        testTag = "derivative-about-settings",
        scrollable = true,
        spacing = 8,
    ) {
        LumenDerivativeTopBar(title = "About", onBack = onBack, backTestTag = "about-settings-back")

        LumenDerivativeSectionLabel("LumenChess")
        LumenDerivativeRow(title = "Version", subtitle = version, showChevron = false, testTag = "about-version")

        LumenDerivativeSectionLabel("This device")
        LumenDerivativeRow(
            title = "Android",
            subtitle = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            showChevron = false,
        )
        LumenDerivativeRow(
            title = "Architecture",
            subtitle = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            showChevron = false,
        )
        if (pageSizeKiB != null) {
            LumenDerivativeRow(title = "Memory page size", subtitle = "$pageSizeKiB KiB", showChevron = false)
        }

        LumenDerivativeSectionLabel("Open-source components")
        LumenDerivativeRow(
            title = "Stockfish ${Stockfish18Engine.VERSION}",
            subtitle = "${Stockfish18Engine.LICENSE_SPDX} · github.com/official-stockfish/Stockfish",
            showChevron = false,
            testTag = "about-license-stockfish",
        )
        LumenDerivativeRow(
            title = "Reckless ${Reckless09Engine.VERSION}",
            subtitle = "${Reckless09Engine.LICENSE_SPDX} · github.com/codedeliveryservice/Reckless",
            showChevron = false,
            testTag = "about-license-reckless",
        )
        LumenDerivativeRow(
            title = "Inter Tight",
            subtitle = "SIL Open Font License 1.1",
            onClick = { showFontLicense = !showFontLicense },
            showChevron = fontLicense.isNotEmpty(),
            enabled = fontLicense.isNotEmpty(),
            testTag = "about-license-font",
        )
        if (showFontLicense && fontLicense.isNotEmpty()) {
            LumenDerivativeSurface(
                role = DerivativeSurfaceRole.RECESSED_TRAY,
                modifier = Modifier.fillMaxWidth().testTag("about-font-license-text"),
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fontLicense, style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
                }
            }
        }
        Text(
            "The engines are free software. Their complete corresponding source, at the exact versions above, is available from the addresses shown.",
            style = MaterialTheme.typography.bodySmall,
            color = LumenColors.OnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
        )
    }
}
