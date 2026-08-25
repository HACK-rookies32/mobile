package com.hack.kidhack.background.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.hack.kidhack.R
import com.hack.kidhack.background.ChildNotifications

/**
 * MediaProjection foreground-service skeleton (REQ-M-12).
 *
 * The capture pipeline is stubbed: we startForeground with type
 * `mediaProjection` when a projection extra is present, then stop.
 * Without a user grant, API 34+ rejects this FGS type — we catch and exit.
 *
 * Parent immediate-request trigger is a no-op until FCM data is defined (GAP-11).
 * Do not add Firebase.
 */
class ScreenCaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra(EXTRA_RESULT_CODE) != true) {
            stopSelf()
            return START_NOT_STICKY
        }
        ChildNotifications.ensureChannel(this)
        try {
            ServiceCompat.startForeground(
                this,
                ChildNotifications.CAPTURE_ID,
                ChildNotifications.foreground(
                    this,
                    getString(R.string.child_capture_notif_title),
                    getString(R.string.child_capture_notif_text),
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Full bitmap/upload pipeline is intentionally not implemented yet.
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"

        fun start(context: Context, resultCode: Int) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
            context.startForegroundService(intent)
        }
    }
}

/** Placeholder until a documented FCM payload exists. */
object ParentCaptureRequestListener {
    fun onRemoteRequest() = Unit
}
