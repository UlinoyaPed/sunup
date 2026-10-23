package org.unifiedpush.distributor.sunup.arrival

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.unifiedpush.distributor.sunup.DatabaseFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PushArrivalNotifierTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Before fun setup() {
        context.deleteDatabase("push_arrival.db")
        manager.cancelAll()
        shadowOf(manager).setNotificationsEnabled(true)
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val db = DatabaseFactory.getDb(context).writableDatabase
        db.delete("apps", null, null)
        listOf("forkgram", "talk").forEach { name ->
            val pkg = "test.$name"
            shadowOf(context.packageManager).installPackage(
                PackageInfo().apply {
                    packageName = pkg
                    applicationInfo = ApplicationInfo().apply {
                        packageName = pkg
                        nonLocalizedLabel = name
                    }
                }
            )
            // Fixture only: avoid registration's worker/network startup.
            db.execSQL(
                "INSERT INTO apps (connectorToken, packageName, appToken, disabled) VALUES (?, ?, ?, 0)",
                arrayOf(name, pkg, name)
            )
        }
    }

    @Test fun defaultOffThenEnabledAppOnlyAndRepeatedIdentityDoesNotIncrement() {
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "1")
        assertTrue(manager.activeNotifications.isEmpty())
        ArrivalStore(context).use { it.setEnabled("test.forkgram", true) }
        PushArrivalNotifier.show(context, "s", "u", "talk", "1")
        assertTrue(manager.activeNotifications.isEmpty())
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "1")
        assertEquals(1, manager.activeNotifications.size)
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "1")
        assertEquals(1, manager.activeNotifications.single().notification.extras.getInt("sunup.arrival.count"))
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "2")
        val notification = manager.activeNotifications.single().notification
        assertEquals(2, notification.extras.getInt("sunup.arrival.count"))
        assertTrue(notification.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(0, notification.flags and android.app.Notification.FLAG_ONGOING_EVENT)
        assertEquals(PushArrivalNotifier.CHANNEL_ID, notification.channelId)
        assertNotNull(notification.contentIntent)
        PushArrivalNotifier.cancel(context, "test.forkgram")
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "3")
        assertEquals(1, manager.activeNotifications.single().notification.extras.getInt("sunup.arrival.count"))
    }

    @Test fun permissionDeniedUnknownDisabledAndUninstalledAppsProduceNoReminder() {
        ArrivalStore(context).use { it.setEnabled("test.forkgram", true) }
        shadowOf(manager).setNotificationsEnabled(false)
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "1")
        assertTrue(manager.activeNotifications.isEmpty())
        shadowOf(manager).setNotificationsEnabled(true)
        PushArrivalNotifier.show(context, "s", "u", "missing", "1")
        DatabaseFactory.getDb(context).writableDatabase.execSQL("UPDATE apps SET disabled = 1 WHERE appToken = 'forkgram'")
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "2")
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    @Config(sdk = [24])
    fun legacyAndroidCanPostAndClear() {
        ArrivalStore(context).use { it.setEnabled("test.forkgram", true) }
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "1")
        assertEquals(1, manager.activeNotifications.size)
        PushArrivalNotifier.cancel(context, "test.forkgram")
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun blockedChannelIsSafe() {
        ArrivalStore(context).use { it.setEnabled("test.forkgram", true) }
        manager.createNotificationChannel(
            android.app.NotificationChannel(PushArrivalNotifier.CHANNEL_ID, "test", NotificationManager.IMPORTANCE_NONE)
        )
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "1")
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun missingPackageIsSafe() {
        ArrivalStore(context).use { it.setEnabled("test.forkgram", true) }
        shadowOf(context.packageManager).removePackage("test.forkgram")
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "1")
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun registrationsOfOnePackageShareTheNotificationAndSwitch() {
        DatabaseFactory.getDb(context).writableDatabase.execSQL(
            "INSERT INTO apps (connectorToken, packageName, appToken, disabled) VALUES (?, ?, ?, 0)",
            arrayOf("forkgram-second", "test.forkgram", "second-channel")
        )
        ArrivalStore(context).use { it.setEnabled("test.forkgram", true) }
        PushArrivalNotifier.show(context, "s", "u", "forkgram", "1")
        PushArrivalNotifier.show(context, "s", "u", "second-channel", "1")
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(2, manager.activeNotifications.single().notification.extras.getInt("sunup.arrival.count"))
        ArrivalStore(context).use { it.setEnabled("test.forkgram", false) }
        PushArrivalNotifier.cancel(context, "test.forkgram")
        PushArrivalNotifier.show(context, "s", "u", "second-channel", "2")
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
