@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package io.github.usmandurrany.tether.demo.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.demo.Attachments
import io.github.usmandurrany.tether.demo.R
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.demo.ui.chats.TickIcon
import io.github.usmandurrany.tether.demo.ui.components.AvatarImage
import io.github.usmandurrany.tether.demo.ui.components.RemotePhoto
import io.github.usmandurrany.tether.demo.ui.components.dayText
import io.github.usmandurrany.tether.demo.ui.components.daysBetween
import io.github.usmandurrany.tether.demo.ui.components.lastSeenText
import io.github.usmandurrany.tether.demo.ui.components.presenceText
import io.github.usmandurrany.tether.demo.ui.components.timeText
import io.github.usmandurrany.tether.demo.ui.home.key
import io.github.usmandurrany.tether.demo.ui.theme.TetherIcons
import io.github.usmandurrany.tether.demo.ui.theme.chatColors
import io.github.usmandurrany.tether.im.Message
import io.github.usmandurrany.tether.im.MessageData
import io.github.usmandurrany.tether.im.MessageStatus

@Composable
fun ChatRoute(session: TetherSession, peer: BareJid, onBack: () -> Unit, showBack: Boolean = true) {
    val viewModel = viewModel(key = session.key("chat-$peer")) { ChatViewModel(session, peer) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Open while on screen: the SDK loads the newest history and sends read markers.
    DisposableEffect(viewModel) {
        viewModel.open()
        onDispose { viewModel.close() }
    }
    ChatScreen(
        state = state,
        showBack = showBack,
        actions = ChatActions(
            onBack = onBack,
            onLoadHistory = viewModel::loadHistory,
            onTyping = viewModel::typing,
            onSend = { viewModel.send(it) },
            onSendData = { caption, data -> viewModel.send(caption, data) },
            onDeleteForEveryone = viewModel::deleteForEveryone,
            onDeleteForMe = viewModel::deleteForMe,
        ),
    )
}

class ChatActions(
    val onBack: () -> Unit = {},
    val onLoadHistory: () -> Unit = {},
    val onTyping: () -> Unit = {},
    val onSend: (String) -> Unit = {},
    val onSendData: (String, MessageData) -> Unit = { _, _ -> },
    val onDeleteForEveryone: (Message) -> Unit = {},
    val onDeleteForMe: (Message) -> Unit = {},
)

/** What is open over the chat: nothing, the attach sheet, one attachment's details, or a message's actions. */
sealed interface ChatOverlay {
    data object None : ChatOverlay
    data object AttachMenu : ChatOverlay
    data class Attaching(val type: String) : ChatOverlay
    data class Acting(val message: Message) : ChatOverlay
}

/** List rows, newest first (the list is reversed so it stays at the bottom). */
private sealed interface ChatRow {
    data class Bubble(val message: Message) : ChatRow
    data class Day(val timestamp: Long) : ChatRow
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    actions: ChatActions,
    showBack: Boolean = true,
    initialOverlay: ChatOverlay = ChatOverlay.None,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var overlay by remember { mutableStateOf(initialOverlay) }
    val colors = chatColors

    val subtitle = when {
        state.typing -> stringResource(R.string.typing)
        !state.online && state.lastSeen != null -> stringResource(R.string.last_seen, lastSeenText(state.lastSeen))
        else -> presenceText(state.show, state.status, state.online)
    }

    Scaffold(
        containerColor = colors.wallpaper,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = actions.onBack) { Icon(TetherIcons.Back, contentDescription = stringResource(R.string.back)) }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(state.avatar, state.name, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(state.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.typing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Composer(
                text = text,
                onTextChange = {
                    text = it
                    if (it.isNotEmpty()) actions.onTyping()
                },
                onAttach = { overlay = ChatOverlay.AttachMenu },
                onSend = {
                    val body = text.trim()
                    text = ""
                    actions.onSend(body)
                },
            )
        },
    ) { padding ->
        Messages(state, actions, padding, onLongPress = { overlay = ChatOverlay.Acting(it) })
    }

    when (val current = overlay) {
        ChatOverlay.None -> Unit
        ChatOverlay.AttachMenu -> AttachMenuSheet(onDismiss = { overlay = ChatOverlay.None }, onPick = { overlay = ChatOverlay.Attaching(it) })
        is ChatOverlay.Attaching -> AttachSheet(current.type, onDismiss = { overlay = ChatOverlay.None }) { caption, data ->
            overlay = ChatOverlay.None
            actions.onSendData(caption, data)
        }
        is ChatOverlay.Acting -> MessageActionsSheet(
            current.message,
            onDismiss = { overlay = ChatOverlay.None },
            onDeleteForEveryone = { overlay = ChatOverlay.None; actions.onDeleteForEveryone(current.message) },
            onDeleteForMe = { overlay = ChatOverlay.None; actions.onDeleteForMe(current.message) },
        )
    }
}

@Composable
private fun Messages(state: ChatUiState, actions: ChatActions, padding: PaddingValues, onLongPress: (Message) -> Unit) {
    val colors = chatColors
    val rows = remember(state.messages) {
        buildList {
            val newestFirst = state.messages.asReversed()
            newestFirst.forEachIndexed { i, message ->
                add(ChatRow.Bubble(message))
                val older = newestFirst.getOrNull(i + 1)
                if (older == null || daysBetween(older.timestamp, message.timestamp) != 0) add(ChatRow.Day(message.timestamp))
            }
        }
    }
    LazyColumn(
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .drawBehind {
                // The chat wallpaper: a quiet grid of dots.
                val step = 22.dp.toPx()
                val radius = 1.2.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(colors.wallpaperDot, radius, Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            .testTag("messages"),
    ) {
        items(
            rows,
            key = { row ->
                when (row) {
                    is ChatRow.Bubble -> (if (row.message.outgoing) "out-" else "in-") + row.message.id
                    is ChatRow.Day -> "day-${row.timestamp}"
                }
            },
            contentType = { it::class },
        ) { row ->
            when (row) {
                is ChatRow.Bubble -> MessageBubble(row.message, onLongPress = { onLongPress(row.message) })
                is ChatRow.Day -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Chip(dayText(row.timestamp)) }
            }
        }
        item(key = "history", contentType = "history") {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                val history = state.history
                when {
                    history.error != null -> Chip(stringResource(R.string.history_error, history.error), onClick = actions.onLoadHistory)
                    !state.historyAvailable -> Chip(stringResource(R.string.no_archive))
                    history.loading -> Chip(stringResource(R.string.loading_history))
                    !history.done -> Chip(stringResource(R.string.load_earlier), onClick = actions.onLoadHistory)
                    else -> Chip(stringResource(R.string.start_of_conversation))
                }
            }
        }
    }
}

/** A centred pill on the wallpaper: a day, or the history line. */
@Composable
private fun Chip(text: String, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 24.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MessageBubble(message: Message, onLongPress: () -> Unit) {
    val colors = chatColors
    val mine = message.outgoing
    val content = if (mine) colors.onBubbleOut else colors.onBubbleIn
    val muted = if (mine) colors.onBubbleOutMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (mine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (mine) colors.bubbleOut else colors.bubbleIn)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("bubble"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (message.deleted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TetherIcons.Blocked, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.message_deleted), fontStyle = FontStyle.Italic, color = muted)
                }
            } else {
                val contents = remember(message.payloads) { Attachments.of(message) }
                contents.forEach { Content(it, content, muted, mine) }
                // A body that only repeats the link (the fallback for other apps) is not shown twice.
                val fallbacks = contents.mapNotNull(::fallbackOf)
                if (message.body.isNotBlank() && message.body !in fallbacks) {
                    Text(message.body, color = content, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Meta(message, muted)
        }
    }
}

/** Time, "other device", ticks, and why it failed. */
@Composable
private fun ColumnScope.Meta(message: Message, muted: Color) {
    val failed = message.status == MessageStatus.FAILED
    val otherDevice = stringResource(R.string.other_device)
    val failedText = stringResource(R.string.status_failed)
    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        val text = buildString {
            append(timeText(message.timestamp))
            if (message.fromOtherDevice) append(" · ").append(otherDevice)
            if (failed) append(" · ").append(failedText)
            message.error?.let { append(" · ").append(it) }
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (failed) MaterialTheme.colorScheme.error else muted)
        if (message.outgoing && !failed) {
            Spacer(Modifier.width(3.dp))
            TickIcon(message.status, tint = muted, readTint = chatColors.readTick)
        }
    }
}

private fun fallbackOf(content: Attachments.Content): String? = when (content) {
    is Attachments.Photo -> content.url
    is Attachments.Video -> content.url
    is Attachments.Location -> Attachments.fallbackText(Attachments.location(content.latitude, content.longitude, null))
    is Attachments.Other -> null
}

/** Draws one piece of custom data from a message, as a card inside the bubble. */
@Composable
private fun Content(content: Attachments.Content, color: Color, muted: Color, mine: Boolean) {
    val links = LocalUriHandler.current
    fun open(url: String) = runCatching { links.openUri(url) }
    val card = if (mine) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh
    when (content) {
        is Attachments.Photo -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RemotePhoto(content.url, content.width, content.height, Modifier.clickable { open(content.url) })
            val info = listOfNotNull(
                if (content.width != null && content.height != null) "${content.width}×${content.height}" else null,
                Attachments.size(content.bytes),
            ).joinToString(" · ")
            if (info.isNotEmpty()) Text(stringResource(R.string.photo_info, info), style = MaterialTheme.typography.labelSmall, color = muted)
        }
        is Attachments.Video -> MediaCard(
            icon = { Icon(TetherIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = stringResource(R.string.play_video),
            detail = listOfNotNull(Attachments.duration(content.seconds), Attachments.size(content.bytes)).joinToString(" · "),
            color = color,
            muted = muted,
            background = card,
            onClick = { open(content.url) },
        )
        is Attachments.Location -> MediaCard(
            icon = { Icon(TetherIcons.Location, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = stringResource(R.string.location_label, content.label ?: stringResource(R.string.location_open_map)),
            detail = "%.5f, %.5f".format(content.latitude, content.longitude),
            color = color,
            muted = muted,
            background = card,
            onClick = { open(content.mapUrl) },
        )
        is Attachments.Other -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TetherIcons.Code, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.custom_data_label, content.data.type), style = MaterialTheme.typography.labelSmall, color = muted)
            }
            Column(Modifier.clip(RoundedCornerShape(10.dp)).background(card).padding(horizontal = 8.dp, vertical = 6.dp)) {
                content.data.fields.forEach { (k, v) ->
                    Text(stringResource(R.string.custom_field, k, v), style = MaterialTheme.typography.bodyMedium, color = color)
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    icon: @Composable () -> Unit,
    title: String,
    detail: String,
    color: Color,
    muted: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = color, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            if (detail.isNotEmpty()) Text(detail, color = muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** The message field in a pill with the attach button, and a round send button. */
@Composable
private fun Composer(text: String, onTextChange: (String) -> Unit, onAttach: () -> Unit, onSend: () -> Unit) {
    Row(
        Modifier
            .background(chatColors.wallpaper)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAttach, modifier = Modifier.testTag("attach")) {
                    Icon(TetherIcons.Attach, contentDescription = stringResource(R.string.attach), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text(stringResource(R.string.message_placeholder)) },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).testTag("message-input"),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = text.isNotBlank(),
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(TetherIcons.Send, contentDescription = stringResource(R.string.send))
        }
    }
}
