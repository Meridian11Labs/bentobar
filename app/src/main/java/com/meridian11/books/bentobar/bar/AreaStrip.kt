package com.meridian11.books.bentobar.bar

import android.graphics.Rect
import androidx.compose.runtime.Composable
import com.meridian11.books.bentobar.data.Position

/**
 * One area's strip: its window and what belongs to that window alone. The controller reaches a strip's window through
 * this, and the popups and the tooltip are told which strip they hang from.
 */
internal class AreaStrip(val area: Position, val window: Overlay) {
    /** Where each item was last drawn (window coordinates): a popup opens under its item, and a press is told to the item it is on. */
    val placed = mutableMapOf<String, Rect>()
    val shown get() = window.shown
    val params get() = window.params

    fun show(content: @Composable () -> Unit) = window.show(content)
    fun hide() { window.hide(); placed.clear() }
    fun setContentVisible(visible: Boolean) = window.setContentVisible(visible)
    fun relayout() = window.relayout()
    fun locationOnScreen() = window.locationOnScreen()
    fun watchOutside(on: Boolean) = window.watchOutside(on)
}
