package com.meridian11.books.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Test

/** When the calendar reads the provider again by itself: once a burst of its changes has settled, or a while after a read failed. */
class ReadAgainTest {
    private var clock = 0L
    private val scheduled = ArrayList<Pair<Long, Runnable>>()
    /** Each read, and whether a change of the provider was behind it. */
    private val reads = ArrayList<Pair<Long, Boolean>>()
    private val again = ReadAgain(quietMs = 2_000, capMs = 10_000, retryMs = 120_000, now = { clock },
        later = { ms, r -> scheduled += (clock + ms) to r }, cancel = { r -> scheduled.removeAll { it.second === r } }) { changed -> reads += clock to changed }

    /** Lets [ms] go by, running what falls due in it, in order. */
    private fun pass(ms: Long) {
        val end = clock + ms
        while (true) {
            val next = scheduled.filter { it.first <= end }.minByOrNull { it.first } ?: break
            scheduled.remove(next)
            clock = next.first
            next.second.run()
        }
        clock = end
    }

    @Test fun aBurstOfChangesIsReadOnceWhenItHasSettled() {
        again.changed()
        repeat(4) { pass(500); again.changed() }
        pass(1_999)
        assertEquals(emptyList<Pair<Long, Boolean>>(), reads)
        pass(1)
        assertEquals(listOf(4_000L to true), reads)
        pass(60_000)
        assertEquals("nothing more", 1, reads.size)
    }

    @Test fun aBurstThatGoesOnIsReadAtMostTheCapAfterItsFirstChange() {
        // A sync of hundreds of events: the bar shows what it brought while it goes on, every ten seconds.
        again.changed()
        repeat(50) { pass(500); again.changed() }
        assertEquals(listOf(10_000L to true, 20_000L to true), reads)
        pass(2_000)
        assertEquals(listOf(10_000L to true, 20_000L to true, 27_000L to true), reads)
    }

    @Test fun aReadThatFailedIsTriedAgainLaterUnlessOneCameBack() {
        again.failed()
        pass(119_999)
        assertEquals(emptyList<Pair<Long, Boolean>>(), reads)
        pass(1)
        assertEquals(listOf(120_000L to false), reads)
        again.failed()
        pass(60_000)
        again.loaded() // a provider change or an item's own ask read it meanwhile
        pass(120_000)
        assertEquals(1, reads.size)
    }

    @Test fun aChangeWhileARetryWaitsIsReadWhenItSettlesAndTheRetryWithIt() {
        again.failed()
        pass(30_000)
        again.changed()
        pass(2_000)
        assertEquals(listOf(32_000L to true), reads)
        pass(200_000)
        assertEquals("one read stands for both", 1, reads.size)
    }

    @Test fun aRetryDueWhileABurstSettlesReadsForTheChangesToo() {
        again.failed()
        pass(119_000)
        again.changed()
        pass(1_000)
        assertEquals("read after the change, so it has it", listOf(120_000L to true), reads)
        pass(10_000)
        assertEquals(1, reads.size)
    }

    @Test fun aReadingThatCameBackLeavesAChangeAfterItToBeRead() {
        again.changed()
        pass(2_000)
        again.changed() // during the load
        again.loaded()
        pass(2_000)
        assertEquals(listOf(2_000L to true, 4_000L to true), reads)
    }

    @Test fun stoppedNothingIsRead() {
        again.changed(); again.failed()
        again.stop()
        pass(300_000)
        assertEquals(emptyList<Pair<Long, Boolean>>(), reads)
        assertEquals(emptyList<Pair<Long, Runnable>>(), scheduled)
        // And a burst after it is a burst of its own.
        again.changed()
        pass(2_000)
        assertEquals(listOf(302_000L to true), reads)
    }
}
