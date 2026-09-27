package com.mp01home.launcher.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.lang.ref.WeakReference

/**
 * A vendor-specific full-refresh hook (UX-6). Return true if the vendor API handled the refresh;
 * false (or throw) to fall through to the next hook / the generic black flash.
 */
fun interface VendorRefreshHook {
    fun fullRefresh(view: View): Boolean
}

object VendorRefreshHooks {
    private const val TAG = "VendorRefresh"

    /**
     * Onyx Boox: only works if the Onyx SDK classes are on the classpath (they are not bundled by
     * default). Pure reflection, so it's a harmless no-op elsewhere.
     */
    val onyx = VendorRefreshHook { view ->
        runCatching {
            val controller = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            val modeClass = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode")
            val gc = modeClass.getField("GC").get(null)
            controller.getMethod("invalidate", View::class.java, modeClass).invoke(null, view, gc)
            true
        }.getOrElse {
            false
        }
    }

    /**
     * Hooks tried in order before the generic fallback. The Minimal Phone MP01 has no public
     * refresh API (decision D3), so the generic flash is what normally runs there.
     */
    val defaults: List<VendorRefreshHook> = listOf(onyx)

    internal fun tryAll(hooks: List<VendorRefreshHook>, view: View): Boolean = hooks.any { hook ->
        runCatching { hook.fullRefresh(view) }.getOrElse { e ->
            Log.d(TAG, "hook failed", e)
            false
        }
    }
}

/**
 * EINK-5 / CORE-6: ghosting control.
 *
 * - [requestFullRefresh] (double-tap gesture): tries vendor hooks, otherwise briefly paints the
 *   whole window black via [EinkFlashOverlay] and then redraws, which on e-ink panels forces a
 *   full waveform and clears ghosting.
 * - Every frame drawn by an attached activity counts as a partial update; after
 *   [fullRefreshEveryN] of them (0 = off) a full refresh is triggered automatically.
 *
 * One instance lives in [com.mp01home.launcher.AppContainer.einkRefresh]. Main-thread only.
 */
class EinkRefreshController(
    /** Replaced at runtime from the UX-6 vendor setting (see eink/VendorRefresh.kt). */
    @Volatile var vendorHooks: List<VendorRefreshHook> = VendorRefreshHooks.defaults
) {
    /** True while the black flash overlay should be shown. Compose-observable. */
    var flashActive: Boolean by mutableStateOf(false)
        private set

    private val counter = PartialUpdateCounter()

    /** Set from settings; 0 disables automatic full refreshes. */
    var fullRefreshEveryN: Int
        get() = counter.everyN
        set(value) {
            counter.everyN = value
        }

    /** Partial updates counted since the last full refresh. */
    val partialUpdateCount: Int get() = counter.count

    private val mainHandler = Handler(Looper.getMainLooper())
    private var attachedView: WeakReference<View>? = null
    private var pendingAutoRefresh = false

    private val drawListener = ViewTreeObserver.OnDrawListener { notePartialUpdate() }

    /** Call from Activity.onCreate (after setContent). Counts every drawn frame. */
    fun attach(activity: Activity) {
        detach()
        val view = activity.window.decorView
        attachedView = WeakReference(view)
        view.viewTreeObserver.addOnDrawListener(drawListener)
    }

    /** Call from Activity.onDestroy. */
    fun detach() {
        attachedView?.get()?.let { view ->
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(drawListener)
        }
        attachedView = null
    }

    /** Forces a full panel refresh now. */
    fun requestFullRefresh() {
        val view = attachedView?.get()
        if (view != null && VendorRefreshHooks.tryAll(vendorHooks, view)) {
            counter.reset()
            return
        }
        if (flashActive) return
        flashActive = true
        mainHandler.postDelayed({
            flashActive = false
            counter.reset()
        }, FLASH_DURATION_MILLIS)
    }

    /**
     * Records one partial screen update. Called automatically for attached activities; call it
     * manually only for updates that do not go through the attached window.
     */
    fun notePartialUpdate() {
        if (flashActive || pendingAutoRefresh) return
        if (counter.note()) {
            // Never mutate UI state from inside a draw pass: post it.
            pendingAutoRefresh = true
            mainHandler.post {
                pendingAutoRefresh = false
                requestFullRefresh()
            }
        }
    }

    companion object {
        const val FLASH_DURATION_MILLIS = 300L
    }
}

/**
 * EINK-5 counting rule, free of Android types so it is unit-testable: [note] records one partial
 * update and returns true when the [everyN]-th one since the last [reset] has been reached
 * (0 or less = never). Counting stops at the threshold until [reset] (the full refresh) runs.
 */
class PartialUpdateCounter(everyN: Int = 0) {

    @Volatile
    var everyN: Int = everyN

    var count: Int = 0
        private set

    fun note(): Boolean {
        val n = everyN
        if (n <= 0) {
            count = 0
            return false
        }
        if (count < n) count++
        return count >= n
    }

    fun reset() {
        count = 0
    }
}

/** Place last inside the root Box so it covers the whole window while a flash is active. */
@Composable
fun EinkFlashOverlay(controller: EinkRefreshController) {
    if (controller.flashActive) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    }
}
