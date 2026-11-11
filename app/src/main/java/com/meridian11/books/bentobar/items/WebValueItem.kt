package com.meridian11.books.bentobar.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Kept
import com.meridian11.books.bentobar.data.Online
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.items.WebValueRules.Reading
import com.meridian11.books.bentobar.net.OwnAddress
import com.meridian11.books.bentobar.ui.Bento
import com.meridian11.books.bentobar.ui.ChoiceRow
import com.meridian11.books.bentobar.ui.MenuCard
import com.meridian11.books.bentobar.ui.MenuDivider
import com.meridian11.books.bentobar.ui.MenuEntry
import com.meridian11.books.bentobar.ui.MenuNote
import com.meridian11.books.bentobar.ui.OffGate
import com.meridian11.books.bentobar.ui.TextRow
import com.meridian11.books.bentobar.ui.Undo
import com.meridian11.books.bentobar.ui.rememberTick
import com.meridian11.books.bentobar.ui.updatedNote
import com.meridian11.books.bentobar.util.Now
import com.meridian11.books.bentobar.util.Sym
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Value from a URL: one value from an address of the user's own (a number, a word, true or false) in the bar, with a
 * label and a unit, asked as often as the user sets. The one item that asks an address rather than a named service, so
 * it is fenced (ruling R12): HTTPS only, no name or password in the address, no redirect followed, a reply over 64 KB
 * refused (all in `net/`, [OwnAddress] and `Http.getOwn`), and its own switch, "Your own addresses" ([Online.Service.OWN]),
 * off until it is turned on on this install.
 *
 * The address is the user's own text and may hold a token ([discreet]): typed in the item's options, kept under the
 * item's id in the no-backup directory (`Kept.own("webvalue")`) and in memory once read, never in the layout (which Copy
 * settings and a backup carry), never in a description, a log line or a debug hook's output. The popup names its host, to
 * the user only. Of a reply only the value it holds is kept, in memory. No logging.
 */
object WebValueItem : ItemType("webvalue", R.string.item_webvalue_title, Sym.DATA_OBJECT, R.string.item_webvalue_desc) {
    override val group = ItemGroup.ONLINE
    override val onlineNote = R.string.about_privacy_own
    override val online = Online.Service.OWN
    override val discreet = true
    override val menuWidthDp = 320

    private const val OWN = "webvalue"

    /** The addresses read or typed so far, by item id, as typed: "" where nothing is kept. Written on the main thread or by a read on the type's thread. */
    internal val addresses = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Addresses typed and not written yet. Main thread. */
    private val unsaved = HashSet<String>()

    private val reading: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** What each item's address came to, by item id. Only the value a reply held, never the reply. */
    internal val values: Refresher<String, Reading> = Background.refresher(
        type = type,
        service = online,
        every = { id, r -> WebValueRules.every(r, minutesOf(id)) },
        load = { id, last -> WebValueLoad.load(addressOf(id), pathOf(id), last, Now.wall()) },
    )

    private fun itemOf(id: String): ItemConfig? = Store.config.value.items.firstOrNull { it.id == id }

    private fun minutesOf(id: String): Int = itemOf(id)?.let { WebValueRules.every.of(it) } ?: WebValueRules.every.default

    private fun pathOf(id: String): String = itemOf(id)?.let { WebValueRules.path.of(it) }.orEmpty()

    /** [id]'s address, if it was read and is one that may be asked. Any thread. */
    internal fun addressOf(id: String): OwnAddress? = addresses.value[id]?.trim()?.let { OwnAddress.parse(it) }

    /** Reads [id]'s address from where it is kept, off the main thread, unless it is in memory already. */
    internal fun read(id: String) {
        if (id in addresses.value || !reading.add(id)) return
        Background.executor(type).execute {
            val text = runCatching { Kept.own(OWN).read(id)?.text }.getOrNull().orEmpty()
            reading.remove(id)
            // Typed in the meantime: what was typed is newer than what was kept.
            addresses.update { if (id in it) it else it + (id to text) }
        }
    }

    /** The options' field changed [id]'s address to [text]: in memory at once, kept by [save]. Main thread. */
    internal fun edit(id: String, text: String) {
        addresses.update { it + (id to text.take(OwnAddress.MOST_CHARS)) }
        unsaved += id
    }

    /**
     * Keeps what was typed for [id], off the main thread: after a pause in typing, and when the field leaves. A blank
     * address keeps nothing. What the old address said is dropped, so the bar never shows one address's value under
     * another. Main thread.
     */
    internal fun save(id: String) {
        if (!unsaved.remove(id)) return
        val text = addresses.value[id] ?: return
        values.forget(id)
        Background.executor(type).execute {
            runCatching { if (text.isBlank()) Kept.own(OWN).remove(id) else Kept.own(OWN).write(id, text, System.currentTimeMillis()) }
        }
        if (Ticker.running) Ticker.refresh(type)
    }

    /** An address makes the item a setup: Remove keeps it as a line in the library, and the address with it. Any thread; it reads no file. */
    override fun keepsOwn(item: ItemConfig): Boolean =
        addresses.value[item.id]?.isNotBlank() == true || runCatching { Kept.own(OWN).has(item.id) }.getOrDefault(false)

    /** A line was forgotten and Undo has let it go: its address goes, from memory and from the device, and what it said. */
    override fun forgetOwn(id: String) {
        addresses.update { it - id }
        unsaved -= id
        values.forget(id)
        Background.executor(type).execute {
            runCatching { Kept.own(OWN).remove(id) }
            // A read that was on its way when the line went must not bring the address back into memory.
            addresses.update { it - id }
        }
    }

    /** The items of the layout, in the bar or a line in the library, and any the Bar page's Undo can still bring back. */
    internal fun ids(): Set<String> = Store.config.value.items.filter { it.type == type }.mapTo(HashSet()) { it.id }.also { it += Undo.held(type) }

    override fun settle(why: Settle) {
        // What neither forgetOwn nor anything else got to (the process ended while Undo still held an item) goes now.
        if (why != Settle.SERVICE_STARTED) return
        val keep = ids()
        addresses.update { it.filterKeys { id -> id in keep } }
        Background.executor(type).execute { runCatching { Kept.own(OWN).keepOnly(keep) } }
    }

    /** Every item's address is read once it may be shown; a value read by another path than the item's now is dropped. Main thread. */
    private fun follow() {
        val mine = Store.config.value.items.filter { it.type == type }
        for (item in mine) {
            read(item.id)
            val r = values.peek(item.id)
            if (r != null && r.path != WebValueRules.path.of(item) && !values.loading(item.id)) values.forget(item.id)
        }
        values.keepOnly(mine.mapTo(HashSet()) { it.id })
    }

    override fun onLive() = follow()

    override fun sample(now: Long) = follow()

    override fun forgetFetched() {
        staged.value = null
    }

    internal fun turnOn() {
        Online.turnOn(online)
        Ticker.refresh(type)
    }

    // ---- what an item shows ---------------------------------------------------------------------------

    /** What [item] stands at, from what is at hand. [host]: the address's host, for the popup alone. */
    internal sealed interface Look {
        data object Off : Look
        data object NoAddress : Look
        data object Loading : Look
        class Failed(val failure: Failure, val host: String?) : Look
        class NoValue(val reading: Reading, val host: String?) : Look
        class Value(val reading: Reading, val value: String, val host: String?) : Look
    }

    internal fun look(item: ItemConfig, ask: Boolean = true): Look {
        staged.of(item)?.let { s ->
            val r = s.reading ?: return Look.NoAddress
            return lookOf(r, s.host)
        }
        if (!Online.on(online)) return Look.Off
        val typed = addresses.value[item.id] ?: return Look.Loading
        val address = OwnAddress.parse(typed.trim()) ?: return Look.NoAddress
        if (ask && item.section != Section.OFF) values.want(item.id)
        val r = values.peek(item.id)?.takeIf { it.path == WebValueRules.path.of(item) } ?: return Look.Loading
        return lookOf(r, address.host)
    }

    private fun lookOf(r: Reading, host: String?): Look = when {
        r.value != null -> Look.Value(r, r.value, host)
        r.failure != null -> Look.Failed(r.failure, host)
        else -> Look.NoValue(r, host)
    }

    private fun quiet(desc: String) = ItemState(icon = Sym.DATA_OBJECT, filled = false, text = null, desc = desc, tooltip = desc)

    override fun state(item: ItemConfig): ItemState = when (val l = look(item)) {
        Look.Off -> quiet(Env.str(if (Online.setUp(online)) R.string.webvalue_desc_off else R.string.webvalue_desc_not_set_up))
        Look.NoAddress -> quiet(Env.str(R.string.webvalue_desc_no_address))
        Look.Loading -> quiet(Env.str(R.string.webvalue_desc_loading))
        is Look.Failed -> quiet(Env.str(if (l.failure == Failure.OFFLINE) R.string.webvalue_desc_offline else R.string.webvalue_desc_no_answer))
        is Look.NoValue -> {
            val text = Env.str(R.string.webvalue_no_value)
            ItemState(icon = Sym.DATA_OBJECT, text = text, tone = Tone.WARN, desc = Env.str(R.string.webvalue_desc_no_value), tooltip = text)
        }
        is Look.Value -> {
            // The label, the value and the unit: never the address or its host.
            val text = WebValueRules.shown(l.value, WebValueRules.label.of(item), WebValueRules.unit.of(item))
            ItemState(icon = Sym.DATA_OBJECT, text = text, desc = text, tooltip = text,
                textLimit = WebValueRules.LABEL_CHARS + WebValueRules.VALUE_CHARS + WebValueRules.UNIT_CHARS + 2)
        }
    }

    /** The menu opened: a value is asked for if there is none yet or it is due, never sooner ("as often as you set"); Ask now is the user's. */
    internal fun opened(item: ItemConfig) {
        if (staged.of(item) != null || item.section == Section.OFF || !Online.on(online) || addressOf(item.id) == null) return
        values.want(item.id)
    }

    internal fun askNow(item: ItemConfig) {
        if (staged.of(item) != null || !Online.on(online) || addressOf(item.id) == null) return
        values.refresh(item.id, floorMs = 10_000L)
    }

    override fun menuOpening(item: ItemConfig) = read(item.id)

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host -> WebValueMenu(item, host) }
    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set -> WebValueOptions(item, set) }

    // ---- tests on a device (debug builds) ------------------------------------------------------------

    private val staged = Staged<WebValueRules.Sample>(type)

    override val stages = listOf("value", "novalue", "offline", "nourl").map { "stage $it" }

    override val stageOptions = mapOf("label" to "Room", "unit" to "°C")

    /**
     * `./bento debug webvalue stage value|novalue|offline|nourl` shows a made-up value from a made-up host in the first
     * item's place; `webvalue off` ends it; bare `webvalue` counts what is known. No line says an address or a host.
     */
    override fun debug(args: List<String>): String? = when (args.firstOrNull()) {
        null -> "items=${ids().size} addresses=${addresses.value.values.count { it.isNotBlank() }} on=${Online.on(online)} staged=${staged.value != null}"
        "stage" -> WebValueRules.sample(args.getOrNull(1).orEmpty(), Now.wall())?.let {
            staged.value = it
            if (Ticker.running) Ticker.refresh(type)
            "staged ${args[1]}"
        }
        "off" -> {
            staged.value = null
            if (Ticker.running) Ticker.refresh(type)
            "staging off"
        }
        else -> null
    }
}

/** How long typing rests before the address is kept. */
private const val SAVE_AFTER_MS = 800L

@Composable
private fun WebValueMenu(item: ItemConfig, host: MenuHost) {
    rememberTick()
    val online by Online.state.collectAsState()
    val addresses by WebValueItem.addresses.collectAsState()
    val on = Online.Service.OWN in online.on
    val label = WebValueRules.label.of(item)
    val title = label.ifEmpty { stringResource(R.string.item_webvalue_title) }
    LaunchedEffect(item.id, on, addresses[item.id]) { WebValueItem.opened(item) }
    val edit = @Composable { MenuEntry(Sym.EDIT, stringResource(R.string.webvalue_edit)) { host.openItemSettings(item.id) } }
    when (val l = WebValueItem.look(item, ask = false)) {
        WebValueItem.Look.Off -> OffGate(Sym.DATA_OBJECT, title,
            stringResource(if (Online.setUp(Online.Service.OWN)) R.string.common_off_in_setup else R.string.common_not_set_up),
            stringResource(R.string.webvalue_consent), stringResource(R.string.webvalue_turn_on)) { WebValueItem.turnOn() }
        WebValueItem.Look.NoAddress -> MenuCard(Sym.DATA_OBJECT, title, stringResource(R.string.webvalue_no_address)) {
            MenuNote(stringResource(R.string.webvalue_address_help))
            MenuEntry(Sym.ADD, stringResource(R.string.webvalue_set_address)) { host.openItemSettings(item.id) }
        }
        WebValueItem.Look.Loading -> MenuCard(Sym.DATA_OBJECT, title, stringResource(R.string.usage_loading)) { edit() }
        is WebValueItem.Look.Failed -> MenuCard(Sym.DATA_OBJECT, title,
            stringResource(if (l.failure == Failure.OFFLINE) R.string.common_no_connection else R.string.webvalue_no_answer)) {
            l.host?.let { MenuNote(stringResource(R.string.webvalue_from, it)) }
            MenuEntry(Sym.REFRESH, stringResource(R.string.common_try_again)) { WebValueItem.askNow(item) }
            edit()
        }
        is WebValueItem.Look.NoValue -> MenuCard(Sym.DATA_OBJECT, title, l.host?.let { stringResource(R.string.webvalue_from, it) }) {
            Text(stringResource(R.string.webvalue_no_value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
            MenuNote(stringResource(if (WebValueRules.path.of(item).isEmpty()) R.string.webvalue_no_text else R.string.webvalue_no_path))
            MenuNote(updatedNote(l.reading.fetchedAt, null))
            MenuDivider()
            MenuEntry(Sym.REFRESH, stringResource(R.string.webvalue_ask_now)) { WebValueItem.askNow(item) }
            edit()
        }
        is WebValueItem.Look.Value -> MenuCard(Sym.DATA_OBJECT, title, l.host?.let { stringResource(R.string.webvalue_from, it) }) {
            Text(WebValueRules.shown(l.value, "", WebValueRules.unit.of(item)), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp))
            MenuNote(updatedNote(l.reading.fetchedAt, l.reading.failure))
            MenuDivider()
            MenuEntry(Sym.REFRESH, stringResource(R.string.webvalue_ask_now)) { WebValueItem.askNow(item) }
            edit()
        }
    }
}

@Composable
private fun WebValueOptions(item: ItemConfig, set: (ItemConfig) -> Unit) {
    val online by Online.state.collectAsState()
    val on = Online.Service.OWN in online.on
    key(item.id) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.webvalue_section), style = Bento.type.strong, color = Bento.colors.ink)
            Text(stringResource(R.string.webvalue_consent), style = Bento.type.help, color = Bento.colors.ink2)
            if (!on) {
                Spacer(Modifier.height(4.dp))
                MenuEntry(Sym.TOGGLE_ON, stringResource(R.string.webvalue_turn_on)) { WebValueItem.turnOn() }
            }
        }
        AddressRow(item.id)
        TextRow(stringResource(R.string.webvalue_path_label), WebValueRules.path.of(item), help = stringResource(R.string.webvalue_path_help),
            maxLength = WebValueRules.PATH_CHARS) { set(WebValueRules.path.set(item, it)) }
        TextRow(stringResource(R.string.webvalue_label_label), WebValueRules.label.of(item), maxLength = WebValueRules.LABEL_CHARS) {
            set(WebValueRules.label.set(item, it))
        }
        TextRow(stringResource(R.string.webvalue_unit_label), WebValueRules.unit.of(item), maxLength = WebValueRules.UNIT_CHARS) {
            set(WebValueRules.unit.set(item, it))
        }
        val every = listOf(
            1 to stringResource(R.string.webvalue_every_1),
            5 to stringResource(R.string.webvalue_every_5),
            15 to stringResource(R.string.webvalue_every_15),
            60 to stringResource(R.string.webvalue_every_60),
            1440 to stringResource(R.string.webvalue_every_1440),
        )
        ChoiceRow(stringResource(R.string.webvalue_every_label), every, WebValueRules.every.of(item)) { set(WebValueRules.every.set(item, it)) }
    }
}

/**
 * The address's field: read from where it is kept when the options open (off the main thread), in memory as it is typed,
 * and kept again off the main thread after a pause in typing and when the field leaves. Never in the item's options.
 */
@Composable
private fun AddressRow(id: String) {
    val addresses by WebValueItem.addresses.collectAsState()
    LaunchedEffect(id) { WebValueItem.read(id) }
    var typed by remember { mutableIntStateOf(0) }
    LaunchedEffect(typed) { if (typed > 0) { delay(SAVE_AFTER_MS); WebValueItem.save(id) } }
    DisposableEffect(id) { onDispose { WebValueItem.save(id) } }
    // Until it is read there is no field: what was typed into an empty one would meet the address on its way in.
    val text = addresses[id] ?: return
    val bad = text.isNotBlank() && OwnAddress.parse(text.trim()) == null
    TextRow(stringResource(R.string.webvalue_address_label), text, help = stringResource(R.string.webvalue_address_help), placeholder = "https://",
        error = if (bad) stringResource(R.string.webvalue_address_bad) else null, maxLength = OwnAddress.MOST_CHARS) { WebValueItem.edit(id, it); typed++ }
}
