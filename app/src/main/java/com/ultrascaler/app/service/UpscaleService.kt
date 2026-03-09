package com.ultrascaler.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ultrascaler.app.R
import com.ultrascaler.app.UltraScalerApp
import com.ultrascaler.app.ui.MainActivity

class UpscaleService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Processing video...", 0)
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    fun updateProgress(progress: Int) {
        val notification = createNotification("Processing: $progress%", progress)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(content: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, UltraScalerApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("UltraScaler")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_upscale)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
