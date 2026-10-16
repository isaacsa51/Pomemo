package com.serranoie.app.pomemo.tiles

import android.content.Intent
import android.service.quicksettings.TileService
import com.serranoie.app.pomemo.MainActivity
import com.serranoie.app.pomemo.R

/** "New note": opens the app on Notes with an empty note already in the editor. */
class NewNoteTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        renderAction(R.string.tab_notes)
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            openAndCollapse(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_NEW_NOTE)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }
}
