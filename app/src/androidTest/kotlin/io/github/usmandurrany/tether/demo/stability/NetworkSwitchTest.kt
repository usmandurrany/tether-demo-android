package io.github.usmandurrany.tether.demo.stability

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Moving between Wi-Fi and mobile data, as when leaving home: Android changes
 * the default network, the old socket dies with it, and the SDK has to come
 * back on the new one (resuming the session, so nothing is lost).
 */
@RunWith(AndroidJUnit4::class)
class NetworkSwitchTest : StabilityTest() {
    @Before
    fun needsWifiAndMobile() = runBlocking<Unit> {
        if (canSwitch == null) canSwitch = network.canSwitch()
        assumeTrue("The emulator needs both Wi-Fi and mobile data", canSwitch == true)
        // canSwitch toggled Wi-Fi; start from a working connection.
        awaitRecovered("setup", now(), limitMs = 30_000)
    }

    @Test
    fun switchesBetweenWifiAndMobile() = runBlocking<Unit> {
        repeat(4) { round ->
            network.wifi(false)
            val (cell, onCell) = network.await { it.cellular } ?: fail("Android did not move to mobile data")
            awaitRecovered("Wi-Fi → mobile #${round + 1} ($cell)", onCell)
            delay(3_000)

            network.wifi(true)
            val (wifi, onWifi) = network.await { it.wifi } ?: fail("Android did not move back to Wi-Fi")
            awaitRecovered("mobile → Wi-Fi #${round + 1} ($wifi)", onWifi)
            delay(3_000)
        }
        assertMessagesFlow("after switching")
    }

    @Test
    fun messagesSentDuringASwitchArrive() = runBlocking<Unit> {
        val bodies = mutableListOf<String>()
        repeat(3) { round ->
            bodies += "before switch $round".also { alice.send(bobJid, it) }
            network.wifi(false)
            bodies += "during switch $round".also { alice.send(bobJid, it) }
            val (_, onCell) = network.await { it.cellular } ?: fail("Android did not move to mobile data")
            awaitRecovered("switch with messages in flight #${round + 1}", onCell)
            network.wifi(true)
            network.await { it.wifi } ?: fail("Android did not move back to Wi-Fi")
        }
        val (_, back) = network.await { it.wifi }!!
        awaitRecovered("back on Wi-Fi", back)
        assertBobReceived("messages sent around switches", bodies, back)
    }

    private companion object {
        var canSwitch: Boolean? = null
    }
}
