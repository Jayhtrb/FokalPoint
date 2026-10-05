package com.fokalpoint.app.data.sync

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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fokalpoint.app.MainActivity
import com.fokalpoint.app.R
import com.fokalpoint.app.data.supabase.RemoteMappers
import com.fokalpoint.app.data.supabase.SupabaseClient
import java.util.concurrent.TimeUnit

/**
 * Periodic background check (every ~15 min, network required) that notifies the
 * signed-in user about new booking requests, booking status changes and messages.
 * Works without Firebase; add FCM later for instant push.
 */
class ActivityNotifier(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val client = SupabaseClient.get(applicationContext)
        if (!client.rest.isAvailable) return Result.success()
        val me = client.auth.currentSession()?.userId ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("fokal_notifier", Context.MODE_PRIVATE)
        val since = prefs.getLong("last_check_$me", System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20))
        val sinceIso = isoUtc(since)
        val now = System.currentTimeMillis()

        return try {
            val messages = client.rest.select(
                "messages", "receiver_id" to "eq.$me", "created_at" to "gt.$sinceIso", "order" to "created_at.desc", "limit" to "20"
            )
            if (messages.length() > 0) {
                val latest = RemoteMappers.message(messages.getJSONObject(0))
                notify(1001, if (messages.length() == 1) "New message" else "${messages.length()} new messages", latest.message)
            }
            val requests = client.rest.select(
                "bookings", "creator_id" to "eq.$me", "status" to "eq.Pending", "created_at" to "gt.$sinceIso"
            )
            if (requests.length() > 0) {
                notify(1002, "New booking request", if (requests.length() == 1) "Someone wants to book you — tap to respond." else "${requests.length()} new requests are waiting.")
            }
            prefs.edit().putLong("last_check_$me", now).apply()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notify(id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(applicationContext)
        val intent = PendingIntent.getActivity(
            applicationContext, id, Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fokal)
            .setColor(0xFF8B5CF6.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, n)
    }

    private fun isoUtc(t: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(t))

    companion object {
        const val CHANNEL = "activity"
        private const val WORK = "fokal_activity_check"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL, "Bookings & messages", NotificationManager.IMPORTANCE_DEFAULT)
                            .apply { description = "New booking requests, booking updates and messages" }
                    )
                }
            }
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ActivityNotifier>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            runCatching { WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request) }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
        }
    }
}
