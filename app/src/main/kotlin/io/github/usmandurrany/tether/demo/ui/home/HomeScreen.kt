@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.usmandurrany.tether.demo.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.DemoApp
import io.github.usmandurrany.tether.demo.R
import io.github.usmandurrany.tether.demo.TetherSession
import io.github.usmandurrany.tether.demo.ui.chat.ChatRoute
import io.github.usmandurrany.tether.demo.ui.chats.ChatsRoute
import io.github.usmandurrany.tether.demo.ui.components.AvatarImage
import io.github.usmandurrany.tether.demo.ui.components.Dot
import io.github.usmandurrany.tether.demo.ui.components.Empty
import io.github.usmandurrany.tether.demo.ui.components.busy
import io.github.usmandurrany.tether.demo.ui.components.color
import io.github.usmandurrany.tether.demo.ui.components.label
import io.github.usmandurrany.tether.demo.ui.contacts.ContactsRoute
import io.github.usmandurrany.tether.demo.ui.log.LogRoute
import io.github.usmandurrany.tether.demo.ui.me.MeRoute
import io.github.usmandurrany.tether.demo.ui.theme.TetherIcons
import io.github.usmandurrany.tether.demo.ui.theme.chatColors

enum class HomeTab(val label: Int, val icon: ImageVector) {
    CHATS(R.string.tab_chats, TetherIcons.Chats),
    CONTACTS(R.string.tab_contacts, TetherIcons.Contacts),
    ME(R.string.tab_me, TetherIcons.Person),
    LOG(R.string.tab_log, TetherIcons.Log),
}

/** A ViewModel key per session, so signing in again gives every screen fresh state. */
fun TetherSession.key(name: String): String = "$name-${System.identityHashCode(this)}"

/**
 * The signed-in app: four tabs under one top bar. On a wide window (tablet,
 * foldable, landscape) Chats shows the open chat beside the list.
 */
@Composable
fun HomeRoute(
    app: DemoApp,
    session: TetherSession,
    onOpenChat: (BareJid) -> Unit,
    onSettings: () -> Unit,
    onSignOut: (forget: Boolean) -> Unit,
) {
    val viewModel = viewModel(key = session.key("home")) { HomeViewModel(session) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(HomeTab.CHATS) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    BoxWithConstraints {
        val twoPane = maxWidth >= 720.dp
        HomeScaffold(state, tab, onTabChange = { tab = it }, onRetry = viewModel::retry, onSettings = onSettings) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when (tab) {
                    HomeTab.CHATS -> if (twoPane) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.width(360.dp).fillMaxHeight()) {
                                ChatsRoute(session, onOpenChat = { selected = it.toString() })
                            }
                            VerticalDivider()
                            val peer = selected
                            if (peer == null) {
                                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    Empty(stringResource(R.string.chats_empty))
                                }
                            } else {
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    ChatRoute(session, BareJid.parse(peer), onBack = { selected = null }, showBack = false)
                                }
                            }
                        }
                    } else {
                        ChatsRoute(session, onOpenChat)
                    }
                    HomeTab.CONTACTS -> ContactsRoute(session, onOpenChat = { if (twoPane) { selected = it.toString(); tab = HomeTab.CHATS } else onOpenChat(it) })
                    HomeTab.ME -> MeRoute(app, session, onSignOut)
                    HomeTab.LOG -> LogRoute(session)
                }
            }
        }
    }
}

/** The top bar (account, live connection state, settings) and the bottom navigation. */
@Composable
fun HomeScaffold(
    state: HomeUiState,
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(state.myAvatar, state.myName, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(state.account, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Dot(state.connection.color())
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        state.connection.label(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (state.connection is ConnectionState.Terminal) {
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                        }
                        IconButton(onClick = onSettings) {
                            Icon(TetherIcons.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                if (state.connection.busy) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().height(3.dp),
                        color = chatColors.away,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                HomeTab.entries.forEach { item ->
                    val badge = when (item) {
                        HomeTab.CHATS -> state.unreadTotal
                        HomeTab.CONTACTS -> state.requests
                        else -> 0
                    }
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { onTabChange(item) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badge > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                                            Text(badge.toString())
                                        }
                                    }
                                },
                            ) { Icon(item.icon, contentDescription = null) }
                        },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
        content = content,
    )
}
