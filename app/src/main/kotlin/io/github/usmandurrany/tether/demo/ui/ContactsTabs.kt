@file:OptIn(ExperimentalFoundationApi::class)

package io.github.usmandurrany.tether.demo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.im.Avatar
import io.github.usmandurrany.tether.im.MessageStatus
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.im.Contact
import io.github.usmandurrany.tether.im.Subscription
import kotlinx.coroutines.launch

/** Conversations of this run, newest first, plus a field to start a new one. */
@Composable
fun ChatsTab(session: TetherSession, onOpenChat: (BareJid) -> Unit) {
    // Newest first, with names, unread counts and typing, kept by the SDK.
    val conversations by session.chat.conversations.collectAsStateWithLifecycle()
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; error = null },
                    label = { Text("Chat with") },
                    placeholder = { Text("bob@example.com") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (error != null) { { Text(error.orEmpty()) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val jid = parseContact(address)
                    if (jid == null) error = "Enter an address like bob@example.com" else { address = ""; onOpenChat(jid) }
                }) { Text("Open") }
            }
        }
        if (conversations.isEmpty()) {
            item { Empty("No messages yet. Open a contact, or type an address above.") }
        }
        items(conversations, key = { it.peer.toString() }) { conversation ->
            val last = conversation.lastMessage
            ListRow(
                title = conversation.name,
                subtitle = when {
                    conversation.typing -> "typing…"
                    last == null -> ""
                    else -> buildString {
                        if (last.outgoing) append("You: ")
                        append(last.body)
                        if (last.status == MessageStatus.FAILED) append("  (failed)")
                    }
                },
                dot = if (conversation.online) Online else Offline,
                avatar = conversation.avatar,
                badge = conversation.unreadCount.takeIf { it > 0 },
                onClick = { onOpenChat(conversation.peer) },
            )
        }
    }
}

/** The contact list with presence, pending subscription requests, and add/remove. */
@Composable
fun ContactsTab(session: TetherSession, onOpenChat: (BareJid) -> Unit) {
    // Name (ours for them, else their profile's), picture, presence and subscription, kept by the SDK.
    val all by session.chat.contacts.collectAsStateWithLifecycle()
    val requests by session.subscriptionRequests.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Contact?>(null) }

    val contacts = all.sortedBy { !it.online } // the SDK sorts by name; online first keeps that order within each

    LazyColumn(Modifier.fillMaxSize()) {
        items(requests.toList(), key = { "request-$it" }) { jid ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("$jid wants to see when you're online.")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { session.answerRequest(jid, approve = true) } }) { Text("Approve") }
                        OutlinedButton(onClick = { scope.launch { session.answerRequest(jid, approve = false) } }) { Text("Deny") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Hint("${all.size} contacts · long-press to remove", Modifier.weight(1f))
                TextButton(onClick = { adding = true }) { Text("Add contact") }
            }
        }
        if (contacts.isEmpty()) item { Empty("Your contact list is empty.") }
        items(contacts, key = { it.jid.toString() }) { contact ->
            ListRow(
                title = contact.name,
                subtitle = buildString {
                    append(contact.jid).append(" · ")
                    append(presenceText(contact.show, contact.status, contact.online))
                    subscriptionNote(contact)?.let { append(" · ").append(it) }
                },
                dot = presenceColor(contact.show, contact.online),
                avatar = contact.avatar,
                onClick = { onOpenChat(contact.jid) },
                onLongClick = { removing = contact },
            )
        }
    }

    if (adding) AddContactDialog(onDismiss = { adding = false }) { jid, name ->
        adding = false
        scope.launch { runCatching { session.addContact(jid, name) } }
    }

    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${item.name}?") },
            text = { Text("They are removed from your contacts and presence sharing ends both ways.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch { runCatching { session.removeContact(item.jid) } }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

private fun subscriptionNote(item: Contact): String? = when {
    item.pending -> "request sent"
    item.subscription == Subscription.NONE -> "not sharing presence"
    item.subscription == Subscription.FROM -> "they can't share theirs yet"
    item.subscription == Subscription.TO -> "you don't share yours"
    else -> null
}

@Composable
private fun AddContactDialog(onDismiss: () -> Unit, onAdd: (BareJid, String?) -> Unit) {
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val jid = parseContact(address)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add contact") },
        text = {
            Column {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    placeholder = { Text("bob@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Hint("Also asks them to share their presence with you.")
            }
        },
        confirmButton = { TextButton(enabled = jid != null, onClick = { jid?.let { onAdd(it, name) } }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun parseContact(text: String): BareJid? =
    runCatching { BareJid.parse(text.trim()) }.getOrNull()?.takeIf { it.local != null }

@Composable
fun ListRow(
    title: String,
    subtitle: String,
    dot: androidx.compose.ui.graphics.Color,
    avatar: Avatar? = null,
    badge: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onLongClick != null) it.combinedClickable(onClick = onClick, onLongClick = onLongClick) else it.clickable(onClick = onClick) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(avatar, title, dot = dot)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (badge != null) Badge { Text(badge.toString()) }
    }
    HorizontalDivider(Modifier.padding(start = 68.dp))
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Hint(text)
    }
}
