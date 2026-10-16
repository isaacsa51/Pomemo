package com.serranoie.app.pomemo.tiles

import android.content.Intent
import android.service.quicksettings.TileService
import com.serranoie.app.pomemo.R

/** "Reminder · What for and when?": a one-field sheet over whatever is on screen; the parser does the rest. */
class ReminderTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        renderAction(R.string.tile_reminder_subtitle)
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun { openAndCollapse(Intent(this, QuickReminderActivity::class.java)) }
    }
}
