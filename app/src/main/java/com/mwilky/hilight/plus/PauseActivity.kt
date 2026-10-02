package com.mwilky.hilight.plus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mwilky.hilight.plus.ui.HiLightPlusTheme
import com.mwilky.hilight.plus.ui.PauseSheet

/** A see-through window holding only the pause sheet, so it opens over the shade or the app. */
class PauseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The sheet slides in and out by itself; the window around it shouldn't animate too.
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        // Opened from the tile, the app's process may have only just started: this is what
        // passes the new pause on to the daemon.
        LightController.get(this)
        setContent {
            HiLightPlusTheme {
                PauseSheet(onDone = ::finish)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, PauseActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
