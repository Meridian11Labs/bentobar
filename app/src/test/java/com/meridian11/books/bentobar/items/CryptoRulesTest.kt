package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.util.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CryptoRulesTest {
    private val now = 1_776_000_000_000L

    /** Kraken's ticker in the shape checked on 2026-11-11: the keys are the exchange's own pair names, padded or not. */
    private val reply = """{"error":[],"result":{
        "XXBTZUSD":{"a":["97412.20","1","1.000"],"b":["97412.00","2","2.000"],"c":["97412.10","0.01"],"v":["100.1","2000.2"],
            "p":["97000.1","96800.2"],"t":[1000,20000],"l":["95500.0","95000.0"],"h":["98000.0","98500.0"],"o":"96000.00"},
        "XETHZUSD":{"c":["3410.55","0.5"],"o":"3500.00","h":["3520.00","3600.00"],"l":["3390.00","3380.00"]},
        "SOLEUR":{"c":["181.20","3"],"o":"180.00","h":["185.00","186.00"],"l":["179.00","170.00"]}}}"""

    @Test fun theExchangesNamesForBitcoinAndDogecoin() {
        assertEquals("XBTUSD", CryptoRules.pair("BTC", "USD"))
        assertEquals("XDGEUR", CryptoRules.pair("DOGE", "EUR"))
        assertEquals("ETHGBP", CryptoRules.pair("ETH", "GBP"))
        assertEquals("SOLUSD", CryptoRules.pair("SOL", "USD"))
    }

    @Test fun aReplyIsReadWhateverItsKeysPadding() {
        val usd = CryptoRules.read(reply, listOf("BTC", "ETH"), "USD", now)!!
        val btc = usd.prices.single { it.coin == "BTC" }
        assertEquals(97412.10, btc.last, 0.0)
        assertEquals(96000.00, btc.open, 0.0)
        // The day's (UTC) high and low are the first of the two the exchange sends; the second is the last 24 hours'.
        assertEquals(98000.0, btc.high, 0.0)
        assertEquals(95500.0, btc.low, 0.0)
        assertEquals(1.47, CryptoRules.change(btc), 0.005)
        assertEquals(now, usd.fetchedAt)
        assertEquals(listOf("BTC", "ETH"), usd.prices.map { it.coin })
        assertTrue(CryptoRules.change(usd.prices[1]) < 0)
        // A key without the padding is found too.
        val eur = CryptoRules.read(reply, listOf("SOL"), "EUR", now)!!
        assertEquals(181.20, eur.prices.single().last, 0.0)
        // A coin the reply has nothing for is left out; none at all is no table.
        assertEquals(listOf("BTC"), CryptoRules.read(reply, listOf("BTC", "ADA"), "USD", now)!!.prices.map { it.coin })
        assertNull(CryptoRules.read(reply, listOf("ADA"), "USD", now))
        assertNull("the same coin in another currency is not this one", CryptoRules.read(reply, listOf("BTC"), "EUR", now))
    }

    @Test fun anErrorReplyIsNoTable() {
        assertNull(CryptoRules.read("""{"error":["EQuery:Unknown asset pair"]}""", listOf("BTC"), "USD", now))
        assertNull(CryptoRules.read("""{"error":["EGeneral:Too many requests"],"result":{"XXBTZUSD":{"c":["1.0","1"],"o":"1.0","h":["1.0","1.0"],"l":["1.0","1.0"]}}}""",
            listOf("BTC"), "USD", now))
        assertNull(CryptoRules.read("<html>sign in here</html>", listOf("BTC"), "USD", now))
        assertNull(CryptoRules.read("""{"result":[]}""", listOf("BTC"), "USD", now))
        assertNull(CryptoRules.read("""{"error":[],"result":{"XXBTZUSD":{"c":["0","1"],"o":"1.0","h":["1.0","1.0"],"l":["1.0","1.0"]}}}""", listOf("BTC"), "USD", now))
        // A warning is no error: the prices beside it are read.
        assertNotNull(CryptoRules.read(reply.replace(""""error":[]""", """"error":["WGeneral:Something to know"]"""), listOf("BTC"), "USD", now))
    }

    @Test fun fiveCoinsAtMostAndOnlyKnownOnes() {
        assertEquals(listOf("BTC", "ETH", "DOGE"), CryptoRules.coins("btc, eth doge"))
        assertEquals(listOf("BTC", "SOL"), CryptoRules.coins("BTC;nonsense  sol,,BTC"))
        // The exchange's own names are understood too.
        assertEquals(listOf("BTC", "DOGE"), CryptoRules.coins("xbt xdg"))
        assertEquals(listOf("BTC", "ETH", "SOL", "XRP", "ADA"), CryptoRules.coins("BTC ETH SOL XRP ADA DOGE LTC"))
        assertEquals(emptyList<String>(), CryptoRules.coins(""))
        assertEquals(10, CryptoRules.COINS.size)
        assertEquals(listOf("USD", "EUR", "GBP"), CryptoRules.QUOTES)
    }

    @Test fun pricesAreWordedByTheirSize() {
        val us = Locale.US
        assertEquals("BTC 97,412", CryptoRules.text(CryptoRules.Price("BTC", 97_412.10, 1.0, 1.0, 1.0), us))
        assertEquals("ETH 3,411", CryptoRules.text(CryptoRules.Price("ETH", 3_410.55, 1.0, 1.0, 1.0), us))
        assertEquals("SOL 181.20", CryptoRules.text(CryptoRules.Price("SOL", 181.2, 1.0, 1.0, 1.0), us))
        assertEquals("DOGE 0.1432", CryptoRules.text(CryptoRules.Price("DOGE", 0.143_21, 1.0, 1.0, 1.0), us))
        assertEquals("0.5000", CryptoRules.price(0.5, us))
        assertEquals("0.00001234", CryptoRules.price(0.000_012_34, us))
        // In the device's own way of writing numbers.
        assertEquals("97.412", CryptoRules.price(97_412.10, Locale.GERMANY))
        assertEquals("181,20", CryptoRules.price(181.2, Locale.GERMANY))
        assertEquals("+1.47", CryptoRules.signed(1.4709, us))
        assertEquals("−0.83", CryptoRules.signed(-0.83, us))
    }

    @Test fun theGlyphFollowsTheDaysMove() {
        fun p(last: Double) = CryptoRules.Price("BTC", last, 100.0, 1.0, 1.0)
        assertEquals(Sym.TRENDING_UP, CryptoRules.icon(p(101.0)))
        assertEquals(Sym.TRENDING_DOWN, CryptoRules.icon(p(99.0)))
        assertEquals(Sym.TRENDING_FLAT, CryptoRules.icon(p(100.01)))
        assertEquals("no opening price is no move", 0.0, CryptoRules.change(CryptoRules.Price("BTC", 5.0, 0.0, 1.0, 1.0)), 0.0)
    }

    @Test fun theStagesAreTheirOwnAndOnlyThose() {
        for (name in listOf("calm", "big", "offline", "old")) {
            val t = CryptoRules.sample(name, now)!!
            assertEquals(CryptoRules.COINS, t.prices.map { it.coin })
        }
        assertEquals(Failure.OFFLINE, CryptoRules.sample("offline", now)!!.failure)
        assertTrue(CryptoRules.sample("big", now)!!.prices.any { kotlin.math.abs(CryptoRules.change(it)) > 5 })
        assertNull(CryptoRules.sample("no-such-state", now))
    }
}
