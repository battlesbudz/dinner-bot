package com.battlesbudz.dinnerbot.feature.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Posts one reminder notification. The text arrives in the work input:
 * the worker holds no state of its own and reads no database, so what
 * fires is exactly what the planner decided when the plan last changed.
 *
 * If the notification permission has been revoked since the reminder was
 * scheduled, the worker quietly does nothing: reminders being
 * unavailable changes nothing else in the app.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    // The POST_NOTIFICATIONS check below is explicit; notify() is only
    // reached with the permission granted (or on a version without it).
    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val text = inputData.getString(KEY_TEXT) ?: return Result.success()
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, title.hashCode())

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Dinner, shopping, and defrost reminders scheduled on this phone."
            },
        )

        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.let { intent ->
                PendingIntent.getActivity(
                    applicationContext,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { if (launchIntent != null) setContentIntent(launchIntent) }
            .build()

        manager.notify(notificationId, notification)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID: String = "dinnerbot-reminders"
        const val KEY_TITLE: String = "title"
        const val KEY_TEXT: String = "text"
        const val KEY_NOTIFICATION_ID: String = "notificationId"
    }
}
