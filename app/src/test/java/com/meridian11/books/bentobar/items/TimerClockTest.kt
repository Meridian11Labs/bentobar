package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.data.MapPrefs
import com.meridian11.books.bentobar.items.Timers.Mode
import com.meridian11.books.bentobar.items.Timers.Phase
import com.meridian11.books.bentobar.util.ClockAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The timer's rules on a test's clock: the Pomodoro's sequence, a clock that moves, the alarm that comes early, a restart. */
class TimerClockTest {
    private var wall = 1_000_000_000L
    private var elapsed = 50_000L
    private var boot = 7
    private val prefs = MapPrefs()
    /** What the alarm was last armed for: a delay, or null for nothing to arm. */
    private val armed = ArrayList<Long?>()
    private val alerts = ArrayList<TimerClock.Done>()
    private var chipUpdates = 0
    private var stops = 0

    private fun clock() = TimerClock(TimerClock.Wiring(
        now = { wall }, anchor = { ClockAnchor(wall, elapsed, boot) }, prefs = { prefs },
        arm = { armed += it }, done = { alerts += it }, changed = { chipUpdates++ }, log = {}, stopped = { stops++ }))

    private val timer = clock()

    /** Time passes: both clocks move. */
    private fun pass(ms: Long) { wall += ms; elapsed += ms }

    /** The clock is set: only the wall clock moves. */
    private fun setClock(by: Long) { wall += by }

    private val minutes = 60_000L

    @Test fun aTimerRunsToItsEndRingsAndIsGone() {
        timer.startTimer(10 * minutes, label = "tea")
        assertEquals(10 * minutes, armed.last())
        pass(9 * minutes); timer.check()
        assertEquals(minutes, timer.remaining(timer.state.value!!))
        assertEquals("not yet", 0, alerts.size)
        pass(1 * minutes); timer.check()
        assertEquals(listOf(TimerClock.Done.Countdown("tea", 10 * minutes)), alerts)
        assertNull(timer.state.value)
        assertEquals(wall, timer.finishedAt)
        assertEquals(wall, prefs.long("finishedAt", 0))
        assertNull("the alarm is taken back", armed.last())
        assertEquals(null, prefs.string("state"))
    }

    @Test fun stopEndsTheRingOfAFinishedTimer() {
        // What rings is the finished timer's notification; Stop on the item reaches it through the wiring, with or without a timer running.
        timer.startTimer(minutes); pass(minutes); timer.check()
        assertEquals(1, alerts.size)
        assertEquals(0, stops)
        timer.stop()
        assertEquals(1, stops)
    }

    @Test fun thePomodoroGoesWorkBreakFourTimesThenALongBreakAndBack() {
        timer.startPomodoro()
        fun end() { pass(timer.state.value!!.at - wall); timer.check() }
        val seen = ArrayList<Pair<Phase, Int>>()
        repeat(9) { end(); val s = timer.state.value!!; seen += s.phase to s.round }
        assertEquals(listOf(
            Phase.BREAK to 1, Phase.WORK to 1, Phase.BREAK to 2, Phase.WORK to 2, Phase.BREAK to 3, Phase.WORK to 3,
            Phase.LONG_BREAK to 4, Phase.WORK to 4, Phase.BREAK to 5), seen)
        assertEquals(TimerClock.Done.Pomodoro(Phase.WORK, Phase.BREAK, 5), alerts[0])
        assertEquals(TimerClock.Done.Pomodoro(Phase.BREAK, Phase.WORK, 25), alerts[1])
        assertEquals("the fourth work round ends in the long break", TimerClock.Done.Pomodoro(Phase.WORK, Phase.LONG_BREAK, 15), alerts[6])
    }

    @Test fun eachPhaseOfThePomodoroGetsItsOwnLength() {
        timer.startPomodoro()
        assertEquals(Timers.POMO_WORK, timer.state.value!!.lengthMs)
        pass(Timers.POMO_WORK); timer.check()
        assertEquals(Timers.POMO_BREAK, timer.state.value!!.lengthMs)
        assertEquals(Timers.POMO_BREAK, timer.remaining(timer.state.value!!))
    }

    @Test fun aSetClockMovesTheEndWithItSoTheTimeLeftStaysWhatItIs() {
        timer.startTimer(10 * minutes)
        pass(1 * minutes)
        setClock(3_600_000) // an hour ahead
        timer.check()
        assertEquals("not ended by the jump", 0, alerts.size)
        assertEquals(9 * minutes, timer.remaining(timer.state.value!!))
        pass(9 * minutes); timer.check()
        assertEquals(1, alerts.size)
    }

    @Test fun aClockSetBackDoesNotAddToTheTime() {
        timer.startTimer(10 * minutes)
        setClock(-3_600_000)
        timer.check()
        assertEquals(10 * minutes, timer.remaining(timer.state.value!!))
    }

    @Test fun aSmallClockCorrectionIsNotFollowed() {
        timer.startTimer(10 * minutes)
        val at = timer.state.value!!.at
        setClock(1_000)
        timer.check()
        assertEquals("under two seconds isn't a clock change", at, timer.state.value!!.at)
    }

    @Test fun aStopwatchIsNeverFinishedByACheckAndArmsNoAlarm() {
        timer.startStopwatch()
        assertNull(armed.last())
        pass(5 * 3_600_000); timer.check()
        assertEquals(Mode.STOPWATCH, timer.state.value!!.mode)
        assertEquals(5 * 3_600_000L, timer.elapsed(timer.state.value!!))
    }

    @Test fun aPausedTimerKeepsItsTimeAndIsNotFinishedThenResumesWhereItWas() {
        timer.startTimer(10 * minutes)
        pass(2 * minutes)
        timer.pause()
        assertNull("nothing to arm while paused", armed.last())
        pass(60 * minutes); timer.check()
        assertTrue(!timer.state.value!!.running)
        timer.resume()
        assertEquals(8 * minutes, timer.remaining(timer.state.value!!))
        assertEquals(8 * minutes, armed.last())
    }

    @Test fun addingTimeMovesTheEndAndRemovingTimeStopsAtOneSecond() {
        timer.startTimer(10 * minutes)
        timer.add(5 * minutes)
        assertEquals(15 * minutes, timer.remaining(timer.state.value!!))
        timer.add(-100 * minutes)
        assertEquals("never past a second left", 1000L, timer.remaining(timer.state.value!!))
    }

    @Test fun aWheelNotchUpStartsATimerAndANotchDownOverNoTimerDoesNothing() {
        timer.wheel(-1)
        assertNull("down over no timer starts none", timer.state.value)
        assertTrue("nothing armed, nothing saved", armed.isEmpty() && prefs.string("state") == null)
        timer.wheel(0)
        assertNull(timer.state.value)
        timer.wheel(2)
        assertEquals(2 * minutes, timer.remaining(timer.state.value!!))
        timer.wheel(3)
        assertEquals(5 * minutes, timer.remaining(timer.state.value!!))
        timer.wheel(-1)
        assertEquals("down takes a minute from a running one", 4 * minutes, timer.remaining(timer.state.value!!))
    }

    @Test fun anAlarmThatComesEarlyIsArmedAgainForWhatIsLeft() {
        // After a small correction the wall clock is behind the alarm's real time: nothing ends and the timer must ring later.
        timer.startTimer(10 * minutes)
        pass(10 * minutes - 1_500)
        armed.clear()
        timer.due()
        assertEquals("not finished", 0, alerts.size)
        assertEquals("armed again for the rest", listOf<Long?>(1_500L), armed)
    }

    @Test fun anAlarmThatComesOnTimeFinishesAndArmsNothing() {
        timer.startTimer(10 * minutes)
        pass(10 * minutes)
        armed.clear()
        timer.due()
        assertEquals(1, alerts.size)
        assertEquals(listOf<Long?>(null), armed)
    }

    @Test fun aRunningTimerSurvivesARestartAndIsArmedAgain() {
        timer.startTimer(10 * minutes, label = "soup")
        pass(4 * minutes)
        val after = clock()
        after.load()
        assertEquals("soup", after.state.value!!.label)
        assertEquals(6 * minutes, after.remaining(after.state.value!!))
        assertEquals(6 * minutes, armed.last())
    }

    @Test fun aTimerThatEndedWhileNothingRanEndsAtTheNextStart() {
        timer.startTimer(10 * minutes)
        pass(30 * minutes)
        val after = clock()
        after.load()
        assertNull(after.state.value)
        assertEquals(1, alerts.size)
    }

    /** The device restarts: real time starts again from nothing, in a new boot, and the wall clock goes on. */
    private fun reboot(downFor: Long, clockBehind: Long = 0) { boot++; elapsed = 20_000; wall += downFor + 20_000 - clockBehind }

    @Test fun aRunningTimerFollowsAClockChangeAgainAfterARestart() {
        timer.startTimer(30 * minutes)
        pass(5 * minutes)
        reboot(downFor = 2 * minutes)
        val after = clock()
        after.load()
        val left = 30 * minutes - 5 * minutes - 2 * minutes - 20_000
        assertEquals(left, after.remaining(after.state.value!!))
        // Once the new boot's clock has settled, the timer is anchored in it: a clock set an hour ahead is followed.
        pass(ClockAnchor.SETTLED_MS); after.check()
        assertEquals(boot, after.state.value!!.anchorBoot)
        setClock(3_600_000); after.check()
        assertEquals(left - ClockAnchor.SETTLED_MS, after.remaining(after.state.value!!))
        assertTrue("not ended by the clock", alerts.isEmpty())
    }

    @Test fun theNetworksCorrectionOfTheClockJustAfterARestartIsNotTakenForAClockChange() {
        // Back after ten minutes with the hardware clock five minutes behind; the network then puts it right.
        timer.startTimer(30 * minutes)
        reboot(downFor = 10 * minutes - 20_000, clockBehind = 5 * minutes)
        val after = clock()
        after.load()
        assertEquals(25 * minutes, after.remaining(after.state.value!!))
        setClock(5 * minutes); after.check()
        assertEquals("by the wall clock, which is right now", 20 * minutes, after.remaining(after.state.value!!))
    }

    @Test fun pauseFollowsAClockChangeNoTickHasSeenYet() {
        timer.startTimer(10 * minutes)
        pass(2 * minutes)
        setClock(3_600_000)
        timer.pause()
        assertEquals(8 * minutes, timer.remaining(timer.state.value!!))
        assertTrue(alerts.isEmpty())
    }

    @Test fun addingTimeFollowsAClockChangeNoTickHasSeenYet() {
        // The chip's +1 min right after the clock was set ahead.
        timer.startTimer(10 * minutes)
        pass(minutes)
        setClock(30 * minutes)
        timer.add(5 * minutes)
        assertEquals(14 * minutes, timer.remaining(timer.state.value!!))
        assertTrue(alerts.isEmpty())
    }

    @Test fun aPauseAfterTheTimeIsUpEndsTheTimerInsteadOfPausingItAtNothing() {
        timer.startTimer(minutes)
        pass(minutes + 500)
        timer.pause()
        assertEquals(1, alerts.size)
        assertNull(timer.state.value)
    }

    @Test fun whatWasSavedByAnEarlierVersionWithoutAnAnchorStillLoads() {
        // Saved before anchors existed: no clock change can be told from it, and none is followed.
        prefs.edit { put("state", """{"mode":"TIMER","running":true,"at":${wall + 5 * minutes},"lengthMs":${10 * minutes}}""") }
        val after = clock()
        after.load()
        assertEquals(5 * minutes, after.remaining(after.state.value!!))
        assertFalse("an unknown anchor is not a jump", alerts.isNotEmpty())
    }

    @Test fun whatCantBeReadIsNoTimerRatherThanACrash() {
        prefs.edit { put("state", "not json") }
        val after = clock()
        after.load()
        assertNull(after.state.value)
    }

    @Test fun stoppingForgetsTheFinishedMoment() {
        timer.startTimer(1 * minutes)
        pass(2 * minutes); timer.check()
        assertTrue(timer.finishedAt != 0L)
        timer.stop()
        assertEquals(0L, timer.finishedAt)
        assertEquals(0L, prefs.long("finishedAt", -1))
    }

    @Test fun everyChangeReachesTheChip() {
        timer.startTimer(1 * minutes)
        val before = chipUpdates
        timer.pause(); timer.resume(); timer.add(1000)
        assertEquals(before + 3, chipUpdates)
    }
}
