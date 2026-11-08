package io.github.usmandurrany.tether.demo

import android.content.Context
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.usmandurrany.tether.android.ConversationNotificationIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.app.Application

@RunWith(RobolectricTestRunner::class)
class ConversationNotificationIdsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun clearIds() {
        context.getSharedPreferences("tether_conversation_notification_ids", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun stableAcrossInstancesAndDistinctForHashCollisions() {
        // These Java strings have the same hash; neither may overwrite the other.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val first = ConversationNotificationIds(context).idFor("Aa")
        val second = ConversationNotificationIds(context).idFor("BB")
        assertNotEquals(first, second)
        assertEquals(first, ConversationNotificationIds(context).idFor("Aa"))
        assertEquals(second, ConversationNotificationIds(context).idFor("BB"))
        assertNotEquals(ConversationNotificationIds.RESERVED_SERVICE_ID, first)
        assertNotEquals(ConversationNotificationIds.RESERVED_SERVICE_ID, second)
    }

    @Test fun foregroundConversationUpdatesInPlaceAndOtherConversationsStaySeparate() {
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val first = "alice@example.test"
        val second = "bob@example.test"
        val id = PushPresentation.begin(context, first, "first")
        manager.notify(id, PushPresentation.preview(context, first, "first"))
        PushPresentation.showOrDefer(context, "Alice", "second", first, 2)
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(id, manager.activeNotifications.single().id)
        assertEquals("second", manager.activeNotifications.single().notification.extras.getCharSequence("android.text"))
        PushPresentation.showOrDefer(context, "Bob", "hello", second, 1)
        assertEquals(2, manager.activeNotifications.size)
        assertNotEquals(id, ConversationNotificationIds(context).idFor(second))
        PushPresentation.finish(context, first, "first")
        manager.cancelAll()
    }
}
