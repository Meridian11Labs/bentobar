package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.undone
import com.meridian11.books.bentobar.data.without
import com.meridian11.books.bentobar.items.Items
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The settings window's one Undo: the last change it made to the layout (on the Bar page a move, a drop, Visibility,
 * Area, Duplicate, Remove, the library's +; on Look, "Where BentoBar sits"), taken back once. Every such
 * command goes through [change], so Ctrl+Z, the Undo in a removed row's place, the library's and the one under Look's
 * control are the same step, [undone]: order, part, rule and area of every item as they were, an option changed since
 * kept. A removed item is deleted from the layout, and this is what has it meanwhile: it comes back as it was, and what
 * its type keeps for it outside the layout stays until the removal can be undone no more ([letGo]). What a page says
 * of the change, with an Undo to click, goes after a few seconds ([over]); the step itself stays until the next change
 * or until the settings window is closed ([end]), whichever is first.
 */
object Undo {
    /**
     * A change to take back: the layout [before] it and [after] it, the item it took out of the bar, if it did, the
     * item the library [added] with it, the area Look's command put every item in ([allTo]), and the [area] that
     * Remove an area took out with every item of it. Each is what the command says it did, never worked out from the
     * two layouts: one item moved on the Bar page can leave them all in one area too, and an area's one item removed
     * is not the area removed.
     */
    data class Kept(val before: BarConfig, val after: BarConfig, val removed: String? = null, val added: String? = null, val allTo: Position? = null,
                    val area: Position? = null)

    val last = MutableStateFlow<Kept?>(null)

    /**
     * The change whose note is over: what a page said of it (the slip in a removed row's place, a closed area's, the
     * library's "Added", Look's line, each with its Undo) stood its time ([NoteClock]) and went. Only the note: the
     * change is still [last], Ctrl+Z still takes it back and what a removed item's type keeps for it still stays.
     * It is the window's and not a page's, so a note that went is gone on every page and after the window was made
     * again, and it is that one change, never one equal to it ([noted]).
     */
    val over = MutableStateFlow<Kept?>(null)

    /** [kept]'s note has stood its time. Nothing if another change has been made since. */
    fun noteOver(kept: Kept) {
        if (last.value !== kept) return
        // A flow keeps the value it has when given an equal one, and an equal change from before is not this one.
        over.value = null
        over.value = kept
    }

    /** [kept] while a page still says it with its Undo: null once its note is [over]. */
    fun noted(kept: Kept?, over: Kept?): Kept? = kept?.takeIf { it !== over }

    /**
     * A type's sweep runs on its own thread and asks what Undo holds ([idsOf], [held]) while the main thread changes the
     * layout and what is kept: this makes the asking one reading of both, and Undo's offer and its hold one step. Held
     * only to set [pending] or [lapsed] and to read and compare what is in memory: never around the Store's write, a
     * flow's subscribers, a type's code or anything that waits, and no other lock is taken inside it, so it can be in no
     * deadlock. The Store's write is outside it: [pending], set before and cleared after under it, holds the items meanwhile.
     */
    private val lock = Any()

    /**
     * The layout a change that is being made started from, from before it is written until it is kept ([change]), and the
     * one an [undo] brings back while it writes it. A type's sweep runs on its own thread ([held]), and in between the
     * item is in no layout and in no kept change: without this its kept text could go while Undo was about to hold it, or
     * had just brought it back. Never read on the main thread's behalf: there the write and the keeping are one step.
     */
    @Volatile var pending: BarConfig? = null
        private set

    /**
     * The kept change that a reading ([held]) found it could hold no more, the layout being arranged otherwise: what was
     * kept for its items may have gone since, so it is [offered] no more, whatever the layout does next.
     */
    @Volatile private var lapsed: Kept? = null

    /** Makes [step] the layout's next change and keeps it to be undone. False if it changed nothing: then nothing is kept. */
    fun change(removed: String? = null, added: String? = null, allTo: Position? = null, area: Position? = null, step: (BarConfig) -> BarConfig): Boolean {
        val before = Store.config.value
        synchronized(lock) { pending = before }
        try {
            Store.update(step)
            return note(before, removed, added, allTo, area)
        } finally {
            synchronized(lock) { pending = null }
        }
    }

    /**
     * Keeps what the Store did since [before] as the change to undo (for a command that is the Store's own: Duplicate and
     * the library's +, which only add, so nothing of [before] is out of the layout in between and nothing is [pending]).
     */
    fun note(before: BarConfig, removed: String? = null, added: String? = null, allTo: Position? = null, area: Position? = null): Boolean {
        val after = Store.config.value
        if (after == before) return false
        val old = last.value
        last.value = Kept(before, after, removed, added, allTo, area)
        over.value = null
        letGo(old)
        return true
    }

    /**
     * The ids of the items of [type] that the kept change can still bring back: what is kept for one outside the layout
     * stays that long. None once the change can no longer be [offered] (the layout was arranged otherwise since): a
     * removed item's kept text then goes at the type's next sweep, not at the next start, and the change is [lapsed].
     * With them the items of a change that is being made or undone right now ([pending]). Any thread.
     */
    fun held(type: String): Set<String> = synchronized(lock) { holding(type, last.value, Store.config.value) }

    /** [type]'s items in the layout and those Undo [held], read as one: what a type's sweep keeps. Any thread. */
    fun idsOf(type: String): Set<String> = synchronized(lock) {
        // The kept change before the layout: a change is kept after its layout is written, so a reading never pairs a
        // change with the layout from before it, which would find it not offered.
        val kept = last.value
        val now = Store.config.value
        holding(type, kept, now).also { ids -> now.items.forEach { if (it.type == type) ids += it.id } }
    }

    /** Under [lock]: what Undo holds of [type], with [kept] the change kept and [now] the layout. */
    private fun holding(type: String, kept: Kept?, now: BarConfig): MutableSet<String> {
        val keeps = when {
            kept == null -> emptyList()
            offered(kept, now) -> kept.before.items
            else -> { lapsed = kept; emptyList() }
        }
        return (pending?.items.orEmpty() + keeps).filter { it.type == type }.mapTo(HashSet()) { it.id }
    }

    /**
     * [old] is the change to undo no more. An item it had, before or after (one it removed, and one it brought in that
     * its Undo took out again), that neither the layout has nor the change kept now can bring back, is gone for good: its
     * type is told ([ItemType.forgetOwn][com.meridian11.books.bentobar.items.ItemType.forgetOwn]), and what it kept for
     * the item outside the layout is deleted. Not before: until then Undo brings the item back with all of it.
     */
    private fun letGo(old: Kept?) {
        old ?: return
        val still = Store.config.value.items.mapTo(HashSet()) { it.id }
        last.value?.before?.items?.forEach { still += it.id }
        (old.before.items + old.after.items).distinctBy { it.id }.forEach { if (it.id !in still) Items.of(it.type)?.forgetOwn(it.id) }
    }

    /**
     * Remove where there is no Undo (the bar's own menu): the item leaves the layout, and since nothing can bring it
     * back, what its type kept for it goes at once. A change kept here is over with it (it is no longer [offered]), so
     * it is let go too, and with it what an item it removed had kept.
     */
    fun removeForGood(id: String) {
        val item = Store.config.value.items.firstOrNull { it.id == id } ?: return
        Store.update { it.without(id) }
        val now = Store.config.value
        if (now.items.any { it.id == id }) return
        val old = last.value
        if (old != null && !offered(old, now)) {
            last.value = null
            over.value = null
            letGo(old)
        }
        Items.of(item.type)?.forgetOwn(id)
    }

    /**
     * The settings window was closed for good: Undo is the window's, and its reach ends with it. The change kept is the
     * one to undo no more, as after a next change ([letGo]): what a type kept for an item it removed is deleted now and
     * not at some change days later, and a window opened afterwards takes nothing back that nobody there saw go. Not
     * for a window that is made again (a rotation, a new size) or only left. Main thread.
     */
    fun end() {
        val old = last.value ?: return
        last.value = null
        over.value = null
        letGo(old)
    }

    /**
     * Whether [kept] can still be taken back from [now]: the layout is arranged as that change left it, and Undo has not
     * let go of its items meanwhile ([lapsed]).
     */
    fun offered(kept: Kept, now: BarConfig): Boolean = kept !== lapsed && now.undone(kept.before, kept.after) != null

    /** The item the library's last + put in the bar, while that can still be taken back. */
    fun added(kept: Kept?, now: BarConfig): ItemConfig? {
        val id = kept?.added ?: return null
        return now.items.firstOrNull { it.id == id }?.takeIf { offered(kept, now) }
    }

    /** The area Look's command put every item in, while that is the last change and can still be taken back from [now]. */
    fun allTo(kept: Kept?, now: BarConfig): Position? = kept?.allTo?.takeIf { offered(kept, now) }

    /**
     * Takes the last change back, once. False when there is none, or when the layout was arranged otherwise since (in
     * the bar's own menu, by a pasted layout): then it is dropped and nothing changes.
     */
    fun undo(): Boolean {
        val kept = last.value ?: return false
        // Offered, and held while it is written, in one step: a sweep can't let its items go in between.
        val back = synchronized(lock) { offered(kept, Store.config.value).also { if (it) pending = kept.before } }
        try {
            if (back) Store.update { it.undone(kept.before, kept.after) ?: it }
            last.value = null
            over.value = null
        } finally {
            if (back) synchronized(lock) { pending = null }
        }
        letGo(kept)
        return back
    }
}
