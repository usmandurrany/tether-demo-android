package io.github.usmandurrany.tether.demo.stability

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The network looks fine but the app's traffic goes nowhere, as in a dead
 * zone where the radio still claims a signal, or when the server is down.
 * Android's per-app firewall ([AppFirewall], Android 14 and newer) drops the
 * app's packets while Wi-Fi stays up, so the OS reports nothing and the SDK
 * has to notice on its own.
 *
 * Bob runs in the same app, so he is cut off too; he is only waited for.
 */
@RunWith(AndroidJUnit4::class)
class AppCutOffTest : StabilityTest() {
    @Before
    fun needsTheAppFirewall() = assumeTrue("Needs Android 14 or newer (cmd connectivity set-package-networking-enabled)", AppFirewall.supported)

    @Test
    fun recoversAfterShortCuts() = runBlocking<Unit> {
        repeat(5) { round ->
            AppFirewall.cut(true)
            delay(3_000)
            AppFirewall.cut(false)
            awaitRecovered("cut off for 3 s #${round + 1}", now())
            delay(5_000)
        }
        assertMessagesFlow("after short cuts")
    }

    @Test
    fun recoversAfterTheServerWasUnreachable() = runBlocking<Unit> {
        AppFirewall.cut(true)
        delay(15_000)
        AppFirewall.cut(false)
        awaitRecovered("cut off for 15 s", now())
        assertMessagesFlow("after 15 s cut off")
    }

    @Test
    fun messagesSentWhileCutOffArrive() = runBlocking<Unit> {
        AppFirewall.cut(true)
        delay(5_000)
        val sent = (1..3).map { "while cut off $it" }
        sent.forEach { alice.send(bobJid, it) }
        delay(10_000)
        AppFirewall.cut(false)
        val end = now()
        awaitRecovered("cut off for 15 s with messages waiting", end)
        assertBobReceived("messages sent while cut off", sent, end)
    }

    @Test
    fun recoversWhenSomethingIsSentRightAfter() = runBlocking<Unit> {
        AppFirewall.cut(true)
        delay(15_000)
        AppFirewall.cut(false)
        val end = now()
        // Something to send makes the SDK check the connection at once.
        alice.send(bobJid, "right after the cut")
        awaitRecovered("cut off for 15 s, then a message is sent", end)
        assertBobReceived("message sent right after the cut", listOf("right after the cut"), end)
    }

    @Test
    fun recoversAfterALongCut() = runBlocking<Unit> {
        // Long enough for the SDK to give up on the old connection and back off between attempts.
        AppFirewall.cut(true)
        delay(60_000)
        AppFirewall.cut(false)
        awaitRecovered("cut off for 60 s", now())
        assertMessagesFlow("after 60 s cut off")
    }
}
