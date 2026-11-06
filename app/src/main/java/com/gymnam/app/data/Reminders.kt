// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the two optional notifications (both OFF until you turn them
// on in Settings):
//  1. Log reminder: every day at your chosen hour, ONLY if you logged no food today.
//  2. Weekly summary: Sundays at 6 PM - days logged, average calories and protein,
//     and weight change vs. the week before.
// Both are scheduled with WorkManager (see Backup.kt for what that is).
// ==============================================================================
package com.gymnam.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gymnam.app.GymNamApp
import com.gymnam.app.MainActivity
import com.gymnam.app.R
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Optional notifications. Both are off until you turn them on in Settings. */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val LOG_WORK = "log-reminder"
    private const val WEEKLY_WORK = "weekly-summary"

    // Re-reads your settings and schedules or cancels each notification.
    // setInitialDelay = wait until the first time (e.g. tonight 8 PM), then repeat
    // every 1 day / 7 days. UPDATE = replace the old schedule if the hour changed.
    /** Re-applies the schedule from settings. Safe to call any time. */
    fun sync(context: Context, s: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (s.logReminder) wm.enqueueUniquePeriodicWork(
            LOG_WORK, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<LogReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(nextAt(s.logReminderHour)), TimeUnit.MILLISECONDS).build()
        ) else wm.cancelUniqueWork(LOG_WORK)

        if (s.weeklySummary) {
            val now = LocalDateTime.now()
            var sunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(18, 0)
            if (!sunday.isAfter(now)) sunday = sunday.plusWeeks(1)
            wm.enqueueUniquePeriodicWork(
                WEEKLY_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(delayUntil(sunday), TimeUnit.MILLISECONDS).build()
            )
        } else wm.cancelUniqueWork(WEEKLY_WORK)
    }

    // The next time the clock shows that hour: today if still ahead, otherwise tomorrow.
    private fun nextAt(hour: Int): LocalDateTime {
        val now = LocalDateTime.now()
        val t = now.toLocalDate().atTime(hour, 0)
        return if (t.isAfter(now)) t else t.plusDays(1)
    }

    // Milliseconds from now until that time (never negative).
    private fun delayUntil(t: LocalDateTime) = Duration.between(LocalDateTime.now(), t).toMillis().coerceAtLeast(0)

    // Shows the notification. On Android 13+ (version 33) the app must have your
    // permission to post notifications; if not, it silently does nothing.
    // A "channel" is the category you can mute in Android's settings.
    // Tapping the notification opens the app (PendingIntent).
    // setSmallIcon = the little status-bar icon (must be one color).
    fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

// Runs once a day. Checks: still turned on? any food logged today? If none,
// shows "Don't forget to log today".
/** Evening nudge, only if you haven't logged any food today. */
class LogReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GymNamApp
        if (!app.settings.settings.first().logReminder) return Result.success()
        val today = LocalDate.now().toEpochDay()
        if (app.db.foodDao().entriesForDayNow(today).isEmpty()) {
            Reminders.notify(app, 1, app.getString(R.string.notif_log_title), app.getString(R.string.notif_log_body))
        }
        return Result.success()
    }
}

// Average calories = total calories of logged days / number of logged days
// (days you logged nothing are left out so they don't drag the average down).
// Weight change = average of this week's weigh-ins - average of last week's,
// rounded to 0.1 lb.
/** Sunday evening: your week in numbers. */
class WeeklySummaryWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GymNamApp
        val s = app.settings.settings.first()
        if (!s.weeklySummary) return Result.success()
        val today = LocalDate.now().toEpochDay()
        val days = (today - 6..today).map { app.db.foodDao().entriesForDayNow(it) }.filter { it.isNotEmpty() }
        val avgKcal = if (days.isEmpty()) 0 else days.sumOf { d -> d.sumOf { it.calories } } / days.size
        val avgProtein = if (days.isEmpty()) 0 else (days.sumOf { d -> d.sumOf { it.proteinG } } / days.size).roundToInt()
        val weights = app.db.trackingDao().allWeights().filter { it.epochDay >= today - 13 }.sortedBy { it.epochDay }
        val thisWeek = weights.filter { it.epochDay > today - 7 }.map { it.lb }
        val lastWeek = weights.filter { it.epochDay <= today - 7 }.map { it.lb }
        val change = if (thisWeek.isNotEmpty() && lastWeek.isNotEmpty())
            Math.round((thisWeek.average() - lastWeek.average()) * 10) / 10.0 else null
        val text = buildString {
            append(app.getString(R.string.notif_week_body, days.size, avgKcal, s.targets.calories, avgProtein))
            change?.let { append(" ").append(app.getString(R.string.notif_week_weight, Units.weightChange(it, s.metric))) }
        }
        Reminders.notify(app, 2, app.getString(R.string.notif_week_title), text)
        return Result.success()
    }
}
