package io.cpamonitor.android.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.cpamonitor.android.MainActivity
import io.cpamonitor.android.R
import javax.inject.Inject
import javax.inject.Singleton

enum class AlertChannel(val id: String, val title: String) {
    QUOTA("quota", "配额提醒"),
    FAILURE("failure", "失败率提醒"),
    CONNECTION("connection", "连接与采集器"),
}

@Singleton
class NotificationHelper @Inject constructor(@ApplicationContext private val context: Context) {
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        AlertChannel.entries.forEach {
            manager.createNotificationChannel(
                NotificationChannel(it.id, it.title, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    fun show(id: Int, channel: AlertChannel, title: String, message: String, deepLink: String) {
        createChannels()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(Intent.ACTION_VIEW, deepLink.toUri(), context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
