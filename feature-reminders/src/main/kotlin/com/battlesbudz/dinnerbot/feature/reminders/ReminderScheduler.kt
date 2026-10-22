package com.battlesbudz.dinnerbot.feature.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime

/**
 * Carries out a [ReminderPlanner] decision with WorkManager. WorkManager
 * is the on-device mechanism: requests persist in its own database and
 * are rescheduled by the platform after a reboot, with no server, no
 * account, and no network involved.
 *
 * Rescheduling is wholesale: every reminder request carries [TAG], a
 * reschedule cancels the whole tagged set and enqueues the fresh plan,
 * so plan changes (generate, swap, night-off, reprice) and settings
 * changes can never leave a stale reminder behind. Each reminder also
 * has a stable unique name, and re-enqueueing replaces by name.
 */
object ReminderScheduler {
    const val TAG: String = "dinnerbot-reminders"

    fun reschedule(context: Context, reminders: List<PlannedReminder>, now: LocalDateTime = LocalDateTime.now()) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelAllWorkByTag(TAG)
        for (reminder in reminders) {
            val delay = Duration.between(now, reminder.triggerAt)
            if (delay.isNegative || delay.isZero) continue
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay)
                .addTag(TAG)
                .setInputData(
                    Data.Builder()
                        .putString(ReminderWorker.KEY_TITLE, reminder.title)
                        .putString(ReminderWorker.KEY_TEXT, reminder.text)
                        .putInt(ReminderWorker.KEY_NOTIFICATION_ID, reminder.uniqueName.hashCode())
                        .build(),
                )
                .build()
            workManager.enqueueUniqueWork(reminder.uniqueName, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
