package io.github.usmandurrany.tether.demo.stability

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.github.usmandurrany.tether.android.TetherAndroid
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ConnectResult
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.core.MemoryKeyValueStore
import io.github.usmandurrany.tether.core.XmppClient
import io.github.usmandurrany.tether.core.XmppConfig
import io.github.usmandurrany.tether.demo.ConnectionSettings
import io.github.usmandurrany.tether.demo.DemoApp
import io.github.usmandurrany.tether.demo.SignInResult
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.im.ChatFeatures
import io.github.usmandurrany.tether.im.TetherChat
import io.github.usmandurrany.tether.transport.TlsConfig
import io.github.usmandurrany.tether.xml.element
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.util.UUID

/**
 * Shared setup for the connection stability tests.
 *
 * Alice is the demo app's own session: signed in through [DemoApp.signIn] like
 * the settings page does, and bound to the real app lifecycle and network
 * monitor. Bob is a plain SDK client in the same process, used to check that
 * messages really get through after each disruption.
 *
 * Arguments (instrumentation runner):
 * - `xmppHost`, `xmppDomain`: the server, as the device reaches it, and its
 *   XMPP domain. Without them the tests are skipped.
 * - `aliceJid`, `alicePassword`, `bobJid`, `bobPassword`: the two accounts
 *   (default `alice@domain` and `bob@domain`, both with `xmppPassword`, default
 *   "secret"). Alice is the app's account; the tests sign the app in as her.
 *   These can also come from `credentialsFile` (see [TestArgs]), which keeps
 *   the passwords out of logcat.
 * - `xmppPort` (5222): the server's client port.
 * - `reconnectLimitMs` (10000): how soon the connection must work again.
 * - `mobileData` (true): false when the server is only reachable over Wi-Fi
 *   (a test server on the local network, with a real phone): mobile data is
 *   kept off and the tests that need it are skipped.
 *
 * Recovery is measured from the moment the disruption ends (for a network,
 * when Android reports it back) to the first moment the SDK is online and a
 * ping sent then is answered, so a "zombie" connection that only looks online
 * does not count as recovered.
 */
abstract class StabilityTest {
    protected val args = InstrumentationRegistry.getArguments()
    protected val app: DemoApp get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DemoApp

    protected val limit: Long = args.getString("reconnectLimitMs")?.toLongOrNull() ?: 10_000
    protected val mobileData: Boolean = args.getString("mobileData") != "false"
    protected val report = Report(javaClass.simpleName)
    protected val timeline = Timeline()

    protected lateinit var network: DeviceNetwork
    protected lateinit var alice: TetherSession
    protected lateinit var bob: TetherChat
    protected lateinit var aliceJid: BareJid
    protected lateinit var bobJid: BareJid

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var bobScope: CoroutineScope? = null

    @Before
    fun setUp() = runBlocking<Unit> {
        val host = args.getString("xmppHost")
        val domain = args.getString("xmppDomain")
        assumeTrue("No test server given", host != null && domain != null)
        val directPort = args.getString("xmppPort")?.toIntOrNull() ?: 5222
        aliceJid = BareJid.parse(TestArgs.get("aliceJid") ?: "alice@$domain")
        bobJid = BareJid.parse(TestArgs.get("bobJid") ?: "bob@$domain")
        val alicePassword = TestArgs.get("alicePassword") ?: TestArgs.get("xmppPassword") ?: "secret"
        val bobPassword = TestArgs.get("bobPassword") ?: TestArgs.get("xmppPassword") ?: "secret"

        network = DeviceNetwork(InstrumentationRegistry.getInstrumentation().targetContext, mobileData)
        network.restore()
        scope.launch { network.default.collect { timeline.add("os", it?.toString() ?: "no network") } }

        AppFirewall.restore()

        // The app in the foreground, as a user would have it.
        AppSwitcher.open()

        // Alice signs in as the settings page does, trusting the test server's self-signed certificate.
        var settings = ConnectionSettings(jid = aliceJid.toString(), host = host!!, port = directPort.toString(), pinnedCertificates = pins)
        var result = app.signIn(settings, alicePassword)
        if (result is SignInResult.UntrustedCertificate) {
            pins = setOf(result.sha256)
            settings = settings.copy(pinnedCertificates = pins)
            result = app.signIn(settings, alicePassword)
        }
        check(result is SignInResult.Connected) { "Alice could not sign in: $result" }
        alice = app.session.value!!
        scope.launch { alice.client.state.collect { timeline.add("sdk", it.toString()) } }
        scope.launch { alice.client.foreground.collect { timeline.add("app", if (it) "foreground" else "background") } }

        bob = bareClient(bobJid, host!!, directPort, bobPassword)
        // Bob shares the device's network, so he hears about it like a real app would.
        scope.launch { network.default.collect { bob.client.networkChanged(it != null, it?.handle?.toString(), it?.toString()) } }
        bobReady("setup")
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        report.print()
        runCatching { AppFirewall.restore() }
        if (::network.isInitialized) {
            network.restore()
            network.close()
        }
        if (::bob.isInitialized) runCatching { bob.close() }
        bobScope?.cancel()
        app.signOut(forget = false)
        scope.cancel()
    }

    /**
     * A client with no lifecycle or network binding (it never pauses), so it
     * can stand in for "the other side" while the app's own connection is
     * being disrupted.
     */
    private fun bareClient(jid: BareJid, host: String, port: Int, password: String): TetherChat {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = XmppConfig(jid = jid, password = password, host = host, port = port, tls = TlsConfig(pinnedCertificateSha256 = pins))
        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { bobScope = it }
        val client = XmppClient(TetherAndroid.negotiator(context, config), clientScope, store = MemoryKeyValueStore())
        return TetherChat(client, ChatFeatures(profiles = false), store = MemoryKeyValueStore())
    }

    // --- Checks ----------------------------------------------------------------------

    /**
     * Waits until alice's connection works again: the SDK reports online and a
     * ping sent at that moment is answered. Returns the time from [from], and
     * records it. Fails at once on a terminal state (the SDK gave up), and
     * after three times the limit otherwise, so the report shows how late it was.
     */
    protected suspend fun awaitRecovered(scenario: String, from: Long, limitMs: Long = limit): Long {
        val deadline = from + maxOf(limitMs * 3, 30_000)
        while (now() < deadline) {
            val state = withTimeoutOrNull(deadline - now()) {
                alice.client.state.first { it is ConnectionState.Online || it is ConnectionState.Terminal }
            } ?: break
            if (state is ConnectionState.Terminal) {
                report.record(scenario, null, limitMs, "terminal: ${state.error}")
                fail("$scenario: the connection gave up (${state.error})")
            }
            val sent = now()
            if (ping(timeoutMillis = 5_000)) {
                val took = sent - from
                report.record(scenario, took, limitMs)
                if (took > limitMs) fail("$scenario: working again only after $took ms (limit $limitMs ms)")
                return took
            }
            delay(200) // Online but not answering: a zombie session; wait for the SDK to notice.
        }
        report.record(scenario, null, limitMs, "still ${alice.client.state.value}")
        fail("$scenario: not working again after ${now() - from} ms, still ${alice.client.state.value}")
    }

    /** Fails if alice's connection left Online at any point since [from]. */
    protected fun assertStayedOnline(scenario: String, from: Long) {
        val drops = timeline.since(from, "sdk").filterNot { it.text.startsWith("Online") }
        report.record(scenario, if (drops.isEmpty()) 0 else null, 0, if (drops.isEmpty()) "stayed online" else "dropped ${drops.size} times")
        if (drops.isNotEmpty()) fail("$scenario: the connection dropped: ${drops.joinToString { it.text }}")
        check(alice.client.state.value is ConnectionState.Online) { "$scenario: not online now" }
    }

    /**
     * Waits until bob's own connection answers a ping. He is only the other
     * side of the chat, so his recovery is not measured, only waited for.
     */
    private suspend fun bobReady(scenario: String) {
        val ready = withTimeoutOrNull(45_000) {
            while (true) {
                if (withTimeoutOrNull(15_000) { bob.connect() } is ConnectResult.Connected && ping(bob.client, 3_000)) break
                delay(500)
            }
            true
        }
        if (ready == null) fail("$scenario: bob could not reconnect (${bob.client.state.value})")
    }

    /** One XEP-0199 ping on alice's connection. */
    protected suspend fun ping(timeoutMillis: Long = 5_000): Boolean = ping(alice.client, timeoutMillis)

    private suspend fun ping(client: XmppClient, timeoutMillis: Long): Boolean = runCatching {
        val answer = client.request(
            element("iq", "jabber:client") {
                attribute("type", "get")
                attribute("to", aliceJid.domain)
                element("ping", "urn:xmpp:ping")
            },
            timeoutMillis,
        )
        answer.attribute("type") == "result"
    }.getOrDefault(false)

    /**
     * Messages both ways arrive within the limit: proof the connection is
     * useful, not just up. Bob is nudged first, because he is on the same
     * device and its network outages hit him too.
     */
    protected suspend fun assertMessagesFlow(scenario: String) {
        bobReady(scenario)
        val toAlice = "to alice ${UUID.randomUUID()}"
        val start = now()
        bob.send(aliceJid, toAlice)
        val inbound = withTimeoutOrNull(limit) {
            alice.chat.allMessages.first { all -> all[bobJid].orEmpty().any { !it.outgoing && it.body == toAlice } }
        }
        report.record("$scenario: bob → alice", inbound?.let { now() - start }, limit)
        if (inbound == null) fail("$scenario: bob's message did not reach alice")

        val toBob = "to bob ${UUID.randomUUID()}"
        val start2 = now()
        alice.send(bobJid, toBob)
        val outbound = withTimeoutOrNull(limit) {
            bob.allMessages.first { all -> all[aliceJid].orEmpty().any { !it.outgoing && it.body == toBob } }
        }
        report.record("$scenario: alice → bob", outbound?.let { now() - start2 }, limit)
        if (outbound == null) fail("$scenario: alice's message did not reach bob")
    }

    /** Waits until bob has every message in [bodies] from alice. */
    protected suspend fun assertBobReceived(scenario: String, bodies: Collection<String>, from: Long) {
        bobReady(scenario)
        val got = withTimeoutOrNull(limit) {
            bob.allMessages.first { all -> all[aliceJid].orEmpty().map { it.body }.containsAll(bodies) }
        }
        report.record(scenario, got?.let { now() - from }, limit, "${bodies.size} messages sent while offline")
        if (got == null) {
            val missing = bodies - bob.allMessages.value[aliceJid].orEmpty().map { it.body }.toSet()
            fail("$scenario: ${missing.size} of ${bodies.size} messages never reached bob: $missing")
        }
    }

    protected fun fail(message: String): Nothing {
        val text = "$message\nTimeline:\n${timeline.describe()}"
        Log.e("Stability", text)
        throw AssertionError(text)
    }

    protected companion object {
        /** The test server's certificate, learned on the first sign-in and kept for the run. */
        var pins: Set<String> = emptySet()
    }
}
