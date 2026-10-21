package io.github.usmandurrany.tether.demo.stability

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Opening and closing the app in quick succession. The demo uses
 * `BackgroundMode.CLOSE`: the SDK keeps the connection for 5 s after the app
 * leaves the screen (plus the ~0.7 s Android's process lifecycle waits), then
 * closes it, and reconnects when the app comes back.
 */
@RunWith(AndroidJUnit4::class)
class AppOpenCloseTest : StabilityTest() {
    private val random = Random(20261021)

    private suspend fun background() {
        AppSwitcher.home()
        withTimeoutOrNull(5_000) { alice.client.foreground.first { !it } }
    }

    private suspend fun foreground(): Long {
        AppSwitcher.open()
        withTimeoutOrNull(5_000) { alice.client.foreground.first { it } } ?: fail("The app did not come back to the foreground")
        return now()
    }

    @Test
    fun quickTripsAwayKeepTheConnection() = runBlocking<Unit> {
        val start = now()
        // 15 trips to the home screen of 0.3 to 3 s: shorter than the close delay, so nothing should drop.
        repeat(15) {
            AppSwitcher.home()
            delay(random.nextLong(300, 3_000))
            AppSwitcher.open()
            delay(random.nextLong(300, 1_500))
        }
        assertStayedOnline("15 quick trips away", start)
        assertMessagesFlow("after quick trips")
    }

    @Test
    fun reconnectsAfterLongerTripsAway() = runBlocking<Unit> {
        repeat(5) { round ->
            background()
            delay(7_000) // long enough for the SDK to close the connection
            val missed = "while away $round"
            bob.send(aliceJid, missed)
            val back = foreground()
            awaitRecovered("back after 7 s away #${round + 1}", back)
            withTimeoutOrNull(limit) {
                alice.chat.allMessages.first { all -> all[bobJid].orEmpty().any { it.body == missed } }
            } ?: fail("The message sent while alice was away did not arrive")
        }
    }

    @Test
    fun reconnectsWhenReturningAroundTheCloseDelay() = runBlocking<Unit> {
        // Back just before, at, or just after the connection is closed: the racy window.
        repeat(10) { round ->
            background()
            delay(random.nextLong(4_000, 7_000))
            val back = foreground()
            awaitRecovered("back around the close delay #${round + 1}", back)
            delay(random.nextLong(200, 1_500))
        }
        assertMessagesFlow("after returns around the close delay")
    }

    @Test
    fun reconnectsAfterRapidOpenAndClose() = runBlocking<Unit> {
        // 30 open/close cycles of 0.1 to 0.6 s, some ending with the task removed (swiped from recents).
        repeat(30) { round ->
            if (round % 5 == 4) AppSwitcher.closeTask() else AppSwitcher.home()
            delay(random.nextLong(100, 600))
            AppSwitcher.open()
            delay(random.nextLong(100, 600))
        }
        val back = foreground()
        awaitRecovered("after 30 rapid open/close cycles", back)
        assertMessagesFlow("after rapid cycles")
    }

    @Test
    fun reconnectsAfterTheTaskIsRemoved() = runBlocking<Unit> {
        repeat(6) { round ->
            AppSwitcher.closeTask()
            delay(if (round % 2 == 0) 1_000 else 7_000)
            val back = foreground()
            awaitRecovered("reopened after swiping away #${round + 1}", back)
        }
        assertMessagesFlow("after reopening")
    }
}
