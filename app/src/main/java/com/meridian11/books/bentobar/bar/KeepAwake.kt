package com.meridian11.books.bentobar.bar

import android.app.KeyguardManager
import android.os.Handler
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import com.meridian11.books.bentobar.items.Caffeine
import com.meridian11.books.bentobar.items.DisplaysItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keep awake holds the screen on through the strip's own window (FLAG_KEEP_SCREEN_ON on a
 * window that's visible), not a window of its own. It used to be a separate 1×1 see-through
 * overlay: Play Protect's live threat detection flags an accessibility service that keeps
 * "imperceptible content" on screen ("App displays over other apps"). The catch: while the strip
 * is hidden (a full-screen app, the screen locked), keep awake waits; full-screen video players
 * keep the screen on themselves.
 *
 * [windows] are the strips' windows, and [carrier] says which of them holds the flag right now: one that is on
 * screen with something to draw, or none.
 */
internal class KeepAwake(
    private val main: Handler,
    private val pm: PowerManager,
    private val km: KeyguardManager,
    private val scope: CoroutineScope,
    private val windows: List<Overlay>,
    private val carrier: () -> Overlay?,
) {
    private val tag = BarService.TAG
    private val expiry = Runnable { Caffeine.check() }
    private var stopped = false
    private var wasOn = false

    /** See [update]. Deprecated in favor of FLAG_KEEP_SCREEN_ON, which needs a visible window. */
    @Suppress("DEPRECATION")
    private val screenLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "BentoBar:keepAwake").apply { setReferenceCounted(false) }

    fun start() {
        scope.launch {
            Caffeine.until.collect { until ->
                update()
                main.removeCallbacks(expiry)
                // Real time left, as the timers count it: while the bar is hidden no tick is there to notice the clock moved.
                if (until != 0L && until != Caffeine.FOREVER) main.postDelayed(expiry, Caffeine.left(until).coerceAtLeast(0) + 200)
            }
        }
        scope.launch { DisplaysItem.keepingAwake.collect { update() } }
    }

    fun update() {
        if (stopped) return
        val on = Caffeine.active() || DisplaysItem.keepingAwake.value
        if (on != wasOn) { wasOn = on; Log.i(tag, if (on) "keep awake on" else "keep awake off") }
        val c = KeepAwakeRule.choose(on, if (on) carrier() else null, pm.isInteractive, km.isKeyguardLocked)
        // The flag is on the window that carries it before it leaves any other: at no moment is it on none.
        c.flag?.let { flag(it, true) }
        if (c.clear) for (window in windows) if (window !== c.flag) flag(window, false)
        if (c.lock && !screenLock.isHeld) { screenLock.acquire(); Log.i(tag, "keep awake: wake lock while the bar is hidden") }
        else if (!c.lock && screenLock.isHeld) screenLock.release()
    }

    private fun flag(window: Overlay, on: Boolean) {
        val f = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val flags = if (on) window.params.flags or f else window.params.flags and f.inv()
        if (flags == window.params.flags) return
        window.params.flags = flags
        window.relayout()
    }

    /** The service is going: nothing it held may stay held, and from here on nothing is asked for. */
    fun stop() {
        stopped = true
        if (screenLock.isHeld) screenLock.release()
    }
}

/** What keep awake does with the strips' windows, as plain facts: [KeepAwake.update] carries it out (KeepAwakeTest). */
internal object KeepAwakeRule {
    /** [flag]: the window that holds the screen on, if one does. [clear]: every other window lets go. [lock]: a wake lock holds it instead. */
    data class Choice<W>(val flag: W?, val clear: Boolean, val lock: Boolean)

    /**
     * [carrier] is the strip's window that is on screen with something to draw, or null. With no carrier the flags stay
     * where they are, and the window that comes back with one holds the screen at once.
     *
     * While the strip is hidden (a full-screen app, BentoBar hidden from its tile) or has nothing to draw, no visible
     * window of ours carries the flag: a screen wake lock holds it instead, with no window. Released when the strip is
     * back, keep awake ends, the screen goes off (the power key or the lid) or the lock screen is up.
     */
    fun <W> choose(on: Boolean, carrier: W?, interactive: Boolean, locked: Boolean): Choice<W> {
        val flag = carrier.takeIf { on }
        return Choice(flag, clear = flag != null || !on, lock = on && flag == null && interactive && !locked)
    }
}
