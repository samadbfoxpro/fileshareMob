package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

class FileShareService : Service() {

    private var server: FileShareServer? = null
    private lateinit var repository: FileShareRepository

    companion object {
        const val PORT = 8886
        const val ACTION_START_SERVER = "com.example.ACTION_START_SERVER"
        const val ACTION_STOP_SERVER = "com.example.ACTION_STOP_SERVER"
        
        private const val CHANNEL_ID = "FileShareServerChannel"
        private const val NOTIFICATION_ID = 1205

        val isRunning = MutableStateFlow(false)
        val serverAddresses = MutableStateFlow<List<String>>(emptyList())
        val serverError = MutableStateFlow<String?>(null)

        fun startService(context: Context) {
            val intent = Intent(context, FileShareService::class.java).apply {
                action = ACTION_START_SERVER
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FileShareService::class.java).apply {
                action = ACTION_STOP_SERVER
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = FileShareRepository.getInstance(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START_SERVER) {
            startServer()
        } else if (action == ACTION_STOP_SERVER) {
            stopServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        if (isRunning.value) return

        serverError.value = null
        
        try {
            server?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val newServer = FileShareServer(PORT, repository, applicationContext)
        server = newServer

        try {
            newServer.start()
            isRunning.value = true
            
            val ips = getLocalIpAddresses()
            serverAddresses.value = ips
            
            // Build notification and show
            createNotificationChannel()
            val notification = buildServiceNotification(ips)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

        } catch (e: IOException) {
            e.printStackTrace()
            isRunning.value = false
            serverAddresses.value = emptyList()
            if (e.message?.contains("Bind", ignoreCase = true) == true || e.message?.contains("EADDRINUSE", ignoreCase = true) == true) {
                serverError.value = "پورت ۸۸۸۶ توسط برنامه دیگری در حال استفاده است."
            } else {
                serverError.value = "خطا در اجرای سرور: ${e.message}"
            }
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning.value = false
            serverAddresses.value = emptyList()
            serverError.value = "خطای غیرمنتظره: ${e.message}"
            stopSelf()
        }
    }

    private fun stopServer() {
        try {
            server?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            server = null
            isRunning.value = false
            serverAddresses.value = emptyList()
            serverError.value = null
            
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "سرور انتقال فایل محلی FileShare",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش اتصال فعال برای انتقال فایل‌ها در شبکه محلی"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildServiceNotification(ips: List<String>): Notification {
        // Main intent to open activity
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            mainIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop button intent
        val stopIntent = Intent(this, FileShareService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 
            1, 
            stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val primaryAddress = if (ips.isNotEmpty()) "http://${ips[0]}:$PORT" else "http://localhost:$PORT"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("سرور FileShare v1.0.0 فعال است")
            .setContentText("آدرس های اتصال: $primaryAddress")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(mainPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "توقف سرور",
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    private fun getLocalIpAddresses(): List<String> {
        val wifiAddresses = mutableListOf<String>()
        val otherAddresses = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                val name = iface.name.lowercase()
                if (!iface.isUp) continue
                
                // Skip useless/non-local interfaces
                if (name.contains("dummy") || name.contains("lo") || name.contains("p2p")) {
                    continue
                }
                
                val addrs = Collections.list(iface.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        var sAddr = addr.hostAddress ?: ""
                        if (sAddr.contains("%")) {
                            sAddr = sAddr.split("%")[0]
                        }
                        
                        if (!sAddr.contains(":")) { // Only IPv4
                            val isWifiOrAp = name.contains("wlan") || 
                                             name.contains("ap") || 
                                             name.contains("wifi") || 
                                             name.contains("eth") || 
                                             name.contains("softap") ||
                                             name.contains("rndis")
                            
                            val isCellular = name.contains("rmnet") || 
                                             name.contains("pdp") || 
                                             name.contains("ccmni") || 
                                             (name.contains("usb") && !name.contains("rndis"))
                            
                            if (isWifiOrAp) {
                                wifiAddresses.add(sAddr)
                            } else if (!isCellular) {
                                otherAddresses.add(sAddr)
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        
        return if (wifiAddresses.isNotEmpty()) {
            wifiAddresses.distinct()
        } else {
            otherAddresses.distinct()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}
