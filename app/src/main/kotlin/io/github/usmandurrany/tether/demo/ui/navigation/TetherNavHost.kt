package io.github.usmandurrany.tether.demo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.demo.DemoApp
import io.github.usmandurrany.tether.demo.ui.chat.ChatRoute
import io.github.usmandurrany.tether.demo.ui.home.HomeRoute
import io.github.usmandurrany.tether.demo.ui.signin.SettingsRoute
import kotlinx.serialization.Serializable

/** The app's screens, as type-safe Navigation Compose routes. */
sealed interface Destination {
    /** First run, or after sign-out: the connection settings page with no way back. */
    @Serializable data object SignIn : Destination

    /** The same page opened from the app, to change settings and reconnect. */
    @Serializable data object Settings : Destination

    /** The tabs: Chats, Contacts, Me and Log. */
    @Serializable data object Home : Destination

    @Serializable data class Chat(val jid: String) : Destination
}

/**
 * Navigation follows the session: no session means the sign-in page, a new
 * session leaves it for the app. A chat asked for by a notification opens as
 * soon as there is a session.
 */
@Composable
fun TetherNavHost(app: DemoApp, pendingChat: String?, onPendingChatHandled: () -> Unit) {
    val nav = rememberNavController()
    val session by app.session.collectAsStateWithLifecycle()
    val start: Destination = remember { if (app.session.value == null) Destination.SignIn else Destination.Home }

    LaunchedEffect(session) {
        val current = nav.currentDestination
        when {
            // Signed out, or disconnected from the Me tab.
            session == null && (current?.hasRoute<Destination.Home>() == true || current?.hasRoute<Destination.Chat>() == true) ->
                nav.toSignIn()
            // Signed in from the first-run page, or connected on app start.
            session != null && current?.hasRoute<Destination.SignIn>() == true ->
                nav.navigate(Destination.Home) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }
    LaunchedEffect(pendingChat, session) {
        if (pendingChat != null && session != null) {
            nav.navigate(Destination.Chat(pendingChat)) { launchSingleTop = true }
            onPendingChatHandled()
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable<Destination.SignIn> {
            // Leaving is the session's job (above): a sign-in sets it.
            SettingsRoute(app, canGoBack = false, onBack = {}, onConnected = {})
        }
        composable<Destination.Settings> {
            SettingsRoute(app, canGoBack = true, onBack = { nav.popBackStack() }, onConnected = { nav.popBackStack() })
        }
        composable<Destination.Home> {
            val current = session
            if (current == null) {
                LaunchedEffect(Unit) { nav.toSignIn() }
            } else {
                HomeRoute(
                    app = app,
                    session = current,
                    onOpenChat = { nav.navigate(Destination.Chat(it.toString())) { launchSingleTop = true } },
                    onSettings = { nav.navigate(Destination.Settings) { launchSingleTop = true } },
                    onSignOut = { forget -> app.signOut(forget) },
                )
            }
        }
        composable<Destination.Chat> { entry ->
            val current = session
            val peer = entry.toRoute<Destination.Chat>().jid
            if (current == null) {
                LaunchedEffect(Unit) { nav.toSignIn() }
            } else {
                ChatRoute(current, BareJid.parse(peer), onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.toSignIn() {
    navigate(Destination.SignIn) { popUpTo(graph.id) { inclusive = true } }
}
