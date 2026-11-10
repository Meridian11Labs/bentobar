package com.meridian11.books.bentobar.ui

import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.data.undone
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The Bar page's one Undo: the last change it made to the layout (a move, a drop, Visibility, Area, Duplicate, Remove),
 * taken back once. Every such command goes through [change], so Ctrl+Z and the Undo in a removed row's place are the
 * same step, [undone]: order, part, rule and area of every item as they were, an option changed since kept.
 */
object Undo {
    /** A change to take back: the layout [before] it and [after] it, and the item it took out of the bar, if it did. */
    data class Kept(val before: BarConfig, val after: BarConfig, val removed: String? = null)

    val last = MutableStateFlow<Kept?>(null)

    /** Makes [step] the layout's next change and keeps it to be undone. False if it changed nothing: then nothing is kept. */
    fun change(removed: String? = null, step: (BarConfig) -> BarConfig): Boolean {
        val before = Store.config.value
        Store.update(step)
        return note(before, removed)
    }

    /** Keeps what the Store did since [before] as the change to undo (for a command that is the Store's own, like Duplicate). */
    fun note(before: BarConfig, removed: String? = null): Boolean {
        val after = Store.config.value
        if (after == before) return false
        last.value = Kept(before, after, removed)
        return true
    }

    /** Whether [kept] can still be taken back from [now]: the layout is arranged as that change left it. */
    fun offered(kept: Kept, now: BarConfig): Boolean = now.undone(kept.before, kept.after) != null

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
