package com.serranoie.app.pomemo.ui.components

import com.serranoie.app.pomemo.ui.HapticUtil
import androidx.compose.ui.platform.LocalView
import android.view.View
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import com.serranoie.app.pomemo.ui.theme.PomemoTheme

/**
 * Long-press / hover label for icon-only controls. The wrapped control must still set the same
 * text as its contentDescription — the tooltip is for sighted users, not a replacement.
 */
@Composable
fun WithTooltip(text: String, content: @Composable () -> Unit) = TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(text) } },
    state = rememberTooltipState(),
    content = content,
)

/** Icon-only control: tooltip for sighted users, the same label for TalkBack, morphing press shape. */
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    label: String,
    size: DpSize? = null,
    iconSize: Dp = IconButtonDefaults.smallIconSize,
    filled: Boolean = false,
    tonal: Boolean = false,
    enabled: Boolean = true,
    haptic: (View) -> Unit = HapticUtil::performUIHaptic,
    onClick: () -> Unit,
) = WithTooltip(label) {
    val view = LocalView.current
    val tap = { haptic(view); onClick() }
    val m = if (size != null) Modifier.size(size) else Modifier
    val content: @Composable () -> Unit = { Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize)) }
    when {
        filled -> FilledIconButton(tap, IconButtonDefaults.shapes(), m, enabled, content = content)
        tonal -> FilledTonalIconButton(tap, IconButtonDefaults.shapes(), m, enabled, content = content)
        else -> IconButton(tap, IconButtonDefaults.shapes(), m, enabled, content = content)
    }
}

@Preview(showBackground = true)
@Composable
private fun TooltipIconButtonPreview() = PomemoTheme {
    Row {
        TooltipIconButton(Icons.Rounded.Settings, "Settings") {}
        TooltipIconButton(Icons.Rounded.Settings, "Settings", tonal = true) {}
        TooltipIconButton(Icons.Rounded.Settings, "Settings", filled = true) {}
    }
}
