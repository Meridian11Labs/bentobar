package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Position
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.undone
import com.meridian11.books.bentobar.items.Items
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The settings window's one Undo: the last change it made to the layout (on the Bar page a move, a drop, Visibility,
 * Area, Duplicate, Remove, the library's + and Forget; on Look, "Where BentoBar sits"), taken back once. Every such
 * command goes through [change], so Ctrl+Z, the Undo in a removed row's place, the library's and the one under Look's
 * control are the same step, [undone]: order, part, rule and area of every item as they were, an option changed since
 * kept.
 */
object Undo {
    /**
     * A change to take back: the layout [before] it and [after] it, the item it took out of the bar, if it did, the
     * item the library [added] with it, the setup the library's Forget [forgot] with it, and the area Look's command
     * put every item in ([allTo]). Each is what the command says it did, never worked out from the two layouts: a change
     * also drops what carries no setup from Off, and one item moved on the Bar page can leave them all in one area too.
     */
    data class Kept(val before: BarConfig, val after: BarConfig, val removed: String? = null, val added: String? = null, val forgot: String? = null,
        val allTo: Position? = null)

    val last = MutableStateFlow<Kept?>(null)

    /** Makes [step] the layout's next change and keeps it to be undone. False if it changed nothing: then nothing is kept. */
    fun change(removed: String? = null, added: String? = null, forgot: String? = null, allTo: Position? = null, step: (BarConfig) -> BarConfig): Boolean {
        val before = Store.config.value
        Store.update(step)
        return note(before, removed, added, forgot, allTo)
    }

    /** Keeps what the Store did since [before] as the change to undo (for a command that is the Store's own, like Duplicate). */
    fun note(before: BarConfig, removed: String? = null, added: String? = null, forgot: String? = null, allTo: Position? = null): Boolean {
        val after = Store.config.value
        if (after == before) return false
        val old = last.value
        last.value = Kept(before, after, removed, added, forgot, allTo)
        letGo(old)
        return true
    }

    /**
     * The ids of the items of [type] that the kept change can still bring back: what is kept for one outside the layout
     * stays that long. None once the change can no longer be [offered] (the layout was arranged otherwise since): a
     * forgotten line's kept text then goes at the type's next sweep, not at the next start.
     */
    fun held(type: String): Set<String> =
        last.value?.takeIf { offered(it, Store.config.value) }?.before?.items.orEmpty().filter { it.type == type }.mapTo(HashSet()) { it.id }

    /**
     * [old] is the change to undo no more. An item it could have brought back, that neither the layout has nor the
     * change kept now, is gone for good: its type is told ([ItemType.forgetOwn][com.meridian11.books.bentobar.items.ItemType.forgetOwn]).
     */
    private fun letGo(old: Kept?) {
        old ?: return
        val still = Store.config.value.items.mapTo(HashSet()) { it.id }
        last.value?.before?.items?.forEach { still += it.id }
        old.before.items.forEach { if (it.id !in still) Items.of(it.type)?.forgetOwn(it.id) }
    }

    /** Whether [kept] can still be taken back from [now]: the layout is arranged as that change left it. */
    fun offered(kept: Kept, now: BarConfig): Boolean = now.undone(kept.before, kept.after) != null

    /** The item the library's last + put in the bar, while that can still be taken back. */
    fun added(kept: Kept?, now: BarConfig): ItemConfig? {
        val id = kept?.added ?: return null
        return now.items.firstOrNull { it.id == id }?.takeIf { offered(kept, now) }
    }

    /** The setup the last change forgot (the library's Forget), while it can still come back. */
    fun forgotten(kept: Kept?, now: BarConfig): ItemConfig? {
        val id = kept?.forgot ?: return null
        return kept.before.items.firstOrNull { it.id == id }?.takeIf { offered(kept, now) }
    }

    /** The area Look's command put every item in, while that is the last change and can still be taken back from [now]. */
    fun allTo(kept: Kept?, now: BarConfig): Position? = kept?.allTo?.takeIf { offered(kept, now) }

    /**
     * Takes the last change back, once. False when there is none, or when the layout was arranged otherwise since (in
     * the bar's own menu, by a pasted layout): then it is dropped and nothing changes.
     */
    fun undo(): Boolean {
        val kept = last.value ?: return false
        val back = offered(kept, Store.config.value)
        // The layout first: until the item is back in it, what is kept for it is held by this change being the last.
        if (back) Store.update { it.undone(kept.before, kept.after) ?: it }
        last.value = null
        letGo(kept)
        return back
    }
}
