package io.github.usmandurrany.tether.demo.stability

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A slow, narrow, choppy or dead link between the app and the server, made
 * with Toxiproxy while Android still reports a good network. The SDK cannot
 * learn of these from the OS; it has to notice on its own.
 */
@RunWith(AndroidJUnit4::class)
class PoorNetworkTest : StabilityTest() {
    @Before
    fun needsToxiproxy() = assumeTrue("Needs Toxiproxy between the app and the server", hasProxy)

    /** About a weak 3G signal: ~400 ms round trips with jitter, 16 KB/s each way, data in small pieces. */
    private fun makeLinkPoor() {
        proxy.add("latency", "latency", mapOf("latency" to 150, "jitter" to 50))
        proxy.add("bandwidth", "bandwidth", mapOf("rate" to 16))
        proxy.add("slicer", "slicer", mapOf("average_size" to 64, "size_variation" to 32, "delay" to 2_000))
    }

    @Test
    fun staysConnectedOnAPoorLink() = runBlocking<Unit> {
        makeLinkPoor()
        val start = now()
        repeat(9) {
            delay(5_000)
            if (!ping(timeoutMillis = 10_000)) fail("A ping went unanswered on the poor link")
        }
        assertStayedOnline("45 s on a poor link", start)
        assertMessagesFlow("poor link")
    }

    @Test
    fun reconnectsOnAPoorLink() = runBlocking<Unit> {
        makeLinkPoor()
        // The connection is cut (the link resets) three times, 15 s apart.
        repeat(3) { round ->
            proxy.enabled(false)
            proxy.enabled(true)
            awaitRecovered("poor link, connection cut #${round + 1}", now())
            delay(15_000)
        }
        assertMessagesFlow("poor link after cuts")
    }

    @Test
    fun signsInAgainOnAPoorLink() = runBlocking<Unit> {
        makeLinkPoor()
        // A full login (no stream resumption) on the poor link: disconnect and connect.
        alice.client.disconnect()
        val start = now()
        alice.connect()
        awaitRecovered("full login on a poor link", start)
        assertMessagesFlow("poor link after login")
    }

    @Test
    fun recoversAfterTheServerWasUnreachable() = runBlocking<Unit> {
        // The network is up but the server cannot be reached for 15 s (connections refused).
        proxy.enabled(false)
        delay(15_000)
        proxy.enabled(true)
        awaitRecovered("server unreachable for 15 s", now())
        assertMessagesFlow("after server outage")
    }

    @Test
    fun recoversAfterTheLinkStalls() = runBlocking<Unit> {
        // Nothing moves for 15 s (a dead zone where the radio still claims a signal), then it flows again.
        proxy.add("stall", "latency", mapOf("latency" to 15_000, "jitter" to 0))
        val sentDuringStall = (1..3).map { "stalled $it" }
        delay(5_000)
        sentDuringStall.forEach { alice.send(bobJid, it) }
        delay(10_000)
        proxy.remove("stall")
        val end = now()
        awaitRecovered("link stalled for 15 s", end)
        assertBobReceived("messages sent during the stall", sentDuringStall, end)
    }

    @Test
    fun recoversWhenTheLinkSilentlyDies() = runBlocking<Unit> {
        // Every byte is swallowed and the connection never closes: only the SDK's own checks can tell.
        proxy.add("blackhole", "timeout", mapOf("timeout" to 0))
        delay(15_000)
        proxy.remove("blackhole")
        val end = now()
        // Something to send makes the SDK check the connection at once.
        alice.send(bobJid, "after the black hole")
        awaitRecovered("link silently dead for 15 s, then a message is sent", end)
        assertBobReceived("message sent after the black hole", listOf("after the black hole"), end)
    }
}
