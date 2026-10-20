@file:OptIn(ExperimentalLayoutApi::class)

package io.github.usmandurrany.tether.demo.ui.me

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.demo.DemoApp
import io.github.usmandurrany.tether.demo.DemoPush
import io.github.usmandurrany.tether.demo.R
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.demo.ui.components.AvatarImage
import io.github.usmandurrany.tether.demo.ui.components.Dot
import io.github.usmandurrany.tether.demo.ui.components.FoldingCard
import io.github.usmandurrany.tether.demo.ui.components.Hint
import io.github.usmandurrany.tether.demo.ui.components.SectionCard
import io.github.usmandurrany.tether.demo.ui.components.SwitchRow
import io.github.usmandurrany.tether.demo.ui.components.color
import io.github.usmandurrany.tether.demo.ui.components.label
import io.github.usmandurrany.tether.demo.ui.components.presenceColor
import io.github.usmandurrany.tether.demo.ui.home.key
import io.github.usmandurrany.tether.demo.ui.theme.TetherIcons
import io.github.usmandurrany.tether.demo.ui.theme.chatColors
import io.github.usmandurrany.tether.im.PushState

@Composable
fun MeRoute(app: DemoApp, session: TetherSession, onSignOut: (forget: Boolean) -> Unit) {
    val viewModel = viewModel(key = session.key("me")) { MeViewModel(app, session, createSavedStateHandle()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) viewModel.pickPicture(uri) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.notificationsDenied()
    }
    MeScreen(
        state,
        MeActions(
            onFormChange = viewModel::editForm,
            onChoosePicture = { pick.launch("image/*") },
            onRemovePicture = viewModel::removePicture,
            onSaveProfile = viewModel::saveProfile,
            onUpdatePresence = viewModel::updatePresence,
            onEnablePush = {
                if (!DemoPush.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.enablePush()
            },
            onDisablePush = viewModel::disablePush,
            onRegister = viewModel::registerByHand,
            onUnregister = viewModel::unregister,
            onSignOut = onSignOut,
        ),
    )
}

class MeActions(
    val onFormChange: ((MeForm) -> MeForm) -> Unit = {},
    val onChoosePicture: () -> Unit = {},
    val onRemovePicture: () -> Unit = {},
    val onSaveProfile: () -> Unit = {},
    val onUpdatePresence: () -> Unit = {},
    val onEnablePush: () -> Unit = {},
    val onDisablePush: () -> Unit = {},
    val onRegister: () -> Unit = {},
    val onUnregister: () -> Unit = {},
    val onSignOut: (Boolean) -> Unit = {},
)

/** Own presence and profile, what the server supports, push registration, and sign-out. */
@Composable
fun MeScreen(state: MeUiState, actions: MeActions, byHandInitiallyOpen: Boolean = false) {
    val form = state.form
    val edit = actions.onFormChange
    var byHandOpen by rememberSaveable { mutableStateOf(byHandInitiallyOpen) }
    val displayName = form.nickname.ifBlank { form.fullName.ifBlank { state.account.substringBefore('@') } }
    val fieldShape = RoundedCornerShape(12.dp)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        // Telegram-style header: picture, name, full address and connection state.
        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box {
                AvatarImage(state.picture, displayName, size = 104.dp)
                if (state.profilesEnabled) {
                    FilledIconButton(
                        onClick = actions.onChoosePicture,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(40.dp),
                    ) { Icon(TetherIcons.Camera, contentDescription = stringResource(R.string.choose_picture), modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(displayName, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(state.account, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(state.connection.color())
                Spacer(Modifier.width(6.dp))
                Hint(state.connection.label())
            }
        }

        if (state.profilesEnabled) {
            SectionCard(stringResource(R.string.my_profile), TetherIcons.Person) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = actions.onChoosePicture) { Text(stringResource(R.string.choose_picture)) }
                        if (state.picture != null) TextButton(onClick = actions.onRemovePicture) { Text(stringResource(R.string.remove)) }
                    }
                    Hint(
                        state.picture?.let { stringResource(R.string.picture_info, it.data.size / 1024) } ?: stringResource(R.string.no_picture),
                        Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
                OutlinedTextField(
                    form.fullName,
                    { value -> edit { it.copy(fullName = value) } },
                    label = { Text(stringResource(R.string.full_name)) },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("profile-name"),
                )
                OutlinedTextField(
                    form.nickname,
                    { value -> edit { it.copy(nickname = value) } },
                    label = { Text(stringResource(R.string.nickname)) },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth().testTag("profile-nickname"),
                )
                OutlinedTextField(
                    form.about,
                    { value -> edit { it.copy(about = value) } },
                    label = { Text(stringResource(R.string.about)) },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                ButtonWithNote(
                    text = stringResource(R.string.save_profile),
                    enabled = state.online,
                    onClick = actions.onSaveProfile,
                    note = noteText(state.profileNote) ?: stringResource(if (state.online) R.string.profile_hint else R.string.profile_offline),
                    modifier = Modifier.testTag("profile-save"),
                )
            }
        }

        SectionCard(stringResource(R.string.my_presence), TetherIcons.Presence) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresenceChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = form.presence == choice,
                        onClick = { edit { it.copy(presence = choice) } },
                        label = { Text(stringResource(choice.label)) },
                        leadingIcon = {
                            if (choice == PresenceChoice.INVISIBLE) {
                                Box(Modifier.size(10.dp).background(Color.Transparent, CircleShape))
                            } else {
                                Dot(presenceColor(choice.show, online = true), size = 10.dp)
                            }
                        },
                    )
                }
            }
            OutlinedTextField(
                value = form.status,
                onValueChange = { value -> edit { it.copy(status = value) } },
                label = { Text(stringResource(R.string.status_message)) },
                singleLine = true,
                enabled = form.presence != PresenceChoice.INVISIBLE,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            ButtonWithNote(stringResource(R.string.update_presence), enabled = true, onClick = actions.onUpdatePresence, note = noteText(state.presenceNote))
        }

        SectionCard(stringResource(R.string.server_supports), TetherIcons.Server) {
            Feature(stringResource(R.string.feature_sm), state.streamManagement, if (state.resumed) stringResource(R.string.feature_sm_note) else null)
            Feature(stringResource(R.string.feature_carbons), state.carbons, stringResource(R.string.feature_carbons_note))
            Feature(stringResource(R.string.feature_mam), state.archive, stringResource(R.string.feature_mam_note))
            Feature(
                stringResource(R.string.feature_push),
                state.push !is PushState.Unsupported,
                if (state.push is PushState.Unsupported) null else stringResource(R.string.feature_push_note),
            )
            Hint(stringResource(R.string.features_hint))
        }

        SectionCard(stringResource(R.string.push_title), TetherIcons.Bell) {
            if (!state.firebase) {
                Hint(stringResource(R.string.push_no_firebase))
            } else {
                Hint(stringResource(R.string.push_intro))
                OutlinedTextField(
                    form.pushServer,
                    { value -> edit { it.copy(pushServer = value) } },
                    label = { Text(stringResource(R.string.push_server)) },
                    placeholder = { Text(stringResource(R.string.push_server_placeholder)) },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = form.pushServer.isNotBlank() && state.online, onClick = actions.onEnablePush) { Text(stringResource(R.string.enable_push)) }
                    OutlinedButton(onClick = actions.onDisablePush) { Text(stringResource(R.string.turn_off)) }
                }
                noteText(state.pushNote)?.let { Hint(it) }
            }
            PushStatePill(state.push)
        }

        FoldingCard(
            title = stringResource(R.string.push_by_hand),
            summary = stringResource(R.string.push_by_hand_hint),
            icon = TetherIcons.Wrench,
            expanded = byHandOpen,
            onExpandedChange = { byHandOpen = it },
        ) {
            Hint(stringResource(R.string.push_by_hand_hint))
            OutlinedTextField(
                form.pushService,
                { value -> edit { it.copy(pushService = value) } },
                label = { Text(stringResource(R.string.push_service_jid)) },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                form.pushNode,
                { value -> edit { it.copy(pushNode = value) } },
                label = { Text(stringResource(R.string.push_node)) },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                form.pushOptions,
                { value -> edit { it.copy(pushOptions = value) } },
                label = { Text(stringResource(R.string.push_options)) },
                minLines = 2,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(
                stringResource(R.string.create_node),
                form.createNode,
                { value -> edit { it.copy(createNode = value) } },
                hint = stringResource(R.string.create_node_hint),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = form.pushService.isNotBlank() && form.pushNode.isNotBlank(), onClick = actions.onRegister) { Text(stringResource(R.string.register)) }
                OutlinedButton(onClick = actions.onUnregister) { Text(stringResource(R.string.unregister)) }
            }
        }

        SectionCard(stringResource(R.string.session), TetherIcons.Logout, danger = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.onSignOut(false) }) { Text(stringResource(R.string.disconnect)) }
                Button(
                    onClick = { actions.onSignOut(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(stringResource(R.string.sign_out)) }
            }
            Hint(stringResource(R.string.session_hint))
        }
    }
}

/** A button with the result of its last press, or what it does, beside it. */
@Composable
private fun ButtonWithNote(text: String, enabled: Boolean, onClick: () -> Unit, note: String?, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(enabled = enabled, onClick = onClick, modifier = modifier) { Text(text, maxLines = 1) }
        if (note != null) {
            Spacer(Modifier.width(12.dp))
            Hint(note, Modifier.weight(1f))
        }
    }
}

@Composable
private fun noteText(note: Note?): String? = when (note) {
    null -> null
    Note.ProfileSaved -> stringResource(R.string.profile_saved)
    Note.PresenceUpdated -> stringResource(R.string.presence_updated)
    Note.NotificationsOff -> stringResource(R.string.push_notifications_off)
    is Note.Failed -> stringResource(R.string.failed_reason, note.reason)
}

@Composable
private fun Feature(name: String, on: Boolean, note: String?) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = if (on) chatColors.online.copy(alpha = 0.16f) else colors.surfaceContainerHigh,
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (on) TetherIcons.Check else TetherIcons.Dash,
                    contentDescription = stringResource(if (on) R.string.supported else R.string.not_supported),
                    tint = if (on) chatColors.online else colors.outline,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (on && note != null) Hint(note)
        }
    }
}

@Composable
private fun PushStatePill(push: PushState) {
    val text = stringResource(
        R.string.push_state,
        when (push) {
            PushState.Off -> stringResource(R.string.push_state_off)
            PushState.Unsupported -> stringResource(R.string.push_state_unsupported)
            is PushState.Pending -> stringResource(R.string.push_state_pending, push.target.service)
            is PushState.On -> stringResource(R.string.push_state_on, push.target.service)
            is PushState.Failed -> stringResource(R.string.push_state_failed, push.condition ?: stringResource(R.string.unknown_error))
        },
    )
    val colors = MaterialTheme.colorScheme
    val (background, content) = when (push) {
        is PushState.On -> chatColors.online.copy(alpha = 0.14f) to colors.onSurface
        is PushState.Failed -> colors.errorContainer to colors.onErrorContainer
        else -> colors.surfaceContainerHigh to colors.onSurfaceVariant
    }
    Surface(color = background, shape = RoundedCornerShape(12.dp), border = BorderStroke(0.dp, Color.Transparent)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp))
    }
}
