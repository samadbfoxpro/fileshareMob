package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class ConnectionRequest(
    val ip: String,
    val nickname: String,
    val timestamp: Long
)

object ConnectionApprovalManager {
    val pendingRequest = MutableStateFlow<ConnectionRequest?>(null)
    private var activeApprovalFuture: CompletableFuture<String?>? = null
    
    private var lastApprovedIp = ""
    private var lastApprovedTime = 0L

    @Synchronized
    fun requestApprovalWithMode(ip: String, nickname: String, context: Context): String? {
        // Safe check: if already approved in the last 15 seconds, auto-approve to avoid spam
        val now = System.currentTimeMillis()
        if (ip == lastApprovedIp && now - lastApprovedTime < 15000) {
            return "two_way"
        }

        // Cancel previous pending requests if any
        activeApprovalFuture?.complete(null)

        val request = ConnectionRequest(ip, nickname, now)
        pendingRequest.value = request

        // Show foreground heads-up notification so user knows a request came
        showConnectionNotification(context, nickname, ip)

        val future = CompletableFuture<String?>()
        activeApprovalFuture = future

        return try {
            // Wait up to 30 seconds for the user to respond on the screen
            val approvedMode = future.get(30, TimeUnit.SECONDS)
            if (approvedMode != null) {
                lastApprovedIp = ip
                lastApprovedTime = System.currentTimeMillis()
            }
            approvedMode
        } catch (e: Exception) {
            null // Default to deny on timeout/cancel
        } finally {
            pendingRequest.value = null
            activeApprovalFuture = null
            cancelConnectionNotification(context)
        }
    }

    @Synchronized
    fun requestApproval(ip: String, nickname: String, context: Context): Boolean {
        return requestApprovalWithMode(ip, nickname, context) != null
    }

    fun approveTwoWay() {
        activeApprovalFuture?.complete("two_way")
    }

    fun approveOneWay() {
        activeApprovalFuture?.complete("one_way")
    }

    fun approve() {
        activeApprovalFuture?.complete("two_way")
    }

    fun deny() {
        activeApprovalFuture?.complete(null)
    }

    private const val CHANNEL_ID = "ConnectionApprovalChannel"
    private const val NOTIFICATION_ID = 1206

    private fun showConnectionNotification(context: Context, nickname: String, ip: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "درخواست‌های اتصال",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "نمایش نوتیفیکیشن هنگام تلاش دستگاه‌های دیگر برای اتصال"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("درخواست اتصال همگام‌سازی")
            .setContentText("دستگاه $nickname ($ip) می‌خواهد به شما متصل شود")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelConnectionNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}
