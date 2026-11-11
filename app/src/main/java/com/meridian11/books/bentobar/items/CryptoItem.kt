package com.meridian11.books.bentobar.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Online
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.items.CryptoRules.Price
import com.meridian11.books.bentobar.items.CryptoRules.Table
import com.meridian11.books.bentobar.ui.Bento
import com.meridian11.books.bentobar.ui.ChoiceRow
import com.meridian11.books.bentobar.ui.MenuCard
import com.meridian11.books.bentobar.ui.MenuDivider
import com.meridian11.books.bentobar.ui.MenuEntry
import com.meridian11.books.bentobar.ui.MenuNote
import com.meridian11.books.bentobar.ui.OffGate
import com.meridian11.books.bentobar.ui.TextRow
import com.meridian11.books.bentobar.ui.rememberTick
import com.meridian11.books.bentobar.ui.updatedNote
import com.meridian11.books.bentobar.util.Fmt
import com.meridian11.books.bentobar.util.Now
import com.meridian11.books.bentobar.util.Sym

/**
 * Crypto: the prices of up to five coins the user picks, in US dollars, euros or pounds, one at a time in the bar, with
 * each coin's day in the menu. The prices come from the Kraken exchange's public ticker (`api.kraken.com`, no key). One
 * of the item types that go online ([online]), and only once it was turned on on this install.
 *
 * What the bar and the menu say is [CryptoRules]' (pure, unit-tested), the request [CryptoLoad]'s. No logging.
 */
object CryptoItem : ItemType("crypto", R.string.item_crypto_title, Sym.CURRENCY_BITCOIN, R.string.item_crypto_desc) {
    override val group = ItemGroup.ONLINE
    override val onlineNote = R.string.about_privacy_crypto
    override val menuWidthDp = 340
    override val online = Online.Service.KRAKEN

    /** The coins as typed: a cleared field is kept empty (no coins yet), not turned back into the default. */
    internal val coinsOpt = Opt.text("coins", 60, CryptoRules.DEFAULT_COINS, clearedIsUnset = false)
    internal val quoteOpt = Opt.oneOf("quote", "USD", "USD", "EUR", "GBP")

    internal fun ask(item: ItemConfig) = CryptoLoad.Ask(CryptoRules.coins(coinsOpt.of(item)), quoteOpt.of(item))

    /** One table per set of coins and currency: items that ask the same share it. */
    internal val prices: Refresher<CryptoLoad.Ask, Table> = Background.refresher(
        type = type,
        service = online,
        every = { _, t -> CryptoRules.every(t) },
        load = { ask, last -> CryptoLoad.load(ask, last, Now.wall()) },
    )

    private val staged = Staged<Table>(type)

    internal fun isStaged(item: ItemConfig): Boolean = staged.of(item) != null

    /** [item]'s table: the staged one in a test, else the loader's, asked for when it is due ([ask]). */
    internal fun table(item: ItemConfig, ask: Boolean = true): Table? {
        staged.of(item)?.let { return it }
        if (!Online.on(online)) return null
        val a = ask(item)
        if (a.coins.isEmpty()) return null
        if (ask && item.section != Section.OFF) prices.want(a)
        return prices.peek(a)
    }

    /** [item]'s coins that [table] has a price for, in the item's order. */
    internal fun shown(item: ItemConfig, table: Table?): List<Price> =
        table?.let { t -> ask(item).coins.mapNotNull { c -> t.prices.firstOrNull { it.coin == c } } }.orEmpty()

    internal fun opened(item: ItemConfig) {
        if (isStaged(item) || item.section == Section.OFF || !Online.on(online)) return
        val a = ask(item)
        if (a.coins.isNotEmpty()) prices.menuOpened(a, CryptoRules.MENU_MS) { it.failure != null }
    }

    internal fun refresh(item: ItemConfig) {
        if (isStaged(item) || !Online.on(online)) return
        prices.refresh(ask(item), floorMs = 10_000L)
    }

    internal fun turnOn() {
        Online.turnOn(online)
        Ticker.refresh(type)
    }

    private fun quiet(desc: String) = ItemState(icon = Sym.CURRENCY_BITCOIN, filled = false, text = null, desc = desc, tooltip = desc)

    override fun state(item: ItemConfig): ItemState {
        if (!Online.on(online) && !isStaged(item))
            return quiet(Env.str(if (Online.setUp(online)) R.string.crypto_desc_off else R.string.crypto_desc_not_set_up))
        if (ask(item).coins.isEmpty()) return quiet(Env.str(R.string.crypto_none_yet))
        val t = table(item)
        val shown = shown(item, t)
        if (shown.isEmpty()) return quiet(Env.str(if (t?.failure != null) R.string.crypto_desc_no_prices else R.string.crypto_desc_loading))
        val turns = shown.map { CryptoRules.text(it) }
        val idx = ((Now.wall() / 1000L) / CryptoRules.TURN_SEC).mod(turns.size)
        val summary = shown.joinToString("; ") { "${CryptoRules.text(it)} ${Fmt.percent(CryptoRules.signed(CryptoRules.change(it)))}" }
        val desc = Env.str(R.string.crypto_desc_live, quoteOpt.of(item), summary)
        return ItemState(
            icon = CryptoRules.icon(shown[idx]),
            filled = true,
            text = turns[idx],
            turns = if (turns.size > 1) turns else null,
            desc = desc,
            tooltip = desc,
            textLimit = CryptoRules.BAR_CHARS,
        )
    }

    // ---- what no item asks for any more ----------------------------------------------------------------

    private var seenItems: List<ItemConfig>? = null

    /** The tables no item asks for any more are dropped: looked at whenever the layout's items changed. Main thread. */
    private fun tidy() {
        val items = Store.config.value.items
        if (items === seenItems) return
        seenItems = items
        prices.keepOnly(items.filter { it.type == type }.mapTo(HashSet()) { ask(it) })
    }

    override fun sample(now: Long) = tidy()

    override fun onIdle() = tidy()

    override fun forgetFetched() {
        staged.value = null
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host -> CryptoMenu(item, host) }
    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set -> CryptoOptions(item, set) }

    // ---- tests on a device (debug builds) ------------------------------------------------------------

    override val stages = listOf("calm", "big", "offline", "old").map { "stage $it" }

    /** `./bento debug crypto stage calm|big|offline|old` shows made-up prices in the first Crypto item's place; `crypto off` ends it. */
    override fun debug(args: List<String>): String? = when (args.firstOrNull()) {
        null -> "staged=${staged.value != null} on=${Online.on(online)}"
        "stage" -> CryptoRules.sample(args.getOrNull(1).orEmpty(), Now.wall())?.let {
            staged.value = it
            Ticker.refresh(type)
            "staged ${args[1]}"
        }
        "off" -> {
            staged.value = null
            Ticker.refresh(type)
            "staging off"
        }
        else -> null
    }
}

@Composable
private fun CryptoMenu(item: ItemConfig, host: MenuHost) {
    rememberTick()
    val online by Online.state.collectAsState()
    val on = Online.Service.KRAKEN in online.on
    val title = stringResource(R.string.item_crypto_title)
    LaunchedEffect(item.id, on) { CryptoItem.opened(item) }
    val ask = CryptoItem.ask(item)
    val table = CryptoItem.table(item, ask = false)
    val shown = CryptoItem.shown(item, table)
    val edit = @Composable { MenuEntry(Sym.EDIT, stringResource(R.string.crypto_edit)) { host.openItemSettings(item.id) } }
    when {
        !on && !CryptoItem.isStaged(item) -> OffGate(Sym.CURRENCY_BITCOIN, title,
            stringResource(if (Online.setUp(Online.Service.KRAKEN)) R.string.common_off_in_setup else R.string.common_not_set_up),
            stringResource(R.string.crypto_consent), stringResource(R.string.crypto_turn_on)) { CryptoItem.turnOn() }
        ask.coins.isEmpty() -> MenuCard(Sym.CURRENCY_BITCOIN, title, stringResource(R.string.crypto_none_yet)) {
            MenuEntry(Sym.ADD, stringResource(R.string.crypto_add_coins)) { host.openItemSettings(item.id) }
        }
        shown.isEmpty() && table?.failure == null -> MenuCard(Sym.CURRENCY_BITCOIN, title, stringResource(R.string.usage_loading)) { edit() }
        shown.isEmpty() -> MenuCard(Sym.CURRENCY_BITCOIN, title,
            stringResource(if (table?.failure == Failure.OFFLINE) R.string.common_no_connection else R.string.crypto_desc_no_prices)) {
            MenuEntry(Sym.REFRESH, stringResource(R.string.common_try_again)) { CryptoItem.refresh(item) }
            edit()
        }
        else -> MenuCard(Sym.CURRENCY_BITCOIN, title, stringResource(R.string.crypto_in, ask.quote)) {
            for (coin in ask.coins) {
                val p = shown.firstOrNull { it.coin == coin }
                if (p != null) CoinRow(p) else NoPriceRow(coin)
            }
            MenuNote(stringResource(R.string.crypto_note, updatedNote(table!!.fetchedAt, table.failure)))
            MenuDivider()
            MenuEntry(Sym.REFRESH, stringResource(R.string.common_refresh)) { CryptoItem.refresh(item) }
            edit()
        }
    }
}

/** One coin: its price, its move since the UTC day began, and the day's high and low. */
@Composable
private fun CoinRow(p: Price) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val change = CryptoRules.change(p)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.coin, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(CryptoRules.price(p.last), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.crypto_today, Fmt.percent(CryptoRules.signed(change))),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f),
                color = when {
                    change >= CryptoRules.FLAT_PCT -> MaterialTheme.colorScheme.primary
                    change <= -CryptoRules.FLAT_PCT -> MaterialTheme.colorScheme.error
                    else -> quiet
                })
            Text(stringResource(R.string.crypto_high_low, CryptoRules.price(p.high), CryptoRules.price(p.low)),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = quiet)
        }
    }
}

/** A coin the exchange sent no price for: named, with a word why, rather than left out of the list. */
@Composable
private fun NoPriceRow(coin: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(coin, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.crypto_no_price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CryptoOptions(item: ItemConfig, set: (ItemConfig) -> Unit) {
    val online by Online.state.collectAsState()
    val on = Online.Service.KRAKEN in online.on
    key(item.id) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.crypto_section), style = Bento.type.strong, color = Bento.colors.ink)
            Text(stringResource(R.string.crypto_consent), style = Bento.type.help, color = Bento.colors.ink2)
            if (!on) {
                Spacer(Modifier.height(4.dp))
                MenuEntry(Sym.TOGGLE_ON, stringResource(R.string.crypto_turn_on)) { CryptoItem.turnOn() }
            }
        }
        TextRow(stringResource(R.string.crypto_coins_label), CryptoItem.coinsOpt.of(item), help = stringResource(R.string.crypto_coins_help),
            placeholder = CryptoRules.DEFAULT_COINS, maxLength = 60) { set(CryptoItem.coinsOpt.set(item, it)) }
        ChoiceRow(stringResource(R.string.crypto_quote_label), CryptoRules.QUOTES.map { it to it }, CryptoItem.quoteOpt.of(item)) {
            set(CryptoItem.quoteOpt.set(item, it))
        }
    }
}
