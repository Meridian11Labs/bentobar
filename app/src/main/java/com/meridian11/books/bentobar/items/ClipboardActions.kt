package com.meridian11.books.bentobar.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.items.ClipboardActionsRules.Action
import com.meridian11.books.bentobar.items.OnDeviceModel.Answer
import com.meridian11.books.bentobar.items.OnDeviceModel.Ready
import com.meridian11.books.bentobar.ui.Bento
import com.meridian11.books.bentobar.ui.ChipRow
import com.meridian11.books.bentobar.ui.CopyEntry
import com.meridian11.books.bentobar.ui.MenuEntry
import com.meridian11.books.bentobar.ui.MenuNote
import com.meridian11.books.bentobar.ui.SmallIconButton
import com.meridian11.books.bentobar.util.Sym
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

/**
 * Clipboard actions: a copied or pinned text summarised, proofread, shortened or translated by the system's own model on
 * this device ([OnDeviceModel]), shown under its row as it arrives, with Copy. They offer and never paste: the answer
 * reaches the clipboard only by Copy. The text goes to the model only at a press of an action; the answer lives in the
 * popup (or the settings window) that shows it and goes with it. Nothing is kept, and nothing here writes a log line.
 */
internal object ClipboardActions {
    /**
     * The system's model, or null, and then no row offers an action. None is built in: the one library that reaches the
     * system's model brings a permission and a usage reporter of its own along, and BentoBar takes neither. Tests give a
     * fake; the debug hook stages one.
     */
    @Volatile var model: OnDeviceModel? = null

    /** What the debug hook staged: a model that answers as told, the text whose actions are open, the action asked. */
    private class Staged(val model: OnDeviceModel, val open: String, val action: Action?)
    @Volatile private var staged: Staged? = null

    /** The model the rows ask: a staged one over the system's. */
    fun current(): OnDeviceModel? = staged?.model ?: model

    fun stage(ready: Ready, open: String, action: Action?, answer: String?) {
        staged = Staged(StagedModel(ready, answer), open, action)
    }
    fun unstage() { staged = null }
    fun stagedOpen(): String? = staged?.open
    fun stagedAction(): Action? = staged?.action

    /** For the debug hook: which model there is, never what it was asked. */
    fun describe(): String = "model=" + when { staged != null -> "staged"; model != null -> "system"; else -> "none" }

    /**
     * The text and action the popup sent to BentoBar's window ("Open in BentoBar"), for the item's options there to open
     * and ask at once; taken once, in memory only. Prints no words.
     */
    class Handover(val text: String, val action: Action?) {
        override fun toString() = "Handover(${text.length} chars, $action)"
    }
    @Volatile private var handover: Handover? = null
    fun handOver(text: String, action: Action?) { handover = Handover(text, action) }
    fun peekHandover(): Handover? = handover
    fun takeHandover(): Handover? = handover.also { handover = null }

    /** A staged sample long enough for every action. */
    internal const val SAMPLE = "The meeting moves to Thursday at ten. Bring the figures for the second quarter, the notes from the last " +
        "review and any questions you have about the new plan. We will keep it short, and we will end by eleven at the latest."
    internal const val SAMPLE_ANSWER = "Thursday at ten, with the figures, the review notes and your questions; done by eleven."

    /** The button on a row that opens its actions. */
    @Composable
    fun ActionsButton(onClick: () -> Unit) = SmallIconButton(Sym.AUTO_AWESOME, stringResource(R.string.clipboard_ai_actions), onClick = onClick)

    /**
     * [text]'s actions, under its row: where the model stands while it cannot answer, the actions [text] is offered, and the
     * answer to the one pressed as it arrives, with [copyRow] once it is whole. [first] is asked at once (pressed in the popup,
     * which the model would not answer). [openWindow] is the popup's way to BentoBar's window, with the action pressed; null
     * in the window itself. Every call to the model is caught here too: Compose has no error boundary.
     */
    @Composable
    fun Panel(text: String, model: OnDeviceModel, first: Action?, openWindow: ((Action?) -> Unit)?, copyRow: @Composable (String) -> Unit) {
        val offered = remember(text) { ClipboardActionsRules.offered(text) }
        val language = remember { ClipboardActionsRules.language(Locale.getDefault()) }
        var ready by remember(model, text) { mutableStateOf(Ready.UNKNOWN) }
        var getting by remember(model) { mutableStateOf(false) }
        var asked by remember(model, text) { mutableStateOf(first) }
        var round by remember(model, text) { mutableIntStateOf(0) }
        var raw by remember(model, text) { mutableStateOf("") }
        // Null while it writes; then why it stopped, or null in it when it ended by itself.
        var end by remember(model, text) { mutableStateOf<End?>(null) }

        LaunchedEffect(model, text) { ready = guarded(Ready.FAILED) { model.ready() } }
        if (getting) LaunchedEffect(model) {
            guarded(Unit) { model.download().collect { ready = it } }
            getting = false
        }
        LaunchedEffect(model, text, asked, round) {
            val action = asked ?: return@LaunchedEffect
            raw = ""
            end = null
            var why: Ready? = null
            val failed = guarded(true) {
                model.ask(ClipboardActionsRules.prompt(action, text, language)).collect {
                    when (it) {
                        is Answer.Piece -> raw += it.text
                        is Answer.Stopped -> why = it.why
                    }
                }
                false
            }
            end = End(if (failed) Ready.FAILED else why)
        }

        Column(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp)) {
            val action = asked
            if (action == null && ready != Ready.READY) {
                Status(ready, onGet = { getting = true }, onOpen = openWindow?.let { open -> { open(null) } })
            } else {
                ChipRow(offered.map { label(it, language) }, selected = action?.let { offered.indexOf(it) }?.takeIf { it >= 0 }) {
                    asked = offered[it]
                    round++
                }
                if (action != null) {
                    if (ClipboardActionsRules.cut(text)) MenuNote(stringResource(R.string.clipboard_ai_cut))
                    Answered(ClipboardActionsRules.result(raw), end, onGet = { getting = true },
                        onOpen = openWindow?.let { open -> { open(action) } }, copyRow = copyRow)
                }
            }
        }
    }

    /** The answer as shown so far ([shown]) and, by [end], what follows it: "Writing…", Copy, or why it stopped. */
    @Composable
    private fun Answered(shown: String, end: End?, onGet: () -> Unit, onOpen: (() -> Unit)?, copyRow: @Composable (String) -> Unit) {
        if (shown.isNotEmpty()) {
            Text(shown, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 6.dp))
            MenuNote(stringResource(R.string.clipboard_ai_note))
        }
        val why = end?.why
        when {
            end == null -> if (shown.isEmpty()) MenuNote(stringResource(R.string.clipboard_ai_working))
            why != null -> Status(why, onGet, onOpen)
            shown.isEmpty() -> MenuNote(stringResource(R.string.clipboard_ai_empty))
            else -> copyRow(shown)
        }
    }

    /**
     * The section in the item's options, in BentoBar's window, where the model answers: each of [snippets] that is offered
     * an action, a click opening the same panel as in the popup. What the popup handed over opens at once, its action asked.
     */
    @Composable
    fun OptionsSection(snippets: List<String>) {
        val model = current() ?: return
        val handed = remember { peekHandover() }
        LaunchedEffect(Unit) { takeHandover() }
        val rows = remember(snippets, handed) {
            (listOfNotNull(handed?.text) + snippets).distinct().filter { ClipboardActionsRules.offered(it).isNotEmpty() }
        }
        var open by remember { mutableStateOf(handed?.text) }
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.clipboard_ai_section), style = Bento.type.strong, color = Bento.colors.ink)
            MenuNote(stringResource(R.string.clipboard_ai_section_note))
            if (rows.isEmpty()) MenuNote(stringResource(R.string.clipboard_ai_nothing))
            rows.forEach { snippet ->
                MenuEntry(Sym.AUTO_AWESOME, ClipboardRules.preview(snippet, ClipboardRules.MENU_CHARS)) {
                    open = if (open == snippet) null else snippet
                }
                if (open == snippet) {
                    Panel(snippet, model, first = handed?.action?.takeIf { handed.text == snippet }, openWindow = null) { shown ->
                        CopyEntry(stringResource(R.string.clipboard_ai_copy)) { shown }
                    }
                }
            }
        }
    }

    /** How a model's answer ended: [why] it stopped, or null when it ended by itself. */
    private class End(val why: Ready?)

    @Composable
    private fun label(action: Action, language: String): String = when (action) {
        Action.SUMMARISE -> stringResource(R.string.clipboard_ai_summarize)
        Action.PROOFREAD -> stringResource(R.string.clipboard_ai_proofread)
        Action.SHORTER -> stringResource(R.string.clipboard_ai_shorter)
        Action.TRANSLATE -> stringResource(R.string.clipboard_ai_translate, language)
    }

    /** Where the model stands, in words, with what can be done about it here. */
    @Composable
    private fun Status(ready: Ready, onGet: () -> Unit, onOpen: (() -> Unit)?) {
        when (ready) {
            Ready.UNKNOWN -> MenuNote(stringResource(R.string.clipboard_ai_checking))
            Ready.NONE -> MenuNote(stringResource(R.string.clipboard_ai_none))
            Ready.DOWNLOADABLE -> {
                MenuNote(stringResource(R.string.clipboard_ai_downloadable))
                MenuEntry(Sym.DOWNLOADING, stringResource(R.string.clipboard_ai_get), onClick = onGet)
            }
            Ready.DOWNLOADING -> MenuNote(stringResource(R.string.clipboard_ai_downloading))
            Ready.NOT_IN_FRONT -> {
                MenuNote(stringResource(R.string.clipboard_ai_not_in_front))
                if (onOpen != null) MenuEntry(Sym.OPEN_IN_NEW, stringResource(R.string.clipboard_ai_open), onClick = onOpen)
            }
            Ready.BUSY -> MenuNote(stringResource(R.string.clipboard_ai_busy))
            Ready.FAILED -> MenuNote(stringResource(R.string.clipboard_ai_failed))
            Ready.READY -> {}
        }
    }

    /** [block], or [fallback] if it throws; a cancellation goes on as one. Nothing is logged: what was asked is the user's text. */
    private suspend fun <T> guarded(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback
    }

    /** The debug hook's model: it stands where it was told and answers [answer] word by word, or stops with why it cannot. */
    private class StagedModel(private val status: Ready, private val answer: String?) : OnDeviceModel {
        override suspend fun ready() = status
        override fun download(): Flow<Ready> = flowOf(Ready.DOWNLOADING)
        override fun ask(prompt: String): Flow<Answer> = flow {
            if (status != Ready.READY || answer == null) emit(Answer.Stopped(if (status == Ready.READY) Ready.FAILED else status))
            else answer.split(' ').forEachIndexed { i, word -> emit(Answer.Piece(if (i == 0) word else " $word")) }
        }
    }
}
