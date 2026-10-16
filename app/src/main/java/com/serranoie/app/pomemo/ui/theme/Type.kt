package com.serranoie.app.pomemo.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.serranoie.app.pomemo.R

/**
 * Google Sans Flex is one variable file. One Font entry per weight lets FontWeight in the type
 * scale pick a real weight instead of synthesizing bold. [width] is the wdth axis (25 = very
 * condensed, 100 = normal); ROND 100 gives the rounded terminals used in Minus.
 */
fun googleSansFlex(width: Float = 100f): FontFamily = FontFamily(
    (100..900 step 100).map { w ->
        Font(
            R.font.google_sans_flex,
            FontWeight(w),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w),
                // Width renders incorrectly before API 31, so keep it normal there (same guard as Minus).
                FontVariation.width(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) width else 100f),
                FontVariation.Setting("ROND", 100f),
            ),
        )
    }
)

val GoogleSansFlex = googleSansFlex()

/** Very condensed cut for big clock digits and compact metadata. */
val CondensedFlex = googleSansFlex(width = 25f)

/** M3 type scale (sizes, weights, emphasized variants) with Google Sans Flex everywhere. */
val PomemoTypography = Typography(fontFamily = GoogleSansFlex)
