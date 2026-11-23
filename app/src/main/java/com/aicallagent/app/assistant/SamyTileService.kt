package com.aicallagent.app.assistant

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.aicallagent.app.agent.AgentSettings
import com.aicallagent.app.ui.SamyActivity

/** Quick-settings tile: "Ask Samy" from anywhere, one swipe down and a tap. */
class SamyTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            label = "Ask ${AgentSettings.load(this@SamyTileService).voice}"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, SamyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
