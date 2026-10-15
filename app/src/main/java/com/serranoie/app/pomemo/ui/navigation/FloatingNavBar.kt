package com.serranoie.app.pomemo.ui.navigation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.rounded.Add
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedContent
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.timer.Phase
import com.serranoie.app.pomemo.ui.HapticUtil
import com.serranoie.app.pomemo.ui.components.WithTooltip
import com.serranoie.app.pomemo.ui.theme.PomemoTheme

/** The toolbar's action button for the current page ("+" on Reminders and Notes; none on Timer). */
data class NavFab(val icon: ImageVector, val label: String, val haptic: (View) -> Unit, val onClick: () -> Unit)

private val LABEL_MAX = 120.dp
private val ICON = 24.dp

/** Same gap M3 uses between a floating toolbar and its FAB (internal there). */
private val TOOLBAR_FAB_GAP = 8.dp

private data class Tab(val label: Int, val selected: ImageVector, val unselected: ImageVector)

private val TABS = listOf(
    Tab(R.string.tab_timer, Icons.Rounded.Timer, Icons.Outlined.Timer),
    Tab(R.string.tab_reminders, Icons.Rounded.Alarm, Icons.Outlined.Alarm),
    Tab(R.string.tab_notes, Icons.AutoMirrored.Rounded.Notes, Icons.AutoMirrored.Outlined.Notes),
)

/**
 * Tomato-style floating pill: vibrant container tinted by the timer phase (primary for focus,
 * secondary for breaks), circular toggle buttons, label only on the selected tab (all tabs when wide).
 */
@Composable
fun FloatingNavBar(selected: Int, phase: Phase, fab: NavFab?, onSelect: (Int) -> Unit) {
    val focus = phase == Phase.FOCUS
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(if (focus) cs.primaryContainer else cs.secondaryContainer, label = "navContainer")
    val onContainer by animateColorAsState(if (focus) cs.onPrimaryContainer else cs.onSecondaryContainer, label = "navContent")
    val accent by animateColorAsState(if (focus) cs.primary else cs.secondary, label = "navAccent")
    val onAccent by animateColorAsState(if (focus) cs.onPrimary else cs.onSecondary, label = "navOnAccent")
    val density = LocalDensity.current
    val wide = with(density) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    // Essentials' rule: at very large font sizes labels would crowd the pill, so icons only.
    val largeFont = density.fontScale > 1.25f
    val height = ButtonDefaults.MediumContainerHeight
    val view = LocalView.current
    // Essentials' tab spring: a little overshoot so the selected pill visibly "pops" open.
    val bouncy = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    val labelStyle = ButtonDefaults.textStyleFor(height)
    val labelGap = ButtonDefaults.iconSpacingFor(height)
    val measurer = rememberTextMeasurer()

    // Toolbar and FAB side by side in a plain Row instead of HorizontalFloatingToolbar's FAB slot. That
    // slot forces the FAB to 56dp and sizes itself from the toolbar's max intrinsic width, which differs
    // from the drawn width while the labels spring, leaving the pill shifted right until they settle.
    Box(Modifier.fillMaxWidth().displayCutoutPadding(), Alignment.Center) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(vertical = FloatingToolbarDefaults.ScreenOffset)
                .zIndex(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                    toolbarContainerColor = container,
                    toolbarContentColor = onContainer,
                ),
            ) {
                Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                    TABS.forEachIndexed { i, tab ->
                        val isSelected = selected == i
                        val label = stringResource(tab.label)
                        val showLabel = (isSelected || wide) && !largeFont
                        // Like Essentials, widths are explicit animated Dp values rather than AnimatedVisibility:
                        // the toolbar sizes itself (and centres itself next to the FAB) from its children's
                        // intrinsic width, and only an explicit width reports the same size it draws at.
                        val fullLabel = remember(label, labelStyle, density) {
                            with(density) { measurer.measure(label, labelStyle).size.width.toDp() }.coerceAtMost(LABEL_MAX) + labelGap
                        }
                        val labelWidth by animateDpAsState(if (showLabel) fullLabel else 0.dp, bouncy, label = "navLabel$i")
                        WithTooltip(label) {
                            ToggleButton(
                                checked = isSelected,
                                onCheckedChange = {
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    onSelect(i)
                                },
                                modifier = Modifier
                                    .heightIn(min = height)
                                    .width(height + labelWidth.coerceAtLeast(0.dp))
                                    .semantics { role = Role.Tab },
                                contentPadding = PaddingValues(horizontal = (height - ICON) / 2),
                                colors = ToggleButtonDefaults.colors(
                                    containerColor = container,
                                    contentColor = onContainer,
                                    checkedContainerColor = accent,
                                    checkedContentColor = onAccent,
                                ),
                                shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Crossfade(isSelected, label = "navIcon") { sel ->
                                        // When the label is hidden the icon must carry it for TalkBack.
                                        Icon(if (sel) tab.selected else tab.unselected, if (showLabel) null else label)
                                    }
                                    if (labelWidth > labelGap) {
                                        Text(
                                            label,
                                            style = labelStyle,
                                            maxLines = 1,
                                            softWrap = false,
                                            // Long translations ("Recordatorios") scroll instead of being cut.
                                            modifier = Modifier
                                                .padding(start = labelGap)
                                                .width(labelWidth - labelGap)
                                                .basicMarquee(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // The FAB pops in/out with the page (none on Timer). The gap lives inside the animated part so
            // the pill re-centres smoothly instead of keeping an 8dp hole when the FAB is gone.
            var shown by remember { mutableStateOf(fab) }
            if (fab != null) shown = fab
            AnimatedVisibility(
                visible = fab != null,
                enter = expandHorizontally(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) + scaleIn() + fadeIn(),
                exit = shrinkHorizontally() + scaleOut() + fadeOut(),
            ) {
                shown?.let { f ->
                    WithTooltip(f.label) {
                        // Medium FAB (80dp): the page's main action.
                        MediumFloatingActionButton(
                            onClick = { f.haptic(view); f.onClick() },
                            modifier = Modifier.padding(start = TOOLBAR_FAB_GAP),
                            containerColor = accent,
                            contentColor = onAccent,
                        ) {
                            // Swapping between pages that both have a FAB pops the new one in.
                            AnimatedContent(
                                targetState = f,
                                contentKey = { it.label },
                                transitionSpec = {
                                    (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) + fadeIn()) togetherWith
                                        (scaleOut() + fadeOut())
                                },
                                label = "navFab",
                            ) { Icon(it.icon, contentDescription = it.label, modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize)) }
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FloatingNavBarFocusPreview() = PomemoTheme { FloatingNavBar(selected = 0, phase = Phase.FOCUS, fab = null, onSelect = {}) }

@Preview(showBackground = true, widthDp = 700)
@Composable
private fun FloatingNavBarBreakWidePreview() = PomemoTheme { FloatingNavBar(selected = 1, phase = Phase.SHORT_BREAK, fab = NavFab(Icons.Rounded.Add, "New reminder", {}, {}), onSelect = {}) }
