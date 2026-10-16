package com.serranoie.app.pomemo.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import com.serranoie.app.pomemo.ui.theme.googleSansFlex

private val WideFlex = googleSansFlex(width = 120f)

/**
 * Top bars read their title from the theme's type tokens (headlineMedium expanded, titleLarge
 * collapsed, labelLarge for the subtitle), so swapping those for wide, heavy emphasized styles
 * makes every header expressive without touching each bar's layout or collapse animation.
 */
@Composable
fun ExpressiveHeader(content: @Composable () -> Unit) {
    val t = MaterialTheme.typography
    val header = remember(t) {
        t.copy(
            headlineMedium = t.headlineMediumEmphasized.copy(fontFamily = WideFlex, fontWeight = FontWeight.ExtraBold),
            titleLarge = t.titleLargeEmphasized.copy(fontFamily = WideFlex, fontWeight = FontWeight.ExtraBold),
            labelLarge = t.labelLargeEmphasized,
        )
    }
    MaterialTheme(typography = header, content = content)
}
