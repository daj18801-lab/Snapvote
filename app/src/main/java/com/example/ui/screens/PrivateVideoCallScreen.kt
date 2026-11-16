package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.MicAudioChunk
import com.example.data.model.VideoFrameChunk
import com.example.data.model.VideoSession
import com.example.media.VideoCallCaptureController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PrivateVideoCallScreen(
    session: VideoSession,
    currentUserId: String,
    micLevel: Float,
    isMicCapturing: Boolean,
    videoFrames: List<VideoFrameChunk>,
    audioChunks: List<MicAudioChunk>,
    onSendVideoFrame: (sessionId: String, base64: String, width: Int, height: Int, seq: Long) -> Unit,
    onStartAudioCapture: (CoroutineScope, String) -> Unit,
    onStopAudioCapture: () -> Unit,
    onLeaveRoom: () -> Unit,
    onEndCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val captureScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedExplicitly by remember { mutableStateOf(false) }

    var isCameraEnabled by remember { mutableStateOf(true) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    val videoController = remember(context) { VideoCallCaptureController(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val camGranted = grants[Manifest.permission.CAMERA]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        val micGranted = grants[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        hasCameraPermission = camGranted
        hasAudioPermission = micGranted
        permissionDeniedExplicitly = !camGranted || !micGranted
    }

    BackHandler {
        videoController.unbind()
        onStopAudioCapture()
        onLeaveRoom()
    }

    LaunchedEffect(Unit) {
        val missing = buildList {
            if (!hasCameraPermission) add(Manifest.permission.CAMERA)
            if (!hasAudioPermission) add(Manifest.permission.RECORD_AUDIO)
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(hasAudioPermission, isMuted, session.sessionId) {
        if (hasAudioPermission && !isMuted) {
            onStartAudioCapture(captureScope, session.sessionId)
        } else {
            onStopAudioCapture()
        }
    }

    DisposableEffect(session.sessionId) {
        onDispose {
            videoController.release()
            onStopAudioCapture()
        }
    }

    LaunchedEffect(session.sessionId) {
        while (true) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    val isCreator = session.creatorId == currentUserId
    val peerName = if (isCreator) session.requesterName else session.creatorName

    val latestRemoteFrame = remember(videoFrames, currentUserId) {
        videoFrames.lastOrNull { it.senderId != currentUserId && it.frameBase64.isNotBlank() }
    }
    val remoteBitmap = remember(latestRemoteFrame?.frameId) {
        latestRemoteFrame?.let { VideoCallCaptureController.decodeBase64Bitmap(it.frameBase64)?.asImageBitmap() }
    }
    val remoteAudioPackets = remember(audioChunks, currentUserId) {
        audioChunks.count { it.senderId != currentUserId }
    }

    val smoothMicLevel by animateFloatAsState(targetValue = if (isMuted) 0f else micLevel, label = "videoMicLevel")
    val totalAllowedSeconds = session.durationMinutes * 60L
    val remainingSeconds = (totalAllowedSeconds - elapsedSeconds).coerceAtLeast(0L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    videoController.unbind()
                    onStopAudioCapture()
                    onLeaveRoom()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_call_back_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to private chat")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Video • $peerName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${session.durationMinutes} min • 100 video tokens/min • 70% creator share (${session.creatorShareUsdLabel()})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "${formatVideoSeconds(remainingSeconds)} left",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Main remote video stage + local camera PIP
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Color(0xFF10131C),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (remoteBitmap != null) {
                    Image(
                        bitmap = remoteBitmap,
                        contentDescription = "Live video from $peerName",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.tertiary,
                                            MaterialTheme.colorScheme.primary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = peerName.take(1).uppercase(Locale.ROOT),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Waiting for $peerName's video stream...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your live camera & voice feed automatically streams once permissions are granted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Local CameraX Preview Picture-in-Picture
                if (hasCameraPermission && isCameraEnabled) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .size(width = 112.dp, height = 152.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                            },
                            update = { previewView ->
                                videoController.bindCamera(
                                    lifecycleOwner = lifecycleOwner,
                                    previewView = previewView,
                                    useFrontCamera = useFrontCamera,
                                    isCameraEnabled = isCameraEnabled,
                                    onFrameEncoded = { frame ->
                                        cameraError = null
                                        onSendVideoFrame(
                                            session.sessionId,
                                            frame.base64,
                                            frame.width,
                                            frame.height,
                                            frame.sequenceNumber
                                        )
                                    },
                                    onError = { msg -> cameraError = msg }
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Live audio / camera status overlay badge at top-left
                Surface(
                    color = Color.Black.copy(alpha = 0.58f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = when {
                            cameraError != null -> "Cam fallback active • Voice packets: $remoteAudioPackets"
                            !isCameraEnabled -> "Camera paused • Voice packets: $remoteAudioPackets"
                            isMicCapturing -> "LIVE • Voice packets: $remoteAudioPackets"
                            else -> "Connecting camera & mic..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (!hasCameraPermission || !hasAudioPermission) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (permissionDeniedExplicitly) {
                            "Camera & microphone permissions are needed for 1-on-1 live video calls."
                        } else {
                            "Allow Camera & Microphone access to start live video."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("grant_video_permissions_button")
                    ) {
                        Text("Grant Camera & Mic")
                    }
                }
            }
        } else {
            LinearProgressIndicator(
                progress = { smoothMicLevel.coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }

        // Control deck
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { useFrontCamera = !useFrontCamera },
                enabled = hasCameraPermission && isCameraEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("video_flip_camera_button")
            ) {
                Icon(Icons.Rounded.Cameraswitch, contentDescription = "Flip camera")
            }

            OutlinedButton(
                onClick = {
                    isCameraEnabled = !isCameraEnabled
                    if (!isCameraEnabled) {
                        videoController.unbind()
                    }
                },
                enabled = hasCameraPermission,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("video_toggle_camera_button")
            ) {
                Icon(
                    imageVector = if (isCameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                    contentDescription = if (isCameraEnabled) "Turn camera off" else "Turn camera on"
                )
            }

            OutlinedButton(
                onClick = { isMuted = !isMuted },
                enabled = hasAudioPermission,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("video_toggle_mic_button")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = if (isMuted) "Unmute mic" else "Mute mic"
                )
            }

            Button(
                onClick = {
                    videoController.unbind()
                    onStopAudioCapture()
                    onEndCall(session.sessionId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .weight(1.3f)
                    .height(50.dp)
                    .testTag("end_video_call_button")
            ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("End")
            }
        }
    }
}

private fun formatVideoSeconds(totalSeconds: Long): String {
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
