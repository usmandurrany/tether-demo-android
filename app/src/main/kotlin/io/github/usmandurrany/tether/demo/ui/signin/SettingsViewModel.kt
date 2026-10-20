package io.github.usmandurrany.tether.demo.ui.signin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.ConnectionSettings
import io.github.usmandurrany.tether.demo.DemoApp
import io.github.usmandurrany.tether.demo.SignInResult
import io.github.usmandurrany.tether.demo.TetherSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the connection settings page shows. */
data class SettingsUiState(
    val settings: ConnectionSettings = ConnectionSettings(),
    /** The pinned fingerprints as typed, one per line. */
    val pinsText: String = "",
    val password: String = "",
    /** This device has login keys for the address, so the password may stay empty. */
    val savedLogin: Boolean = false,
    val busy: Boolean = false,
    /** The state of the sign-in in progress, shown next to the button. */
    val connecting: ConnectionState? = null,
    val error: String? = null,
    /** A fingerprint is not 64 hex characters. */
    val pinsError: Boolean = false,
    /** The server's certificate failed verification: offer to trust it. */
    val untrusted: SignInResult.UntrustedCertificate? = null,
    /** Signed in; the screen moves on and calls [SettingsViewModel.connectedHandled]. */
    val connected: Boolean = false,
)

/**
 * The connection settings page's state holder. The form survives rotation and
 * process death through [SavedStateHandle]; the password deliberately does not
 * (it would end up in the saved instance state on disk).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val app: DemoApp, private val handle: SavedStateHandle) : ViewModel() {
    private val settings = handle.getStateFlow(KEY_SETTINGS, app.settingsStore.load())
    private val pinsText = handle.getStateFlow(KEY_PINS, settings.value.pinnedCertificates.joinToString("\n"))
    private val password = MutableStateFlow("")

    private data class Progress(
        val busy: Boolean = false,
        val error: String? = null,
        val pinsError: Boolean = false,
        val untrusted: SignInResult.UntrustedCertificate? = null,
        val connected: Boolean = false,
    )

    private val progress = MutableStateFlow(Progress())

    private val savedLogin = settings.map { it.jid }.distinctUntilChanged()
        .mapLatest { TetherSession.hasSavedLogin(app.secureStore, it) }

    private val connecting = app.connecting.flatMapLatest { it?.client?.state ?: flowOf(null) }

    val state: StateFlow<SettingsUiState> = combine(
        settings,
        pinsText,
        password,
        progress,
        combine(savedLogin, connecting, ::Pair),
    ) { settings, pins, password, progress, (saved, connecting) ->
        SettingsUiState(
            settings = settings,
            pinsText = pins,
            password = password,
            savedLogin = saved,
            busy = progress.busy,
            connecting = connecting,
            error = progress.error,
            pinsError = progress.pinsError,
            untrusted = progress.untrusted,
            connected = progress.connected,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(settings = settings.value, pinsText = pinsText.value),
    )

    fun updateSettings(transform: (ConnectionSettings) -> ConnectionSettings) {
        handle[KEY_SETTINGS] = transform(settings.value)
    }

    fun setPins(text: String) {
        handle[KEY_PINS] = text
        progress.update { it.copy(pinsError = false) }
    }

    fun setPassword(text: String) {
        password.value = text
    }

    fun connect() {
        val pins = parsePins(pinsText.value)
        if (pins == null) {
            progress.update { it.copy(pinsError = true) }
            return
        }
        updateSettings { it.copy(pinnedCertificates = pins) }
        signIn(settings.value)
    }

    /** Pins the offered certificate and connects again. */
    fun trust() {
        val offer = progress.value.untrusted ?: return
        val pins = (parsePins(pinsText.value) ?: emptySet()) + offer.sha256
        handle[KEY_PINS] = pins.joinToString("\n")
        updateSettings { it.copy(pinnedCertificates = pins) }
        progress.update { it.copy(untrusted = null) }
        signIn(settings.value)
    }

    fun dismissUntrusted() = progress.update { it.copy(untrusted = null) }

    fun connectedHandled() = progress.update { it.copy(connected = false) }

    private fun signIn(withSettings: ConnectionSettings) {
        progress.update { it.copy(busy = true, error = null, pinsError = false) }
        viewModelScope.launch {
            val result = app.signIn(withSettings, password.value.ifEmpty { null })
            progress.update {
                when (result) {
                    SignInResult.Connected -> {
                        password.value = ""
                        it.copy(busy = false, connected = true)
                    }
                    is SignInResult.Error -> it.copy(busy = false, error = result.message)
                    is SignInResult.UntrustedCertificate -> it.copy(busy = false, untrusted = result)
                }
            }
        }
    }

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_PINS = "pins"
    }
}

/** Fingerprints in any common notation, normalised to lowercase hex. Null if one is malformed. */
internal fun parsePins(text: String): Set<String>? {
    val pins = text.lines().map { line -> line.filter { it.isLetterOrDigit() }.lowercase() }.filter { it.isNotEmpty() }
    return if (pins.all { it.matches(Regex("[0-9a-f]{64}")) }) pins.toSet() else null
}
