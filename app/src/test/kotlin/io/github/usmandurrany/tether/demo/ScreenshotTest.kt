package io.github.usmandurrany.tether.demo

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.core.FullJid
import io.github.usmandurrany.tether.debug.Direction
import io.github.usmandurrany.tether.debug.LogEntry
import io.github.usmandurrany.tether.debug.LogLevel
import io.github.usmandurrany.tether.debug.RemoteLogStats
import io.github.usmandurrany.tether.demo.ui.chat.ChatActions
import io.github.usmandurrany.tether.demo.ui.chat.ChatOverlay
import io.github.usmandurrany.tether.demo.ui.chat.ChatScreen
import io.github.usmandurrany.tether.demo.ui.chat.ChatUiState
import io.github.usmandurrany.tether.demo.ui.chats.ChatsScreen
import io.github.usmandurrany.tether.demo.ui.chats.ChatsUiState
import io.github.usmandurrany.tether.demo.ui.contacts.ContactsScreen
import io.github.usmandurrany.tether.demo.ui.contacts.ContactsUiState
import io.github.usmandurrany.tether.demo.ui.home.HomeScaffold
import io.github.usmandurrany.tether.demo.ui.home.HomeTab
import io.github.usmandurrany.tether.demo.ui.home.HomeUiState
import io.github.usmandurrany.tether.demo.ui.log.LogScreen
import io.github.usmandurrany.tether.demo.ui.log.LogUiState
import io.github.usmandurrany.tether.demo.ui.me.MeActions
import io.github.usmandurrany.tether.demo.ui.me.MeForm
import io.github.usmandurrany.tether.demo.ui.me.MeScreen
import io.github.usmandurrany.tether.demo.ui.me.MeUiState
import io.github.usmandurrany.tether.demo.ui.me.PresenceChoice
import io.github.usmandurrany.tether.demo.ui.signin.SettingsActions
import io.github.usmandurrany.tether.demo.ui.signin.SettingsScreen
import io.github.usmandurrany.tether.demo.ui.signin.SettingsUiState
import io.github.usmandurrany.tether.demo.ui.theme.TetherTheme
import io.github.usmandurrany.tether.im.Contact
import io.github.usmandurrany.tether.im.Conversation
import io.github.usmandurrany.tether.im.Message
import io.github.usmandurrany.tether.im.MessageData
import io.github.usmandurrany.tether.im.MessageStatus
import io.github.usmandurrany.tether.im.PushState
import io.github.usmandurrany.tether.im.PushTarget
import io.github.usmandurrany.tether.im.Show
import io.github.usmandurrany.tether.im.Subscription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen with sample data on the JVM (Robolectric) and saves a
 * PNG of each to app/build/screenshots, for reviewing the design without a
 * device. Run with `./gradlew testDebugUnitTest`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the screens take their state as parameters, so no SDK starts.
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TetherTheme(dark = dark, content = content) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    /** For dialogs and bottom sheets, which live in their own windows. */
    private fun shootScreen(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TetherTheme(dark = dark, content = content) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/$name.png")
    }

    @Test fun signIn() = shoot("01-sign-in") {
        SettingsScreen(SettingsUiState(), canGoBack = false, actions = SettingsActions())
    }

    @Test
    @Config(qualifiers = "w390dp-h2300dp-xxhdpi")
    fun settingsAllOpen() = shoot("02-settings-all-open") {
        SettingsScreen(filledSettings, canGoBack = true, actions = SettingsActions(), initiallyExpanded = true)
    }

    @Test fun trustDialog() = shootScreen("03-trust-certificate") {
        SettingsScreen(
            filledSettings.copy(
                busy = false,
                untrusted = SignInResult.UntrustedCertificate(
                    sha256 = "3f2a9c41d07be8125f6a0c93e4b7d2a1c8f05e6b9a3d7c2e1f4b8a0d6c9e3f7a",
                    message = "The server's certificate is not trusted by this device (self-signed).",
                ),
            ),
            canGoBack = true,
            actions = SettingsActions(),
        )
    }

    @Test fun chats() = shoot("04-chats") { Home(HomeTab.CHATS) { ChatsScreen(ChatsUiState(conversations), onOpenChat = {}) } }

    @Test fun chat() = shoot("05-chat") { ChatScreen(chatState, ChatActions()) }

    @Test fun attachMenu() = shootScreen("06-chat-attach") { ChatScreen(chatState, ChatActions(), initialOverlay = ChatOverlay.AttachMenu) }

    @Test fun attachLocation() = shootScreen("07-chat-attach-location") {
        ChatScreen(chatState, ChatActions(), initialOverlay = ChatOverlay.Attaching(Attachments.LOCATION))
    }

    @Test fun messageActions() = shootScreen("08-chat-message-actions") {
        ChatScreen(chatState, ChatActions(), initialOverlay = ChatOverlay.Acting(chatState.messages[3]))
    }

    @Test fun contacts() = shoot("09-contacts") { Home(HomeTab.CONTACTS) { ContactsScreen(contactsState, onOpenChat = {}) } }

    @Test
    @Config(qualifiers = "w390dp-h2600dp-xxhdpi")
    fun me() = shoot("10-me") { Home(HomeTab.ME) { MeScreen(meState, MeActions(), byHandInitiallyOpen = true) } }

    @Test fun log() = shoot("11-log") { Home(HomeTab.LOG) { LogScreen(logState) } }

    @Test fun chatsDark() = shoot("12-chats-dark", dark = true) {
        Home(HomeTab.CHATS) { ChatsScreen(ChatsUiState(conversations), onOpenChat = {}) }
    }

    @Test fun chatDark() = shoot("13-chat-dark", dark = true) { ChatScreen(chatState, ChatActions()) }

    @Test
    @Config(qualifiers = "w1000dp-h700dp-xhdpi")
    fun tablet() = shoot("14-tablet-two-pane") {
        Home(HomeTab.CHATS) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(360.dp).fillMaxHeight()) { ChatsScreen(ChatsUiState(conversations), onOpenChat = {}) }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) { ChatScreen(chatState, ChatActions(), showBack = false) }
            }
        }
    }

    @Composable
    private fun Home(tab: HomeTab, content: @Composable () -> Unit) {
        HomeScaffold(homeState, tab, onTabChange = {}, onRetry = {}, onSettings = {}) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
        }
    }

    private companion object {
        val now = System.currentTimeMillis()
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR

        val me = BareJid.parse("alice@example.com")
        val bob = BareJid.parse("bob@example.com")
        val carol = BareJid.parse("carol@example.com")
        val dave = BareJid.parse("dave@chat.example.org")
        val erin = BareJid.parse("erin@example.com")

        val homeState = HomeUiState(
            account = me.toString(),
            connection = ConnectionState.Online(FullJid.parse("alice@example.com/phone")),
            myName = "Alice",
            unreadTotal = 5,
            requests = 1,
        )

        val filledSettings = SettingsUiState(
            settings = ConnectionSettings(
                jid = "alice@example.com",
                resource = "phone",
                host = "xmpp.example.com",
                port = "5223",
                directTls = true,
                autoConnect = true,
                logEndpoint = "https://logs.example.com",
                logLabel = "pixel-8",
            ),
            pinsText = "3f2a9c41d07be8125f6a0c93e4b7d2a1c8f05e6b9a3d7c2e1f4b8a0d6c9e3f7a",
            password = "correct horse",
            savedLogin = true,
        )

        fun msg(id: String, outgoing: Boolean, body: String, ago: Long, status: MessageStatus, vararg data: MessageData) = Message(
            id = id,
            peer = bob,
            outgoing = outgoing,
            body = body,
            timestamp = now - ago,
            status = status,
            payloads = data.map { it.toElement() },
        )

        val chatState = ChatUiState(
            peer = bob,
            name = "Bob Martin",
            online = true,
            show = Show.AWAY,
            status = "At lunch",
            historyAvailable = true,
            messages = listOf(
                msg("1", false, "Did you get the build working?", DAY + 3 * HOUR, MessageStatus.READ),
                msg("2", true, "Yes, the emulator test passes now 🎉", DAY + 2 * HOUR, MessageStatus.READ),
                msg("3", false, "Here's where we're meeting", 40 * MINUTE, MessageStatus.READ,
                    MessageData(Attachments.LOCATION, "lat" to 51.5072, "lon" to -0.1276, "label" to "Trafalgar Square")),
                msg("4", true, "Perfect, see you there at 6", 35 * MINUTE, MessageStatus.READ),
                msg("5", true, "The demo from yesterday", 20 * MINUTE, MessageStatus.DELIVERED,
                    MessageData(Attachments.VIDEO, "url" to "https://example.com/demo.mp4", "duration" to 94, "size" to 4_800_000)),
                msg("6", false, "", 12 * MINUTE, MessageStatus.READ,
                    MessageData("poll", "question" to "Pizza or tacos?", "closes" to "18:00")),
                msg("7", true, "Tacos, obviously", 2 * MINUTE, MessageStatus.SENT),
                msg("8", true, "Leaving now", 0, MessageStatus.SENDING),
            ),
        )

        val conversations = listOf(
            Conversation(bob, "Bob Martin", chatState.messages.last(), unreadCount = 0, typing = false, online = true),
            Conversation(carol, "Carol", Message("c", carol, false, "Can you review my PR when you get a minute?", now - 25 * MINUTE, MessageStatus.UNREAD), unreadCount = 3, typing = false, online = true),
            Conversation(dave, "Dave", Message("d", dave, false, "", now - 3 * HOUR, MessageStatus.UNREAD, payloads = listOf(MessageData(Attachments.PHOTO, "url" to "https://example.com/p.jpg").toElement())), unreadCount = 2, typing = true, online = true),
            Conversation(erin, "Erin", Message("e", erin, true, "Thanks, talk tomorrow!", now - DAY - HOUR, MessageStatus.READ), unreadCount = 0, typing = false, online = false, lastSeen = now - DAY),
            Conversation(BareJid.parse("frank@example.net"), "frank", Message("f", BareJid.parse("frank@example.net"), true, "ok", now - 5 * DAY, MessageStatus.FAILED, error = "service-unavailable"), unreadCount = 0, typing = false, online = false),
        )

        fun contact(jid: BareJid, name: String, online: Boolean, show: Show? = null, status: String? = null, pending: Boolean = false, lastSeen: Long? = null) =
            Contact(jid, name, online, show, status, if (pending) Subscription.NONE else Subscription.BOTH, pending, emptySet(), lastSeen)

        val contactsState = ContactsUiState(
            contacts = listOf(
                contact(bob, "Bob Martin", true, Show.AWAY, "At lunch"),
                contact(carol, "Carol", true, null, "Reviewing PRs"),
                contact(dave, "Dave", true, Show.DND, "Focus time"),
                contact(erin, "Erin", false, lastSeen = now - DAY),
                contact(BareJid.parse("frank@example.net"), "frank", false, pending = true),
            ),
            requests = listOf(BareJid.parse("grace@example.org")),
        )

        val meState = MeUiState(
            account = "alice@example.com/phone",
            connection = ConnectionState.Online(FullJid.parse("alice@example.com/phone")),
            form = MeForm(
                fullName = "Alice Smith",
                nickname = "Alice",
                about = "Building chat apps with Tether",
                presence = PresenceChoice.AVAILABLE,
                status = "Around",
                pushServer = "https://push.example.com",
                pushService = "push.example.com",
                pushNode = "node-123",
            ),
            streamManagement = true,
            resumed = true,
            carbons = true,
            archive = true,
            push = PushState.On(PushTarget("push.example.com", "node-123", emptyMap(), false)),
            firebase = true,
        )

        val logState = LogUiState(
            entries = listOf(
                LogEntry(1, now - 9_000, LogLevel.INFO, "connection", null, "Connecting to example.com (SRV)"),
                LogEntry(2, now - 8_500, LogLevel.DEBUG, LogEntry.STANZA_TAG, null, "<stream:features><starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>", Direction.RECEIVED),
                LogEntry(3, now - 8_000, LogLevel.DEBUG, LogEntry.STANZA_TAG, null, "<auth mechanism='SCRAM-SHA-256'>[redacted]</auth>", Direction.SENT),
                LogEntry(4, now - 7_000, LogLevel.INFO, "connection", "alice@example.com/phone", "Online as alice@example.com/phone"),
                LogEntry(5, now - 6_000, LogLevel.DEBUG, LogEntry.STANZA_TAG, "alice@example.com/phone", "<iq type='get' id='r1'><query xmlns='jabber:iq:roster'/></iq>", Direction.SENT),
                LogEntry(6, now - 5_000, LogLevel.DEBUG, LogEntry.STANZA_TAG, "alice@example.com/phone", "<message to='bob@example.com' id='m7' type='chat'><body>Tacos, obviously</body><request xmlns='urn:xmpp:receipts'/></message>", Direction.SENT),
                LogEntry(7, now - 4_000, LogLevel.DEBUG, LogEntry.STANZA_TAG, "alice@example.com/phone", "<message from='bob@example.com/laptop' id='r9'><received xmlns='urn:xmpp:receipts' id='m7'/></message>", Direction.RECEIVED),
                LogEntry(8, now - 2_000, LogLevel.ERROR, "push", "alice@example.com/phone", "Push registration failed", error = "item-not-found"),
            ),
            portal = "https://logs.example.com",
            portalStats = RemoteLogStats(queued = 2, uploaded = 148, dropped = 0),
        )
    }
}
