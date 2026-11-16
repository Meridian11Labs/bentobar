package com.meridian11.books.bentobar.compose

import android.app.Application
import android.net.ConnectivityManager
import android.net.wifi.MloLink
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.LinkRules.Band
import com.meridian11.books.bentobar.items.LinkRules.Radio
import com.meridian11.books.bentobar.items.LinkWatch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * The Network menu's link is followed only while a Network item is live: one callback on the default network however
 * often it goes live, and none once it is idle, with what was read forgotten. The Wi-Fi values are not kept: they
 * are read at each ask while such an item is live, from a Wi-Fi network alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class LinkWatchTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val cm = app.getSystemService(ConnectivityManager::class.java)
    private val callbacks get() = shadowOf(cm).networkCallbacks

    @Before fun env() = Env.init(app)
    @After fun end() = LinkWatch.idle()

    @Test fun oneCallbackWhileLiveAndNoneOnceIdle() {
        assertEquals(0, callbacks.size)
        LinkWatch.live()
        assertEquals(1, callbacks.size)
        // A second Network item in the layout, or live() again: still the one callback.
        LinkWatch.live()
        assertEquals(1, callbacks.size)
        LinkWatch.idle()
        assertEquals(0, callbacks.size)
        assertNull(LinkWatch.current())
        // Idle twice is idle.
        LinkWatch.idle()
        assertEquals(0, callbacks.size)
    }

    @Test fun wifiValuesAreReadAtEachAskAndOnlyWhileLive() {
        // The numbers are the rules' to test: a WifiInfo built here can carry neither a speed nor a frequency.
        val wifi = ShadowNetworkCapabilities.newInstance().also { shadowOf(it).setTransportInfo(WifiInfo.Builder().build()) }
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, wifi)
        assertNull(LinkWatch.currentWifi())

        LinkWatch.live()
        assertNotNull(LinkWatch.currentWifi())
        // Any other network, a tunnel over Wi-Fi too, has no WifiInfo. No report is waited for: the next ask reads it.
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, ShadowNetworkCapabilities.newInstance())
        assertNull(LinkWatch.currentWifi())
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, wifi)
        assertNotNull(LinkWatch.currentWifi())

        LinkWatch.idle()
        assertNull(LinkWatch.currentWifi())
    }

    @Test fun theCallbackKeepsNoWifiValues() {
        LinkWatch.live()
        assertNull(LinkWatch.currentWifi())
        val wifi = ShadowNetworkCapabilities.newInstance().also { shadowOf(it).setTransportInfo(WifiInfo.Builder().build()) }
        // Android reports a Wi-Fi link's capabilities every few seconds: nothing is done with a report.
        callbacks.single().onCapabilitiesChanged(cm.activeNetwork!!, wifi)
        assertNull(LinkWatch.currentWifi())
    }

    @Test fun aMultiLinkConnectionsLinksAreRead() {
        // Android keeps a link's setters to itself, so they are reached by name.
        fun link(band: Int, channel: Int, state: Int) = MloLink().also {
            for ((setter, value) in listOf("setBand" to band, "setChannel" to channel, "setState" to state)) {
                MloLink::class.java.getMethod(setter, Int::class.javaPrimitiveType).invoke(it, value)
            }
        }
        // One link carries traffic, one is linked and idle, and one the access point has but this device is not on.
        // Set on the built WifiInfo, as an app gets it: Android's copy of one drops the links where the access
        // point's own address is gone, which it is for an app without the location permission.
        val info = WifiInfo.Builder().build()
        WifiInfo::class.java.getMethod("setAffiliatedMloLinks", List::class.java).invoke(info, listOf(
            link(ScanResult.WIFI_BAND_5_GHZ, 44, MloLink.MLO_LINK_STATE_ACTIVE),
            link(ScanResult.WIFI_BAND_6_GHZ, 37, MloLink.MLO_LINK_STATE_IDLE),
            link(ScanResult.WIFI_BAND_24_GHZ, 6, MloLink.MLO_LINK_STATE_UNASSOCIATED)))
        val wifi = ShadowNetworkCapabilities.newInstance().also { shadowOf(it).setTransportInfo(info) }
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, wifi)

        LinkWatch.live()
        assertEquals(listOf(Radio(Band.GHZ_5, 44), Radio(Band.GHZ_6, 37)), LinkWatch.currentWifi()!!.links)
    }
}
