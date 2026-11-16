package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.items.LinkRules.Band
import com.meridian11.books.bentobar.items.LinkRules.Field
import com.meridian11.books.bentobar.items.LinkRules.Link
import com.meridian11.books.bentobar.items.LinkRules.Radio
import com.meridian11.books.bentobar.items.LinkRules.Signal
import com.meridian11.books.bentobar.items.LinkRules.Wifi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The Wi-Fi and Addresses sections of the Network menu and its subtitle: which rows a link gets and what each one says,
 * in the app's own words. Addresses are written as Android hands them over: IPv6 in its short form, a zone after "%",
 * no prefix length.
 */
class LinkRulesTest {
    private val texts = StringFiles.read(listOf("strings.xml", "strings_network.xml"))
    private val words = LinkRules.Words(on = texts.strings.getValue("common_on"),
        off = texts.strings.getValue("network_link_private_dns_unused"))

    private val wifiWords = LinkRules.WifiWords(
        both = { down, up -> text("network_wifi_speed_both", down, up) },
        down = { text("network_wifi_speed_down", it) },
        up = { text("network_wifi_speed_up", it) },
        band = { text(when (it) { Band.GHZ_2_4 -> "network_wifi_band_24"; Band.GHZ_5 -> "network_wifi_band_5"; Band.GHZ_6 -> "network_wifi_band_6" }) },
        bandChannel = { band, channel -> text("network_wifi_band_channel", band, channel) },
        bands = { first, next -> text("network_wifi_bands", first, next) },
        signal = { text("network_wifi_signal_" + it.name.lowercase()) },
    )

    private fun text(name: String, vararg args: Any) = String.format(Locale.US, texts.strings.getValue(name), *args)
    private fun rows(link: Link?) = LinkRules.rows(link, words).map { it.field to it.value }
    private fun wifiRows(wifi: Wifi?) = LinkRules.wifiRows(wifi, wifiWords).map { it.field to it.value }
    /** The rows of a link of which the frequency alone is known. */
    private fun band(mhz: Int) = wifiRows(Wifi(-1, -1, mhz, -1, -1))

    @Test fun theFirstIpv4AddressIsTheRow() {
        assertEquals("192.168.1.42", LinkRules.ipv4(listOf("fe80::1:2:3:4%wlan0", "192.168.1.42", "10.0.0.5")))
        assertNull(LinkRules.ipv4(listOf("fe80::1:2:3:4%wlan0")))
    }

    @Test fun ipv6SkipsTheLinkLocalAndTheLoopback() {
        assertNull(LinkRules.ipv6(listOf("fe80::1c2d:3e4f:5a6b:7c8d%wlan0", "febf::1%wlan0", "::1", "192.168.1.42")))
    }

    @Test fun ipv6PrefersAGlobalAddressToALocalOne() {
        assertEquals("2001:db8::7f3e", LinkRules.ipv6(listOf("fd12:3456:789a::1", "2001:db8::7f3e")))
        assertEquals("fd12:3456:789a::1", LinkRules.ipv6(listOf("fe80::1%wlan0", "fd12:3456:789a::1")))
        assertEquals("fec0::1a2b:3c4d:5e6f:202a", LinkRules.ipv6(listOf("fe80::1:2:3:4%wlan0", "fec0::1a2b:3c4d:5e6f:202a")))
    }

    @Test fun aLongIpv6IsShortenedToItsEndsAndAnythingElseIsWhole() {
        assertEquals("2a02:c7c:…:7f3e", LinkRules.short("2a02:c7c:1234:5600:abcd:ef01:2345:7f3e"))
        assertEquals("fec0:0:…:202a", LinkRules.short("fec0::1a2b:3c4d:5e6f:202a"))
        assertEquals("2001:db8::7f3e", LinkRules.short("2001:db8::7f3e"))
        assertEquals("192.168.1.42", LinkRules.short("192.168.1.42"))
        assertEquals("255.255.255.255", LinkRules.short("255.255.255.255"))
    }

    @Test fun theGatewayIsTheIpv4OneAheadOfAnIpv6One() {
        assertEquals("192.168.1.1", LinkRules.gateway(listOf("fe80::1%wlan0", "192.168.1.1")))
        assertEquals("fe80::1", LinkRules.gateway(listOf("fe80::1%wlan0")))
        assertNull(LinkRules.gateway(emptyList()))
    }

    @Test fun aLongIpv6GatewayIsShortenedToo() {
        val link = Link(addresses = listOf("2001:db8::2"), gateways = listOf("fe80::1a2b:3c4d:5e6f:7a8b%wlan0"))
        assertTrue((Field.GATEWAY to "fe80:0:…:7a8b") in rows(link))
    }

    @Test fun dnsShowsTwoServersAtMostAndShortensALongOne() {
        assertEquals("10.0.0.1 · 2001:4860:…:8888", LinkRules.dns(listOf("10.0.0.1", "2001:4860:4860::8888", "1.1.1.1")))
        assertEquals("192.168.1.1 · 2001:4860:…:8888", LinkRules.dns(listOf("192.168.1.1", "2001:4860:4860::8888")))
        assertEquals("10.0.0.1", LinkRules.dns(listOf("10.0.0.1")))
        assertNull(LinkRules.dns(emptyList()))
    }

    @Test fun dnsShowsTheFirstServerAloneWhereTwoDontFitTheRow() {
        // Two of these pushed the label into a column of letters.
        assertEquals("2001:4860:…:8888", LinkRules.dns(listOf("2001:4860:4860::8888", "2001:4860:4860::8844")))
        assertEquals("192.168.100.100", LinkRules.dns(listOf("192.168.100.100", "192.168.100.101")))
    }

    @Test fun privateDnsReadsNotInUseOnOrOnWithItsHost() {
        val link = Link(addresses = listOf("10.0.0.2"))
        assertTrue((Field.PRIVATE_DNS to "Not in use") in rows(link))
        assertTrue((Field.PRIVATE_DNS to "On") in rows(link.copy(privateDns = true)))
        assertTrue((Field.PRIVATE_DNS to "On · dns.google") in rows(link.copy(privateDns = true, privateDnsHost = "dns.google")))
    }

    @Test fun aLongPrivateDnsHostKeepsItsLastLabelsWithinTheDnsRowsRoom() {
        val on = Link(addresses = listOf("10.0.0.2"), privateDns = true)
        // Cloudflare's own host for Android, 32 characters: whole, the label broke into a column of letters.
        assertTrue((Field.PRIVATE_DNS to "On · …cloudflare-dns.com") in rows(on.copy(privateDnsHost = "1dot1dot1dot1.cloudflare-dns.com")))
        // A host that names an account loses that part first.
        assertEquals("…dns.nextdns.io", LinkRules.host("abc123def456.dns.nextdns.io", 20))
        assertEquals("dns.google", LinkRules.host("dns.google", 25))
        // A last label longer than the room is cut to its end.
        assertEquals("…hijklmnopqrstuvwxyz", LinkRules.host("abcdefghijklmnopqrstuvwxyz", 20))
        // Whatever the host, the row's value keeps to the DNS row's thirty characters.
        val long = "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + ".example"
        for (host in listOf(long, "x".repeat(253), "1dot1dot1dot1.cloudflare-dns.com", "dns.adguard-dns.com")) {
            val value = rows(on.copy(privateDnsHost = host)).single { it.first == Field.PRIVATE_DNS }.second
            assertTrue("$value is ${value.length} characters", value.length <= 30)
        }
    }

    @Test fun noLinkAndALinkWithoutAnAddressHaveNoRows() {
        assertTrue(rows(null).isEmpty())
        assertTrue(rows(Link(dns = listOf("1.1.1.1"), gateways = listOf("10.0.0.1"))).isEmpty())
    }

    @Test fun theRowsComeInTheMenusOrder() {
        val wifi = LinkSamples.of("wifi")!!.link
        assertEquals(listOf(Field.IPV4, Field.IPV6, Field.GATEWAY, Field.DNS, Field.PRIVATE_DNS), rows(wifi).map { it.first })
        assertEquals(listOf("192.168.1.42", "2001:db8:…:7f3e", "192.168.1.1", "192.168.1.1 · 2001:4860:…:8888", "Not in use"), rows(wifi).map { it.second })
    }

    @Test fun aTunnelHasNoGatewayRow() {
        val vpn = LinkSamples.of("vpn")!!.link
        assertEquals(listOf(Field.IPV4, Field.DNS, Field.PRIVATE_DNS), rows(vpn).map { it.first })
        assertTrue((Field.PRIVATE_DNS to "On · …cloudflare-dns.com") in rows(vpn))
    }

    @Test fun aNetworkWithIpv6AloneHasNoIpv4Row() {
        val v6 = LinkSamples.of("ipv6")!!.link
        assertEquals(listOf(Field.IPV6 to "2001:db8:…:7f3e", Field.GATEWAY to "fe80:0:…:7a8b", Field.DNS to "2001:4860:…:8888",
            Field.PRIVATE_DNS to "On"), rows(v6))
    }

    @Test fun everySampleHasANameAndOfflineIsNoLink() {
        for (name in LinkSamples.names) assertNotNull("$name is no sample", LinkSamples.of(name))
        assertNull(LinkSamples.of("offline")!!.link)
        assertNotNull(LinkSamples.of("ethernet")!!.link)
        assertNull(LinkSamples.of("moon"))
        // The samples on Wi-Fi have its values, and no other has.
        assertEquals(listOf("wifi", "ipv6", "weak", "mlo"), LinkSamples.names.filter { LinkSamples.of(it)!!.wifi != null })
    }

    @Test fun eachWifiSampleShowsItsLook() {
        fun values(name: String) = wifiRows(LinkSamples.of(name)!!.wifi).map { it.second }
        assertEquals(listOf("866 down · 780 up Mbps", "5 GHz · channel 44", "Good"), values("wifi"))
        assertEquals(listOf("5764 down · 5764 up Mbps", "6 GHz · channel 233", "Excellent"), values("ipv6"))
        // One direction unknown, and the lowest word.
        assertEquals(listOf("72 down Mbps", "2.4 GHz · channel 11", "Poor"), values("weak"))
        // Two links at once, and a Band row for each.
        assertEquals(listOf("2882 down · 2594 up Mbps", "5 GHz · channel 44", "6 GHz · channel 37", "Excellent"), values("mlo"))
    }

    @Test fun everySamplesWifiValuesFitTheRow() {
        // The room the DNS row's value is held to: a longer one broke the label into a column of letters.
        for (name in LinkSamples.names) for ((field, value) in wifiRows(LinkSamples.of(name)!!.wifi)) {
            assertTrue("$name $field: $value is ${value.length} characters", value.length <= 30)
        }
    }

    @Test fun theDebugLineNamesTheRowsAndNoAddress() {
        fun line(name: String) = LinkSamples.of(name)!!.let { LinkRules.present(it.link, it.wifi) }
        assertEquals("link_speed band signal ipv4 ipv6 gateway dns private_dns=off", line("wifi"))
        assertEquals("link_speed band signal ipv4 gateway dns private_dns=off", line("weak"))
        assertEquals("link_speed band band signal ipv4 gateway dns private_dns=off", line("mlo"))
        assertEquals("link_speed band signal ipv6 gateway dns private_dns=on", line("ipv6"))
        assertEquals("ipv4 dns private_dns=on", line("vpn"))
        assertEquals("no link", LinkRules.present(null, null))
        // Whatever the sample: no dot and no colon, so nothing of an address or a host.
        for (name in LinkSamples.names) assertFalse(name, line(name).any { it == '.' || it == ':' })
    }

    @Test fun twoPointFourGigahertzGivesBandAndChannel() {
        assertEquals(listOf(Field.BAND to "2.4 GHz · channel 1"), band(2412))
        assertEquals(listOf(Field.BAND to "2.4 GHz · channel 11"), band(2462))
        assertEquals(listOf(Field.BAND to "2.4 GHz · channel 14"), band(2484))
    }

    @Test fun fiveGigahertzGivesBandAndChannel() {
        assertEquals(listOf(Field.BAND to "5 GHz · channel 36"), band(5180))
        assertEquals(listOf(Field.BAND to "5 GHz · channel 44"), band(5220))
        assertEquals(listOf(Field.BAND to "5 GHz · channel 165"), band(5825))
    }

    @Test fun sixGigahertzGivesBandAndChannel() {
        // 5935 MHz stands alone below the band's other channels, and is its channel 2.
        assertEquals(listOf(Field.BAND to "6 GHz · channel 2"), band(5935))
        assertEquals(listOf(Field.BAND to "6 GHz · channel 1"), band(5955))
        assertEquals(listOf(Field.BAND to "6 GHz · channel 233"), band(7115))
    }

    @Test fun aFrequencyOutsideTheBandsHasNoBandRow() {
        for (mhz in listOf(-1, 0, 3000, 58320)) assertTrue("$mhz", band(mhz).isEmpty())
    }

    @Test fun linkSpeedShowsBothDirectionsOnOneRow() {
        assertEquals("866 down · 780 up Mbps", LinkRules.speed(866, 780, wifiWords))
    }

    @Test fun anUnknownDirectionIsLeftOutAndTwoUnknownHaveNoRow() {
        assertEquals("72 down Mbps", LinkRules.speed(72, -1, wifiWords))
        assertEquals("780 up Mbps", LinkRules.speed(0, 780, wifiWords))
        assertNull(LinkRules.speed(-1, 0, wifiWords))
    }

    @Test fun theSignalWordFollowsAndroidsLevel() {
        assertEquals(listOf(Signal.EXCELLENT, Signal.GOOD, Signal.FAIR, Signal.POOR, Signal.POOR), (4 downTo 0).map { LinkRules.signal(it, 4) })
    }

    @Test fun aScaleOfAnotherSizeIsReadByItsShare() {
        assertEquals(listOf(Signal.EXCELLENT, Signal.GOOD, Signal.FAIR, Signal.POOR), (5 downTo 2).map { LinkRules.signal(it, 5) })
    }

    @Test fun anUnknownLevelOrAScaleWithNoTopHasNoSignalRow() {
        assertNull(LinkRules.signal(-1, 4))
        assertNull(LinkRules.signal(3, 0))
        assertNull(LinkRules.signal(3, -1))
        assertEquals(listOf(Field.LINK_SPEED, Field.BAND), wifiRows(Wifi(866, 780, 5220, -1, -1)).map { it.first })
    }

    @Test fun anUnknownSignalIsNotAskedAbout() {
        val wifi = LinkRules.wifi(866, 780, 5220, LinkRules.NO_SIGNAL,
            level = { throw AssertionError("asked for the level of no signal") }, maxLevel = { throw AssertionError("asked for the top of the scale") })
        assertEquals(Wifi(866, 780, 5220, -1, -1), wifi)
    }

    @Test fun aLevelThatFailsCostsTheSignalRowOnly() {
        assertEquals(Wifi(866, 780, 5220, 3, 4), LinkRules.wifi(866, 780, 5220, -60, level = { 3 }, maxLevel = { 4 }))
        val noLevel = LinkRules.wifi(866, 780, 5220, -60, level = { null }, maxLevel = { 4 })
        val noTop = LinkRules.wifi(866, 780, 5220, -60, level = { 3 }, maxLevel = { null })
        for (wifi in listOf(noLevel, noTop)) assertEquals(listOf(Field.LINK_SPEED, Field.BAND), wifiRows(wifi).map { it.first })
    }

    @Test fun theWifiRowsComeInTheMenusOrder() {
        assertEquals(listOf(Field.LINK_SPEED to "866 down · 780 up Mbps", Field.BAND to "5 GHz · channel 44", Field.SIGNAL to "Good"),
            wifiRows(Wifi(866, 780, 5220, 3, 4)))
    }

    @Test fun aLinkOnTwoBandsHasABandRowForEach() {
        // Whatever order Android lists them in, the lower band comes first.
        val both = Wifi(2882, 2594, 6135, 4, 4, links = listOf(Radio(Band.GHZ_6, 37), Radio(Band.GHZ_5, 44)))
        assertEquals(listOf(Field.LINK_SPEED to "2882 down · 2594 up Mbps", Field.BAND to "5 GHz · channel 44",
            Field.BAND to "6 GHz · channel 37", Field.SIGNAL to "Excellent"), wifiRows(both))
        // Two links in one band are two rows too.
        val oneBand = Wifi(-1, -1, 5745, -1, -1, links = listOf(Radio(Band.GHZ_5, 149), Radio(Band.GHZ_5, 36)))
        assertEquals(listOf(Field.BAND to "5 GHz · channel 36", Field.BAND to "5 GHz · channel 149"), wifiRows(oneBand))
    }

    @Test fun oneLinkAloneReadsAsTheFrequency() {
        // An access point that can do several links, with one in use: the row is the frequency's, as on any Wi-Fi.
        val one = Wifi(866, 780, 5220, 3, 4, links = listOf(Radio(Band.GHZ_5, 44)))
        assertEquals(wifiRows(Wifi(866, 780, 5220, 3, 4)), wifiRows(one))
        assertEquals("5 GHz", LinkRules.bands(one, wifiWords))
    }

    @Test fun theSubtitleNamesEachBandOfTheLinkOnce() {
        fun bands(vararg links: Radio) = LinkRules.bands(Wifi(-1, -1, 6135, -1, -1, links.toList()), wifiWords)
        assertEquals("5 GHz + 6 GHz", bands(Radio(Band.GHZ_6, 37), Radio(Band.GHZ_5, 44)))
        assertEquals("2.4 GHz + 5 GHz + 6 GHz", bands(Radio(Band.GHZ_6, 37), Radio(Band.GHZ_2_4, 6), Radio(Band.GHZ_5, 44)))
        assertEquals("5 GHz", bands(Radio(Band.GHZ_5, 149), Radio(Band.GHZ_5, 36)))
        // No links listed: the frequency's band, and none for a frequency outside the three.
        assertEquals("6 GHz", bands())
        assertNull(LinkRules.bands(Wifi(-1, -1, -1, -1, -1), wifiWords))
        assertNull(LinkRules.bands(null, wifiWords))
    }

    @Test fun androidsBandNumbersAreTheThreeBands() {
        assertEquals(Radio(Band.GHZ_2_4, 6), LinkRules.radio(1, 6))
        assertEquals(Radio(Band.GHZ_5, 44), LinkRules.radio(2, 44))
        assertEquals(Radio(Band.GHZ_6, 37), LinkRules.radio(8, 37))
        // 60 GHz, no band, and a link without a channel yet.
        assertNull(LinkRules.radio(16, 2))
        assertNull(LinkRules.radio(0, 1))
        assertNull(LinkRules.radio(2, 0))
    }

    @Test fun theLinksComeThroughWithTheSignalOrWithout() {
        val links = listOf(Radio(Band.GHZ_5, 44), Radio(Band.GHZ_6, 37))
        assertEquals(Wifi(2882, 2594, 6135, 4, 4, links), LinkRules.wifi(2882, 2594, 6135, -48, links, level = { 4 }, maxLevel = { 4 }))
        assertEquals(Wifi(2882, 2594, 6135, -1, -1, links), LinkRules.wifi(2882, 2594, 6135, -48, links, level = { null }, maxLevel = { 4 }))
    }

    @Test fun noWifiHasNoWifiRows() {
        assertTrue(wifiRows(null).isEmpty())
        assertTrue(wifiRows(Wifi(-1, -1, -1, -1, -1)).isEmpty())
    }

    @Test fun theSubtitlePutsTheBandAfterTheKind() {
        val wifi = text("network_kind_wifi")
        val vpn = text("network_kind_vpn")
        val metered = text("network_kind_metered")
        assertEquals("Wi-Fi · 5 GHz", LinkRules.subtitle(wifi, true, "5 GHz", null, null))
        assertEquals("Wi-Fi · 5 GHz · metered", LinkRules.subtitle(wifi, true, "5 GHz", null, metered))
        assertEquals("Wi-Fi · VPN", LinkRules.subtitle(wifi, true, null, vpn, null))
        // Only Wi-Fi has a band, whatever is handed over.
        assertEquals("Ethernet", LinkRules.subtitle(text("network_kind_ethernet"), false, "5 GHz", null, null))
    }

    @Test fun theDebugLineNamesTheWifiRowsAheadOfTheLinks() {
        val all = Wifi(866, 780, 5220, 3, 4)
        assertEquals("link_speed band signal no link", LinkRules.present(null, all))
        assertEquals("link_speed band signal no address", LinkRules.present(Link(dns = listOf("1.1.1.1")), all))
        assertEquals("ipv4 private_dns=off", LinkRules.present(Link(addresses = listOf("10.0.0.2")), Wifi(-1, -1, -1, -1, -1)))
    }
}
