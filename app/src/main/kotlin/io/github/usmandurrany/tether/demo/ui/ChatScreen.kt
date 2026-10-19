@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.usmandurrany.tether.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.im.Message
import io.github.usmandurrany.tether.im.MessageStatus
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * One conversation, all from the SDK's TetherChat: messages with ticks (sent,
 * delivered, read), typing both ways, and older messages from the server
 * archive. While this screen is open and the app is in the foreground, the SDK
 * marks the chat read and tells the contact.
 */
@Composable
fun ChatScreen(session: TetherSession, peer: BareJid, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val chat = session.chat
    val messages by remember(peer) { chat.messages(peer) }.collectAsStateWithLifecycle()
    val typing by chat.typingContacts.collectAsStateWithLifecycle()
    val roster by session.im.roster.items.collectAsStateWithLifecycle()
    val presences by session.im.presences.contacts.collectAsStateWithLifecycle()
    val historyAvailable by session.im.history.available.collectAsStateWithLifecycle()
    val list = rememberLazyListState()

    var text by rememberSaveable { mutableStateOf("") }
    var archiveDone by remember { mutableStateOf(false) }
    var loadingHistory by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }

    fun loadHistory() {
        if (loadingHistory) return
        loadingHistory = true
        scope.launch {
            runCatching { chat.loadOlder(peer) }
                .onSuccess { more -> archiveDone = !more; historyError = null }
                .onFailure { historyError = "Couldn't load history: ${it.message}" }
            loadingHistory = false
        }
    }

    // Opening a chat loads its newest history and marks it read; closing stops our "typing".
    DisposableEffect(peer) {
        chat.openChat(peer)
        onDispose { chat.closeChat(peer) }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size) }

    val name = roster[peer]?.name ?: peer.toString()
    val subtitle = if (peer in typing) "typing…" else presenceText(presences[peer].best())

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                title = {
                    Column {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        when {
                            historyError != null -> TextButton(onClick = { loadHistory() }) { Text(historyError!!) }
                            !historyAvailable -> Hint("No server archive: only messages from this run and offline messages show here.")
                            loadingHistory -> Hint("Loading history…")
                            !archiveDone -> TextButton(onClick = { loadHistory() }) { Text("Load earlier messages") }
                            else -> Hint("Start of the conversation")
                        }
                    }
                }
                items(messages) { Bubble(it) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        if (it.isNotEmpty()) chat.typing(peer) // the SDK sends "typing", then "paused" when idle
                    },
                    placeholder = { Text("Message") },
                    maxLines = 4,
                    modifier = Modifier.weight(1f).testTag("message-input"),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val body = text.trim()
                        text = ""
                        session.send(peer, body)
                    },
                ) { Text("Send") }
            }
        }
    }
}

@Composable
private fun Bubble(message: Message) {
    val mine = message.outgoing
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            Text(message.body)
            Text(
                buildString {
                    append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp)))
                    if (message.fromOtherDevice) append(" · other device")
                    if (mine) append("  ").append(message.status.ticks())
                    message.error?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (message.status == MessageStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun MessageStatus.ticks(): String = when (this) {
    MessageStatus.SENDING -> "sending"
    MessageStatus.SENT -> "✓"
    MessageStatus.DELIVERED -> "✓✓"
    MessageStatus.READ -> "✓✓ read"
    MessageStatus.FAILED -> "failed"
    MessageStatus.UNREAD -> ""
}
