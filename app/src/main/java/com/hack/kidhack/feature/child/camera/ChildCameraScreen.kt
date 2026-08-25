package com.hack.kidhack.feature.child.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.BrandGradientHeader
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.core.permission.KidhackPermissions
import kotlinx.coroutines.delay

private data class GalleryTile(val emoji: String, val labelRes: Int)

@Composable
fun ChildCameraScreen() {
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    var tab by remember { mutableStateOf("camera") }
    var captured by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(captured) {
        if (captured) {
            delay(2000)
            captured = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        BrandGradientHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.child_camera_title),
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 20.sp,
                )
                CameraTabChip(
                    selected = tab == "camera",
                    label = stringResource(R.string.child_camera_tab_camera),
                    onClick = { tab = "camera" },
                )
                Spacer(Modifier.size(8.dp))
                CameraTabChip(
                    selected = tab == "gallery",
                    label = stringResource(R.string.child_camera_tab_gallery),
                    onClick = { tab = "gallery" },
                )
            }
        }

        if (tab == "camera") {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF111827)),
                contentAlignment = Alignment.Center,
            ) {
                if (hasPermission) {
                    CameraPreview(
                        lensFacing = lensFacing,
                        imageCapture = imageCapture,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 56.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.child_camera_need_permission),
                            color = Color(0xFF9CA3AF),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        KidButton(
                            text = stringResource(R.string.child_camera_grant),
                            onClick = { launcher.launch(KidhackPermissions.camera) },
                        )
                    }
                }
                GridOverlay()
                if (captured) {
                    Text(
                        text = "✓  ${stringResource(R.string.child_camera_captured)}",
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(colors.safe)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color.White,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleControl("🖼️") { tab = "gallery" }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            if (hasPermission) {
                                imageCapture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            image.close()
                                            captured = true
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            captured = true
                                        }
                                    },
                                )
                            } else {
                                launcher.launch(KidhackPermissions.camera)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(colors.brand),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📸", fontSize = 22.sp)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                CircleControl("🔄") {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }
            }
        } else {
            val tiles = listOf(
                GalleryTile("🏫", R.string.child_camera_school),
                GalleryTile("😄", R.string.child_camera_selfie),
                GalleryTile("🍕", R.string.child_camera_lunch),
                GalleryTile("📚", R.string.child_camera_study),
                GalleryTile("🐶", R.string.child_camera_dog),
                GalleryTile("🌅", R.string.child_camera_sunset),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.child_camera_recent),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                Spacer(Modifier.height(12.dp))
                tiles.chunked(3).forEach { row ->
                    Row {
                        row.forEach { tile ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFFE5E7EB), Color(0xFFD1D5DB)),
                                        ),
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(Modifier.weight(1f))
                                Text(tile.emoji, fontSize = 28.sp)
                                Text(
                                    text = stringResource(tile.labelRes),
                                    color = colors.muted,
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontSemibold,
                                    fontSize = 12.sp,
                                )
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraTabChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = if (selected) colors.brand else Color.White,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

@Composable
private fun CircleControl(emoji: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF374151))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 20.sp)
    }
}

@Composable
private fun GridOverlay() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .offset(y = (-80).dp)
                .background(Color.White.copy(alpha = 0.1f)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .offset(y = 80.dp)
                .background(Color.White.copy(alpha = 0.1f)),
        )
    }
}

@Composable
private fun CameraPreview(
    lensFacing: Int,
    imageCapture: ImageCapture,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lensFacing, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
