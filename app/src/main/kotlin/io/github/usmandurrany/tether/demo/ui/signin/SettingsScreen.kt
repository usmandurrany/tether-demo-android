@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.usmandurrany.tether.demo.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.core.BackgroundMode
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.ConnectionSettings
import io.github.usmandurrany.tether.demo.DemoApp
import io.github.usmandurrany.tether.demo.R
import io.github.usmandurrany.tether.demo.SignInResult
import io.github.usmandurrany.tether.demo.ui.components.FoldingCard
import io.github.usmandurrany.tether.demo.ui.components.Hint
import io.github.usmandurrany.tether.demo.ui.components.RadioCard
import io.github.usmandurrany.tether.demo.ui.components.SectionCard
import io.github.usmandurrany.tether.demo.ui.components.SwitchRow
import io.github.usmandurrany.tether.demo.ui.components.label
import io.github.usmandurrany.tether.demo.ui.theme.TetherIcons
import io.github.usmandurrany.tether.demo.ui.theme.TetherTheme

/** The connection settings page, with its ViewModel. */
@Composable
fun SettingsRoute(app: DemoApp, canGoBack: Boolean, onBack: () -> Unit, onConnected: () -> Unit) {
    val viewModel = viewModel { SettingsViewModel(app, createSavedStateHandle()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.connected) {
        if (state.connected) {
            viewModel.connectedHandled()
            onConnected()
        }
    }
    SettingsScreen(
        state = state,
        canGoBack = canGoBack,
        actions = SettingsActions(
            onSettingsChange = viewModel::updateSettings,
            onPasswordChange = viewModel::setPassword,
            onPinsChange = viewModel::setPins,
            onConnect = viewModel::connect,
            onTrust = viewModel::trust,
            onDismissTrust = viewModel::dismissUntrusted,
            onBack = onBack,
        ),
    )
}

/** What the page can ask of its owner. Defaults do nothing, for previews and screenshots. */
class SettingsActions(
    val onSettingsChange: ((ConnectionSettings) -> ConnectionSettings) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onPinsChange: (String) -> Unit = {},
    val onConnect: () -> Unit = {},
    val onTrust: () -> Unit = {},
    val onDismissTrust: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The generic connection settings page: any XMPP server, found through DNS SRV
 * or given as a host and port, with optional certificate pinning for
 * self-signed servers. The account is always open; the other groups fold to a
 * summary of their values.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    canGoBack: Boolean,
    actions: SettingsActions,
    initiallyExpanded: Boolean = false,
) {
    val settings = state.settings
    val change = actions.onSettingsChange
    var serverOpen by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var loginOpen by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var backgroundOpen by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var chatsOpen by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var portalOpen by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val page = MaterialTheme.colorScheme.surfaceContainerLow

    Scaffold(
        containerColor = page,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = actions.onBack) { Icon(TetherIcons.Back, contentDescription = stringResource(R.string.back)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = page),
            )
        },
        bottomBar = {
            Surface(color = page) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val error = if (state.pinsError) stringResource(R.string.error_pins) else state.error
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    if (state.busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            val connecting = state.connecting
                            if (connecting != null && connecting != ConnectionState.Idle) {
                                Spacer(Modifier.width(10.dp))
                                Hint(connecting.label())
                            }
                        }
                    }
                    Button(
                        enabled = !state.busy && settings.jid.isNotBlank(),
                        onClick = actions.onConnect,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(if (canGoBack) R.string.reconnect else R.string.connect)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            if (!canGoBack) {
                Header()
            } else {
                Hint(stringResource(R.string.settings_intro), Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
            }

            SectionCard(stringResource(R.string.section_account), TetherIcons.Person) {
                OutlinedTextField(
                    value = settings.jid,
                    onValueChange = { value -> change { it.copy(jid = value) } },
                    label = { Text(stringResource(R.string.field_jid)) },
                    placeholder = { Text(stringResource(R.string.field_jid_placeholder)) },
                    singleLine = true,
                    shape = FieldShape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = actions.onPasswordChange,
                        label = { Text(stringResource(R.string.field_password)) },
                        placeholder = { if (state.savedLogin) Text(stringResource(R.string.field_password_saved)) },
                        singleLine = true,
                        shape = FieldShape,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) TetherIcons.EyeOff else TetherIcons.Eye,
                                    contentDescription = stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint(
                        stringResource(if (state.savedLogin) R.string.hint_saved_login else R.string.hint_password),
                        Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                OutlinedTextField(
                    value = settings.resource,
                    onValueChange = { value -> change { it.copy(resource = value) } },
                    label = { Text(stringResource(R.string.field_resource)) },
                    placeholder = { Text(stringResource(R.string.field_resource_placeholder)) },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FoldingCard(
                title = stringResource(R.string.section_server),
                summary = serverSummary(settings),
                icon = TetherIcons.Server,
                expanded = serverOpen,
                onExpandedChange = { serverOpen = it },
            ) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = settings.host,
                            onValueChange = { value -> change { it.copy(host = value) } },
                            label = { Text(stringResource(R.string.field_host)) },
                            placeholder = { Text(stringResource(R.string.field_host_placeholder)) },
                            singleLine = true,
                            shape = FieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = settings.port,
                            onValueChange = { value -> change { it.copy(port = value.filter(Char::isDigit).take(5)) } },
                            label = { Text(stringResource(R.string.field_port)) },
                            placeholder = { Text(if (settings.directTls) "5223" else "5222") },
                            singleLine = true,
                            enabled = settings.host.isNotBlank(),
                            shape = FieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(128.dp),
                        )
                    }
                    Hint(stringResource(R.string.hint_host), Modifier.padding(start = 4.dp, top = 6.dp))
                }
                SwitchRow(
                    stringResource(R.string.direct_tls),
                    settings.directTls,
                    { value -> change { it.copy(directTls = value) } },
                    hint = stringResource(R.string.hint_direct_tls),
                )
                Column {
                    OutlinedTextField(
                        value = state.pinsText,
                        onValueChange = actions.onPinsChange,
                        label = { Text(stringResource(R.string.field_pins)) },
                        placeholder = { Text(stringResource(R.string.field_pins_placeholder)) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        isError = state.pinsError,
                        minLines = 2,
                        shape = FieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint(stringResource(R.string.hint_pins), Modifier.padding(start = 4.dp, top = 6.dp))
                }
            }

            FoldingCard(
                title = stringResource(R.string.section_login_methods),
                summary = loginSummary(settings),
                icon = TetherIcons.Key,
                expanded = loginOpen,
                onExpandedChange = { loginOpen = it },
            ) {
                SwitchRow(
                    stringResource(R.string.allow_plain),
                    settings.allowPlain,
                    { value -> change { it.copy(allowPlain = value) } },
                    hint = stringResource(R.string.hint_allow_plain),
                )
                SwitchRow(
                    stringResource(R.string.allow_scram_sha1),
                    settings.allowScramSha1,
                    { value -> change { it.copy(allowScramSha1 = value) } },
                    hint = stringResource(R.string.hint_allow_scram_sha1),
                )
            }

            FoldingCard(
                title = stringResource(R.string.section_background),
                summary = backgroundSummary(settings),
                icon = TetherIcons.Moon,
                expanded = backgroundOpen,
                onExpandedChange = { backgroundOpen = it },
            ) {
                BackgroundMode.entries.forEach { mode ->
                    RadioCard(
                        title = stringResource(mode.title()),
                        hint = stringResource(mode.hint()),
                        selected = settings.backgroundMode == mode,
                        onSelect = { change { it.copy(backgroundMode = mode) } },
                    )
                }
                SwitchRow(
                    stringResource(R.string.auto_connect),
                    settings.autoConnect,
                    { value -> change { it.copy(autoConnect = value) } },
                    hint = stringResource(R.string.hint_auto_connect),
                )
            }

            FoldingCard(
                title = stringResource(R.string.section_chats),
                summary = chatsSummary(settings),
                icon = TetherIcons.Chats,
                expanded = chatsOpen,
                onExpandedChange = { chatsOpen = it },
            ) {
                SwitchRow(
                    stringResource(R.string.auto_accept),
                    settings.autoAcceptContacts,
                    { value -> change { it.copy(autoAcceptContacts = value) } },
                    hint = stringResource(R.string.hint_auto_accept),
                )
                SwitchRow(
                    stringResource(R.string.keep_messages),
                    settings.keepMessages,
                    { value -> change { it.copy(keepMessages = value) } },
                    hint = stringResource(R.string.hint_keep_messages),
                )
                SwitchRow(
                    stringResource(R.string.profiles),
                    settings.profiles,
                    { value -> change { it.copy(profiles = value) } },
                    hint = stringResource(R.string.hint_profiles),
                )
            }

            FoldingCard(
                title = stringResource(R.string.section_log_portal),
                summary = settings.logEndpoint.ifBlank { stringResource(R.string.summary_portal_off) },
                icon = TetherIcons.Bug,
                expanded = portalOpen,
                onExpandedChange = { portalOpen = it },
            ) {
                Hint(stringResource(R.string.hint_log_portal))
                OutlinedTextField(
                    value = settings.logEndpoint,
                    onValueChange = { value -> change { it.copy(logEndpoint = value) } },
                    label = { Text(stringResource(R.string.field_portal_url)) },
                    placeholder = { Text(stringResource(R.string.field_portal_url_placeholder)) },
                    singleLine = true,
                    shape = FieldShape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = settings.logIngestKey,
                    onValueChange = { value -> change { it.copy(logIngestKey = value) } },
                    label = { Text(stringResource(R.string.field_ingest_key)) },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = settings.logLabel,
                    onValueChange = { value -> change { it.copy(logLabel = value) } },
                    label = { Text(stringResource(R.string.field_label)) },
                    placeholder = { Text(stringResource(R.string.field_label_placeholder)) },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(
                    stringResource(R.string.redact_bodies),
                    settings.redactBodies,
                    { value -> change { it.copy(redactBodies = value) } },
                )
            }
        }
    }

    state.untrusted?.let { offer -> TrustDialog(offer, onTrust = actions.onTrust, onDismiss = actions.onDismissTrust) }
}

private val FieldShape = RoundedCornerShape(12.dp)

/** The app's mark and what it works with, on the first-run page. */
@Composable
private fun Header() {
    Column(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TetherIcons.AppMark, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Offers to pin a certificate that failed verification, with its fingerprint to compare. */
@Composable
private fun TrustDialog(offer: SignInResult.UntrustedCertificate, onTrust: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TetherIcons.Shield, contentDescription = null) },
        title = { Text(stringResource(R.string.trust_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.trust_text, offer.message))
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        offer.sha256.chunked(2).chunked(8).joinToString(":\n") { it.joinToString(":") },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
                Text(stringResource(R.string.trust_warning))
            }
        },
        confirmButton = { Button(onClick = onTrust) { Text(stringResource(R.string.trust_and_connect)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun BackgroundMode.title(): Int = when (this) {
    BackgroundMode.CLOSE -> R.string.mode_close
    BackgroundMode.DETACH -> R.string.mode_detach
    BackgroundMode.STAY_CONNECTED -> R.string.mode_stay
}

private fun BackgroundMode.hint(): Int = when (this) {
    BackgroundMode.CLOSE -> R.string.mode_close_hint
    BackgroundMode.DETACH -> R.string.mode_detach_hint
    BackgroundMode.STAY_CONNECTED -> R.string.mode_stay_hint
}

@Composable
private fun joined(vararg parts: String): String = parts.joinToString(stringResource(R.string.summary_separator))

@Composable
private fun onOff(name: String, on: Boolean): String = stringResource(if (on) R.string.summary_on else R.string.summary_off, name)

@Composable
private fun serverSummary(s: ConnectionSettings): String {
    val where = when {
        s.host.isBlank() -> stringResource(R.string.summary_dns)
        s.port.isBlank() -> stringResource(R.string.summary_host, s.host.trim())
        else -> stringResource(R.string.summary_host_port, s.host.trim(), s.port)
    }
    val tls = stringResource(if (s.directTls && s.host.isNotBlank()) R.string.direct_tls else R.string.summary_starttls)
    return if (s.pinnedCertificates.isEmpty()) joined(where, tls) else joined(where, tls, stringResource(R.string.summary_pins, s.pinnedCertificates.size))
}

@Composable
private fun loginSummary(s: ConnectionSettings): String =
    joined(onOff(stringResource(R.string.summary_plain), s.allowPlain), onOff(stringResource(R.string.summary_scram_sha1), s.allowScramSha1))

@Composable
private fun backgroundSummary(s: ConnectionSettings): String {
    val mode = stringResource(s.backgroundMode.title())
    return if (s.autoConnect) joined(mode, stringResource(R.string.summary_auto_connect)) else mode
}

@Composable
private fun chatsSummary(s: ConnectionSettings): String = joined(
    onOff(stringResource(R.string.summary_auto_accept), s.autoAcceptContacts),
    stringResource(if (s.keepMessages) R.string.summary_kept else R.string.summary_memory),
    onOff(stringResource(R.string.summary_profiles), s.profiles),
)

@Preview
@Composable
private fun SettingsPreview() {
    TetherTheme {
        SettingsScreen(SettingsUiState(settings = ConnectionSettings(jid = "bob@demo.test", autoConnect = true)), canGoBack = false, actions = SettingsActions())
    }
}
