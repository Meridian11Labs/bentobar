package com.meridian11.books.bentobar.ui

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.delay

/**
 * How long a note with Undo stands: the slip in a removed row's place, the one for a closed or a removed area, the
 * library's "Added", Look's line. One rule for all of them. A note goes [Motion.NOTE] after it appeared. The time waits
 * while the pointer is over the note or the keyboard is in it, and starts again, whole, when they have left: nobody
 * reaches for an Undo that goes under the hand. A new change replaces the note, as ever. When the time is up only the
 * note goes ([Undo.noteOver]): Ctrl+Z takes the change back until the next one.
 */
internal object NoteClock {
    /** What a note still waits, in milliseconds, counted from now: all of [time] again, or nothing to wait for (null) while it is [held]. */
    fun wait(held: Boolean, time: Long): Long? = if (held) null else time
}

/** What holds a note: the pointer over one of its parts ([holdsNote]: its words, its Undo), or the keyboard in one. */
@Stable
internal class NoteHold {
    internal var parts by mutableIntStateOf(0)
    val held: Boolean get() = parts > 0
}

/**
 * Runs the time of the last change's note while the note is drawn: called by the note itself, so one that is not on
 * screen (the list behind the panel in a narrow window, another tab) has its time when it is. The time is the one a
 * person's accessibility settings ask for a text with a control ("Time to take action").
 */
@Composable
internal fun NoteTime(hold: NoteHold) {
    val kept = Undo.last.collectAsState().value ?: return
    val manager = LocalAccessibilityManager.current
    val time = manager?.calculateRecommendedTimeoutMillis(Motion.NOTE.toLong(), containsText = true, containsControls = true) ?: Motion.NOTE.toLong()
    val held = hold.held
    LaunchedEffect(kept, held, time) {
        delay(NoteClock.wait(held, time) ?: return@LaunchedEffect)
        Undo.noteOver(kept)
    }
}

/** A part of a note: the pointer over it, or the keyboard in it, holds the note ([NoteHold]). */
internal fun Modifier.holdsNote(hold: NoteHold): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val on = hovered || focused
    DisposableEffect(hold, on) {
        if (on) hold.parts++
        onDispose { if (on) hold.parts-- }
    }
    hoverable(source).onFocusChanged { focused = it.hasFocus }
}
