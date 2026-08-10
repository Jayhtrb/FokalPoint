package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Booking
import com.example.ui.viewmodel.FokalViewModel
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderService(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "booking_reminders"
        const val CHANNEL_NAME = "Booking Reminders"
        const val NOTIFICATION_ID = 1000
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for upcoming bookings"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    fun getCreatorName(creatorId: String): String {
        return when (creatorId) {
            "amit_sharma_creator" -> "Amit Sharma"
            "riya_sen_creator" -> "Riya Sen"
            "kabir_singh_creator" -> "Kabir Studios"
            "vikram_goa_creator" -> "Vikram Fernandes"
            "manisha_mehta_creator" -> "Manisha Mehta"
            "current_customer_test" -> "Ananya Rao"
            else -> "Fokal Creator"
        }
    }

    fun scheduleReminder(booking: Booking) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Calculate reminder times
        val reminders = listOf(
            ReminderTime(24, TimeUnit.HOURS, "1 day before"),
            ReminderTime(2, TimeUnit.HOURS, "2 hours before"),
            ReminderTime(30, TimeUnit.MINUTES, "30 minutes before")
        )
        
        reminders.forEach { reminder ->
            val calendar = Calendar.getInstance().apply {
                time = booking.date.toDate()
                try {
                    val timeParts = booking.time.split(":")
                    if (timeParts.size == 2) {
                        set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                        set(Calendar.MINUTE, timeParts[1].toInt())
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                } catch (e: Exception) {
                    // fallback
                }
                
                if (reminder.unit == TimeUnit.HOURS) {
                    add(Calendar.HOUR_OF_DAY, -reminder.value.toInt())
                } else if (reminder.unit == TimeUnit.MINUTES) {
                    add(Calendar.MINUTE, -reminder.value.toInt())
                }
            }
            
            val intent = Intent(context, BookingReminderReceiver::class.java).apply {
                putExtra("booking_id", booking.id)
                putExtra("booking_event", booking.eventType)
                putExtra("booking_date", booking.date)
                putExtra("booking_time", booking.time)
                putExtra("creator_name", getCreatorName(booking.creatorId))
                putExtra("reminder_text", reminder.text)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                booking.id.toInt() + reminder.hours.toInt() + reminder.minutes.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (calendar.timeInMillis > System.currentTimeMillis()) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExact(
                                AlarmManager.RTC_WAKEUP,
                                calendar.timeInMillis,
                                pendingIntent
                            )
                        } else {
                            alarmManager.set(
                                AlarmManager.RTC_WAKEUP,
                                calendar.timeInMillis,
                                pendingIntent
                            )
                        }
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                } catch (e: SecurityException) {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            }
        }
    }
    
    fun cancelReminders(bookingId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Cancel all reminders for this booking
        val reminderTimes = listOf(
            Pair(24, 0), Pair(2, 0), Pair(0, 30)
        )
        
        reminderTimes.forEach { (hours, minutes) ->
            val intent = Intent(context, BookingReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                bookingId.toInt() + hours + minutes,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}

fun String.toDate(): java.util.Date {
    return try {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(this) ?: java.util.Date()
    } catch (e: Exception) {
        java.util.Date()
    }
}

data class ReminderTime(
    val value: Long,
    val unit: TimeUnit,
    val text: String
) {
    val hours: Long get() = if (unit == TimeUnit.HOURS) value else 0L
    val minutes: Long get() = if (unit == TimeUnit.MINUTES) value else 0L
}

class BookingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bookingId = intent.getLongExtra("booking_id", -1)
        val eventType = intent.getStringExtra("booking_event") ?: "Booking"
        val date = intent.getStringExtra("booking_date") ?: ""
        val time = intent.getStringExtra("booking_time") ?: ""
        val creatorName = intent.getStringExtra("creator_name") ?: "Creator"
        val reminderText = intent.getStringExtra("reminder_text") ?: "Upcoming"
        
        showReminderNotification(
            context = context,
            bookingId = bookingId,
            eventType = eventType,
            date = date,
            time = time,
            creatorName = creatorName,
            reminderText = reminderText
        )
    }
    
    private fun showReminderNotification(
        context: Context,
        bookingId: Long,
        eventType: String,
        date: String,
        time: String,
        creatorName: String,
        reminderText: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, ReminderService.CHANNEL_ID)
            .setContentTitle("📸 Booking Reminder: $eventType")
            .setContentText("$reminderText with $creatorName on $date at $time")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$reminderText with $creatorName\nDate: $date\nTime: $time\nEvent: $eventType")
            )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        
        notificationManager.notify(bookingId.toInt(), notification)
    }
}

// Composable for Reminder Settings
@Composable
fun ReminderSettings(
    viewModel: FokalViewModel = viewModel()
) {
    var enableReminders by remember { mutableStateOf(true) }
    var remind24h by remember { mutableStateOf(true) }
    var remind2h by remember { mutableStateOf(true) }
    var remind30m by remember { mutableStateOf(true) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Booking Reminders",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Reminders")
                Switch(
                    checked = enableReminders,
                    onCheckedChange = { enableReminders = it }
                )
            }
            
            if (enableReminders) {
                Spacer(modifier = Modifier.height(8.dp))
                
                ReminderOption(
                    label = "1 Day Before",
                    checked = remind24h,
                    onCheckedChange = { remind24h = it }
                )
                ReminderOption(
                    label = "2 Hours Before",
                    checked = remind2h,
                    onCheckedChange = { remind2h = it }
                )
                ReminderOption(
                    label = "30 Minutes Before",
                    checked = remind30m,
                    onCheckedChange = { remind30m = it }
                )
            }
        }
    }
}

@Composable
fun ReminderOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
