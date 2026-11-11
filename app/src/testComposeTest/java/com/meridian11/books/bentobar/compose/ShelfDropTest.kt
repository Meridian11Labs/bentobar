package com.meridian11.books.bentobar.compose

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Rect
import android.net.Uri
import android.view.DragEvent
import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.bar.Strip
import com.meridian11.books.bentobar.bar.StripEntry
import com.meridian11.books.bentobar.bar.StripEvents
import com.meridian11.books.bentobar.bar.StripLook
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Pill
import com.meridian11.books.bentobar.data.TextSize
import com.meridian11.books.bentobar.items.Background
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.ItemState
import com.meridian11.books.bentobar.items.ShelfItem
import com.meridian11.books.bentobar.util.Sym
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Text dragged onto a Shelf item in the strip, as Android delivers a drag to the strip's window: it reaches the Shelf
 * (behind the guard), and only there. A file is refused, a drag of something that is not text is not taken up at all,
 * and another item takes no drop.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class ShelfDropTest {
    @get:Rule val rule = createComposeRule()

    private object Quiet : StripEvents {
        override fun click(item: ItemConfig, at: Rect) {}
        override fun context(item: ItemConfig, at: Rect) {}
        override fun scroll(item: ItemConfig, steps: Int) {}
        override fun chevron(at: Rect) {}
        override fun chevronContext(at: Rect) {}
        override fun hover(inside: Boolean) {}
        override fun placed(id: String, at: Rect) {}
    }

    private lateinit var view: View
    private val text = ClipDescription("dragged", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN))

    @Before fun strip() {
        Env.init(ApplicationProvider.getApplicationContext<Application>())
        val look = StripLook(Color.White, lightText = true, TextSize.DEFAULT, 8.dp, Pill.NONE)
        val items = listOf(
            StripEntry(ItemConfig("s", ShelfItem.type), ItemState(icon = Sym.INBOX, desc = "the shelf")),
            StripEntry(ItemConfig("c", "cpu"), ItemState(icon = Sym.MEMORY, text = "12%", desc = "the cpu")),
        )
        rule.setContent {
            view = LocalView.current
            Strip(items, emptyList(), showChevron = false, chevronAlways = false, chevronReservePx = 30, expanded = false, chevronOnLeft = true,
                look = look, maxWidthPx = 2000, heightDp = 36.dp, events = Quiet)
        }
        rule.waitForIdle()
    }

    @After fun gone() {
        ShelfItem.forgetOwn("s")
        settled()
    }

    private fun settled() {
        val done = CountDownLatch(1)
        Background.executor(ShelfItem.type).execute { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
    }

    /** The middle of the item that says [desc], in the strip's window, in px. */
    private fun middleOf(desc: String): Pair<Float, Float> {
        val b = rule.onNodeWithContentDescription(desc).getBoundsInRoot()
        return with(rule.density) { ((b.left + b.right) / 2).toPx() to ((b.top + b.bottom) / 2).toPx() }
    }

    /** One step of a drag as Android hands it to a window ([DragEvent.obtain] is the platform's own, hidden from apps). */
    private fun send(action: Int, at: Pair<Float, Float>, description: ClipDescription, clip: ClipData? = null): Boolean {
        val obtain = DragEvent::class.java.methods.single { it.name == "obtain" && it.parameterCount == 13 }
        val event = obtain.invoke(null, action, at.first, at.second, 0f, 0f, 0, 0, null, description, clip, null, null, false) as DragEvent
        var taken = false
        rule.runOnUiThread { taken = view.dispatchDragEvent(event) }
        rule.waitForIdle()
        return taken
    }

    /** A whole drag from another app, let go at [at]: whether the drop was taken. */
    private fun dropAt(at: Pair<Float, Float>, description: ClipDescription, clip: ClipData): Boolean {
        assertTrue("a drag of text is taken up by the strip", send(DragEvent.ACTION_DRAG_STARTED, 0f to 0f, description))
        send(DragEvent.ACTION_DRAG_ENTERED, at, description)
        send(DragEvent.ACTION_DRAG_LOCATION, at, description)
        val taken = send(DragEvent.ACTION_DROP, at, description, clip)
        send(DragEvent.ACTION_DRAG_ENDED, at, description)
        settled()
        return taken
    }

    @Test fun textDroppedOnTheShelfIsOnTheShelf() {
        val clip = ClipData(text, ClipData.Item("A note from another app"))
        assertTrue(dropAt(middleOf("the shelf"), text, clip))
        assertEquals(listOf("A note from another app"), ShelfItem.held("s")?.map { it.text })
    }

    @Test fun anotherItemTakesNoDrop() {
        val clip = ClipData(text, ClipData.Item("A note from another app"))
        assertFalse(dropAt(middleOf("the cpu"), text, clip))
        assertNull("nothing reached the shelf", ShelfItem.held("s"))
    }

    @Test fun aFileIsRefused() {
        val clip = ClipData(text, ClipData.Item(Uri.parse("content://media/external/file/1")))
        assertFalse(dropAt(middleOf("the shelf"), text, clip))
        assertNull(ShelfItem.held("s"))
    }

    @Test fun aDragOfSomethingElseIsNotTakenUp() {
        val image = ClipDescription("dragged", arrayOf("image/png"))
        assertFalse(send(DragEvent.ACTION_DRAG_STARTED, 0f to 0f, image))
        send(DragEvent.ACTION_DRAG_ENDED, 0f to 0f, image)
    }
}
