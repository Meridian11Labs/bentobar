package com.meridian11.books.bentobar.items

/**
 * When [Calendar] reads the provider again by itself, as one read that waits: after a burst of the provider's changes has
 * settled ([changed]: [quietMs] after the last of them, and never more than [capMs] after the first, so a sync that goes on
 * for minutes still shows what it brings), or a while after a read that failed ([failed]: [retryMs] later, the last reading
 * kept meanwhile). One read stands for both, whichever falls due first; [read] hears whether a change is behind it (a load
 * running then needs one more after it). A reading that came back ([loaded]) owes no retry. The delay and its cancelling
 * come in, so the tests run it by hand. Main thread; pure.
 */
internal class ReadAgain(
    private val quietMs: Long,
    private val capMs: Long,
    private val retryMs: Long,
    private val now: () -> Long,
    private val later: (delayMs: Long, run: Runnable) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val read: (changed: Boolean) -> Unit,
) {
    /** When the burst of changes being settled began; null with none. */
    private var burstFrom: Long? = null
    private var settleAt: Long? = null
    private var retryAt: Long? = null
    /** When [due] is set to run; null while it isn't. */
    private var postedAt: Long? = null

    private val due = Runnable {
        val changed = settleAt != null
        postedAt = null; burstFrom = null; settleAt = null; retryAt = null
        read(changed)
    }

    /** The provider says something changed. */
    fun changed() {
        val t = now()
        val from = burstFrom ?: t.also { burstFrom = it }
        settleAt = minOf(t + quietMs, from + capMs)
        post()
    }

    /** A read failed: it is tried again in [retryMs]. */
    fun failed() {
        retryAt = now() + retryMs
        post()
    }

    /** A reading came back: no retry is owed. Changes since it began are still read. */
    fun loaded() {
        retryAt = null
        post()
    }

    /** Nobody needs the calendar: nothing is read. */
    fun stop() {
        burstFrom = null; settleAt = null; retryAt = null
        post()
    }

    private fun post() {
        val at = listOfNotNull(settleAt, retryAt).minOrNull()
        if (at == postedAt) return
        cancel(due)
        postedAt = at
        if (at != null) later((at - now()).coerceAtLeast(0), due)
    }
}
