package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.items.LinkRules.Band
import com.meridian11.books.bentobar.items.LinkRules.Link
import com.meridian11.books.bentobar.items.LinkRules.Radio
import com.meridian11.books.bentobar.items.LinkRules.Wifi

/**
 * Made-up links for testing on a device (debug builds: `./bento debug network stage wifi`), one for each look of the
 * Wi-Fi and Addresses sections. Their addresses are the documentation ranges', private ones and public resolvers';
 * none is any device's, and neither are the Wi-Fi values. Pure, so a unit test checks each one's rows.
 */
object LinkSamples {
    val names = listOf("wifi", "ethernet", "vpn", "ipv6", "weak", "mlo", "offline")

    /** [link]: what is shown instead of the real one; null is no network. [wifi]: its Wi-Fi link; null where it is not Wi-Fi. */
    class Sample(val name: String, val link: Link?, val wifi: Wifi? = null)

    /** The sample called [name]; null for a name that is none. */
    fun of(name: String): Sample? = when (name) {
        // A home network with both address families, Private DNS not in use, and the widest pair of resolvers the row has room for.
        "wifi" -> Sample(name, Link(
            addresses = listOf("192.168.1.42", "fe80::1c2d:3e4f:5a6b:7c8d%wlan0", "2001:db8:85a3:0:8a2e:370:7334:7f3e"),
            gateways = listOf("192.168.1.1", "fe80::1%wlan0"),
            dns = listOf("192.168.1.1", "2001:4860:4860::8888"),
        ), Wifi(rxMbps = 866, txMbps = 780, frequencyMhz = 5220, level = 3, maxLevel = 4))
        // IPv4 alone, two resolvers, Private DNS in the automatic mode.
        "ethernet" -> Sample(name, Link(
            addresses = listOf("10.0.0.23"),
            gateways = listOf("10.0.0.1"),
            dns = listOf("10.0.0.1", "1.1.1.1"),
            privateDns = true,
        ))
        // A tunnel: no gateway on its default route, the VPN's resolver, Private DNS set to a host too long for its row.
        "vpn" -> Sample(name, Link(
            addresses = listOf("10.8.0.2"),
            dns = listOf("10.8.0.1"),
            privateDns = true,
            privateDnsHost = "1dot1dot1dot1.cloudflare-dns.com",
        ))
        // IPv6 alone: the widest values there are, a long gateway and two long resolvers, of which the row has room for one.
        // On 6 GHz at its last channel, with four digits of speed each way.
        "ipv6" -> Sample(name, Link(
            addresses = listOf("fe80::1c2d:3e4f:5a6b:7c8d%wlan0", "2001:db8:85a3:0:8a2e:370:7334:7f3e"),
            gateways = listOf("fe80::1a2b:3c4d:5e6f:7a8b%wlan0"),
            dns = listOf("2001:4860:4860::8888", "2001:4860:4860::8844"),
            privateDns = true,
        ), Wifi(rxMbps = 5764, txMbps = 5764, frequencyMhz = 7115, level = 4, maxLevel = 4))
        // Far from the access point on 2.4 GHz: the lowest signal, and a speed Android knows one way only.
        "weak" -> Sample(name, Link(
            addresses = listOf("192.168.0.57"),
            gateways = listOf("192.168.0.1"),
            dns = listOf("192.168.0.1"),
        ), Wifi(rxMbps = 72, txMbps = -1, frequencyMhz = 2462, level = 1, maxLevel = 4))
        // A Wi-Fi 7 connection on two links at once, one in the 5 GHz band and one in the 6 GHz band.
        "mlo" -> Sample(name, Link(
            addresses = listOf("192.168.68.31"),
            gateways = listOf("192.168.68.1"),
            dns = listOf("192.168.68.1"),
        ), Wifi(rxMbps = 2882, txMbps = 2594, frequencyMhz = 6135, level = 4, maxLevel = 4,
            links = listOf(Radio(Band.GHZ_5, 44), Radio(Band.GHZ_6, 37))))
        "offline" -> Sample(name, null)
        else -> null
    }
}
