package io.github.usmandurrany.tether.demo.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.im.Avatar
import io.github.usmandurrany.tether.im.Message
import io.github.usmandurrany.tether.im.MessageData
import io.github.usmandurrany.tether.im.Show
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Paging older messages in from the server archive. */
data class HistoryState(val loading: Boolean = false, val done: Boolean = false, val error: String? = null)

/** One conversation and its header. */
data class ChatUiState(
    val peer: BareJid,
    val name: String = peer.toString(),
    val avatar: Avatar? = null,
    val typing: Boolean = false,
    val online: Boolean = false,
    val show: Show? = null,
    val status: String? = null,
    val lastSeen: Long? = null,
    /** Oldest first, as the SDK keeps them. */
    val messages: List<Message> = emptyList(),
    /** The server keeps an archive (XEP-0313), so older messages can be loaded. */
    val historyAvailable: Boolean = false,
    val history: HistoryState = HistoryState(),
)

/**
 * One conversation, all from the SDK's TetherChat: messages with ticks (sent,
 * delivered, read), typing both ways, and older messages from the server
 * archive. While the chat is open and the app is in the foreground, the SDK
 * marks it read and tells the contact.
 */
class ChatViewModel(private val session: TetherSession, val peer: BareJid) : ViewModel() {
    private val chat = session.chat
    private val history = MutableStateFlow(HistoryState())

    val state: StateFlow<ChatUiState> = combine(
        chat.messages(peer),
        chat.typingContacts,
        session.im.history.available,
        combine(chat.contacts, chat.profiles, ::Pair),
        history,
    ) { messages, typing, archive, (contacts, profiles), history ->
        // The SDK's Contact has the name (ours for them, else their profile's), presence and
        // last seen. Someone not in our contacts still has a profile once fetched.
        val contact = contacts.firstOrNull { it.jid == peer }
        val profile = profiles[peer]
        ChatUiState(
            peer = peer,
            name = contact?.name ?: profile?.displayName ?: peer.toString(),
            avatar = contact?.avatar ?: profile?.avatar,
            typing = peer in typing,
            online = contact?.online == true,
            show = contact?.show,
            status = contact?.status,
            lastSeen = contact?.lastSeen,
            messages = messages,
            historyAvailable = archive,
            history = history,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState(peer, messages = chat.messages(peer).value))

    /** Opening a chat loads its newest history and marks it read. */
    fun open() = chat.openChat(peer)

    /** Closing stops our "typing" and the read markers. */
    fun close() = chat.closeChat(peer)

    fun loadHistory() {
        if (history.value.loading) return
        history.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching { chat.loadOlder(peer) }
                .onSuccess { more -> history.value = HistoryState(done = !more) }
                .onFailure { e -> history.value = HistoryState(error = e.message ?: e.toString()) }
        }
    }

    /** The SDK sends "typing", then "paused" when idle. */
    fun typing() = chat.typing(peer)

    /** Works offline too: the SDK keeps it in its outbox. */
    fun send(text: String) = session.send(peer, text)

    fun send(caption: String, data: MessageData) = session.send(peer, caption, data)

    fun deleteForEveryone(message: Message) {
        chat.deleteForEveryone(message)
    }

    fun deleteForMe(message: Message) = chat.deleteForMe(message)
}
