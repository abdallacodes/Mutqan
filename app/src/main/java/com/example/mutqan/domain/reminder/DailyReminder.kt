package com.example.mutqan.domain.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mutqan.MainActivity
import com.example.mutqan.R
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.HabitSnapshot
import com.example.mutqan.domain.HabitSnapshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * One reminder a day, at the time the user chose, saying what is left of today's ward; nothing when the ward is done.
 * An inexact alarm (no exact-alarm permission): it may come some minutes late, never early. Each firing sets the next
 * day's; app start, boot, an update and clock or time zone changes set it again from the saved settings.
 */
object DailyReminder {
    const val CHANNEL_ID = "daily_reminder"
    const val ACTION_FIRE = "com.mutqan.quran.action.DAILY_REMINDER"
    private const val NOTIFICATION_ID = 4101
    private const val TAG = "DailyReminder"

    fun createChannel(context: Context) {
        // Android 7 has no channels; its notifications go out without one.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.reminder_channel_desc)
            }
        )
    }

    /** The next time it goes off: today at [minutes] after midnight if that is still ahead, else tomorrow. */
    fun nextTime(now: Long, minutes: Int, zone: TimeZone): Long {
        // Calendar, not java.time: the app runs on Android 7, where java.time does not exist.
        val at = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minutes / 60 % 24)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // A firing a few seconds after its time must not set itself again for the same moment. A day on keeps the
        // time of day across a clock change.
        if (at.timeInMillis <= now + 30_000L) at.add(Calendar.DAY_OF_MONTH, 1)
        return at.timeInMillis
    }

    /** Turns the reminder on at [minutes] after midnight, or off, and sets or clears the alarm. */
    suspend fun set(context: Context, on: Boolean, minutes: Int) {
        val app = context.applicationContext
        UserPreferencesRepository(app).setReminder(on, minutes)
        if (on) schedule(app, minutes) else cancel(app)
    }

    /** Sets the alarm again from the saved settings. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        val settings = UserPreferencesRepository(app).reminder.first()
        if (settings.on) schedule(app, settings.minutes) else alarm(app)?.cancel(pending(app))
    }

    private fun schedule(context: Context, minutes: Int) {
        val at = nextTime(System.currentTimeMillis(), minutes, TimeZone.getDefault())
        alarm(context)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    private fun cancel(context: Context) {
        alarm(context)?.cancel(pending(context))
        clear(context)
    }

    /** Takes down today's reminder if it is still showing (the ward is done, or the reminder was turned off). */
    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun alarm(context: Context): AlarmManager? = context.getSystemService(AlarmManager::class.java)

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Posts today's reminder unless today's ward is done (or notifications are not allowed). */
    suspend fun notifyIfDue(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val (habit, _) = HabitSnapshots.load(context)
        if (habit.ward.complete) return
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = body(context, habit)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ward)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notifications are not allowed", e)
        }
    }

    /** What is left: the revision (done of all), the questions (answered of all), and the run of days so far. */
    fun body(context: Context, habit: HabitSnapshot): String {
        val ward = habit.ward
        val res = context.resources
        val parts = ArrayList<String>()
        if (ward.hasPortions && !ward.revisionDone) parts += context.getString(R.string.reminder_revision, ward.portionsDone, ward.portions)
        if (!ward.questionsDone) parts += context.getString(R.string.reminder_questions, ward.answered, ward.questions)
        if (habit.streak.days > 0) parts += res.getQuantityString(R.plurals.habit_streak_days, habit.streak.days, habit.streak.days)
        return parts.joinToString(" · ")
    }
}

/** Fires the daily reminder, and sets it again after a boot, an update of the app, or a change of the clock. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (intent.action == DailyReminder.ACTION_FIRE && UserPreferencesRepository(app).reminder.first().on) {
                    DailyReminder.notifyIfDue(app)
                }
                DailyReminder.refresh(app)
            } catch (e: Exception) {
                Log.w("ReminderReceiver", "Could not handle ${intent.action}", e)
            } finally {
                pending.finish()
            }
        }
    }
}
