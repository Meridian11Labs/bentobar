package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.undone
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The Bar page's one Undo: the last change it made to the layout (a move, a drop, Visibility, Area, Duplicate, Remove,
 * the library's + and Forget), taken back once. Every such command goes through [change], so Ctrl+Z, the Undo in a
 * removed row's place and the library's are the same step, [undone]: order, part, rule and area of every item as they
 * were, an option changed since kept.
 */
object Undo {
    /**
     * A change to take back: the layout [before] it and [after] it, the item it took out of the bar, if it did, and the
     * item the library [added] with it.
     */
    data class Kept(val before: BarConfig, val after: BarConfig, val removed: String? = null, val added: String? = null)

    val last = MutableStateFlow<Kept?>(null)

    /** Makes [step] the layout's next change and keeps it to be undone. False if it changed nothing: then nothing is kept. */
    fun change(removed: String? = null, added: String? = null, step: (BarConfig) -> BarConfig): Boolean {
        val before = Store.config.value
        Store.update(step)
        return note(before, removed, added)
    }

    /** Keeps what the Store did since [before] as the change to undo (for a command that is the Store's own, like Duplicate). */
    fun note(before: BarConfig, removed: String? = null, added: String? = null): Boolean {
        val after = Store.config.value
        if (after == before) return false
        last.value = Kept(before, after, removed, added)
        return true
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
        if (kept == null || !offered(kept, now)) return null
        return kept.before.items.firstOrNull { was -> was.section == Section.OFF && kept.after.items.none { it.id == was.id } }
    }

    /**
     * Takes the last change back, once. False when there is none, or when the layout was arranged otherwise since (in
     * the bar's own menu, on Look): then it is dropped and nothing changes.
     */
    fun undo(): Boolean {
        val kept = last.value ?: return false
        last.value = null
        if (!offered(kept, Store.config.value)) return false
        Store.update { it.undone(kept.before, kept.after) ?: it }
        return true
    }
}
