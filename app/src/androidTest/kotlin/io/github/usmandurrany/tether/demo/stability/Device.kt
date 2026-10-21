package io.github.usmandurrany.tether.demo.stability

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Stability"

/** Runs shell commands as the shell user, which may switch radios and send key events. */
object Shell {
    fun run(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        // Reading to the end waits for the command to finish.
        val output = android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().decodeToString() }
        Log.d(TAG, "$ $command${if (output.isBlank()) "" else "\n$output"}")
        return output
    }
}

/** A network as Android reports it: which one is the default, and over what. */
data class OsNetwork(val handle: Long, val transports: Set<String>) {
    val wifi: Boolean get() = "wifi" in transports
    val cellular: Boolean get() = "cellular" in transports
    override fun toString(): String = "${transports.joinToString("+")}#$handle"
}

/**
 * Switches the device's networks the way a user (or a train tunnel) would, and
 * watches the default network independently of the SDK, so a test can tell
 * the time Android took to bring a network up from the time the SDK took to
 * reconnect on it.
 */
class DeviceNetwork(context: Context, private val useMobileData: Boolean = true) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _default = MutableStateFlow<OsNetwork?>(null)

    /** The default network with internet, or null while there is none. */
    val default: StateFlow<OsNetwork?> = _default.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update(network, connectivity.getNetworkCapabilities(network))
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update(network, capabilities)
        override fun onLost(network: Network) {
            if (_default.value?.handle == network.networkHandle) _default.value = null
        }
    }

    init {
        connectivity.registerDefaultNetworkCallback(callback)
    }

    fun close() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }

    private fun update(network: Network, caps: NetworkCapabilities?) {
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return
        val transports = buildSet {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
        }
        _default.value = OsNetwork(network.networkHandle, transports)
    }

    fun wifi(on: Boolean) = Shell.run("svc wifi ${if (on) "enable" else "disable"}")
    fun mobileData(on: Boolean) = Shell.run("svc data ${if (on) "enable" else "disable"}")
    fun airplaneMode(on: Boolean) = Shell.run("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")

    /**
     * Waits until Android has a default network matching [predicate] and
     * returns it with the time it was first seen, or null after [timeoutMillis].
     */
    suspend fun await(timeoutMillis: Long = 30_000, predicate: (OsNetwork) -> Boolean = { true }): Pair<OsNetwork, Long>? =
        withTimeoutOrNull(timeoutMillis) {
            val net = default.first { it != null && predicate(it) }!!
            net to SystemClock.elapsedRealtime()
        }

    /** Waits until Android reports no default network at all. */
    suspend fun awaitNone(timeoutMillis: Long = 30_000): Boolean =
        withTimeoutOrNull(timeoutMillis) { default.first { it == null } } != null || default.value == null

    /**
     * Everything back on: no airplane mode, Wi-Fi enabled, and mobile data
     * enabled, or kept off when the server is only reachable over Wi-Fi.
     */
    suspend fun restore() {
        airplaneMode(false)
        wifi(true)
        mobileData(useMobileData)
        await(60_000)
    }

    /** True when the emulator has both Wi-Fi and a mobile network to switch between. */
    suspend fun canSwitch(): Boolean {
        if (!useMobileData) return false
        restore()
        val wifi = await(30_000) { it.wifi } != null
        this.wifi(false)
        val cell = await(30_000) { it.cellular } != null
        this.wifi(true)
        await(30_000) { it.wifi }
        return wifi && cell
    }
}

/** Sends the demo to the background and brings it back, as the user would. */
object AppSwitcher {
    private const val COMPONENT = "io.github.usmandurrany.tether.demo/.MainActivity"

    /** Opens the app, or brings its existing task to the front, and waits until it is shown. */
    fun open() {
        Shell.run("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $COMPONENT")
    }

    /** The home button: the app goes to the background but keeps running. */
    fun home() {
        Shell.run("input keyevent KEYCODE_HOME")
    }

    /** Removes the app's task, as swiping it away in recents does; the process stays. */
    fun closeTask() {
        val activity = currentActivity() ?: return
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.finishAndRemoveTask() }
    }

    private fun currentActivity(): android.app.Activity? {
        var found: android.app.Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val registry = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            found = listOf(
                androidx.test.runner.lifecycle.Stage.RESUMED,
                androidx.test.runner.lifecycle.Stage.STARTED,
                androidx.test.runner.lifecycle.Stage.PAUSED,
                androidx.test.runner.lifecycle.Stage.STOPPED,
            ).firstNotNullOfOrNull { registry.getActivitiesInStage(it).firstOrNull() }
        }
        return found
    }
}

/**
 * Blocks the app's own traffic while the device's networks stay up, with the
 * per-app firewall of Android 14 and newer. Needs no root: the shell user may
 * use it. Affects every connection of the app, the test's included.
 */
object AppFirewall {
    private val pkg get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    val supported: Boolean by lazy {
        android.os.Build.VERSION.SDK_INT >= 34 &&
            Shell.run("cmd connectivity help").contains("set-package-networking-enabled")
    }

    fun cut(on: Boolean) {
        if (on) Shell.run("cmd connectivity set-chain3-enabled true")
        Shell.run("cmd connectivity set-package-networking-enabled ${!on} $pkg")
    }

    /** Lets the app's traffic through again, whatever a failed test left behind. */
    fun restore() {
        if (!supported) return
        Shell.run("cmd connectivity set-package-networking-enabled true $pkg")
        Shell.run("cmd connectivity set-chain3-enabled false")
    }
}
