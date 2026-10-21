package io.github.usmandurrany.tether.demo.ui.me

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.DemoApp
import io.github.usmandurrany.tether.demo.DemoPush
import io.github.usmandurrany.tether.demo.R
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.demo.ui.components.Avatars
import io.github.usmandurrany.tether.im.Avatar
import io.github.usmandurrany.tether.im.Profile
import io.github.usmandurrany.tether.im.PushState
import io.github.usmandurrany.tether.im.PushTarget
import io.github.usmandurrany.tether.im.Show
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PresenceChoice(val label: Int, val show: Show?) {
    AVAILABLE(R.string.presence_available, null),
    CHAT(R.string.presence_chat, Show.CHAT),
    AWAY(R.string.presence_away, Show.AWAY),
    XA(R.string.presence_xa, Show.XA),
    DND(R.string.presence_dnd, Show.DND),
    INVISIBLE(R.string.presence_invisible, null),
}

/** A line under a button after it was pressed. */
sealed interface Note {
    data object ProfileSaved : Note
    data object PresenceUpdated : Note
    data object NotificationsOff : Note
    data class Failed(val reason: String) : Note
}

/** The Me tab's form fields, kept across rotation and process death. */
data class MeForm(
    val fullName: String = "",
    val nickname: String = "",
    val about: String = "",
    val presence: PresenceChoice = PresenceChoice.AVAILABLE,
    val status: String = "",
    val pushServer: String = "",
    val pushService: String = "",
    val pushNode: String = "",
    val pushOptions: String = "",
    val createNode: Boolean = false,
)

data class MeUiState(
    val account: String,
    val connection: ConnectionState = ConnectionState.Idle,
    val profilesEnabled: Boolean = true,
    val form: MeForm = MeForm(),
    /** The picture in the form: ours from the server, a newly picked one, or none. */
    val picture: Avatar? = null,
    val profileNote: Note? = null,
    val presenceNote: Note? = null,
    val pushNote: Note? = null,
    val streamManagement: Boolean = false,
    val resumed: Boolean = false,
    val carbons: Boolean = false,
    val archive: Boolean = false,
    val push: PushState = PushState.Off,
    /** This build has a google-services.json. */
    val firebase: Boolean = false,
) {
    val online: Boolean get() = connection is ConnectionState.Online
}

/** Own presence, profile, what the server supports, push registration, and sign-out. */
class MeViewModel(private val app: DemoApp, private val session: TetherSession, private val handle: SavedStateHandle) : ViewModel() {
    private val chat = session.chat

    private val form = MutableStateFlow(
        MeForm(
            fullName = handle[KEY_NAME] ?: "",
            nickname = handle[KEY_NICK] ?: "",
            about = handle[KEY_ABOUT] ?: "",
            presence = handle.get<String>(KEY_PRESENCE)?.let { runCatching { PresenceChoice.valueOf(it) }.getOrNull() } ?: PresenceChoice.AVAILABLE,
            status = handle[KEY_STATUS] ?: "",
            pushServer = handle[KEY_PUSH_SERVER] ?: app.pushSettings.server,
        ),
    )

    private data class Local(
        val picture: Avatar? = null,
        val profileNote: Note? = null,
        val presenceNote: Note? = null,
        val pushNote: Note? = null,
    )

    private val local = MutableStateFlow(Local())

    init {
        // The form starts from our profile on the server, once it has been read.
        viewModelScope.launch {
            chat.myProfile.collect { mine ->
                editForm { it.copy(fullName = mine?.fullName.orEmpty(), nickname = mine?.nickname.orEmpty(), about = mine?.note.orEmpty()) }
                local.update { it.copy(picture = mine?.avatar) }
            }
        }
    }

    private val server = combine(
        session.client.state,
        session.client.session,
        session.im.carbons.enabled,
        session.im.history.available,
        chat.pushState,
    ) { connection, live, carbons, archive, push ->
        MeUiState(
            account = (connection as? ConnectionState.Online)?.jid?.toString() ?: session.account.toString(),
            connection = connection,
            streamManagement = live?.streamManagement != null,
            resumed = live?.resumed == true,
            carbons = carbons,
            archive = archive,
            push = push,
        )
    }

    val state: StateFlow<MeUiState> = combine(server, form, local) { server, form, local ->
        server.copy(
            profilesEnabled = chat.features.profiles,
            form = form,
            picture = local.picture,
            profileNote = local.profileNote,
            presenceNote = local.presenceNote,
            pushNote = local.pushNote,
            firebase = DemoPush.available(app),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeUiState(session.account.toString(), form = form.value))

    fun editForm(transform: (MeForm) -> MeForm) {
        val updated = transform(form.value)
        form.value = updated
        handle[KEY_NAME] = updated.fullName
        handle[KEY_NICK] = updated.nickname
        handle[KEY_ABOUT] = updated.about
        handle[KEY_PRESENCE] = updated.presence.name
        handle[KEY_STATUS] = updated.status
        handle[KEY_PUSH_SERVER] = updated.pushServer
    }

    fun pickPicture(uri: Uri) {
        viewModelScope.launch {
            val picked = Avatars.fromUri(app, uri) ?: return@launch
            local.update { it.copy(picture = picked) }
        }
    }

    fun removePicture() = local.update { it.copy(picture = null) }

    fun saveProfile() {
        val f = form.value
        // Copy what the server has, so fields this form does not show are kept.
        val updated = (chat.myProfile.value ?: Profile()).copy(
            fullName = f.fullName.trim().ifEmpty { null },
            nickname = f.nickname.trim().ifEmpty { null },
            note = f.about.trim().ifEmpty { null },
            avatar = local.value.picture,
        )
        viewModelScope.launch {
            val note = runCatching { chat.updateMyProfile(updated) }
                .fold({ Note.ProfileSaved }, { Note.Failed(it.message ?: it.toString()) })
            local.update { it.copy(profileNote = note) }
        }
    }

    fun updatePresence() {
        val f = form.value
        viewModelScope.launch {
            val note = runCatching {
                chat.setPresence(f.presence.show, f.status.ifBlank { null }, invisible = f.presence == PresenceChoice.INVISIBLE)
            }.fold({ Note.PresenceUpdated }, { Note.Failed(it.message ?: it.toString()) })
            local.update { it.copy(presenceNote = note) }
        }
    }

    fun enablePush() {
        app.pushSettings.server = form.value.pushServer
        app.pushSettings.enabled = true
        viewModelScope.launch {
            val note = runCatching { app.enablePush(session) }.fold({ null }, { Note.Failed(it.message ?: it.toString()) })
            local.update { it.copy(pushNote = note) }
        }
    }

    fun disablePush() {
        app.pushSettings.enabled = false
        local.update { it.copy(pushNote = null) }
        viewModelScope.launch { session.unregisterPush() }
    }

    fun notificationsDenied() = local.update { it.copy(pushNote = Note.NotificationsOff) }

    fun registerByHand() {
        val f = form.value
        val publish = f.pushOptions.lines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i > 0) line.substring(0, i).trim() to line.substring(i + 1).trim() else null
        }.toMap()
        viewModelScope.launch { session.registerPush(PushTarget(f.pushService.trim(), f.pushNode.trim(), publish, f.createNode)) }
    }

    fun unregister() {
        viewModelScope.launch { session.unregisterPush() }
    }

    private companion object {
        const val KEY_NAME = "fullName"
        const val KEY_NICK = "nickname"
        const val KEY_ABOUT = "about"
        const val KEY_PRESENCE = "presence"
        const val KEY_STATUS = "status"
        const val KEY_PUSH_SERVER = "pushServer"
    }
}
