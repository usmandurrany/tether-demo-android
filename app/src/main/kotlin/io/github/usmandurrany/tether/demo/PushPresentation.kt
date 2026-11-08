package io.github.usmandurrany.tether.demo

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.usmandurrany.tether.android.ConversationNotificationIds

/** Coordinates provisional foreground and confirmed chat notifications in this process. */
internal object PushPresentation {
    private const val CHANNEL = "messages"
    private data class Message(val title: String, val text: String, val peer: String, val unread: Int)
    private var foregroundPeer: String? = null
    private var foregroundId: Int? = null
    private val latest = linkedMapOf<String, Message>()

    @Synchronized fun begin(context: Context, sender: String?, body: String?): Int {
        foregroundPeer = sender?.substringBefore('/')?.takeIf { it.isNotBlank() }
        val peer = foregroundPeer
        foregroundId = if (peer == null) ConversationNotificationIds.RESERVED_SERVICE_ID else idFor(context, peer)
        if (peer != null) latest[peer] = Message(senderName(context, peer), body.orEmpty(), peer, 1)
        return foregroundId!!
    }

    @Synchronized fun showOrDefer(context: Context, title: String, text: String, peer: String, unread: Int) {
        if (title.isNotBlank()) context.getSharedPreferences("push_sender_names", Context.MODE_PRIVATE).edit().putString(peer, title).apply()
        val message = Message(title, text, peer, unread)
        latest[peer] = message
        post(context, message, peer == foregroundPeer && foregroundId != null)
    }

    @Synchronized fun finish(context: Context, @Suppress("UNUSED_PARAMETER") sender: String?, body: String?) {
        val peer = foregroundPeer
        foregroundPeer = null
        foregroundId = null
        val inBackground = !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (inBackground && peer != null) {
            post(context, latest[peer] ?: Message(senderName(context, peer), body.orEmpty(), peer, 1))
        } else if (peer != null) {
            context.getSystemService(NotificationManager::class.java).cancel(idFor(context, peer))
        }
        latest.clear()
    }

    @Synchronized fun pushArrived(context: Context, sender: String?, body: String?) {
        val peer = sender?.substringBefore('/')?.takeIf { it.isNotBlank() } ?: return
        val existing = latest[peer]
        val message = Message(senderName(context, peer), body ?: existing?.text.orEmpty(), peer, existing?.unread ?: 1)
        latest[peer] = message
        post(context, message, peer == foregroundPeer && foregroundId != null)
    }

    @Synchronized fun refreshForeground(context: Context) {
        val peer = foregroundPeer ?: return
        val message = latest[peer] ?: return
        val refreshed = message.copy(title = senderName(context, peer))
        latest[peer] = refreshed
        post(context, refreshed, true)
    }

    /** The SDK's conversation title is also used by the confirmed message notification. */
    fun senderName(context: Context, peer: String): String =
        (context.applicationContext as DemoApp).session.value?.chat?.conversations?.value
            ?.firstOrNull { it.peer.toString() == peer }?.name
            ?: context.getSharedPreferences("push_sender_names", Context.MODE_PRIVATE).getString(peer, null)
            ?: peer

    fun preview(context: Context, sender: String?, body: String?): android.app.Notification {
        val peer = sender?.substringBefore('/')
        ensureChannel(context)
        return builder(context, Message(peer?.let { senderName(context, it) } ?: context.getString(R.string.app_name),
            body.orEmpty(), peer.orEmpty(), 1)).setOngoing(true).build()
    }

    private fun idFor(context: Context, peer: String): Int =
        ConversationNotificationIds(context).idFor(peer)

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun builder(context: Context, message: Message): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            context, message.peer.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                if (message.peer.isNotBlank()) putExtra(MainActivity.EXTRA_PEER, message.peer)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(message.title)
            .setContentText(message.text.ifBlank { context.getString(R.string.new_message) })
            .setNumber(message.unread)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, message: Message, ongoing: Boolean = false) {
        if (!DemoPush.canNotify(context)) return
        ensureChannel(context)
        val notification = builder(context, message).setOngoing(ongoing).setAutoCancel(!ongoing).build()
        try { NotificationManagerCompat.from(context).notify(idFor(context, message.peer), notification) } catch (_: SecurityException) { }
    }
}
