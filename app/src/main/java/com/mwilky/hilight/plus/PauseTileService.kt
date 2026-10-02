package com.mwilky.hilight.plus

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mwilky.hilight.plus.ui.pauseTileSubtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: lit while a pause is in force. A tap resumes straight away; otherwise it
 * opens the pause sheet. Long-pressing it opens the app.
 */
class PauseTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render(AppStore.get(applicationContext).activePause.first()) }
    }

    override fun onClick() {
        super.onClick()
        // Decided from what the tile shows, so the sheet opens inside the tap rather than after
        // a read, which Android may no longer count as the user's own action.
        if (qsTile?.state == Tile.STATE_ACTIVE) {
            LightController.get(applicationContext)
            scope.launch {
                AppStore.get(applicationContext).endPause()
                render(null)
            }
        } else if (isLocked) {
            unlockAndRun(::openSheet)
        } else {
            openSheet()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openSheet() {
        startActivityAndCollapse(
            PendingIntent.getActivity(this, 0, PauseActivity.intent(this), PendingIntent.FLAG_IMMUTABLE)
        )
    }

    private fun render(pause: PauseState?) {
        val tile = qsTile ?: return
        tile.state = if (pause != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = pause?.let { pauseTileSubtitle(this, it) }
        tile.updateTile()
    }

    companion object {
        /** Asks the system to redraw the tile, if it's in the shade at all. */
        fun refresh(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, PauseTileService::class.java))
            }
        }
    }
}
