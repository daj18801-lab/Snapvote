package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ChatPresence
import com.example.data.model.ChatRoomState
import com.example.ui.theme.formatDisplayNameWithRole
import com.example.ui.theme.roleBadgeForegroundColor
import kotlinx.coroutines.CoroutineScope

@Composable
fun ChatRoomVoiceStageBanner(
    roomState: ChatRoomState?,
    roomPresence: List<ChatPresence>,
    currentUserId: String,
    isCurrentUserMonitor: Boolean,
    isVoiceMutedByMonitor: Boolean,
    isRoomAudioMutedLocally: Boolean,
    onToggleLocalListenMute: () -> Unit,
    onOpenMembersSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleMembers = remember(roomPresence) { roomPresence.filter { !it.isInvisible } }
    val monitorPresence = remember(roomState?.monitorUid, visibleMembers) {
        val monitorUid = roomState?.monitorUid
        if (!monitorUid.isNullOrBlank()) {
            visibleMembers.firstOrNull { it.userId == monitorUid }
        } else {
            visibleMembers.firstOrNull()
        }
    }
    val monitorName = remember(roomState, monitorPresence) {
        when {
            monitorPresence != null -> formatDisplayNameWithRole(
                monitorPresence.displayName,
                monitorPresence.role
            )
            !roomState?.monitorName.isNullOrBlank() -> roomState?.monitorName.orEmpty()
            else -> "Waiting for first member"
        }
    }
    val monitorColor = monitorPresence?.let { roleBadgeForegroundColor(it.role) }
        ?: MaterialTheme.colorScheme.primary

    val activeSpeakers = remember(visibleMembers) {
        visibleMembers.filter { it.isSpeaking && !it.isVoiceMuted }
    }
    val raisedHands = remember(visibleMembers) {
        visibleMembers
            .filter { it.handRaised }
            .sortedWith(compareBy<ChatPresence> { it.handRaisedAt }.thenBy { it.userId })
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("room_voice_stage_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isCurrentUserMonitor) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .clickable(onClick = onOpenMembersSheet)
                            .testTag("room_monitor_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Room Monitor",
                                tint = monitorColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isCurrentUserMonitor) {
                                    "Monitor: You"
                                } else {
                                    "Monitor: $monitorName"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentUserMonitor) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    activeSpeakers.forEach { speaker ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("room_speaker_chip_${speaker.userId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Speaking",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (speaker.userId == currentUserId) {
                                        "You (Live)"
                                    } else {
                                        speaker.displayName
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (raisedHands.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable(onClick = onOpenMembersSheet)
                                .testTag("room_raised_hands_summary")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PanTool,
                                    contentDescription = "Raised hands",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                val previewNames = raisedHands.take(2).joinToString(", ") {
                                    if (it.userId == currentUserId) "You" else it.displayName
                                }
                                val extraCount = (raisedHands.size - 2).coerceAtLeast(0)
                                Text(
                                    text = if (extraCount > 0) {
                                        "${raisedHands.size}: $previewNames +$extraCount"
                                    } else {
                                        "${raisedHands.size}: $previewNames"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onToggleLocalListenMute,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("room_listen_toggle")
                ) {
                    Icon(
                        imageVector = if (isRoomAudioMutedLocally) {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        },
                        contentDescription = if (isRoomAudioMutedLocally) {
                            "Unmute room voice audio"
                        } else {
                            "Mute room voice audio"
                        },
                        tint = if (isRoomAudioMutedLocally) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (isVoiceMutedByMonitor) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("room_voice_muted_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MicOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "You are muted — mic and chat are disabled.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ChatRoomVoiceControlsBar(
    roomId: String,
    currentUserId: String,
    isStaffInvisible: Boolean,
    isGlobalMuted: Boolean,
    isVoiceMutedByMonitor: Boolean,
    isHandRaised: Boolean,
    micLevel: Float,
    isMicCapturing: Boolean,
    onStartVoiceTalk: (CoroutineScope) -> Unit,
    onStopVoiceTalk: () -> Unit,
    onToggleRaiseHand: () -> Unit,
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
    var permissionDenied by remember { mutableStateOf(false) }
    var isHoldingTalk by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        permissionDenied = !granted
    }

    val canTalk = !isStaffInvisible && !isGlobalMuted && !isVoiceMutedByMonitor

    LaunchedEffect(canTalk, isMicCapturing) {
        if (!canTalk && isMicCapturing) {
            isHoldingTalk = false
            onStopVoiceTalk()
        }
    }

    val isTalkingActive = isHoldingTalk || isMicCapturing
    val smoothMicLevel by animateFloatAsState(
        targetValue = if (isTalkingActive) micLevel else 0f,
        label = "roomMicLevel"
    )
    val pttContainerColor by animateColorAsState(
        targetValue = when {
            !canTalk -> MaterialTheme.colorScheme.surfaceContainerHighest
            isTalkingActive -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        label = "pttContainerColor"
    )
    val pttContentColor = when {
        !canTalk -> MaterialTheme.colorScheme.onSurfaceVariant
        isTalkingActive -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isTalkingActive) {
                LinearProgressIndicator(
                    progress = { smoothMicLevel.coerceIn(0.05f, 1f) },
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
            if (permissionDenied && !hasAudioPermission) {
                Text(
                    text = "Microphone permission is required for Push-to-Talk.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pttShape = RoundedCornerShape(24.dp)
                val pttModifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(pttShape)
                    .then(
                        when {
                            !canTalk -> Modifier
                            !hasAudioPermission -> Modifier.clickable {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            else -> Modifier.pointerInput(roomId, currentUserId, canTalk, hasAudioPermission) {
                                detectTapGestures(
                                    onPress = {
                                        isHoldingTalk = true
                                        onStartVoiceTalk(captureScope)
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            isHoldingTalk = false
                                            onStopVoiceTalk()
                                        }
                                    }
                                )
                            }
                        }
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = when {
                            isStaffInvisible -> "Push to talk unavailable in invisible mode"
                            isVoiceMutedByMonitor -> "Push to talk disabled, mic muted by room monitor"
                            isGlobalMuted -> "Push to talk disabled, account muted"
                            !hasAudioPermission -> "Enable microphone permission to talk"
                            isTalkingActive -> "Talking live, release to stop"
                            else -> "Hold button to talk"
                        }
                    }
                    .testTag("room_push_to_talk_button")

                Surface(
                    color = pttContainerColor,
                    contentColor = pttContentColor,
                    shape = pttShape,
                    modifier = pttModifier
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (canTalk) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = when {
                                    isStaffInvisible -> "Invisible Mode"
                                    isVoiceMutedByMonitor || isGlobalMuted -> "Muted • Mic & Chat Off"
                                    !hasAudioPermission -> "Enable Mic to Talk"
                                    isTalkingActive -> "Talking • Release"
                                    else -> "Hold to Talk"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isHandRaised) {
                    FilledTonalButton(
                        onClick = onToggleRaiseHand,
                        enabled = !isStaffInvisible,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("room_raise_hand_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PanTool,
                            contentDescription = "Lower raised hand",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hand Up", maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = onToggleRaiseHand,
                        enabled = !isStaffInvisible,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("room_raise_hand_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PanTool,
                            contentDescription = "Raise hand to speak",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Raise Hand", maxLines = 1)
                    }
                }
            }
        }
    }
}
