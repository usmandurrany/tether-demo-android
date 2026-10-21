package io.github.usmandurrany.tether.demo.stability

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.usmandurrany.tether.demo.ConnectionSettings
import io.github.usmandurrany.tether.demo.DemoApp
import io.github.usmandurrany.tether.demo.SignInResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test on its own: signs alice in with "Connect on app start" and leaves
 * her signed in, so `scripts/stability.sh` can kill and restart the
 * app over and over and time how soon each cold start is online. Runs only
 * when asked for with the `prepareColdStart` argument.
 */
@RunWith(AndroidJUnit4::class)
class ColdStartSetup {
    @Test
    fun signInWithConnectOnStart() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Only for the cold start run", args.getString("prepareColdStart") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DemoApp
        val host = args.getString("xmppHost")!!
        val domain = args.getString("xmppDomain")!!
        val direct = args.getString("toxiproxyApi") == "none"
        val port = if (direct) args.getString("xmppPort") ?: "5222" else args.getString("proxyPort") ?: "15222"
        var settings = ConnectionSettings(
            jid = args.getString("aliceJid") ?: "alice@$domain",
            host = if (direct) host else args.getString("proxyHost") ?: host,
            port = port,
            autoConnect = true,
        )
        val password = args.getString("alicePassword") ?: args.getString("xmppPassword") ?: "secret"
        var result = app.signIn(settings, password)
        if (result is SignInResult.UntrustedCertificate) {
            settings = settings.copy(pinnedCertificates = setOf(result.sha256))
            result = app.signIn(settings, password)
        }
        check(result is SignInResult.Connected) { "Could not sign in: $result" }
        // The process ends with this test. The settings are saved with apply(), which writes
        // in the background: an empty commit() waits for that write. The SDK saves its own
        // state (the session to resume) half a second after a change.
        app.getSharedPreferences("connection_settings", android.content.Context.MODE_PRIVATE).edit().commit()
        delay(2_000)
        check(app.settingsStore.load().autoConnect) { "Connect on app start was not saved" }
    }
}
