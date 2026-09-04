package com.project011.lifehealthplanner.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.project011.lifehealthplanner.R
import com.project011.lifehealthplanner.data.local.PlanItemEntity
import java.util.concurrent.TimeUnit
import java.time.ZonedDateTime

class ReminderWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        createChannel(applicationContext)
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        val message = inputData.getString(KEY_MESSAGE) ?: "计划即将开始，打开应用确认当前状态。"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(itemId.hashCode(), notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "plan_reminders"
        private const val KEY_TITLE = "title"
        private const val KEY_ITEM_ID = "item_id"
        private const val KEY_MESSAGE = "message"

        fun schedule(context: Context, item: PlanItemEntity, reminderMinutes: Int) {
            val triggerAt = item.startAt - TimeUnit.MINUTES.toMillis(reminderMinutes.toLong())
            val delay = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0)
            val data = Data.Builder()
                .putString(KEY_TITLE, item.title)
                .putString(KEY_ITEM_ID, item.id)
                .build()
            val work = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(ReminderPolicy.itemTag(item.id))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ReminderPolicy.workName(item.id, reminderMinutes),
                ExistingWorkPolicy.REPLACE,
                work,
            )
        }

        fun cancel(context: Context, itemId: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag(ReminderPolicy.itemTag(itemId))
        }

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "计划提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "已确认生活计划的本地提醒" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun scheduleDailyReview(context: Context) {
            val now = ZonedDateTime.now()
            var nextReview = now.withHour(21).withMinute(0).withSecond(0).withNano(0)
            if (!nextReview.isAfter(now)) nextReview = nextReview.plusDays(1)
            val delay = java.time.Duration.between(now, nextReview).toMillis()
            val data = Data.Builder()
                .putString(KEY_TITLE, "晚间复盘")
                .putString(KEY_ITEM_ID, "daily-review")
                .putString(KEY_MESSAGE, "记录今天的完成情况、难度和精力，为下一周期调整计划。")
                .build()
            val work = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("daily-review")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily-review",
                ExistingPeriodicWorkPolicy.UPDATE,
                work,
            )
        }
    }
}
