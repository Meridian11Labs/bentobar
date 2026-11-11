package com.meridian11.books.bentobar.compose

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.bar.ItemMenu
import com.meridian11.books.bentobar.bar.MenuSurface
import com.meridian11.books.bentobar.data.BarConfig
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.items.ClipboardActions
import com.meridian11.books.bentobar.items.ClipboardItem
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.MenuHost
import com.meridian11.books.bentobar.items.OnDeviceModel
import com.meridian11.books.bentobar.items.OnDeviceModel.Answer
import com.meridian11.books.bentobar.items.OnDeviceModel.Ready
import com.meridian11.books.bentobar.ui.SettingsTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Clipboard's actions on a fake on-device model: a copied paragraph's row offers them, an action's answer shows under
 * the row as it arrives and Copy puts it on the clipboard, and what the model cannot do is said in words, with BentoBar's
 * window where the model answers only the app in front. Nothing is asked before a press, nothing is fetched before
 * "Get the model", and the answer goes with the popup.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class ClipboardActionsTest {
    @get:Rule val rule = createComposeRule()

    private class Host : MenuHost {
        var closed = 0
        val opened = mutableListOf<String>()
        override fun close() { closed++ }
        override fun openItemSettings(id: String) { opened += id }
        override fun afterClose(action: () -> Unit) {}
    }

    /** A model the test speaks for: its readiness, and each answer handed out piece by piece until the test ends it. */
    private class FakeModel(val status: Ready) : OnDeviceModel {
        val pieces = Channel<Answer>(Channel.UNLIMITED)
        val asked = mutableListOf<String>()
        var downloads = 0
        var stopped = 0
        override suspend fun ready() = status
        override fun download(): Flow<Ready> = flow { downloads++; emit(Ready.DOWNLOADING) }
        override fun ask(prompt: String): Flow<Answer> = flow {
            asked += prompt
            try { for (p in pieces) emit(p) } finally { stopped++ }
        }
    }

    private val paragraph = "The meeting moves to Thursday at ten. Bring the figures for the second quarter, the notes from the last " +
        "review and any questions you have about the new plan. We will keep it short, and we will end by eleven at the latest."
    private val item = ItemConfig("c", ClipboardItem.type)
    private val host = Host()
    /** What the test shows: the popup in the bar, the item's options in BentoBar's window, or nothing. */
    private enum class Where { POPUP, OPTIONS, NOTHING }
    private var where by mutableStateOf(Where.POPUP)
    private fun str(id: Int, vararg args: Any) = Env.str(id, *args)
    private fun app() = ApplicationProvider.getApplicationContext<Application>()

    private fun popup(model: OnDeviceModel?) {
        Env.init(app())
        ClipboardActions.model = model
        Store.update { BarConfig(items = listOf(item)) }
        // Copied in another app: the popup reads it as it opens, as it does on the device.
        app().getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("copied", paragraph))
        rule.setContent {
            when (where) {
                Where.POPUP -> MenuSurface(ClipboardItem.menuWidthDp.dp, 2000.dp, null) { ItemMenu(item.id, host) }
                Where.OPTIONS -> SettingsTheme(dark = false) { Column { ClipboardItem.options!!(item) {} } }
                Where.NOTHING -> {}
            }
        }
        rule.waitForIdle()
    }

    private fun send(model: FakeModel, answer: Answer) {
        model.pieces.trySend(answer)
        rule.waitForIdle()
    }

    private fun openActions() {
        rule.onNodeWithContentDescription(str(R.string.clipboard_ai_actions)).performClick()
        rule.waitForIdle()
    }

    @Before fun fresh() {
        ClipboardItem.debug(listOf("off"))
        ClipboardActions.takeHandover()
    }

    @After fun gone() {
        ClipboardActions.model = null
        ClipboardActions.takeHandover()
        ClipboardItem.clearRecent()
        Store.update { BarConfig() }
    }

    @Test fun anAnswerArrivesInPiecesAndCanBeCopied() {
        val model = FakeModel(Ready.READY)
        popup(model)
        openActions()
        assertEquals("nothing is asked before an action is pressed", emptyList<String>(), model.asked)
        rule.onNodeWithText(str(R.string.clipboard_ai_proofread)).performClick()
        rule.waitForIdle()
        val prompt = model.asked.single()
        assertTrue(prompt, prompt.startsWith("Correct") && paragraph in prompt)
        rule.onNodeWithText(str(R.string.clipboard_ai_working)).assertExists()
        // The model's announcement is not shown; the answer is, piece by piece, and Copy only once it is whole.
        send(model, Answer.Piece("Here is the corrected text:\n"))
        rule.onAllNodesWithText("Here is the corrected text:", substring = true).assertCountEquals(0)
        send(model, Answer.Piece("The meeting moves"))
        rule.onNodeWithText("The meeting moves").assertExists()
        rule.onAllNodesWithText(str(R.string.clipboard_ai_copy)).assertCountEquals(0)
        send(model, Answer.Piece(" to Thursday."))
        rule.onNodeWithText("The meeting moves to Thursday.").assertExists()
        model.pieces.close()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.clipboard_ai_note)).assertExists()
        rule.onNodeWithText(str(R.string.clipboard_ai_copy)).performClick()
        rule.waitForIdle()
        val clip = app().getSystemService(ClipboardManager::class.java).primaryClip
        assertEquals("The meeting moves to Thursday.", clip?.getItemAt(0)?.text?.toString())
        assertEquals("Copy closes the popup, as a row's click does", 1, host.closed)
    }

    @Test fun aModelThatIsNotThereSaysSo() {
        val model = FakeModel(Ready.NONE)
        popup(model)
        openActions()
        rule.onNodeWithText(str(R.string.clipboard_ai_none)).assertExists()
        rule.onAllNodesWithText(str(R.string.clipboard_ai_proofread)).assertCountEquals(0)
        assertEquals(emptyList<String>(), model.asked)
    }

    @Test fun notInFrontOffersBentoBarsWindow() {
        val model = FakeModel(Ready.READY)
        popup(model)
        openActions()
        rule.onNodeWithText(str(R.string.clipboard_ai_summarize)).performClick()
        rule.waitForIdle()
        // As the library's model does from a popup in the bar: it answers only the app in front.
        send(model, Answer.Stopped(Ready.NOT_IN_FRONT))
        model.pieces.close()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.clipboard_ai_not_in_front)).assertExists()
        rule.onNodeWithText(str(R.string.clipboard_ai_open)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(item.id), host.opened)
        // There, the item's options have the same section, and the action the user pressed goes on at once on the same text.
        where = Where.NOTHING
        rule.waitForIdle()
        val again = FakeModel(Ready.READY)
        ClipboardActions.model = again
        where = Where.OPTIONS
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.clipboard_ai_section)).assertExists()
        val prompt = again.asked.single()
        assertTrue(prompt, prompt.startsWith("Summarize") && paragraph in prompt)
        send(again, Answer.Piece("Thursday at ten, with the figures."))
        again.pieces.close()
        rule.waitForIdle()
        rule.onNodeWithText("Thursday at ten, with the figures.").assertExists()
        rule.onNodeWithText(str(R.string.clipboard_ai_copy)).performClick()
        rule.waitForIdle()
        assertEquals("Thursday at ten, with the figures.",
            app().getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test fun withoutAModelNoRowOffersActions() {
        popup(null)
        // The row is there (its start shows), and offers nothing.
        rule.onNodeWithText(paragraph.take(30), substring = true).assertExists()
        rule.onAllNodesWithContentDescription(str(R.string.clipboard_ai_actions)).assertCountEquals(0)
    }

    @Test fun theModelIsFetchedOnlyAtThePressOfGetTheModel() {
        val model = FakeModel(Ready.DOWNLOADABLE)
        popup(model)
        openActions()
        rule.onNodeWithText(str(R.string.clipboard_ai_downloadable)).assertExists()
        assertEquals(0, model.downloads)
        rule.onNodeWithText(str(R.string.clipboard_ai_get)).performClick()
        rule.waitForIdle()
        assertEquals(1, model.downloads)
        rule.onNodeWithText(str(R.string.clipboard_ai_downloading)).assertExists()
    }

    @Test fun theAnswerGoesWithThePopup() {
        val model = FakeModel(Ready.READY)
        popup(model)
        openActions()
        rule.onNodeWithText(str(R.string.clipboard_ai_proofread)).performClick()
        rule.waitForIdle()
        send(model, Answer.Piece("Half an ans"))
        rule.onNodeWithText("Half an ans").assertExists()
        // Closed while it writes: the question is called off, and nothing of the answer is there when the popup opens again.
        where = Where.NOTHING
        rule.waitForIdle()
        assertEquals(1, model.stopped)
        where = Where.POPUP
        rule.waitForIdle()
        rule.onAllNodesWithText("Half an ans", substring = true).assertCountEquals(0)
        assertFalse(ClipboardItem.debug(emptyList())!!.contains("Half"))
    }
}
