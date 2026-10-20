package io.github.usmandurrany.tether.demo

import android.content.Context
import android.util.Log
import io.github.usmandurrany.tether.android.SqliteMessageStore
import io.github.usmandurrany.tether.android.TetherAndroid
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ClientPolicy
import io.github.usmandurrany.tether.core.ConnectResult
import io.github.usmandurrany.tether.core.CredentialVault
import io.github.usmandurrany.tether.core.KeyValueStore
import io.github.usmandurrany.tether.core.NegotiationFailure
import io.github.usmandurrany.tether.core.ReconnectPolicy
import io.github.usmandurrany.tether.core.XmppClient
import io.github.usmandurrany.tether.core.XmppConfig
import io.github.usmandurrany.tether.core.XmppNegotiationException
import io.github.usmandurrany.tether.debug.DebugConfig
import io.github.usmandurrany.tether.debug.DeviceInfo
import io.github.usmandurrany.tether.debug.LogFormatter
import io.github.usmandurrany.tether.debug.LogLevel
import io.github.usmandurrany.tether.debug.LogSink
import io.github.usmandurrany.tether.debug.MemorySink
import io.github.usmandurrany.tether.debug.RemoteLogConfig
import io.github.usmandurrany.tether.debug.RemoteLogSink
import io.github.usmandurrany.tether.debug.TetherDebug
import io.github.usmandurrany.tether.debug.http
import io.github.usmandurrany.tether.im.ChatFeatures
import io.github.usmandurrany.tether.im.InstantMessaging
import io.github.usmandurrany.tether.im.MessageData
import io.github.usmandurrany.tether.im.MessageStore
import io.github.usmandurrany.tether.im.PushAppServer
import io.github.usmandurrany.tether.im.PushTarget
import io.github.usmandurrany.tether.im.SubscriptionEvent
import io.github.usmandurrany.tether.im.TetherChat
import io.github.usmandurrany.tether.sasl.SaslConfig
import io.github.usmandurrany.tether.transport.TlsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One signed-in account: everything the demo does with the SDK is in this file.
 *
 * An app without the demo's debug tools and settings screen only needs
 * `TetherAndroid.chat(context, jid, password)`. The demo builds the same thing
 * by hand so it can add traffic logging and apply its settings:
 * 1. Build an [XmppConfig] from the settings (password the first time, saved SCRAM keys after).
 * 2. Create the [XmppClient], wrap it in a [TetherChat] (every chat feature on) and bind it
 *    to the Android lifecycle and network.
 * 3. [connect]. From then on the SDK reconnects and resumes on its own until [signOut].
 *
 * The screens read [chat]'s flows (chat list, messages with ticks, typing, contacts) and
 * call `send`, `typing`, `openChat` and `closeChat`; read markers, history and
 * de-duplication are the SDK's job.
 */
class TetherSession private constructor(
    context: Context,
    val settings: ConnectionSettings,
    val account: BareJid,
    private val store: KeyValueStore,
    config: XmppConfig,
    parentScope: CoroutineScope,
    installId: String,
) {
    private val app = context.applicationContext

    /** Cancelled on sign-out, which stops everything this session started. */
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    // --- Debug logging (tether-debug) -------------------------------------------------

    /** The last 2000 log entries, for the Log tab. */
    val logs = MemorySink(capacity = 2_000)

    /** Uploads to a tether-debug log portal, when one is configured. */
    val remoteLogs: RemoteLogSink? = if (settings.remoteLoggingEnabled) {
        RemoteLogSink.http(
            RemoteLogConfig(
                endpoint = settings.logEndpoint,
                ingestKey = settings.logIngestKey,
                device = DeviceInfo.current(installId = installId, appId = app.packageName, appVersion = BuildInfo.versionName(app)),
                label = settings.logLabel.ifBlank { null },
            ),
            scope,
        )
    } else {
        null
    }

    val debug = TetherDebug(
        config = DebugConfig(redactBodies = settings.redactBodies),
        sinks = listOfNotNull(
            logs,
            LogcatSink,
            remoteLogs,
        ),
    )

    // --- The client and its features -------------------------------------------------

    val client: XmppClient = XmppClient(
        // TetherAndroid.negotiator: Android DNS (SRV) and TLS (Conscrypt).
        TetherAndroid.negotiator(app, config, logger = debug.trafficLogger()),
        scope,
        ClientPolicy(reconnect = ReconnectPolicy(backgroundMode = settings.backgroundMode)),
        store = store,
        credentials = CredentialVault(store),
    )

    /** Kept in the SDK's SQLite database, one per account, unless the settings say memory only. */
    private val messageStore: MessageStore? = if (settings.keepMessages) SqliteMessageStore(app, account) else null

    /**
     * Messages, read markers, typing, contacts, presence, carbons, history, push,
     * last seen, delete for everyone and profiles, on [client]. Auto-accept is the one
     * feature that is off unless the settings turn it on.
     */
    val chat = TetherChat(
        client,
        ChatFeatures(autoAcceptContacts = settings.autoAcceptContacts, profiles = settings.profiles),
        store = store,
        messageStore = messageStore,
    )

    /** The protocol-level features under [chat], for the Me tab's details. */
    val im: InstantMessaging get() = chat.im

    /**
     * Follows the app lifecycle (closing the connection five seconds after the app is
     * backgrounded, reconnecting on return) and the network. `TetherAndroid.client(...)`
     * does the same in one call; the demo keeps the handle so sign-out can stop it.
     */
    private val binding: AutoCloseable = TetherAndroid.bind(app, client, scope)

    /** Contacts asking to see our presence, waiting for approve or deny. */
    val subscriptionRequests: StateFlow<Set<BareJid>> get() = chat.contactRequests

    init {
        scope.launch {
            im.presences.subscriptions.collect { event ->
                when (event) {
                    is SubscriptionEvent.Requested -> debug.log(
                        LogLevel.INFO,
                        "app",
                        "${event.from} asked to see our presence" + if (settings.autoAcceptContacts) " (accepted automatically)" else "",
                    )
                    is SubscriptionEvent.Approved -> debug.log(LogLevel.INFO, "app", "${event.from} approved our request")
                    is SubscriptionEvent.Revoked -> debug.log(LogLevel.INFO, "app", "${event.from} denied or revoked our subscription")
                    is SubscriptionEvent.Unsubscribed -> debug.log(LogLevel.INFO, "app", "${event.from} stopped seeing our presence")
                }
            }
        }
        debug.trackConnection(client.state, scope)
    }

    suspend fun connect(): ConnectResult {
        debug.log(LogLevel.INFO, "app", "Connecting as $account")
        return chat.connect()
    }

    // --- Messaging -------------------------------------------------------------------

    /** Works offline too: the SDK keeps the message in its outbox and sends it on the next connection. */
    fun send(to: BareJid, body: String) {
        chat.send(to, body)
    }

    /**
     * Sends custom data (a photo, video, location or any other type) with a
     * caption. Without one, the body is a link other XMPP apps can open.
     */
    fun send(to: BareJid, caption: String, data: MessageData) {
        chat.send(to, caption.ifBlank { Attachments.fallbackText(data) }, data)
    }

    // --- Contacts and presence -------------------------------------------------------

    /** Adds the contact to the roster and asks to see their presence. */
    suspend fun addContact(jid: BareJid, name: String?) = chat.addContact(jid, name)

    suspend fun removeContact(jid: BareJid) = chat.removeContact(jid)

    /** Approving also asks for their presence, so both sides see each other. */
    suspend fun answerRequest(jid: BareJid, approve: Boolean) {
        if (approve) chat.approve(jid) else chat.deny(jid)
    }

    // --- Push (XEP-0357) -------------------------------------------------------------

    suspend fun registerPush(target: PushTarget) = chat.registerPush(target)

    /**
     * Gets a node for this device from a push app server that speaks p2's
     * registration command, such as tether-push, and turns push on with it.
     */
    suspend fun registerPush(server: String, fcmToken: String, installId: String): PushTarget {
        val target = chat.registerPush(PushAppServer.p2(server, fcmToken, installId))
        debug.log(LogLevel.INFO, "app", "Push registered with ${target.service}")
        return target
    }

    /** Call when an FCM message arrives: connects, also in the background, so waiting messages come in. */
    suspend fun wakeForPush() {
        debug.log(LogLevel.INFO, "app", "Push received; connecting to fetch messages")
        chat.wakeForPush()
    }

    /** Shows a notification for each message that arrives while the app is in the background. */
    private val notifier = MessageNotifier(app, this, scope)

    suspend fun unregisterPush() = chat.unregisterPush()

    // --- Ending ----------------------------------------------------------------------

    /**
     * Disconnects and stops this session. With [forget], also deletes the saved
     * login keys and state, so the next sign-in needs the password again.
     */
    suspend fun signOut(forget: Boolean) {
        debug.log(LogLevel.INFO, "app", if (forget) "Signing out" else "Disconnecting")
        if (forget) runCatching { chat.unregisterPush() }
        runCatching { client.disconnect() }
        remoteLogs?.flush()
        binding.close()
        chat.close()
        remoteLogs?.close()
        if (forget) {
            CredentialVault(store).clear(account)
            // Signing out also forgets this account's chats on the device.
            runCatching { (messageStore ?: SqliteMessageStore(app, account)).clear() }
        }
        scope.cancel()
    }

    companion object {
        /**
         * Builds a session from the settings. With a [password], logs in with it;
         * without one, uses the SCRAM keys saved by an earlier login.
         * Throws [IllegalArgumentException] with a readable message for bad settings.
         */
        suspend fun create(
            context: Context,
            settings: ConnectionSettings,
            password: String?,
            store: KeyValueStore,
            installId: String,
            scope: CoroutineScope,
        ): TetherSession {
            val account = runCatching { BareJid.parse(settings.jid.trim()) }.getOrNull()?.takeIf { it.local != null }
                ?: throw IllegalArgumentException("Enter an address like alice@example.com")
            val keys = if (password.isNullOrEmpty()) CredentialVault(store).loadKeys(account) else emptyList()
            require(!password.isNullOrEmpty() || keys.isNotEmpty()) { "Enter the password (no saved login for $account)" }
            val port = settings.port.trim().takeIf { it.isNotEmpty() }?.let {
                requireNotNull(it.toIntOrNull()?.takeIf { p -> p in 1..65535 }) { "Port must be a number from 1 to 65535" }
            }
            val host = settings.host.trim().ifEmpty { null }
            require(port == null || host != null) { "Set a host to use a custom port" }
            val config = XmppConfig(
                jid = account,
                password = password?.ifEmpty { null },
                scramKeys = keys,
                resource = settings.resource.trim().ifEmpty { null },
                host = host,
                port = port,
                directTls = settings.directTls && host != null,
                tls = TlsConfig(pinnedCertificateSha256 = settings.pinnedCertificates),
                sasl = SaslConfig(allowPlain = settings.allowPlain, allowScramSha1 = settings.allowScramSha1),
            )
            return TetherSession(context, settings, account, store, config, scope, installId)
        }

        /** True when an earlier login saved keys for [jid], so no password is needed. */
        suspend fun hasSavedLogin(store: KeyValueStore, jid: String): Boolean {
            val account = runCatching { BareJid.parse(jid.trim()) }.getOrNull() ?: return false
            if (account.local == null) return false
            return CredentialVault(store).loadKeys(account).isNotEmpty()
        }

        /** A short, human message for a connection error. */
        fun describe(error: Throwable): String = when (error) {
            is XmppNegotiationException -> when (error.failure) {
                NegotiationFailure.UNREACHABLE -> "Can't reach the server. Check the host, port and network."
                NegotiationFailure.CONNECTION_LOST -> "The connection dropped: ${error.message}"
                NegotiationFailure.TLS_UNAVAILABLE -> "The server doesn't offer TLS, so Tether won't connect."
                NegotiationFailure.TLS_VERIFICATION -> "The server's certificate isn't trusted."
                NegotiationFailure.AUTHENTICATION -> "Login failed: wrong address or password, or no allowed login method."
                NegotiationFailure.STREAM_ERROR -> "The server ended the stream (${error.streamError ?: "unknown"})."
                NegotiationFailure.PROTOCOL -> "The server answered unexpectedly: ${error.message}"
            }
            else -> error.message ?: error.toString()
        }
    }
}

/** Mirrors the SDK log to Logcat (tag "Tether"). */
private object LogcatSink : LogSink {
    private val formatter = LogFormatter()
    override fun write(entry: io.github.usmandurrany.tether.debug.LogEntry) {
        val line = formatter.format(entry)
        when (entry.level) {
            LogLevel.ERROR -> Log.e("Tether", line)
            LogLevel.WARN -> Log.w("Tether", line)
            LogLevel.INFO -> Log.i("Tether", line)
            else -> Log.d("Tether", line)
        }
    }
}

internal object BuildInfo {
    fun versionName(context: Context): String? =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
}
