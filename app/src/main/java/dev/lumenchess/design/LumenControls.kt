package dev.lumenchess.design

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

object LumenDimensions {
    val MinimumTouchTarget = 48.dp
    val ControlHeight = 48.dp
    val ScreenPadding = 16.dp
    val CardSpacing = 12.dp
}

@Composable
fun LumenButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(
                minWidth = LumenDimensions.MinimumTouchTarget,
                minHeight = LumenDimensions.MinimumTouchTarget,
            )
            .semantics { role = Role.Button },
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(label)
    }
}

@Composable
fun LumenIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(contentDescription.isNotBlank()) { "Interactive icons require a content description." }
    IconButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(
            minWidth = LumenDimensions.MinimumTouchTarget,
            minHeight = LumenDimensions.MinimumTouchTarget,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
