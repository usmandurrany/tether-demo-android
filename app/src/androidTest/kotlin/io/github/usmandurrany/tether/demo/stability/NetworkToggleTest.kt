package io.github.usmandurrany.tether.demo.stability

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * The network going away and coming back, over and over: airplane mode
 * flipped by a restless user, or a phone on the edge of coverage. Whatever
 * the sequence, the connection must work again soon after the network does,
 * and must never give up.
 */
@RunWith(AndroidJUnit4::class)
class NetworkToggleTest : StabilityTest() {
    private val random = Random(20261021) // Same "random" timing on every run.

    @Test
    fun recoversAfterEachShortDrop() = runBlocking<Unit> {
        repeat(6) { round ->
            network.airplaneMode(true)
            delay(2_000)
            network.airplaneMode(false)
            val (_, back) = network.await() ?: fail("No network after airplane mode #${round + 1}")
            awaitRecovered("2 s without network #${round + 1}", back)
            delay(3_000)
        }
        assertMessagesFlow("after short drops")
    }

    @Test
    fun recoversAfterAirplaneModeFlapping() = runBlocking<Unit> {
        val sent = mutableListOf<String>()
        // 12 flips, 0.5 to 3 s apart, sending a message now and then.
        repeat(12) { flip ->
            network.airplaneMode(flip % 2 == 0)
            if (flip % 3 == 0) sent += "flap $flip".also { alice.send(bobJid, it) }
            delay(random.nextLong(500, 3_000))
        }
        network.airplaneMode(false)
        val (_, back) = network.await() ?: fail("No network after flapping")
        awaitRecovered("after 12 airplane mode flips", back)
        assertBobReceived("messages sent while flapping", sent, back)
        assertMessagesFlow("after flapping")
    }

    @Test
    fun recoversAfterWifiFlappingWithMobileUp() = runBlocking<Unit> {
        // Wi-Fi drops in and out while mobile data stays on: the default network flips each time.
        repeat(8) { flip ->
            network.wifi(flip % 2 == 1)
            delay(random.nextLong(1_000, 4_000))
        }
        network.wifi(true)
        val (_, back) = network.await { it.wifi } ?: fail("Wi-Fi did not come back")
        awaitRecovered("after 8 Wi-Fi flips", back)
        assertMessagesFlow("after Wi-Fi flapping")
    }

    @Test
    fun recoversWhenTheNetworkReturnsMidConnect() = runBlocking<Unit> {
        // Drops that land while the SDK is still connecting from the previous drop.
        repeat(5) { round ->
            network.airplaneMode(true)
            delay(1_500)
            network.airplaneMode(false)
            network.await() ?: fail("No network #${round + 1}")
            delay(random.nextLong(100, 1_200)) // likely mid-connect
            network.airplaneMode(true)
            delay(1_000)
            network.airplaneMode(false)
            val (_, back) = network.await() ?: fail("No network #${round + 1}")
            awaitRecovered("drop during reconnect #${round + 1}", back)
        }
        assertMessagesFlow("after drops during reconnects")
    }
}
