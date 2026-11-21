package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

/** Calendar's loading is [Refresher]'s on its own numbers: a change reloads it at once, and otherwise it is good for a while; one more after a burst of changes. */
class CalendarLoaderTest {
    private var clock = 1_000_000L
    private var loads = 0
    private var may = true
    private val waiting = ArrayDeque<Runnable>()
    private var changes = 0
    private val background = Executor { waiting.addLast(it) }
    private fun run() { while (waiting.isNotEmpty()) waiting.removeFirst().run() }

    private var failures = 0
    private var broken = false
    private val lasts = ArrayList<List<Calendar.Event>?>()

    private val loader = calendarLoader<List<Calendar.Event>>(calendarWiring(background, { it.run() }, { clock }, changed = { changes++ }, failed = { failures++ }, mayLoad = { may })) { last ->
        lasts += last
        loads++
        if (broken) throw IllegalStateException("the provider is away")
        listOf(Calendar.Event(loads.toLong(), "e", 0, 1, false, 0, "", true, false, null))
    }

    @Test fun itIsReadAgainOnlyAfterItsFreshnessHasRunOut() {
        loader.want(Unit); run()
        assertEquals(1, loads)
        clock += Calendar.RELOAD_MS - 1_000; loader.want(Unit); run()
        assertEquals(1, loads)
        clock += 1_000; loader.want(Unit); run()
        assertEquals(2, loads)
        assertEquals(2, changes)
    }

    @Test fun aProviderChangeReloadsItAtOnceWhateverItsFreshness() {
        loader.want(Unit); run()
        assertEquals(true, loader.refresh(Unit, afterRunning = true))
        run()
        assertEquals(2, loads)
    }

    @Test fun nobodyUsingItMeansNothingLoadsAndItsReadingIsForgotten() {
        // F28: a provider change or an item's state with no calendar item and the chip off reloaded 41 days.
        loader.want(Unit); run()
        may = false
        loader.forget()
        loader.want(Unit); assertFalse(loader.refresh(Unit, afterRunning = true)); run()
        assertEquals(1, loads)
        assertEquals(null, loader.peek(Unit))
    }

    @Test fun onlyAFirstLoadMeansNothingIsKnownYet() {
        assertFalse(loader.loadingFirst())
        loader.want(Unit)
        assertTrue("the first load is on its way", loader.loadingFirst())
        run()
        assertFalse("something has been read", loader.loadingFirst())
        assertTrue(loader.refresh(Unit, afterRunning = true))
        assertFalse("a reload still has the last reading", loader.loadingFirst())
        run()
    }

    @Test fun aBurstOfProviderChangesQueuesOneMoreLoadNotHundreds() {
        loader.want(Unit) // the first load is running
        repeat(300) { loader.refresh(Unit, afterRunning = true) }
        run()
        assertEquals("the running load and one more", 2, loads)
    }

    @Test fun switchingTheCalendarOffDropsWhatARunningLoadBrings() {
        loader.want(Unit)
        loader.forget()
        run()
        assertEquals(null, loader.peek(Unit))
        assertEquals(0, changes)
    }

    @Test fun whileItMayNotLoadNothingHappens() {
        may = false
        loader.want(Unit); assertFalse(loader.refresh(Unit, afterRunning = true)); run()
        assertEquals(0, loads)
    }

    @Test fun aReadThatFailsKeepsTheLastReadingAndSaysSo() {
        // A meeting ten minutes away must not go from the bar and the chip because the provider was away for one read.
        loader.want(Unit); run()
        broken = true
        clock += Calendar.RELOAD_MS; loader.want(Unit); run()
        assertEquals(2, loads)
        assertEquals("the last reading stays", 1L, loader.peek(Unit)?.single()?.eventId)
        assertEquals("told, so nobody waits for it", 1, failures)
        assertEquals(1, changes)
    }

    @Test fun afterAFailureItIsReadAgainAfterTheRetryOrAtAProviderChange() {
        loader.want(Unit); run()
        broken = true
        clock += Calendar.RELOAD_MS; loader.want(Unit); run()
        clock += Calendar.RETRY_MS - 1_000; loader.want(Unit); run()
        assertEquals("not every second meanwhile", 2, loads)
        clock += 1_000; loader.want(Unit); run()
        assertEquals(3, loads)
        assertTrue(loader.refresh(Unit, afterRunning = true)); run()
        assertEquals(4, loads)
        broken = false
        assertTrue(loader.refresh(Unit, afterRunning = true)); run()
        assertEquals(5L, loader.peek(Unit)?.single()?.eventId)
    }

    @Test fun eachLoadIsGivenTheLastReading() {
        loader.want(Unit); run()
        loader.refresh(Unit); run()
        assertEquals(listOf(null, 1L), lasts.map { it?.single()?.eventId })
    }

    @Test fun aReadingWithoutItsAttendeesTakesNoReadingsPlace() {
        // Nobody is known to be invited then: every meeting with others in it would be one with only the user, and go.
        val cals = emptyMap<Long, Meetings.Cal>()
        val good = Meetings.Reading(cals, emptyList())
        val unread = Meetings.Reading(cals, emptyList(), guestsFailed = "SQLiteException")
        assertFalse(unread.replaces(good))
        assertFalse(unread.replaces(good, failedInARow = 2))
        assertTrue("better than nothing", unread.replaces(null))
        assertTrue(good.replaces(good))
        // A calendar whose attendees are never to be had is not held at its first reading for good.
        assertTrue("no better than the last", unread.replaces(unread))
        assertTrue("the third in a row", unread.replaces(good, failedInARow = Meetings.GUESTS_FAILED_TO_REPLACE))
    }

    @Test fun aQueryWithNoAnswerIsAFailureNotAnEmptyCalendar() {
        // A null cursor read as no calendars hid every event, and as no rows emptied the day.
        val code = SourceScan.code(SourceScan.sources.single { it.name == "Calendar.kt" })
        assertFalse(code, Regex("""\)\?\.use \{""").containsMatchIn(code))
        assertEquals("the calendars, the occurrences and the attendees", 3, Regex("""\?: throw Unanswered\(\)\)\.use \{""").findAll(code).count())
        // And a read that threw is no reading of nothing: it leaves the last one as it is.
        assertFalse(code, "Meetings.Reading(emptyMap(), emptyList())" in code)
    }

    @Test fun theCalendarKeepsLoadingWhileTheServiceIsOffForTheChip() {
        // Background's wiring only loads while Ticker.running; the Live Update chip needs meetings with the bar gone.
        val code = SourceScan.code(SourceScan.sources.single { it.name == "Calendar.kt" })
        assertFalse(code, code.contains("Ticker"))
        assertTrue(code.contains("Refresher.Wiring("))
        assertFalse(code, code.contains("Background.wiring"))
    }
}
