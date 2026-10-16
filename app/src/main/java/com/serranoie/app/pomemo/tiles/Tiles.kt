package com.serranoie.app.pomemo.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.StringRes

/** Opens [intent] and closes the shade. Android 14+ only takes a PendingIntent here; older versions only an Intent. */
@SuppressLint("StartActivityAndCollapseDeprecated")
fun TileService.openAndCollapse(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }
}

/** Sets the tile's state and its subtitle (the subtitle line exists from Android 10). */
fun TileService.render(state: Int, subtitle: String) {
    val tile = qsTile ?: return
    tile.state = state
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
    tile.updateTile()
}

fun TileService.render(state: Int, @StringRes subtitle: Int) = render(state, getString(subtitle))

/** For tiles that only launch something: always tappable, never "on". */
fun TileService.renderAction(@StringRes subtitle: Int) = render(Tile.STATE_INACTIVE, subtitle)
