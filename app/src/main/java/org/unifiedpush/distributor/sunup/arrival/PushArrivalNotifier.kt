package org.unifiedpush.distributor.sunup.arrival

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.distributor.sunup.DatabaseFactory
import org.unifiedpush.distributor.sunup.R
import org.unifiedpush.distributor.sunup.activities.MainActivity

object PushArrivalNotifier {
    const val CHANNEL_ID = "push_arrival"
    private const val NOTIFICATION_ID = 51315
    private const val COUNT = "sunup.arrival.count"
    private const val LAST_ALERT = "sunup.arrival.lastAlert"

    // Uses the shared coroutine pool, with serialized updates; no dedicated thread or connection.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun enqueue(
        context: Context,
        server: String,
        uaid: String?,
        channel: String,
        version: String
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                show(appContext, server, uaid.orEmpty(), channel, version)
            } catch (e: Exception) {
                // Notification permission, package and storage failures cannot affect delivery/ACK.
                Log.w("PushArrivalNotifier", "Could not show push arrival reminder", e)
            }
        }
    }

    internal fun show(
        context: Context,
        server: String,
        uaid: String,
        channel: String,
        version: String
    ) {
        val app = DatabaseFactory.getDb(context).getAppFromChanId(channel) ?: return
        if (app.isLocal(context)) return
        ArrivalStore(context).use { store ->
            if (!store.isEnabled(app.packageName)) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.arrival_channel),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = context.getString(R.string.arrival_channel_description)
                    }
                )
                if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return
            }
            val pm = context.packageManager
            val info = try {
                pm.getApplicationInfo(app.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                manager.cancel(tag(app.packageName), NOTIFICATION_ID)
                return
            }
            val now = System.currentTimeMillis()
            if (!store.firstSeen(server, uaid, channel, version, now)) return
            val previous = manager.activeNotifications.firstOrNull {
                it.tag == tag(app.packageName) && it.id == NOTIFICATION_ID
            }?.notification?.extras
            val state = ArrivalPolicy.next(previous?.getInt(COUNT), previous?.getLong(LAST_ALERT), now)
            val launch = pm.getLaunchIntentForPackage(app.packageName)
                ?: Intent(context, MainActivity::class.java)
            val contentIntent = PendingIntent.getActivity(
                context,
                app.packageName.hashCode(),
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = context.resources.getQuantityString(R.plurals.arrival_body, state.count, state.count)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(pm.getApplicationLabel(info))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(!state.alert)
                .setDefaults(android.app.Notification.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addExtras(
                    Bundle().apply {
                        putInt(COUNT, state.count)
                        putLong(LAST_ALERT, state.lastAlert)
                    }
                )
            // Missing or malformed icons should not prevent a generic reminder.
            runCatching { pm.getApplicationIcon(info).toBitmap(96, 96) }.getOrNull()?.let {
                notification.setLargeIcon(it)
            }
            // A disable commits after any in-flight post, then cancels it from the UI process.
            store.ifEnabled(app.packageName) {
                manager.notify(tag(app.packageName), NOTIFICATION_ID, notification.build())
            }
        }
    }

    fun cancel(context: Context, packageName: String) {
        context.getSystemService(NotificationManager::class.java).cancel(tag(packageName), NOTIFICATION_ID)
    }

    private fun tag(packageName: String) = "push-arrival:$packageName"
}
