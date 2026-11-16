package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.MicAudioChunk
import com.example.data.model.MicSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MicCallRoomScreen(
    session: MicSession,
    currentUserId: String,
    micLevel: Float,
    isMicCapturing: Boolean,
    audioChunks: List<MicAudioChunk>,
    onStartCapture: (CoroutineScope, String) -> Unit,
    onStopCapture: () -> Unit,
    onLeaveRoom: () -> Unit,
    onEndCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val captureScope = rememberCoroutineScope()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedExplicitly by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        permissionDeniedExplicitly = !granted
    }

    BackHandler {
        onStopCapture()
        onLeaveRoom()
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(hasAudioPermission, isMuted, session.sessionId) {
        if (hasAudioPermission && !isMuted) {
            onStartCapture(captureScope, session.sessionId)
        } else {
            onStopCapture()
        }
    }

    DisposableEffect(session.sessionId) {
        onDispose { onStopCapture() }
    }

    LaunchedEffect(session.sessionId) {
        while (true) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    val isCreator = session.creatorId == currentUserId
    val peerName = if (isCreator) session.requesterName else session.creatorName
    val remotePacketCount = remember(audioChunks, currentUserId) {
        audioChunks.count { it.senderId != currentUserId }
    }
    val smoothMicLevel by animateFloatAsState(targetValue = if (isMuted) 0f else micLevel, label = "micLevel")
    val totalAllowedSeconds = session.durationMinutes * 60L
    val remainingSeconds = (totalAllowedSeconds - elapsedSeconds).coerceAtLeast(0L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    onStopCapture()
                    onLeaveRoom()
                },
                modifier = Modifier.testTag("mic_call_back_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Mic Calls")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Mic-to-Mic Call",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = session.topic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = formatSeconds(remainingSeconds) + " left",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
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

                Text(
                    text = peerName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${session.durationMinutes} min • 5 tokens/min • Creator receives 70% (${session.creatorShareUsdLabel()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (!hasAudioPermission) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (permissionDeniedExplicitly) {
                                    "Microphone permission was denied. Grant microphone access to talk live."
                                } else {
                                    "Microphone permission is required for live voice conversation."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Enable Microphone")
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = when {
                                isMuted -> "Microphone muted"
                                isMicCapturing -> "Live Mic Connected • ${formatSeconds(elapsedSeconds)}"
                                else -> "Connecting microphone..."
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { smoothMicLevel.coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "Remote voice packets received: $remotePacketCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { isMuted = !isMuted },
                enabled = hasAudioPermission,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("mic_mute_toggle_button")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isMuted) "Unmute Mic" else "Mute Mic")
            }

            Button(
                onClick = { onEndCall(session.sessionId) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("end_mic_call_button")
            ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("End Call")
            }
        }
    }
}

private fun formatSeconds(totalSeconds: Long): String {
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
