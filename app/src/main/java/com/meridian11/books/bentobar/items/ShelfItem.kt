package com.meridian11.books.bentobar.items

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Kept
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.items.ShelfRules.Entry
import com.meridian11.books.bentobar.util.Sym
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shelf: text and links kept for a moment, up to [ShelfRules.MOST]. Dragged onto its item in the bar from another app,
 * or added from what is copied; copied, opened or dragged out again from its popup. Text and links only, never files:
 * a service's window cannot be given the right to read a dragged file (that belongs to an activity).
 *
 * What is on a shelf is the user's own ([discreet]): kept under the item's id in the no-backup directory
 * (`Kept.own("shelf")`) and in memory once read, never in the layout, a log line or a debug hook's output. Each change
 * is made on the type's own thread from what the file holds, one after another, so none is lost to another.
 */
object ShelfItem : ItemType("shelf", R.string.item_shelf_title, Sym.INBOX, R.string.item_shelf_desc) {
    override val group = ItemGroup.TOOLS
    override val takesDrops = true
    override val discreet = true
    override val canBeActive = true
    override val menuWidthDp = 340
    override val trigger = Trigger(R.string.trigger_shelf, R.string.trigger_shelf_short)

    private const val OWN = "shelf"

    /** Each item's entries as its file holds them, by item id, once read. Written on the type's thread (and emptied on the main thread when an item goes). */
    internal val shelves = MutableStateFlow<Map<String, List<Entry>>>(emptyMap())

    /** Debug builds: made-up entries in every Shelf item's place (`./bento debug shelf stage NAME`); nothing is kept for them. */
    internal val staged = MutableStateFlow<List<Entry>?>(null)

    private val reading: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** [id]'s entries as far as they have been read, or null: not read yet. */
    internal fun held(id: String): List<Entry>? = staged.value ?: shelves.value[id]

    private fun read(id: String): List<Entry> = Kept.own(OWN).read(id)?.text?.let(ShelfRules::decode).orEmpty()

    /** An empty shelf keeps no file: the item is then as the + made it. */
    private fun write(id: String, entries: List<Entry>): Boolean =
        if (entries.isEmpty()) { Kept.own(OWN).remove(id); true } else Kept.own(OWN).write(id, ShelfRules.encode(entries), System.currentTimeMillis())

    /** The bar shows the new count now, where it shows anything at all. */
    private fun changed() { if (Ticker.running) Ticker.refresh(type) }

    /** Reads [id]'s entries from where they are kept, off the main thread, unless they are read already. */
    internal fun load(id: String) {
        if (id in shelves.value || !reading.add(id)) return
        Background.executor(type).execute {
            val entries = runCatching { read(id) }.getOrDefault(emptyList())
            reading.remove(id)
            // A change that came first has read the file itself, and what it left is newer.
            shelves.update { if (id in it) it else it + (id to entries) }
            changed()
        }
    }

    /** Changes [id]'s shelf by [step], on the type's thread, from what its file holds. A file that cannot be written keeps what it had. */
    private fun change(id: String, step: (List<Entry>) -> List<Entry>) {
        staged.value?.let { staged.value = step(it); changed(); return }
        Background.executor(type).execute {
            runCatching {
                val before = read(id)
                val after = step(before)
                val now = if (after == before || write(id, after)) after else before
                shelves.update { it + (id to now) }
            }
            changed()
        }
    }

    internal fun add(id: String, texts: List<String>) = change(id) { ShelfRules.take(it, texts) }

    internal fun remove(id: String, text: String) = change(id) { ShelfRules.without(it, text) }

    internal fun clear(id: String) = change(id) { emptyList() }

    /**
     * The texts [clip] gives a shelf, by [ShelfRules.dropped]. None from a clip marked private (a password from a password
     * manager): a shelf keeps what it takes on the device, and such a clip is meant to be pasted once.
     */
    private fun texts(clip: ClipData): List<String> {
        if (clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) return emptyList()
        return ShelfRules.dropped((0 until clip.itemCount).map { i -> clip.getItemAt(i).let { ShelfRules.Part(it.text?.toString(), it.uri?.toString()) } })
    }

    /** Whether [texts] would add anything to [id]'s shelf as it is known now. */
    private fun adds(id: String, texts: List<String>): Boolean = held(id).orEmpty().let { ShelfRules.take(it, texts) != it }

    override fun onDrop(item: ItemConfig, clip: ClipData): Boolean {
        val texts = texts(clip)
        if (texts.isEmpty()) return false
        val took = adds(item.id, texts)
        add(item.id, texts)
        return took
    }

    /**
     * Takes what is copied now. Android gives the clipboard to the app whose window has the keyboard, which an open popup
     * has. True if it added anything. Main thread.
     */
    internal fun addCopied(id: String): Boolean {
        val clip = runCatching { Env.app.getSystemService(ClipboardManager::class.java)?.primaryClip }.getOrNull() ?: return false
        val texts = texts(clip)
        if (texts.isEmpty()) return false
        val took = adds(id, texts)
        add(id, texts)
        return took
    }

    /** Opens a link in whatever opens it. Not through `Env.launch`, which logs the intent, and with it the link's host. */
    internal fun open(link: String) {
        runCatching { Env.app.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { Toast.makeText(Env.app, Env.str(R.string.toast_nothing_can_open), Toast.LENGTH_SHORT).show() } }
    }

    /** Entries make the item a setup: Remove keeps it as a line in the library, with what it holds. Any thread; it reads no file. */
    override fun keepsOwn(item: ItemConfig): Boolean =
        shelves.value[item.id]?.isNotEmpty() == true || runCatching { Kept.own(OWN).has(item.id) }.getOrDefault(false)

    /** A line was forgotten and Undo has let it go: what it held goes, from memory and from the device. */
    override fun forgetOwn(id: String) {
        shelves.update { it - id }
        Background.executor(type).execute {
            runCatching { Kept.own(OWN).remove(id) }
            // A read or a change that was on its way when the line went must not bring it back into memory.
            shelves.update { it - id }
        }
    }


    override fun settle(why: Settle) {
        // What neither forgetOwn nor anything else got to (the process ended while Undo still held an item) goes now.
        if (why != Settle.SERVICE_STARTED) return
        val keep = ids()
        shelves.update { it.filterKeys { id -> id in keep } }
        Background.executor(type).execute { runCatching { Kept.own(OWN).keepOnly(keep) } }
    }

    /** Reads what each Shelf item outside Off holds, where that is not known yet. */
    private fun loadAll() = Store.config.value.items.forEach { if (it.type == type && it.section != Section.OFF) load(it.id) }

    override fun onLive() = loadAll()

    // An item added while the bar shows, or brought back from the library, is read at the next tick.
    override fun sample(now: Long) = loadAll()

    override fun menuOpening(item: ItemConfig) = load(item.id)

    override fun state(item: ItemConfig): ItemState {
        val n = held(item.id)?.size ?: 0
        return ItemState(icon = Sym.INBOX, text = if (n > 0) n.toString() else null, active = n > 0,
            desc = if (n > 0) Env.plural(R.plurals.shelf_desc_count, n) else Env.str(R.string.shelf_desc_empty))
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host -> ShelfMenu(item, host) }

    // ---- for tests on a device (debug builds)

    private val three = ShelfRules.take(emptyList(), listOf("https://example.com/handbook/first-week",
        "Room 4.12, second floor, left of the lifts", "Call back after three about the quote"))

    private val full = ShelfRules.take(emptyList(), (1..ShelfRules.MOST).map { "Sample note $it" } + "https://example.com/more")

    override val stages = listOf("stage three", "stage full", "stage empty")

    /**
     * `shelf stage three|full|empty` shows made-up entries in every Shelf item's place; `shelf off` ends it; bare `shelf`
     * counts what is known. No line says a word of an entry.
     */
    override fun debug(args: List<String>): String? = when (args.firstOrNull()) {
        null -> "items=${ids().size} read=${shelves.value.size} entries=${shelves.value.values.sumOf { it.size }} staged=${staged.value?.size}"
        "stage" -> when (args.getOrNull(1)) {
            "three" -> three
            "full" -> full
            "empty" -> emptyList()
            else -> null
        }?.let { staged.value = it; changed(); "staged ${it.size} entries" }
        "off" -> { staged.value = null; changed(); "staging off" }
        else -> null
    }
}
