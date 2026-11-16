package com.meridian11.books.bentobar.data

import com.meridian11.books.bentobar.data.MeetingChoices.OnlyMe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Which events count: the install's choices, in their own file in the no-backup directory and never in the layout. */
class MeetingChoicesTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "bentobar-meetings-${System.nanoTime()}")
    private var told = 0

    @Before fun fresh() {
        dir.mkdirs()
        MeetingChoices.onChange = { told++ }
        MeetingChoices.init(dir)
    }

    @After fun gone() {
        MeetingChoices.onChange = {}
        dir.deleteRecursively()
        // Leave the defaults for whoever runs next.
        MeetingChoices.init(File(dir, "empty"))
    }

    @Test fun theDefaults() {
        val s = MeetingChoices.state.value
        assertEquals(OnlyMe.CALL, s.onlyMe)
        assertTrue(s.unanswered)
        assertFalse(s.free)
        assertEquals(emptyList<String>(), s.words)
        assertEquals(emptySet<String>(), s.calendarsOff)
    }

    @Test fun whatIsChosenIsThereAfterARestart() {
        val chosen = MeetingChoices.State(OnlyMe.NEVER, unanswered = false, free = true, words = listOf("Hold", "Out of office"), calendarsOff = setOf("com.google\nyou@example.com\nteam@example.com"))
        MeetingChoices.update { chosen }
        assertEquals(1, told)
        MeetingChoices.init(dir)
        assertEquals(chosen, MeetingChoices.state.value)
        assertTrue(File(dir, "meetings.json").isFile)
    }

    @Test fun nothingChangedIsNothingTold() {
        MeetingChoices.update { it }
        assertEquals(0, told)
        assertFalse(File(dir, "meetings.json").exists())
    }

    @Test fun aBrokenFileIsTheDefaults() {
        File(dir, "meetings.json").writeText("{ not json")
        MeetingChoices.init(dir)
        assertEquals(MeetingChoices.State(), MeetingChoices.state.value)
        File(dir, "meetings.json").writeText("""{"onlyMe":"sometimes","words":["a"]}""")
        MeetingChoices.init(dir)
        assertEquals("an answer it doesn't know", OnlyMe.CALL, MeetingChoices.state.value.onlyMe)
        assertEquals(listOf("a"), MeetingChoices.state.value.words)
    }

    @Test fun wordsAreCleanedOnTheWayInAndOnRead() {
        MeetingChoices.update { it.copy(words = listOf(" Hold ", "hold", "", "Lunch\nbreak")) }
        assertEquals(listOf("Hold", "Lunch break"), MeetingChoices.state.value.words)
        File(dir, "meetings.json").writeText("""{"words":["x","X","‮y"]}""")
        MeetingChoices.init(dir)
        assertEquals(listOf("x", "y"), MeetingChoices.state.value.words)
    }

    @Test fun sixtyFourCalendarsAtMost() {
        MeetingChoices.update { it.copy(calendarsOff = (1..100).mapTo(HashSet()) { "local\ncal$it\n" }) }
        assertEquals(MeetingChoices.MAX_CALENDARS, MeetingChoices.state.value.calendarsOff.size)
        MeetingChoices.init(dir)
        assertEquals(MeetingChoices.MAX_CALENDARS, MeetingChoices.state.value.calendarsOff.size)
    }

    @Test fun noWordAndNoCalendarIsInTheLayout() {
        val prefs = MapPrefs()
        Store.open(prefs) { }
        MeetingChoices.update { it.copy(words = listOf("Therapy"), calendarsOff = setOf("com.google\nyou@example.com\nteam@example.com")) }
        val exported = Store.export()
        assertFalse("Therapy" in exported)
        assertFalse("team@example.com" in exported)
        assertFalse(prefs.map.values.any { it is String && ("Therapy" in it || "team@example.com" in it) })
    }
}
