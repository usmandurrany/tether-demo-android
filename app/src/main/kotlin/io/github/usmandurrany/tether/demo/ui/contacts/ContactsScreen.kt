@file:OptIn(ExperimentalFoundationApi::class)

package io.github.usmandurrany.tether.demo.ui.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.demo.R
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.demo.ui.chats.parseContact
import io.github.usmandurrany.tether.demo.ui.components.AvatarImage
import io.github.usmandurrany.tether.demo.ui.components.Empty
import io.github.usmandurrany.tether.demo.ui.components.Hint
import io.github.usmandurrany.tether.demo.ui.components.ListHeader
import io.github.usmandurrany.tether.demo.ui.components.presenceColor
import io.github.usmandurrany.tether.demo.ui.components.presenceText
import io.github.usmandurrany.tether.demo.ui.home.key
import io.github.usmandurrany.tether.demo.ui.theme.TetherIcons
import io.github.usmandurrany.tether.im.Contact
import io.github.usmandurrany.tether.im.Subscription
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactsUiState(
    /** Sorted by name by the SDK. */
    val contacts: List<Contact> = emptyList(),
    /** People asking to see our presence. */
    val requests: List<BareJid> = emptyList(),
)

/** The contact list with presence, pending subscription requests, and add/remove. */
class ContactsViewModel(private val session: TetherSession) : ViewModel() {
    // Name (ours for them, else their profile's), picture, presence and subscription, kept by the SDK.
    val state: StateFlow<ContactsUiState> = combine(session.chat.contacts, session.subscriptionRequests) { contacts, requests ->
        ContactsUiState(contacts, requests.toList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun answer(jid: BareJid, approve: Boolean) {
        viewModelScope.launch { session.answerRequest(jid, approve) }
    }

    fun add(jid: BareJid, name: String?) {
        viewModelScope.launch { runCatching { session.addContact(jid, name?.ifBlank { null }) } }
    }

    fun remove(jid: BareJid) {
        viewModelScope.launch { runCatching { session.removeContact(jid) } }
    }
}

@Composable
fun ContactsRoute(session: TetherSession, onOpenChat: (BareJid) -> Unit) {
    val viewModel = viewModel(key = session.key("contacts")) { ContactsViewModel(session) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContactsScreen(
        state,
        onOpenChat = onOpenChat,
        onAnswer = viewModel::answer,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
    )
}

@Composable
fun ContactsScreen(
    state: ContactsUiState,
    onOpenChat: (BareJid) -> Unit,
    onAnswer: (BareJid, Boolean) -> Unit = { _, _ -> },
    onAdd: (BareJid, String?) -> Unit = { _, _ -> },
    onRemove: (BareJid) -> Unit = {},
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Contact?>(null) }
    val online = state.contacts.filter { it.online }
    val offline = state.contacts.filterNot { it.online }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (state.requests.isNotEmpty()) {
                item(key = "requests-header", contentType = "header") { ListHeader(stringResource(R.string.requests)) }
                items(state.requests, key = { "request-$it" }, contentType = { "request" }) { jid -> RequestCard(jid, onAnswer) }
            }
            item(key = "count", contentType = "count") {
                Hint(stringResource(R.string.contacts_count, state.contacts.size), Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            }
            if (state.contacts.isEmpty()) item(key = "empty", contentType = "empty") { Empty(stringResource(R.string.contacts_empty)) }
            if (online.isNotEmpty()) {
                item(key = "online", contentType = "header") { ListHeader(stringResource(R.string.section_online)) }
                items(online, key = { it.jid.toString() }, contentType = { "contact" }) { ContactRow(it, onOpenChat, onLongClick = { removing = it }) }
            }
            if (offline.isNotEmpty()) {
                item(key = "offline", contentType = "header") { ListHeader(stringResource(R.string.section_offline)) }
                items(offline, key = { it.jid.toString() }, contentType = { "contact" }) { ContactRow(it, onOpenChat, onLongClick = { removing = it }) }
            }
        }
        ExtendedFloatingActionButton(
            text = { Text(stringResource(R.string.add_contact)) },
            icon = { Icon(TetherIcons.PersonAdd, contentDescription = null) },
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        AddContactDialog(onDismiss = { adding = false }) { jid, name ->
            adding = false
            onAdd(jid, name)
        }
    }

    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            icon = { Icon(TetherIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.remove_contact_title, item.name)) },
            text = { Text(stringResource(R.string.remove_contact_text)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    onRemove(item.jid)
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RequestCard(jid: BareJid, onAnswer: (BareJid, Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(14.dp)) {
            AvatarImage(null, jid.toString(), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val text = stringResource(R.string.request_text, jid.toString())
                val address = jid.toString()
                Text(
                    buildAnnotatedString {
                        val start = text.indexOf(address)
                        if (start < 0) {
                            append(text)
                        } else {
                            append(text.substring(0, start))
                            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(address) }
                            append(text.substring(start + address.length))
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAnswer(jid, true) }) { Text(stringResource(R.string.approve)) }
                    OutlinedButton(onClick = { onAnswer(jid, false) }) { Text(stringResource(R.string.deny)) }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onOpenChat: (BareJid) -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onOpenChat(contact.jid) }, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(contact.avatar, contact.name, size = 50.dp, dot = presenceColor(contact.show, contact.online))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                presenceText(contact.show, contact.status, contact.online),
                style = MaterialTheme.typography.bodyMedium,
                color = if (contact.online) presenceColor(contact.show, true) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val note = subscriptionNote(contact)
            Text(
                if (note == null) contact.jid.toString() else "${contact.jid} · $note",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun subscriptionNote(item: Contact): String? = when {
    item.pending -> stringResource(R.string.sub_request_sent)
    item.subscription == Subscription.NONE -> stringResource(R.string.sub_none)
    item.subscription == Subscription.FROM -> stringResource(R.string.sub_from)
    item.subscription == Subscription.TO -> stringResource(R.string.sub_to)
    else -> null
}

@Composable
private fun AddContactDialog(onDismiss: () -> Unit, onAdd: (BareJid, String?) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val jid = parseContact(address)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TetherIcons.PersonAdd, contentDescription = null) },
        title = { Text(stringResource(R.string.add_contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.field_address)) },
                    placeholder = { Text(stringResource(R.string.chat_with_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name_optional)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Hint(stringResource(R.string.hint_add_contact))
            }
        },
        confirmButton = { TextButton(enabled = jid != null, onClick = { jid?.let { onAdd(it, name) } }) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
