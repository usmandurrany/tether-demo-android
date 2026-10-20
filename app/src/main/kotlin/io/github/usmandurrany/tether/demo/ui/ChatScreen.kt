@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package io.github.usmandurrany.tether.demo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import io.github.usmandurrany.tether.demo.Attachments
import io.github.usmandurrany.tether.im.MessageData
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
    val historyAvailable by session.im.history.available.collectAsStateWithLifecycle()
    val contacts by chat.contacts.collectAsStateWithLifecycle()
    // Someone not in our contacts still has a profile (name and picture) once fetched.
    val profile = chat.profiles.collectAsStateWithLifecycle().value[peer]
    val list = rememberLazyListState()

    var text by rememberSaveable { mutableStateOf("") }
    var archiveDone by remember { mutableStateOf(false) }
    var loadingHistory by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var attachMenu by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf<String?>(null) }
    var acting by remember { mutableStateOf<Message?>(null) }

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
    // Jump to the newest message when one arrives (index 0 is the history row above the messages).
    LaunchedEffect(messages.size, messages.lastOrNull()?.id) { if (messages.isNotEmpty()) list.scrollToItem(messages.size) }

    // The SDK's Contact has the name (ours for them, else their profile's), presence and last seen.
    val contact = contacts.firstOrNull { it.jid == peer }
    val name = contact?.name ?: profile?.displayName ?: peer.toString()
    val lastSeen = contact?.lastSeen
    val subtitle = when {
        peer in typing -> "typing…"
        contact?.online != true && lastSeen != null -> "last seen ${lastSeenText(lastSeen)}"
        else -> presenceText(contact?.show, contact?.status, contact?.online == true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(profile?.avatar, name, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("messages")) {
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
                items(messages, key = { (if (it.outgoing) "out-" else "in-") + it.id }) { Bubble(it, onLongPress = { acting = it }) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { attachMenu = true }, modifier = Modifier.testTag("attach")) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        listOf(Attachments.PHOTO to "Photo", Attachments.VIDEO to "Video", Attachments.LOCATION to "Location", CUSTOM to "Custom data").forEach { (type, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { attachMenu = false; attaching = type })
                        }
                    }
                }
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

    attaching?.let { type ->
        AttachDialog(type, onDismiss = { attaching = null }) { caption, data ->
            attaching = null
            session.send(peer, caption, data)
        }
    }

    acting?.let { message ->
        MessageActions(
            message,
            onDismiss = { acting = null },
            onDeleteForEveryone = { acting = null; chat.deleteForEveryone(message) },
            onDeleteForMe = { acting = null; chat.deleteForMe(message) },
        )
    }
}

private const val CUSTOM = "custom"

/** Long-press on a message: delete it for everyone (ours only) or just here. */
@Composable
private fun MessageActions(message: Message, onDismiss: () -> Unit, onDeleteForEveryone: () -> Unit, onDeleteForMe: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (message.deleted) "Remove the placeholder?" else "Delete message?") },
        text = {
            Text(
                if (message.outgoing && !message.deleted) {
                    "For everyone removes it from their chat too, if their app supports it. For me removes it only on this device."
                } else {
                    "It is removed on this device only."
                },
            )
        },
        confirmButton = {
            Row {
                if (message.outgoing && !message.deleted && message.status != MessageStatus.FAILED) {
                    TextButton(onClick = onDeleteForEveryone, modifier = Modifier.testTag("delete-everyone")) { Text("For everyone") }
                }
                TextButton(onClick = onDeleteForMe, modifier = Modifier.testTag("delete-me")) { Text(if (message.deleted) "Remove" else "For me") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The details of a photo, video, location or custom message. The demo has no
 * file hosting, so photos and videos are links to files already online; the
 * demo reads their size from the web server before sending.
 */
@Composable
private fun AttachDialog(type: String, onDismiss: () -> Unit, onSend: (String, MessageData) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember {
        mutableStateOf(
            when (type) {
                Attachments.PHOTO -> "https://picsum.photos/id/1015/800/533.jpg"
                Attachments.VIDEO -> "https://download.samplelib.com/mp4/sample-5s.mp4"
                else -> ""
            },
        )
    }
    var seconds by remember { mutableStateOf("5") }
    var lat by remember { mutableStateOf("51.50135") }
    var lon by remember { mutableStateOf("-0.14189") }
    var label by remember { mutableStateOf("Buckingham Palace") }
    var customType by remember { mutableStateOf("contact-card") }
    var fields by remember { mutableStateOf("name=Ada Lovelace\nphone=+44 20 7946 0000") }
    var caption by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        error = null
        when (type) {
            Attachments.PHOTO, Attachments.VIDEO -> {
                val link = url.trim()
                if (!link.startsWith("https://") && !link.startsWith("http://")) {
                    error = "Enter a link starting with https://"
                    return
                }
                busy = true
                scope.launch {
                    val data = if (type == Attachments.PHOTO) {
                        val info = MediaProbe.photo(link)
                        Attachments.photo(link, info?.width, info?.height, info?.bytes, info?.mime)
                    } else {
                        Attachments.video(link, seconds.trim().toIntOrNull(), MediaProbe.size(link))
                    }
                    busy = false
                    onSend(caption.trim(), data)
                }
            }
            Attachments.LOCATION -> {
                val la = lat.trim().toDoubleOrNull()
                val lo = lon.trim().toDoubleOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                    error = "Latitude goes from -90 to 90 and longitude from -180 to 180."
                    return
                }
                onSend(caption.trim(), Attachments.location(la, lo, label.trim().ifEmpty { null }))
            }
            else -> {
                val pairs = fields.lines().mapNotNull { line ->
                    val i = line.indexOf('=')
                    if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
                }
                runCatching { MessageData(customType.trim(), pairs.toMap()) }
                    .onSuccess { onSend(caption.trim(), it) }
                    .onFailure { error = it.message }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (type) {
                    Attachments.PHOTO -> "Send a photo"
                    Attachments.VIDEO -> "Send a video"
                    Attachments.LOCATION -> "Share a location"
                    else -> "Send custom data"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (type) {
                    Attachments.PHOTO, Attachments.VIDEO -> {
                        OutlinedTextField(url, { url = it }, label = { Text("Link") }, singleLine = true, modifier = Modifier.testTag("attach-url"))
                        if (type == Attachments.VIDEO) {
                            OutlinedTextField(
                                seconds,
                                { seconds = it },
                                label = { Text("Length in seconds") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                    Attachments.LOCATION -> {
                        val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, singleLine = true, keyboardOptions = decimal)
                        OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") }, singleLine = true, keyboardOptions = decimal)
                        OutlinedTextField(label, { label = it }, label = { Text("Place name") }, singleLine = true)
                    }
                    else -> {
                        OutlinedTextField(customType, { customType = it }, label = { Text("Type") }, singleLine = true)
                        OutlinedTextField(fields, { fields = it }, label = { Text("Fields, one key=value per line") }, minLines = 3)
                    }
                }
                OutlinedTextField(caption, { caption = it }, label = { Text("Caption (optional)") }, singleLine = true, modifier = Modifier.testTag("attach-caption"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { submit() }, modifier = Modifier.testTag("attach-send")) { Text(if (busy) "Reading…" else "Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Bubble(message: Message, onLongPress: () -> Unit) {
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
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("bubble"),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (message.deleted) {
                Text("This message was deleted", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val contents = remember(message.payloads) { Attachments.of(message) }
                contents.forEach { Content(it) }
                // A body that only repeats the link (the fallback for other apps) is not shown twice.
                val fallbacks = contents.mapNotNull(::fallbackOf)
                if (message.body.isNotBlank() && message.body !in fallbacks) Text(message.body)
            }
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

private fun fallbackOf(content: Attachments.Content): String? = when (content) {
    is Attachments.Photo -> content.url
    is Attachments.Video -> content.url
    is Attachments.Location -> Attachments.fallbackText(Attachments.location(content.latitude, content.longitude, null))
    is Attachments.Other -> null
}

/** Draws one piece of custom data from a message. */
@Composable
private fun Content(content: Attachments.Content) {
    val links = LocalUriHandler.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun open(url: String) = runCatching { links.openUri(url) }
    when (content) {
        is Attachments.Photo -> {
            RemotePhoto(content.url, content.width, content.height, Modifier.combinedClickable(onClick = { open(content.url) }))
            val info = listOfNotNull(
                if (content.width != null && content.height != null) "${content.width}×${content.height}" else null,
                Attachments.size(content.bytes),
            ).joinToString(" · ")
            if (info.isNotEmpty()) Text("Photo · $info", style = MaterialTheme.typography.labelSmall, color = muted)
        }
        is Attachments.Video -> {
            val info = listOfNotNull(Attachments.duration(content.seconds), Attachments.size(content.bytes)).joinToString(" · ")
            Text(
                "▶ Play video" + if (info.isNotEmpty()) "  $info" else "",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.combinedClickable(onClick = { open(content.url) }),
            )
        }
        is Attachments.Location -> Column(Modifier.combinedClickable(onClick = { open(content.mapUrl) })) {
            Text(
                "Location: " + (content.label ?: "open the map"),
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
            Text("%.5f, %.5f".format(content.latitude, content.longitude), style = MaterialTheme.typography.labelSmall, color = muted)
        }
        is Attachments.Other -> Column {
            Text("Custom data: ${content.data.type}", style = MaterialTheme.typography.labelSmall, color = muted)
            content.data.fields.forEach { (k, v) -> Text("$k = $v", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun lastSeenText(at: Long): String {
    val minutes = (System.currentTimeMillis() - at).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 20 * 60 -> "at " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
        else -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(at))
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
