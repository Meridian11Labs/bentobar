package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.items.CryptoRules.Table
import com.meridian11.books.bentobar.net.Host
import com.meridian11.books.bentobar.net.Http
import com.meridian11.books.bentobar.net.Reply
import com.meridian11.books.bentobar.net.Request

// Requests and loading for the Crypto item (Kraken's public ticker, no key). No Android, and no logging.

object CryptoLoad {
    const val PATH = "/0/public/Ticker"

    /** What an item asks for: its coins, in one currency. Items that ask the same share one table. */
    data class Ask(val coins: List<String>, val quote: String)

    /**
     * One request for every coin of [ask] the exchange lists in its currency: `pair=XBTUSD,ETHUSD`. Nothing else goes
     * with it. Null when it lists none of them.
     */
    fun request(ask: Ask): Request? = ask.coins.filter { CryptoRules.listed(it, ask.quote) }.takeIf { it.isNotEmpty() }
        ?.let { coins -> Request(Host.KRAKEN, PATH, listOf("pair" to coins.joinToString(",") { CryptoRules.pair(it, ask.quote) })) }

    /**
     * Asks the exchange; blocks. A good answer is a new table; a failure is a table too, with what went wrong and the prices
     * there were. Null: it was not asked after all (the switch went off, the bar hid).
     */
    fun load(ask: Ask, last: Table?, now: Long): Table? {
        // Nothing the exchange lists: no request, and a table that says there are no prices (not "Loading" for ever).
        val request = request(ask) ?: return Table(fetchedAt = now, failure = Failure.NO_ANSWER)
        val reply = Http.get(request)
        val good = (reply as? Reply.Ok)?.let { CryptoRules.read(it.text, ask.coins, ask.quote, now) }
        if (good != null) return good
        val missed = Failures.missed(reply, slowAfterMs = CryptoRules.RETRY_MS) ?: return null
        val known = last ?: Table()
        return known.copy(failure = missed.failure, misses = known.misses + if (missed.went) 1 else 0, retryAfterSec = missed.waitSec)
    }
}
