package com.novarun.lite.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.novarun.lite.MainActivity
import com.novarun.lite.R
import com.novarun.lite.files.ProjectFileStore
import java.io.File

class LocalServerService : Service() {
    @Volatile private var localServer: LocalHttpServer? = null
    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                return START_NOT_STICKY
            }
            ACTION_START -> startServer(intent)
            else -> return START_NOT_STICKY
        }
        return START_REDELIVER_INTENT
    }

    private fun startServer(intent: Intent) {
        stopping = true
        localServer?.stop()
        localServer = null
        stopping = false
        isRunning = false

        val projectPath = intent.getStringExtra(EXTRA_PROJECT_PATH)
        val port = intent.getIntExtra(EXTRA_PORT, -1)
        val bindAddress = intent.getStringExtra(EXTRA_BIND_ADDRESS) ?: "127.0.0.1"
        val token = intent.getStringExtra(EXTRA_AUTH_TOKEN)

        if (projectPath.isNullOrBlank() || port !in 1024..65535) {
            sendLog("Invalid server start configuration")
            stopSelf()
            return
        }

        startForegroundCompat(buildNotification(port, bindAddress))
        runCatching {
            ProjectFileStore(File(projectPath))
            LocalHttpServer(
                File(projectPath),
                port,
                onLog = ::sendLog,
                onState = { running ->
                    isRunning = running
                    sendState(running)
                    if (!running && !stopping) {
                        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                },
                bindAddress = bindAddress,
                authToken = token
            ).also { server ->
                localServer = server
                server.start()
            }
        }.onFailure {
            sendLog(it.message ?: "Could not start local server")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopServer() {
        stopping = true
        localServer?.stop()
        localServer = null
        isRunning = false
        sendState(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    private fun buildNotification(port: Int, bindAddress: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, LocalServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("NovaRun Lite server")
            .setContentText("Serving project on $bindAddress:$port")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local development server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when a NovaRun project server is running"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun sendLog(message: String) {
        sendBroadcast(
            Intent(ACTION_LOG)
                .setPackage(packageName)
                .putExtra(EXTRA_MESSAGE, message)
        )
    }

    private fun sendState(running: Boolean) {
        sendBroadcast(
            Intent(ACTION_STATE)
                .setPackage(packageName)
                .putExtra(EXTRA_RUNNING, running)
        )
    }

    override fun onDestroy() {
        stopping = true
        localServer?.stop()
        localServer = null
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.novarun.lite.server.START"
        const val ACTION_STOP = "com.novarun.lite.server.STOP"
        const val ACTION_LOG = "com.novarun.lite.server.LOG"
        const val ACTION_STATE = "com.novarun.lite.server.STATE"
        const val EXTRA_PROJECT_PATH = "project_path"
        const val EXTRA_PORT = "port"
        const val EXTRA_BIND_ADDRESS = "bind_address"
        const val EXTRA_AUTH_TOKEN = "auth_token"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_RUNNING = "running"
        private const val CHANNEL_ID = "novarun_local_server"
        private const val NOTIFICATION_ID = 7301
        private const val REQUEST_OPEN = 7302
        private const val REQUEST_STOP = 7303

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
