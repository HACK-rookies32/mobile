package com.hack.kidhack.background.geofence

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.hack.kidhack.background.ChildAlertUploadWorker
import com.hack.kidhack.background.location.LocationBatchWorker
import com.hack.kidhack.background.location.LocationQueue
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.model.LocationPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

/**
 * 안전 구역 진입·이탈 처리.
 *
 * 예전에는 로그만 찍고 끝나서, 자녀가 구역을 벗어나도 서버가 그 사실을 알 방법이 없었다
 * (다음 15분 주기 위치 배치에 섞여 올라갈 뿐이라 판정이 그만큼 늦었다).
 *
 * 명세에 「지오펜스 이벤트」 전용 엔드포인트가 없으므로, 경계를 넘은 **바로 그 좌표**를
 * 큐에 넣고 즉시 업로드한다. 안전 구역은 서버가 갖고 있으므로(`GET/POST
 * /children/{childId}/geofences`) 그 좌표를 받은 서버가 이탈을 판정해 보호자 알림을 만든다.
 *
 * 자녀 화면에도 남겨서 「최근 알림」에 바로 뜨게 한다.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val locationQueue: LocationQueue by inject()
    private val alertStore: ChildAlertStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "geofence error " + GeofenceStatusCodes.getStatusCodeString(event.errorCode))
            return
        }
        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            return
        }

        val exited = transition == Geofence.GEOFENCE_TRANSITION_EXIT
        val ids = event.triggeringGeofences.orEmpty().joinToString { it.requestId }
        val location = event.triggeringLocation
        Log.i(TAG, "geofence " + (if (exited) "EXIT" else "ENTER") + " " + ids)

        // BroadcastReceiver 는 onReceive 가 끝나면 프로세스가 죽을 수 있다.
        // 업로드가 끝날 때까지 살려 둔다.
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                if (location != null) {
                    locationQueue.enqueue(
                        LocationPoint(
                            lat = location.latitude.toString(),
                            lng = location.longitude.toString(),
                            recordedAt = Instant.now().toString(),
                            label = null,
                        ),
                    )
                }
                // 큐에 좌표가 없더라도(위치 권한 문제 등) 그동안 쌓인 것을 바로 올린다.
                LocationBatchWorker.enqueueNow(appContext)

                alertStore.record(
                    type = if (exited) {
                        ChildAlertStore.TYPE_GEOFENCE_EXIT
                    } else {
                        ChildAlertStore.TYPE_GEOFENCE_ENTER
                    },
                    title = if (exited) "안전 구역을 벗어났어요" else "안전 구역에 도착했어요",
                    body = "보호자에게 알려졌어요",
                    // 경계에서 서성이면 ENTER/EXIT 가 번갈아 쏟아진다. 종류별로 묶어 준다.
                    dedupeKey = (if (exited) ChildAlertStore.TYPE_GEOFENCE_EXIT else ChildAlertStore.TYPE_GEOFENCE_ENTER) + "-" + ids,
                    dedupeWindowMs = DEDUPE_MS,
                )
                ChildAlertUploadWorker.enqueueNow(appContext)
            }
            pending.finish()
        }
    }

    companion object {
        private const val TAG = "KidhackGeofence"
        private const val DEDUPE_MS = 60_000L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            return PendingIntent.getBroadcast(context, 0, intent, flags)
        }
    }
}
