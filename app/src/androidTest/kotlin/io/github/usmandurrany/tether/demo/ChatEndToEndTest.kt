package io.github.usmandurrany.tether.demo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ConnectResult
import io.github.usmandurrany.tether.core.MemoryKeyValueStore
import io.github.usmandurrany.tether.im.MessageData
import io.github.usmandurrany.tether.im.data
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Signs in through the settings page, trusts the server's self-signed
 * certificate, and chats with a second account, against a real server:
 * ticks, custom data (locations and an app-defined type), deleting for
 * everyone both ways, and last seen.
 *
 * Needs two accounts with the same password on a test server, for example:
 * ```
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.xmppHost=10.0.2.2 \
 *   -Pandroid.testInstrumentationRunnerArguments.xmppDomain=localhost \
 *   -Pandroid.testInstrumentationRunnerArguments.xmppPassword=secret
 * ```
 * Accounts `alice@<domain>` and `bob@<domain>` must exist. Skipped without these arguments.
 */
@RunWith(AndroidJUnit4::class)
class ChatEndToEndTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val args = InstrumentationRegistry.getArguments()

    @Test
    fun signInTrustTheCertificateAndChat() {
        val host = args.getString("xmppHost")
        val domain = args.getString("xmppDomain")
        assumeTrue("No test server given", host != null && domain != null)
        val password = args.getString("xmppPassword") ?: "secret"
        val alice = "alice@$domain"
        val bob = "bob@$domain"

        // Sign in as alice through the settings page.
        compose.onNodeWithText("Address (JID)").performTextInput(alice)
        compose.onNodeWithText("Password").performTextInput(password)
        compose.onNodeWithText("Host (optional)").performTextInput(host!!)
        compose.onNodeWithText("Connect").performScrollTo().performClick()

        waitFor { shows("Trust and connect") || shows("Online") }
        if (shows("Trust and connect")) compose.onNodeWithText("Trust and connect").performClick()
        waitFor { shows("Online") }

        // Bob signs in with the same SDK, outside the UI.
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DemoApp
        val pins = app.settingsStore.load().pinnedCertificates
        val bobSession = runBlocking {
            TetherSession.create(
                app,
                ConnectionSettings(jid = bob, host = host, pinnedCertificates = pins),
                password,
                MemoryKeyValueStore(),
                installId = "test",
                scope = app.scope,
            )
        }
        assertTrue(runBlocking { bobSession.connect() } is ConnectResult.Connected)
        var bobSignedIn = true

        try {
            // Alice writes to bob.
            compose.onNodeWithText("Chat with").performTextInput(bob)
            compose.onNodeWithText("Open").performClick()
            compose.onNodeWithTag("message-input").performTextInput("Hello from the test")
            compose.onNodeWithText("Send").performClick()

            val aliceJid = BareJid.parse(alice)
            waitFor {
                bobSession.chat.allMessages.value[aliceJid].orEmpty().any { it.body == "Hello from the test" }
            }
            // Bob's device confirmed it (XEP-0184): two ticks.
            waitFor { shows("✓✓", substring = true) }
            // Bob opens the chat: the SDK sends a read marker (XEP-0333) and alice sees "read".
            bobSession.chat.openChat(aliceJid)
            waitFor { shows("✓✓ read", substring = true) }

            // Bob answers; alice sees it.
            runBlocking { bobSession.send(aliceJid, "Hi alice") }
            waitFor { inChat("Hi alice") }

            // Custom data: a location and an app-defined type arrive as their own tags and are drawn.
            bobSession.send(aliceJid, "", Attachments.location(51.50135, -0.14189, "Buckingham Palace"))
            waitFor { inChat("Location: Buckingham Palace") }
            bobSession.send(aliceJid, "My card", MessageData("contact-card", "name" to "Ada Lovelace"))
            waitFor { inChat("Custom data: contact-card") && inChat("name = Ada Lovelace") }

            // Alice shares a location from the chat's + menu; bob reads it as data.
            compose.onNodeWithTag("attach").performClick()
            compose.onNodeWithText("Location").performClick()
            compose.onNodeWithTag("attach-send").performClick()
            waitFor {
                // Incoming only: bob's own location from above has the same label.
                bobSession.chat.allMessages.value[aliceJid].orEmpty().any { !it.outgoing && it.data(Attachments.LOCATION)?.string("label") == "Buckingham Palace" }
            }

            // Bob deletes a message for everyone: alice sees the placeholder.
            val oops = bobSession.chat.send(aliceJid, "oops, wrong chat")
            waitFor { inChat("oops, wrong chat") }
            bobSession.chat.deleteForEveryone(oops)
            waitFor { inChat("This message was deleted") && !shows("oops, wrong chat") }

            // Alice deletes a message for everyone with a long press: gone on bob's side too.
            compose.onNodeWithTag("message-input").performTextInput("Delete me")
            compose.onNodeWithText("Send").performClick()
            waitFor { bobSession.chat.allMessages.value[aliceJid].orEmpty().any { it.body == "Delete me" } }
            waitFor { inChat("Delete me") }
            compose.onNodeWithText("Delete me", substring = true).performTouchInput { longClick() }
            compose.onNodeWithTag("delete-everyone").performClick()
            waitFor {
                bobSession.chat.allMessages.value[aliceJid].orEmpty().let { list -> list.any { it.outgoing.not() && it.deleted } && list.none { it.body == "Delete me" } }
            }

            // Bob goes offline: alice sees when he was last seen.
            runBlocking { bobSession.signOut(forget = true) }
            bobSignedIn = false
            waitFor { shows("last seen", substring = true) }
        } finally {
            if (bobSignedIn) runBlocking { bobSession.signOut(forget = true) }
        }
    }

    /** Like waitUntil, but a timeout says what alice's app and SDK held at the time. */
    private fun waitFor(condition: () -> Boolean) {
        try {
            compose.waitUntil(TIMEOUT, condition)
        } catch (e: ComposeTimeoutException) {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DemoApp
            val session = app.session.value
            val screen = compose.onAllNodes(isRoot()).fetchSemanticsNodes().flatMap { root ->
                generateSequence(listOf(root)) { level -> level.flatMap { it.children }.ifEmpty { null } }.flatten()
                    .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" | ") }
            }
            throw AssertionError(
                "Timed out. Alice is ${session?.client?.state?.value}. Her chats: " +
                    session?.chat?.allMessages?.value?.mapValues { (_, list) -> list.map { "${if (it.outgoing) ">" else "<"} ${it.body.take(40)} ${it.status}${if (it.deleted) " deleted" else ""}" } } +
                    "\nOn screen: $screen",
                e,
            )
        }
    }

    /** On the chat screen, scrolling the message list to it if needed. */
    private fun inChat(text: String): Boolean =
        shows(text) || runCatching { compose.onNodeWithTag("messages").performScrollToNode(hasText(text)) }.isSuccess && shows(text)

    private fun shows(text: String, substring: Boolean = false): Boolean =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val TIMEOUT = 30_000L
    }
}
