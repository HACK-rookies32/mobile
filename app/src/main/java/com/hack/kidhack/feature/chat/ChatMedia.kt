package com.hack.kidhack.feature.chat

import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.permission.KidhackPermissions
import java.io.File

private const val TAG = "ChatMedia"

/**
 * 채팅 입력줄 옆 첨부 버튼 세 개.
 *
 * - 🎤 마이크: 탭하면 녹음 시작, 다시 탭하면 멈춰 [onAudio] 로 넘긴다(m4a/AAC 임시 파일).
 * - 📷 카메라: [ActivityResultContracts.TakePicture] 로 임시 파일에 찍어 [onImage].
 * - 🖼 갤러리: 안드로이드 포토 피커([ActivityResultContracts.PickVisualMedia], 권한 불필요) → [onImage].
 *
 * 카메라/마이크 권한은 버튼을 누르는 순간에 없으면 다시 요청한다(온보딩 거절에 대한 안전망).
 * 콜백은 업로드 전의 로컬 URI(문자열)를 준다. 업로드·전송은 호출부(ViewModel)가 맡는다.
 */
@Composable
fun ChatMediaButtons(
    onImage: (String) -> Unit,
    onAudio: (String) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val context = LocalContext.current

    // 카메라가 찍을 임시 파일의 FileProvider URI. 촬영 성공 시 이 값을 넘긴다.
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (success && uri != null) onImage(uri.toString())
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "chat-photo-${System.currentTimeMillis()}.jpg")
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return
        pendingPhotoUri = uri
        runCatching { takePicture.launch(uri) }
            .onFailure { Log.w(TAG, "camera launch failed", it) }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCamera() }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onImage(uri.toString()) }

    // ---- 녹음 상태 ----
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }

    fun stopRecording(emit: Boolean) {
        val rec = recorder
        recorder = null
        recording = false
        val file = audioFile
        audioFile = null
        runCatching {
            rec?.stop()
        }.onFailure { Log.w(TAG, "recorder stop failed", it) }
        runCatching { rec?.release() }
        if (emit && file != null && file.exists() && file.length() > 0) {
            onAudio(Uri.fromFile(file).toString())
        } else {
            runCatching { file?.delete() }
        }
    }

    fun startRecording() {
        val file = File(context.cacheDir, "chat-audio-${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        val ok = runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
        }.isSuccess
        if (ok) {
            recorder = rec
            audioFile = file
            recording = true
        } else {
            runCatching { rec.release() }
            runCatching { file.delete() }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startRecording() }

    fun toggleRecording() {
        if (recording) {
            stopRecording(emit = true)
        } else if (KidhackPermissions.hasMicrophone(context)) {
            startRecording()
        } else {
            micPermission.launch(KidhackPermissions.microphone)
        }
    }

    // 화면을 벗어나면 녹음을 버린다(파일도 지운다).
    DisposableEffect(Unit) {
        onDispose { if (recorder != null) stopRecording(emit = false) }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        MediaIconButton(
            iconRes = R.drawable.ic_mic,
            highlighted = recording,
            tint = tint,
            contentDescription = "음성 녹음",
            onClick = { toggleRecording() },
        )
        Spacer(Modifier.width(2.dp))
        MediaIconButton(
            iconRes = R.drawable.ic_camera,
            tint = tint,
            contentDescription = "사진 촬영",
            onClick = {
                if (KidhackPermissions.hasCamera(context)) launchCamera()
                else cameraPermission.launch(KidhackPermissions.camera)
            },
        )
        Spacer(Modifier.width(2.dp))
        MediaIconButton(
            iconRes = R.drawable.ic_image,
            tint = tint,
            contentDescription = "사진 첨부",
            onClick = {
                pickMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
    }
}

@Composable
private fun MediaIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    tint: Color = Color.Unspecified,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (highlighted) {
                com.hack.kidhack.core.designsystem.theme.LocalKidhackColors.current.danger
            } else if (tint == Color.Unspecified) {
                com.hack.kidhack.core.designsystem.theme.LocalKidhackColors.current.muted
            } else {
                tint
            },
        )
    }
}

/** 이미지 메시지 본문. 서버 상대 경로를 절대 URL 로 바꿔 그린다. */
@Composable
fun ChatImageBubble(mediaUrl: String?, modifier: Modifier = Modifier) {
    if (mediaUrl.isNullOrBlank()) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(RemoteUrl.absolute(mediaUrl))
            .crossfade(true)
            .build(),
        contentDescription = "사진 메시지",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .widthIn(max = 200.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp)),
    )
}

/**
 * 음성 메시지 본문. 재생 버튼 하나로 [MediaPlayer] 를 켜고 끈다.
 * [bubbleColor] 배경 위에 [contentColor] 로 아이콘·글자를 그린다.
 */
@Composable
fun ChatAudioBubble(
    mediaUrl: String?,
    bubbleColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    fun release() {
        runCatching { player?.release() }
        player = null
        playing = false
    }

    DisposableEffect(mediaUrl) { onDispose { release() } }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bubbleColor)
            .clickable(enabled = !mediaUrl.isNullOrBlank()) {
                if (playing) {
                    release()
                } else {
                    val url = mediaUrl?.let { RemoteUrl.absolute(it) } ?: return@clickable
                    val mp = MediaPlayer()
                    val ok = runCatching {
                        mp.setDataSource(url)
                        mp.setOnCompletionListener { release() }
                        mp.setOnErrorListener { _, _, _ -> release(); true }
                        mp.setOnPreparedListener { it.start() }
                        mp.prepareAsync()
                    }.isSuccess
                    if (ok) {
                        player = mp
                        playing = true
                    } else {
                        runCatching { mp.release() }
                    }
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (playing) "⏸" else "▶",
            color = contentColor,
            fontSize = 15.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "음성 메시지",
            color = contentColor,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
    }
}
