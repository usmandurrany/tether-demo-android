package io.github.usmandurrany.tether.demo.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import io.github.usmandurrany.tether.demo.ui.components.AvatarImage
import io.github.usmandurrany.tether.demo.ui.components.Empty
import io.github.usmandurrany.tether.demo.ui.components.listTime
import io.github.usmandurrany.tether.demo.ui.home.key
import io.github.usmandurrany.tether.demo.ui.theme.TetherIcons
import io.github.usmandurrany.tether.demo.ui.theme.chatColors
import io.github.usmandurrany.tether.im.Conversation
import io.github.usmandurrany.tether.im.MessageStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ChatsUiState(val conversations: List<Conversation> = emptyList())

class ChatsViewModel(session: TetherSession) : ViewModel() {
    // Newest first, with names, unread counts and typing, kept by the SDK.
    val state: StateFlow<ChatsUiState> = session.chat.conversations
        .map { ChatsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUiState(session.chat.conversations.value))
}

@Composable
fun ChatsRoute(session: TetherSession, onOpenChat: (BareJid) -> Unit) {
    val viewModel = viewModel(key = session.key("chats")) { ChatsViewModel(session) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChatsScreen(state, onOpenChat)
}

/** Conversations, newest first, under a field to start a new one. */
@Composable
fun ChatsScreen(state: ChatsUiState, onOpenChat: (BareJid) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "chat-with", contentType = "chat-with") { ChatWithField(onOpenChat) }
        if (state.conversations.isEmpty()) {
            item(key = "empty", contentType = "empty") { Empty(stringResource(R.string.chats_empty)) }
        }
        items(state.conversations, key = { it.peer.toString() }, contentType = { "conversation" }) { conversation ->
            ConversationRow(conversation, onClick = { onOpenChat(conversation.peer) })
        }
    }
}

/** "Chat with" any address, as a rounded field at the top of the list. */
@Composable
private fun ChatWithField(onOpenChat: (BareJid) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    val open = {
        val jid = parseContact(address)
        if (jid == null) {
            error = true
        } else {
            address = ""
            onOpenChat(jid)
        }
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
        TextField(
            value = address,
            onValueChange = { address = it; error = false },
            label = { Text(stringResource(R.string.chat_with)) },
            placeholder = { Text(stringResource(R.string.chat_with_placeholder)) },
            leadingIcon = { Icon(TetherIcons.Add, contentDescription = null) },
            trailingIcon = {
                FilledIconButton(onClick = open, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(TetherIcons.ArrowForward, contentDescription = stringResource(R.string.open), modifier = Modifier.size(20.dp))
                }
            },
            isError = error,
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { open() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error) {
            Text(
                stringResource(R.string.error_address),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val last = conversation.lastMessage
    val unread = conversation.unreadCount > 0
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(
            conversation.avatar,
            conversation.name,
            size = 54.dp,
            dot = if (conversation.online) chatColors.online else chatColors.offline,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (last != null) {
                    if (last.outgoing) {
                        TickIcon(last.status, Modifier.padding(end = 3.dp))
                    }
                    Text(
                        listTime(last.timestamp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (unread) colors.primary else colors.onSurfaceVariant,
                        fontWeight = if (unread) FontWeight.Bold else null,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview(conversation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread && !conversation.typing) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        conversation.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimary,
                        modifier = Modifier
                            .background(colors.primary, CircleShape)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun preview(conversation: Conversation): AnnotatedString {
    val last = conversation.lastMessage
    val accent = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val typing = stringResource(R.string.typing)
    val failed = stringResource(R.string.failed_suffix)
    val body = when {
        last == null -> ""
        last.outgoing -> stringResource(R.string.you_prefix, last.body)
        else -> last.body
    }
    return buildAnnotatedString {
        when {
            conversation.typing -> withStyle(SpanStyle(color = accent, fontStyle = FontStyle.Italic)) { append(typing) }
            last == null -> Unit
            else -> {
                append(body)
                if (last.status == MessageStatus.FAILED) {
                    append("  ")
                    withStyle(SpanStyle(color = error)) { append(failed) }
                }
            }
        }
    }
}

/** The ticks of one of our messages: clock, one tick, two ticks, two coloured ticks when read. */
@Composable
fun TickIcon(status: MessageStatus, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, readTint: Color = MaterialTheme.colorScheme.primary) {
    val (icon, description) = when (status) {
        MessageStatus.SENDING -> TetherIcons.Clock to R.string.status_sending
        MessageStatus.SENT -> TetherIcons.Check to R.string.status_sent
        MessageStatus.DELIVERED -> TetherIcons.DoubleCheck to R.string.status_delivered
        MessageStatus.READ -> TetherIcons.DoubleCheck to R.string.status_read
        MessageStatus.FAILED, MessageStatus.UNREAD -> return
    }
    Icon(
        icon,
        contentDescription = stringResource(description),
        tint = if (status == MessageStatus.READ) readTint else tint,
        modifier = modifier.size(16.dp),
    )
}

fun parseContact(text: String): BareJid? =
    runCatching { BareJid.parse(text.trim()) }.getOrNull()?.takeIf { it.local != null }
