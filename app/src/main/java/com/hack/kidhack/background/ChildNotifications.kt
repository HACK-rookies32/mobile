package com.hack.kidhack.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hack.kidhack.R

object ChildNotifications {
    /** 상시 실행 서비스용(조용한 채널). */
    const val CHANNEL_ID = "kidhack_child_platform"

    /** 부모 요청 알림용. 헤드업으로 떠야 하므로 IMPORTANCE_HIGH. */
    const val REQUEST_CHANNEL_ID = "kidhack_child_request"

    const val LOCATION_ID = 7101
    const val CAPTURE_ID = 7102
    const val COMMAND_ID = 7103

    /** 요청 알림은 requestId 해시로 id 를 만들어 요청마다 개별 알림이 뜨게 한다. */
    fun requestNotificationId(requestId: String): Int = 7200 + (requestId.hashCode() and 0x0FFF)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.child_platform_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.child_platform_channel_desc)
                    setShowBadge(false)
                },
            )
        }
        if (manager.getNotificationChannel(REQUEST_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    REQUEST_CHANNEL_ID,
                    context.getString(R.string.child_request_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.child_request_channel_desc)
                    enableVibration(true)
                },
            )
        }
    }

    fun foreground(context: Context, title: String, text: String): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
