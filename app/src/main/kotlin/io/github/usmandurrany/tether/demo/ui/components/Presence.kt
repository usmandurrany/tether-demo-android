package io.github.usmandurrany.tether.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.R
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.demo.ui.theme.chatColors
import io.github.usmandurrany.tether.im.ContactPresence
import io.github.usmandurrany.tether.im.Show

/** A short label for the connection state, for the top bar. */
@Composable
fun ConnectionState.label(): String = when (this) {
    ConnectionState.Idle -> stringResource(R.string.state_idle)
    ConnectionState.WaitingForNetwork -> stringResource(R.string.state_waiting_for_network)
    is ConnectionState.Connecting ->
        if (attempt > 1) stringResource(R.string.state_connecting_attempt, attempt) else stringResource(R.string.state_connecting)
    is ConnectionState.Online -> stringResource(R.string.state_online)
    is ConnectionState.Backoff -> stringResource(R.string.state_backoff, ((delayMillis + 999) / 1000).toInt())
    ConnectionState.Paused -> stringResource(R.string.state_paused)
    is ConnectionState.Terminal -> stringResource(R.string.state_terminal, TetherSession.describe(error))
}

@Composable
fun ConnectionState.color(): Color = when (this) {
    is ConnectionState.Online -> chatColors.online
    is ConnectionState.Terminal -> MaterialTheme.colorScheme.error
    else -> chatColors.away
}

/** True while the SDK is working on a connection, for the top bar's progress line. */
val ConnectionState.busy: Boolean
    get() = this is ConnectionState.Connecting || this is ConnectionState.Backoff || this == ConnectionState.WaitingForNetwork

/** The best presence of a contact's online devices, or null when offline. */
fun List<ContactPresence>?.best(): ContactPresence? = this?.maxWithOrNull(
    compareBy<ContactPresence> { it.priority }.thenBy { if (it.show == null || it.show == Show.CHAT) 1 else 0 },
)

@Composable
fun presenceColor(show: Show?, online: Boolean): Color {
    val colors = chatColors
    if (!online) return colors.offline
    return when (show) {
        null, Show.CHAT -> colors.online
        Show.AWAY, Show.XA -> colors.away
        Show.DND -> colors.busy
    }
}

@Composable
fun showText(show: Show?): String = when (show) {
    null -> stringResource(R.string.presence_available)
    Show.CHAT -> stringResource(R.string.presence_chat)
    Show.AWAY -> stringResource(R.string.presence_away)
    Show.XA -> stringResource(R.string.presence_xa)
    Show.DND -> stringResource(R.string.presence_dnd)
}

@Composable
fun presenceText(show: Show?, status: String?, online: Boolean): String {
    if (!online) return stringResource(R.string.presence_offline)
    val text = showText(show)
    return if (status.isNullOrBlank()) text else stringResource(R.string.presence_with_status, text, status)
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Spacer(modifier.size(size).clip(CircleShape).background(color))
}
