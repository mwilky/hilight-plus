package com.mwilky.hilight.plus

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Play's in-app review sheet, asked for once the app has proved itself: installed a few
 * days, a custom rule saved, the ring lit for real a number of times, and working right now.
 * Also asked straight after a purchase. Play decides whether the sheet actually shows and never
 * says, so every request is recorded and the next waits out [COOLDOWN_MS] either way.
 *
 * Everyone who passes the checks is asked the same way; Play policy forbids asking first
 * whether they like the app.
 */
internal object ReviewPrompt {
    private const val TAG = "ReviewPrompt"
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    const val MIN_INSTALL_AGE_MS = 3 * DAY_MS
    const val MIN_LIT_ALERTS = 10
    const val COOLDOWN_MS = 90 * DAY_MS

    fun cooldownOver(now: Long, lastRequestMs: Long?): Boolean =
        lastRequestMs == null || now - lastRequestMs >= COOLDOWN_MS

    fun isDue(
        now: Long,
        installedAtMs: Long,
        litAlerts: Int,
        hasCustomRule: Boolean,
        entitled: Boolean,
        connected: Boolean,
        lastRequestMs: Long?
    ): Boolean =
        cooldownOver(now, lastRequestMs) &&
            now - installedAtMs >= MIN_INSTALL_AGE_MS &&
            litAlerts >= MIN_LIT_ALERTS &&
            hasCustomRule &&
            entitled &&
            connected

    /**
     * Asks Play for the sheet if it's due. [afterPurchase] skips the usage checks: someone who
     * has just paid has already decided the app works for them.
     */
    suspend fun maybeAsk(activity: Activity, controller: LightController, afterPurchase: Boolean = false) {
        val store = controller.store
        val now = System.currentTimeMillis()
        val lastRequest = store.reviewRequestedMillis.first()
        val due = if (afterPurchase) {
            cooldownOver(now, lastRequest)
        } else {
            val snapshot = store.snapshot()
            isDue(
                now = now,
                installedAtMs = installedAt(activity) ?: return,
                litAlerts = store.litAlertCount.first(),
                hasCustomRule = snapshot.contactRules.isNotEmpty() ||
                    snapshot.messageContactRules.isNotEmpty() ||
                    snapshot.appRules.isNotEmpty(),
                entitled = controller.licensing.status.value.entitled,
                connected = controller.daemon.state.value == DaemonBridge.State.CONNECTED,
                lastRequestMs = lastRequest
            )
        }
        if (!due) return

        // Recorded first, so a request Play turns down isn't retried on every resume.
        store.setReviewRequestedMillis(now)
        try {
            val manager = ReviewManagerFactory.create(activity)
            val info = manager.requestReviewFlow().await()
            // Logged before launching: the sheet pauses this activity, which cancels this coroutine.
            DebugLog.i(TAG, "Asked Play for the review sheet (after purchase: $afterPurchase)")
            manager.launchReviewFlow(activity, info).await()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Not installed from Play, Play Store missing or out of date: nothing to show.
            DebugLog.w(TAG, "Review sheet unavailable: ${t.message}")
        }
    }

    /** Opens the app's Play listing, for people who choose to rate from About. */
    fun openListing(context: Context) {
        val pkg = context.packageName
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(web) }
        }
    }

    // The review library's Tasks, awaited without pulling in its ktx artifact, which references a
    // Play services class this app doesn't ship and so fails R8.
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val error = task.exception
            if (error == null) cont.resume(task.result) else cont.resumeWithException(error)
        }
    }

    private fun installedAt(context: Context): Long? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrNull()
}
