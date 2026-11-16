package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.data.MeetingChoices
import com.meridian11.books.bentobar.data.MeetingChoices.OnlyMe
import com.meridian11.books.bentobar.items.Meetings.Cal
import com.meridian11.books.bentobar.items.Meetings.Guest
import com.meridian11.books.bentobar.items.Meetings.Row
import com.meridian11.books.bentobar.items.Meetings.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an event is ([Meetings.judge]): one test per verdict, in their order, and one per case of whose event it is on a
 * shared calendar. Made-up calendars and addresses at example.com.
 */
class MeetingsJudgeTest {
    private val you = "you@example.com"
    private val home = "you.home@example.com"
    private val work = Cal(1, you, "com.google", you, "Work", 0, 700, visible = true, flagPrimary = true)
    private val second = Cal(2, home, "com.google", home, "Home", 0, 700, visible = true, flagPrimary = true)
    private val team = Cal(3, you, "com.google", "team@group.calendar.google.com", "Team", 0, 600, visible = true, flagPrimary = false)
    private val colleague = Cal(4, you, "com.google", "ana@example.com", "Ana", 0, 600, visible = true, flagPrimary = false)
    private val holidays = Cal(5, you, "com.google", "en.usa#holiday@group.v.calendar.google.com", "Holidays", 0, 200, visible = true, flagPrimary = false)
    private val hidden = Cal(6, you, "com.google", you, "Hidden", 0, 700, visible = false, flagPrimary = false)
    /** A calendar of no account, kept on the device: the user's own, though its "account" is nobody. */
    private val local = Cal(7, "Device", Meetings.ACCOUNT_TYPE_LOCAL, "Device", "On this device", 0, 700, visible = true, flagPrimary = false)
    private val cals = listOf(work, second, team, colleague, holidays, hidden, local)
    private val mine = Meetings.mine(cals)
    private val link = "https://meet.example.com/x"
    private val ana = Guest("ana@example.com")
    private val ben = Guest("ben@example.com")
    private val me = Guest(you)
    private val defaults = MeetingChoices.State()

    private fun row(cal: Cal = work, title: String = "Sync", organizer: String = you, link: String? = null, allDay: Boolean = false,
                    status: Int = 1, self: Int = 0, availability: Int = 0, linksTask: Boolean = false) =
        Row(1, cal.id, title, 0, 1, allDay = allDay, status = status, selfStatus = self, availability = availability,
            organizer = organizer, link = link, linksTask = linksTask)

    private fun judge(r: Row, guests: List<Guest> = emptyList(), choices: MeetingChoices.State = defaults, cal: Cal? = cals.first { it.id == r.calendarId }) =
        Meetings.judge(r, cal, guests, mine, choices)

    // ---- who the user is

    @Test fun everyAccountIsTheUserButADeviceCalendar() {
        assertEquals(setOf(you, home), mine)
    }

    @Test fun anOwnCalendarIsThePrimaryOneOrOneOwnedByItsAccount() {
        assertTrue(work.own)
        assertTrue(Cal(8, you, "com.google", "YOU@example.com", "Mine", 0, 700, visible = true, flagPrimary = false).own)
        assertFalse(team.own)
        assertFalse(colleague.own)
        assertTrue("a calendar on the device is the user's", local.own)
    }

    @Test fun aRoomOrACalendarIsNoPerson() {
        assertTrue(Meetings.isPerson(1, "ana@example.com"))
        assertFalse("a room by its type", Meetings.isPerson(Meetings.TYPE_RESOURCE, "room-4@example.com"))
        assertFalse("a room whose type was not synced", Meetings.isPerson(1, "c_4f2a1b@resource.calendar.google.com"))
        assertFalse("a calendar as a guest", Meetings.isPerson(1, "team@group.calendar.google.com"))
        assertFalse("no address", Meetings.isPerson(1, " "))
    }

    // ---- one test per verdict, in order

    @Test fun aHiddenOrUnknownCalendarShowsNothing() {
        assertEquals(Verdict.HIDDEN_CALENDAR, judge(row(hidden), listOf(me, ana)))
        assertEquals(Verdict.HIDDEN_CALENDAR, judge(row(work), listOf(me, ana), cal = null))
        assertFalse(Verdict.HIDDEN_CALENDAR.event)
    }

    @Test fun aCanceledEventShowsNowhereAndComesBeforeDeclined() {
        assertEquals(Verdict.CANCELED, judge(row(status = Meetings.STATUS_CANCELED, self = Meetings.SELF_DECLINED), listOf(me, ana)))
        assertFalse(Verdict.CANCELED.event)
    }

    @Test fun aDeclinedEventShowsNowhere() {
        assertEquals(Verdict.DECLINED, judge(row(self = Meetings.SELF_DECLINED), listOf(me, ana)))
        assertFalse(Verdict.DECLINED.event)
    }

    @Test fun theTaskRuleIsAsItWas() {
        assertEquals("a task synced in from a task app", Verdict.TASK, judge(row(linksTask = true)))
        assertEquals("a real meeting whose notes link a task", Verdict.ONLY_ME_CALL, judge(row(linksTask = true, link = link)))
        assertEquals(Verdict.WITH_OTHERS, judge(row(linksTask = true), listOf(me, ana)))
        assertEquals("all-day with others is no task", Verdict.ALL_DAY, judge(row(linksTask = true, allDay = true), listOf(me, ana)))
        assertFalse(Verdict.TASK.event)
    }

    @Test fun allDayIsShownAndNoMeeting() {
        val v = judge(row(allDay = true, link = link), listOf(me, ana))
        assertEquals(Verdict.ALL_DAY, v)
        assertTrue(v.event)
        assertFalse(v.meeting)
    }

    @Test fun aCalendarYouCannotChangeIsShownAndHasNoMeetings() {
        assertEquals(Verdict.READ_ONLY, judge(row(holidays, link = link), listOf(me, ana)))
    }

    @Test fun anUntickedCalendarHasNoMeetings() {
        val off = defaults.copy(calendarsOff = setOf(work.ident))
        assertEquals(Verdict.CALENDAR_OFF, judge(row(link = link), listOf(me, ana), off))
        assertEquals("another calendar still counts", Verdict.WITH_OTHERS, judge(row(second, organizer = home), listOf(Guest(home), ana), off))
        assertTrue(Verdict.CALENDAR_OFF.event)
    }

    @Test fun someoneElsesMeetingIsNotYours() {
        assertEquals(Verdict.NOT_MINE, judge(row(team, organizer = "ana@example.com", link = link), listOf(ana, ben)))
        assertTrue("on a calendar the user chose to see: in the month view", Verdict.NOT_MINE.event)
    }

    @Test fun aWordInTheTitleLeavesItOut() {
        val words = defaults.copy(words = listOf("Hold"))
        assertEquals(Verdict.WORD, judge(row(title = "HOLD: desk"), listOf(me, ana), words))
        assertEquals(Verdict.WITH_OTHERS, judge(row(title = "Shareholders call"), listOf(me, ana), words))
    }

    @Test fun anEventMarkedFreeFollowsItsSwitch() {
        val free = row(availability = Meetings.AVAILABILITY_FREE)
        assertEquals("off by default", Verdict.FREE, judge(free, listOf(me, ana)))
        assertEquals(Verdict.WITH_OTHERS, judge(free, listOf(me, ana), defaults.copy(free = true)))
    }

    @Test fun anInvitationNotAnsweredFollowsItsSwitch() {
        val invited = row(organizer = "ana@example.com", self = Meetings.SELF_INVITED)
        assertEquals("on by default", Verdict.WITH_OTHERS, judge(invited, listOf(me, ana)))
        assertEquals(Verdict.UNANSWERED, judge(invited, listOf(me, ana), defaults.copy(unanswered = false)))
    }

    @Test fun maybeAcceptedAndNoAnswerAskedAllCount() {
        for (self in listOf(0, 1, Meetings.SELF_TENTATIVE)) assertEquals("status $self", Verdict.WITH_OTHERS, judge(row(self = self), listOf(me, ana)))
    }

    @Test fun withOthersIsAMeeting() {
        assertTrue(judge(row(), listOf(me, ana)).meeting)
    }

    @Test fun anEventWithOnlyYouFollowsTheChoice() {
        val call = row(link = link)
        val plain = row()
        assertEquals(Verdict.ONLY_ME_CALL, judge(call))
        assertEquals(Verdict.ONLY_ME, judge(plain))
        assertEquals(Verdict.ONLY_ME, judge(call, choices = defaults.copy(onlyMe = OnlyMe.NEVER)))
        assertEquals(Verdict.ONLY_ME_ANY, judge(plain, choices = defaults.copy(onlyMe = OnlyMe.ALWAYS)))
        assertTrue(Verdict.ONLY_ME_CALL.meeting)
        assertTrue(Verdict.ONLY_ME_ANY.meeting)
        assertFalse(Verdict.ONLY_ME.meeting)
        assertTrue(Verdict.ONLY_ME.event)
    }

    // ---- whose event it is on a shared calendar (the plan's table, row by row)

    @Test fun aColleaguesMeetingOnATeamCalendarIsNotMine() {
        assertEquals(Verdict.NOT_MINE, judge(row(team, organizer = "ana@example.com"), listOf(ana, ben)))
    }

    @Test fun theSameWithTheUserAsAGuestIsAMeetingWithOthers() {
        assertEquals(Verdict.WITH_OTHERS, judge(row(team, organizer = "ana@example.com"), listOf(ana, me, ben)))
    }

    @Test fun aColleaguesSoloBlockOnTheirOwnCalendarIsNotMineWithOrWithoutALink() {
        assertEquals(Verdict.NOT_MINE, judge(row(colleague, organizer = "ana@example.com")))
        assertEquals(Verdict.NOT_MINE, judge(row(colleague, organizer = "ana@example.com", link = link)))
        assertEquals("the colleague among the guests too", Verdict.NOT_MINE, judge(row(colleague, organizer = "ana@example.com"), listOf(ana)))
    }

    @Test fun anEventOfATeamCalendarWithNoGuestsIsOnlyYou() {
        val r = row(team, organizer = "team@group.calendar.google.com", link = link)
        assertEquals(Verdict.ONLY_ME_CALL, judge(r))
        assertEquals("and the Calendars list can leave it out", Verdict.CALENDAR_OFF, judge(r, choices = defaults.copy(calendarsOff = setOf(team.ident))))
    }

    @Test fun theUserAndARoomIsOnlyYou() {
        assertEquals(Verdict.ONLY_ME, judge(row(), listOf(me, Guest("room-4@example.com", Meetings.TYPE_RESOURCE))))
        assertEquals(Verdict.ONLY_ME, judge(row(), listOf(me, Guest("c_4f2a1b@resource.calendar.google.com", 1))))
    }

    @Test fun theUserAndTheirSecondAccountIsOnlyYou() {
        assertEquals(Verdict.ONLY_ME, judge(row(), listOf(me, Guest(home))))
    }

    @Test fun anInvitationUnderAnAliasOnAnOwnCalendarIsWithOthers() {
        assertEquals(Verdict.WITH_OTHERS, judge(row(organizer = "ana@example.com"), listOf(ana, Guest("alias@example.com"))))
    }

    // ---- the rest

    @Test fun aCalendarInvitedAsAGuestIsNoOneElse() {
        assertEquals(Verdict.ONLY_ME, judge(row(), listOf(me, Guest("team@group.calendar.google.com"))))
    }

    @Test fun anOrganizerAndNoAttendeeRowsOnAnOwnCalendarIsWithOthers() {
        // A webinar added from an invitation file: an organizer and nobody listed.
        assertEquals(Verdict.WITH_OTHERS, judge(row(organizer = "host@example.com")))
    }

    @Test fun twoAccountsAreBothTheUser() {
        // Invited under the second account, on the first account's team calendar.
        assertEquals(Verdict.WITH_OTHERS, judge(row(team, organizer = "ana@example.com"), listOf(ana, Guest(home))))
        assertEquals("organized by the second account", Verdict.WITH_OTHERS, judge(row(team, organizer = home), listOf(ana)))
    }

    @Test fun aFailedAttendeeReadMeansOnlyYou() {
        // Nobody known to be invited: as before there were rules, a call link alone counts.
        assertEquals(Verdict.ONLY_ME_CALL, judge(row(link = link), emptyList()))
        assertEquals(Verdict.ONLY_ME, judge(row(), emptyList()))
    }

    @Test fun notMineHoldsUnderEveryChoice() {
        val r = row(team, organizer = "ana@example.com", link = link, availability = Meetings.AVAILABILITY_FREE, self = Meetings.SELF_INVITED)
        for (onlyMe in OnlyMe.entries) for (unanswered in listOf(true, false)) for (free in listOf(true, false))
            for (words in listOf(emptyList(), listOf("Sync"))) {
                val c = MeetingChoices.State(onlyMe, unanswered, free, words)
                assertEquals("$onlyMe $unanswered $free $words", Verdict.NOT_MINE, judge(r, listOf(ana, ben), c))
            }
    }

    @Test fun aDeviceCalendarIsTheUsersOwnAndItsNameIsNoAccount() {
        assertFalse("device" in mine)
        assertEquals(Verdict.ONLY_ME, judge(row(local, organizer = "")))
        assertEquals(Verdict.WITH_OTHERS, judge(row(local, organizer = ""), listOf(ana)))
    }
}
