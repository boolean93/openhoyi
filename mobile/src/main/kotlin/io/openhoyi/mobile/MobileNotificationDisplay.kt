package io.openhoyi.mobile

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Pure Android display construction using the owner's live resources. No posting or control calls. */
internal class MobileNotificationDisplay(private val context: Context) {
    fun connection(warning: String?, channel: String): Notification {
        val open = PendingIntent.getActivity(context, 0, Intent(context, HomeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(context, 1, Intent(context, MobileService::class.java).setAction(MobileService.STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(if (warning == null) "OpenHOYI Alpha" else context.getString(R.string.notification_attention))
            .setContentText(warning ?: context.getString(R.string.notification_running))
            .setStyle(warning?.let { Notification.BigTextStyle().bigText(it) })
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.notification_disconnect), stop).build()).build()
    }
    fun createChannels(manager: NotificationManager, connection: String, safety: String) {
        manager.createNotificationChannel(NotificationChannel(connection, context.getString(R.string.home_device_connection), NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(safety, context.getString(R.string.notification_safety_channel), NotificationManager.IMPORTANCE_HIGH))
    }
    fun safety(warning: String, destination: Class<out Activity>, channel: String, displayOnly: Boolean): Notification {
        val open = PendingIntent.getActivity(context, 2, Intent(context, destination),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(context.getString(R.string.notification_check_machine))
            .setContentText(warning)
            .setOnlyAlertOnce(displayOnly)
            .setStyle(Notification.BigTextStyle().bigText(warning))
            .setContentIntent(open).setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true).build()
    }
}
