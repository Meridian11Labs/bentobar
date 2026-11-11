package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.items.CryptoRules.Table
import com.meridian11.books.bentobar.net.FakeHttp
import com.meridian11.books.bentobar.net.Host
import com.meridian11.books.bentobar.net.HttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CryptoLoadTest : OnlineServiceContract<Table>() {
    private val now = 1_776_000_000_000L
    private val ask = CryptoLoad.Ask(listOf("BTC", "ETH"), "USD")
    private val reply = """{"error":[],"result":{
        "XXBTZUSD":{"c":["97412.10","0.01"],"o":"96000.00","h":["98000.0","98500.0"],"l":["95500.0","95000.0"]},
        "XETHZUSD":{"c":["3410.55","0.5"],"o":"3500.00","h":["3520.00","3600.00"],"l":["3390.00","3380.00"]}}}"""

    override val host = Host.KRAKEN
    override val path = CryptoLoad.PATH
    override val goodReply = reply
    override fun load(last: Table?) = CryptoLoad.load(ask, last, now)
    override fun failure(v: Table) = v.failure?.name
    override fun misses(v: Table) = v.misses
    override fun waitSec(v: Table) = v.retryAfterSec
    override fun numbers(v: Table) = v.prices
    override fun every(v: Table) = CryptoRules.every(v)
    // Said in About and the item's own words: literals on purpose, so that changing a constant fails here.
    override val freshMs: Long? = 2 * 60_000L
    override val retryMs = 5 * 60_000L
    override val slowMs = 30 * 60_000L

    @Test fun theConstantsAreTheIntervalsTheWordsSay() {
        assertEquals(freshMs, CryptoRules.EVERY_MS as Long?)
        assertEquals(30_000L, CryptoRules.MENU_MS)
        assertEquals(retryMs, CryptoRules.RETRY_MS)
        assertEquals(slowMs, CryptoRules.SLOW_MS)
    }

    @Test fun theRequestIsOnePairPerCoinAndNothingElse() {
        val r = CryptoLoad.request(CryptoLoad.Ask(listOf("BTC", "ETH", "DOGE"), "EUR"))!!
        assertEquals(Host.KRAKEN, r.host)
        assertEquals("/0/public/Ticker", r.path)
        assertEquals(listOf("pair" to "XBTEUR,ETHEUR,XDGEUR"), r.query)
        assertEquals("https://api.kraken.com/0/public/Ticker?pair=XBTEUR%2CETHEUR%2CXDGEUR", HttpTransport.address(r))
    }

    @Test fun aGoodReplyHoldsEachCoinAskedFor() = FakeHttp.use { net ->
        net.reply(Host.KRAKEN, CryptoLoad.PATH, reply)
        val t = CryptoLoad.load(ask, null, now)
        assertNotNull(t)
        assertEquals(listOf("BTC", "ETH"), t!!.prices.map { it.coin })
        assertEquals("XBTUSD,ETHUSD", net.query("pair"))
    }

    // Kraken refuses a whole request for one pair it doesn't list ({"error":["EQuery:Unknown asset pair"]}), and it has
    // no AVAX in pounds: that pair is never asked for, so the other coins keep their prices.
    @Test fun aPairTheExchangeDoesNotListIsLeftOutOfTheRequest() {
        assertEquals(false, CryptoRules.listed("AVAX", "GBP"))
        for (c in CryptoRules.COINS) for (q in CryptoRules.QUOTES) if (c != "AVAX" || q != "GBP") assertEquals("$c$q", true, CryptoRules.listed(c, q))
        val r = CryptoLoad.request(CryptoLoad.Ask(listOf("BTC", "AVAX"), "GBP"))!!
        assertEquals(listOf("pair" to "XBTGBP"), r.query)
    }

    @Test fun onlyUnlistedPairsAskNothingAndSayThereAreNoPrices() = FakeHttp.use { net ->
        val t = CryptoLoad.load(CryptoLoad.Ask(listOf("AVAX"), "GBP"), null, now)
        assertEquals(emptyList<Any>(), net.asked)
        assertEquals(Failure.NO_ANSWER, t!!.failure)
        assertEquals(false, t.hasPrices)
    }
}
