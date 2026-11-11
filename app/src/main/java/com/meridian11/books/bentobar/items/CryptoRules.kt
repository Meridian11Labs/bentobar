package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.util.Sym
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Pure rules for the Crypto item: the coins it knows and the exchange's names for them, Kraken's public ticker reply,
 * the day's move and the words for a price. No Android imports, and no logging.
 */
object CryptoRules {
    val COINS = listOf("BTC", "ETH", "SOL", "XRP", "ADA", "DOGE", "LTC", "DOT", "LINK", "AVAX")
    val QUOTES = listOf("USD", "EUR", "GBP")
    const val MOST = 5
    const val DEFAULT_COINS = "BTC ETH"
    /** A good table is asked for again after this, while the bar is on screen. */
    const val EVERY_MS = 120_000L
    /** The menu opened: a table older than this is asked for again. */
    const val MENU_MS = 30_000L
    /** After no answer. */
    const val RETRY_MS = 5 * 60_000L
    /** After being told to slow down, unless the exchange named a longer time. */
    const val SLOW_MS = 30 * 60_000L
    const val BAR_CHARS = 20
    /** Each coin's turn in the bar, seconds. */
    const val TURN_SEC = 5
    /** A day's move smaller than this, in percent either way, reads as flat. */
    const val FLAT_PCT = 0.05

    /** Where the exchange's name for a coin is not the usual one. */
    private val EXCHANGE = mapOf("BTC" to "XBT", "DOGE" to "XDG")

    /**
     * Pairs of [COINS] and [QUOTES] the exchange doesn't list (its `/0/public/AssetPairs`, checked 2026-11-11). They are
     * never asked for: one unknown pair makes the exchange refuse the whole request, every other coin's price with it.
     */
    private val UNLISTED = setOf("AVAXGBP")

    /** Whether the exchange lists [coin] in [quote]. */
    fun listed(coin: String, quote: String): Boolean = pair(coin, quote) !in UNLISTED

    /** A price of one coin in the quote currency: the last trade, and the UTC day's opening price, high and low. */
    data class Price(val coin: String, val last: Double, val open: Double, val high: Double, val low: Double)

    data class Table(
        val prices: List<Price> = emptyList(),
        val fetchedAt: Long = 0,
        val failure: Failure? = null,
        /** How many tries in a row went out and failed. */
        val misses: Int = 0,
        /** How long the exchange asked to be left alone. */
        val retryAfterSec: Long = 0,
    ) {
        val hasPrices: Boolean get() = prices.isNotEmpty()
        override fun toString() = "Table(prices=${prices.size}, failure=$failure)"
    }

    /** The exchange's name for [coin] in [quote]: "XBTUSD" for BTC in USD. */
    fun pair(coin: String, quote: String): String = (EXCHANGE[coin] ?: coin) + quote

    /** The coins [text] names, as the user typed them ("btc, eth doge"), in order: known ones only, each once, five at most. */
    fun coins(text: String): List<String> {
        val usual = EXCHANGE.entries.associate { (coin, name) -> name to coin }
        return text.uppercase(Locale.ROOT).split(Regex("[^A-Z0-9]+")).map { usual[it] ?: it }.filter { it in COINS }.distinct().take(MOST)
    }

    /**
     * Kraken's `/0/public/Ticker` reply: `{"error":[],"result":{"XXBTZUSD":{"c":[last,lot],"o":open,"h":[today,24h],"l":[today,24h],…}}}`.
     * Its keys are the exchange's own pair names, some padded with an X before the coin and a Z before the currency
     * (`XXBTZUSD` for `XBTUSD`), some not (`SOLEUR`): either is matched. An error (a word that starts with E; one that starts
     * with W is a warning) or nothing for any of [coins] is null; a coin it has nothing for is left out.
     */
    fun read(body: String, coins: List<String>, quote: String, now: Long): Table? = try {
        val root = Json.parseToJsonElement(body) as? JsonObject
        val errors = (root?.get("error") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        val result = root?.get("result") as? JsonObject
        if (result == null || errors.any { !it.startsWith("W") }) null else {
            val prices = coins.mapNotNull { coin -> keys(coin, quote).firstNotNullOfOrNull { result[it] as? JsonObject }?.let { price(coin, it) } }
            if (prices.isEmpty()) null else Table(prices, now)
        }
    } catch (e: Exception) {
        null
    } catch (e: StackOverflowError) {
        null
    }

    /** The names a reply may give [coin] in [quote]: with or without the padding, on either side. */
    private fun keys(coin: String, quote: String): List<String> {
        val c = EXCHANGE[coin] ?: coin
        return listOf("$c$quote", "X${c}Z$quote", "X$c$quote", "${c}Z$quote")
    }

    private fun price(coin: String, o: JsonObject): Price? {
        val last = number(o["c"]) ?: return null
        val open = number(o["o"]) ?: return null
        val high = number(o["h"]) ?: return null
        val low = number(o["l"]) ?: return null
        return Price(coin, last, open, high, low)
    }

    /** A price as the exchange writes it: a string, or the first of a list of them. Positive and finite, or null. */
    private fun number(e: JsonElement?): Double? {
        val p = (e as? JsonArray)?.firstOrNull() ?: e
        return (p as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }
    }

    /** The day's move, in percent: from the UTC day's opening price to the last trade. */
    fun change(p: Price): Double = if (p.open > 0.0) (p.last - p.open) / p.open * 100.0 else 0.0

    fun icon(p: Price): String = change(p).let {
        when {
            it >= FLAT_PCT -> Sym.TRENDING_UP
            it <= -FLAT_PCT -> Sym.TRENDING_DOWN
            else -> Sym.TRENDING_FLAT
        }
    }

    /**
     * A price by its size, in the device's own way of writing numbers: whole from 1 000 ("97,412"), two decimals from 1
     * ("181.20"), else four significant digits ("0.1432").
     */
    fun price(value: Double, locale: Locale = Locale.getDefault()): String = when {
        value >= 1000.0 -> NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = true; roundingMode = RoundingMode.HALF_UP }.format(value)
        value >= 1.0 -> String.format(locale, "%.2f", value)
        else -> significant(value, 4).replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    }

    /** [value] with exactly [digits] significant digits, written out in full ("0.5000", "0.00001234"). */
    private fun significant(value: Double, digits: Int): String {
        val rounded = BigDecimal(value).round(MathContext(digits, RoundingMode.HALF_UP))
        return rounded.setScale(maxOf(rounded.scale(), rounded.scale() + digits - rounded.precision()), RoundingMode.HALF_UP).toPlainString()
    }

    /** "BTC 97,412": what the bar shows of a coin. */
    fun text(p: Price, locale: Locale = Locale.getDefault()): String = "${p.coin} ${price(p.last, locale)}"

    /** A move as "+1.47" or "−0.83", the sign always there. */
    fun signed(pct: Double, locale: Locale = Locale.getDefault()): String = (if (pct < 0) "−" else "+") + String.format(locale, "%.2f", abs(pct))

    /** Two minutes after a good table; a failure at its own pace, as the other online items. */
    fun every(table: Table): Long = if (table.failure == null) EVERY_MS else Pace.after(table.failure, table.misses, table.retryAfterSec, RETRY_MS, SLOW_MS)

    /** Debug samples (`./bento debug crypto stage calm|big|offline|old`): a price for every coin, made up. */
    fun sample(name: String, now: Long): Table? {
        val base = listOf(97_412.10, 3_410.55, 181.20, 2.4120, 0.7310, 0.143_21, 88.40, 6.120, 17.85, 36.40)
        fun table(moves: List<Double>, fetchedAt: Long, failure: Failure? = null) = Table(
            COINS.mapIndexed { i, coin ->
                val last = base[i]
                val open = last / (1 + moves[i % moves.size] / 100)
                Price(coin, last, open, maxOf(last, open) * 1.01, minOf(last, open) * 0.99)
            }, fetchedAt, failure)
        return when (name) {
            "calm" -> table(listOf(0.31, -0.12, 0.02, 0.45), now - 60_000)
            "big" -> table(listOf(6.80, -7.25, 11.40, -5.10), now - 30_000)
            "offline" -> table(listOf(1.20, -0.60), now - 15 * 60_000, Failure.OFFLINE)
            "old" -> table(listOf(-2.10, 0.90, 3.30), now - 3 * 60 * 60_000)
            else -> null
        }
    }
}
