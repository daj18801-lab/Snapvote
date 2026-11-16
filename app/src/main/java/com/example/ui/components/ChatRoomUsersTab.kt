package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatPresence
import com.example.data.model.EffectiveStaffTier
import com.example.data.model.FriendRelationship
import com.example.data.model.UserProfile
import com.example.data.model.UserReport
import com.example.ui.theme.AdminRed
import com.example.ui.theme.SupportBlue
import com.example.ui.theme.SupportBlueContainer
import com.example.ui.theme.SupportBlueOnContainer
import com.example.ui.theme.formatDisplayNameWithRole
import com.example.ui.theme.normalizeStaffRole
import com.example.ui.theme.roleBadgeContainerColor
import com.example.ui.theme.roleBadgeForegroundColor
import com.example.ui.theme.roleBadgeOnContainerColor
import java.util.Locale

data class RoomUserTarget(
    val userId: String,
    val displayName: String,
    val role: String,
    val messageId: String? = null,
    val isVoiceMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val handRaised: Boolean = false
)

@Composable
fun ChatRoomUsersTabContent(
    roomName: String,
    roomPresence: List<ChatPresence>,
    effectiveMonitorUid: String?,
    currentUserId: String,
    staffTier: EffectiveStaffTier,
    globallyMutedUserIds: Set<String>,
    friendRelationships: List<FriendRelationship>,
    onUserClick: (ChatPresence) -> Unit,
    modifier: Modifier = Modifier
) {
    val isAdminViewer = staffTier == EffectiveStaffTier.OWNER || staffTier == EffectiveStaffTier.ADMIN
    val visibleMembers = remember(roomPresence) { roomPresence.filter { !it.isInvisible } }
    val invisibleCount = remember(roomPresence) { roomPresence.count { it.isInvisible } }

    val activeSpeakers = remember(visibleMembers, globallyMutedUserIds) {
        visibleMembers.filter {
            it.isSpeaking && !it.isVoiceMuted && !globallyMutedUserIds.contains(it.userId)
        }
    }

    val monitorMember = remember(visibleMembers, effectiveMonitorUid) {
        visibleMembers.firstOrNull { it.userId == effectiveMonitorUid }
    }

    val sortedMembers = remember(roomPresence, effectiveMonitorUid, globallyMutedUserIds) {
        roomPresence.sortedWith(
            compareBy<ChatPresence> { it.isInvisible }
                .thenByDescending {
                    it.isSpeaking && !it.isVoiceMuted && !globallyMutedUserIds.contains(it.userId)
                }
                .thenByDescending { it.userId == effectiveMonitorUid && !it.isInvisible }
                .thenByDescending { it.handRaised }
                .thenBy { if (it.handRaised) it.handRaisedAt else Long.MAX_VALUE }
                .thenBy {
                    if (it.joinedAtMillis > 0L) it.joinedAtMillis
                    else it.joinedAt?.toDate()?.time ?: Long.MAX_VALUE
                }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("room_users_tab_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "live_mic_summary_card") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (activeSpeakers.isNotEmpty()) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    if (activeSpeakers.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("users_tab_live_mic_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (activeSpeakers.isNotEmpty()) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                            contentDescription = "Live microphone status",
                            tint = if (activeSpeakers.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (activeSpeakers.isNotEmpty()) {
                                    "Live on Mic (${activeSpeakers.size})"
                                } else {
                                    "Live Mic Stage"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = monitorMember?.let {
                                    val formatted = formatDisplayNameWithRole(it.displayName, it.role)
                                    "Monitor: $formatted • Tap any user below"
                                } ?: "Tap any user's name to add, DM, or view profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (activeSpeakers.isEmpty()) {
                        Text(
                            text = "Nobody is holding the mic right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeSpeakers.forEach { speaker ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable { onUserClick(speaker) }
                                        .testTag("live_mic_speaker_${speaker.userId}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Mic,
                                            contentDescription = "Using microphone",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = formatDisplayNameWithRole(speaker.displayName, speaker.role),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = roleBadgeForegroundColor(speaker.role)
                                                ?: MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "users_tab_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isAdminViewer && invisibleCount > 0) {
                        "Users in $roomName (${visibleMembers.size} visible, $invisibleCount invisible)"
                    } else {
                        "Users in $roomName (${visibleMembers.size})"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (sortedMembers.isEmpty()) {
            item(key = "empty_room_users") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No visible users in $roomName right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(sortedMembers, key = { it.userId }) { member ->
                val isEffectivelyMuted = member.isVoiceMuted || globallyMutedUserIds.contains(member.userId)
                val isUsingMic = member.isSpeaking && !isEffectivelyMuted
                val isMemberMonitor = member.userId == effectiveMonitorUid && !member.isInvisible
                val normalizedRole = normalizeStaffRole(member.role)
                val nameColor = roleBadgeForegroundColor(member.role) ?: MaterialTheme.colorScheme.onSurface
                val formattedName = formatDisplayNameWithRole(member.displayName, member.role)
                val friendRel = friendRelationships.firstOrNull { it.otherUserId(currentUserId) == member.userId }

                Surface(
                    color = if (isUsingMic) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(
                        width = 1.dp,
                        color = when {
                            isUsingMic -> MaterialTheme.colorScheme.primary
                            isEffectivelyMuted -> AdminRed.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onUserClick(member) }
                        .testTag("room_user_row_${member.userId}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isUsingMic -> MaterialTheme.colorScheme.primaryContainer
                                        isEffectivelyMuted -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isUsingMic -> Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Using mic",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                isEffectivelyMuted -> Icon(
                                    imageVector = Icons.Rounded.MicOff,
                                    contentDescription = "Muted",
                                    tint = AdminRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                else -> Text(
                                    text = member.displayName.trim().take(1).uppercase(Locale.ROOT).ifBlank { "U" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = nameColor
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isEffectivelyMuted) {
                                    MutedRedDot(
                                        testTag = "muted_red_dot_${member.userId}"
                                    )
                                }
                                Text(
                                    text = formattedName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = nameColor,
                                    fontWeight = if (normalizedRole != null) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (normalizedRole != null) {
                                    RoomUserRoleBadgeChip(role = member.role)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                if (isUsingMic) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.testTag("user_on_mic_badge_${member.userId}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Mic,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "ON MIC",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                                if (isMemberMonitor) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.testTag("monitor_member_badge_${member.userId}")
                                    ) {
                                        Text(
                                            text = "MONITOR",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (member.handRaised) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "HAND UP",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (isEffectivelyMuted) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.testTag("user_muted_badge_${member.userId}")
                                    ) {
                                        Text(
                                            text = "MUTED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (member.isInvisible) {
                                    Surface(
                                        color = SupportBlueContainer,
                                        border = BorderStroke(1.dp, SupportBlue.copy(alpha = 0.65f)),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.testTag("invisible_member_badge_${member.userId}")
                                    ) {
                                        Text(
                                            text = "INVISIBLE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SupportBlueOnContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                val subtitleText = when {
                                    member.userId == currentUserId -> "You"
                                    friendRel?.status == FriendRelationship.STATUS_ACCEPTED -> "Friend • Tap for actions"
                                    friendRel?.status == FriendRelationship.STATUS_PENDING &&
                                        friendRel.senderId == currentUserId -> "Request sent • Tap"
                                    friendRel?.status == FriendRelationship.STATUS_PENDING -> "Accept request • Tap"
                                    else -> "Tap for Add, DM & Profile"
                                }
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (member.userId == currentUserId) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MutedRedDot(
    testTag: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(AdminRed)
            .semantics { contentDescription = "User is muted" }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    )
}

@Composable
fun RoomUserRoleBadgeChip(role: String) {
    val normalized = normalizeStaffRole(role)
    val label = when (normalized) {
        UserProfile.ROLE_ADMIN -> "ADMIN"
        UserProfile.ROLE_SUPPORT -> "SUPPORT"
        else -> role.uppercase(Locale.ROOT)
    }
    val bgColor = roleBadgeContainerColor(role) ?: MaterialTheme.colorScheme.secondaryContainer
    val fgColor = roleBadgeOnContainerColor(role) ?: MaterialTheme.colorScheme.onSecondaryContainer
    val borderColor = when (normalized) {
        UserProfile.ROLE_ADMIN -> AdminRed.copy(alpha = 0.65f)
        UserProfile.ROLE_SUPPORT -> SupportBlue.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fgColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun RoomUserActionsDialog(
    target: RoomUserTarget,
    currentUserId: String,
    staffTier: EffectiveStaffTier,
    isCurrentUserMonitor: Boolean,
    roomPresence: List<ChatPresence>,
    globallyMutedUserIds: Set<String>,
    friendRelationships: List<FriendRelationship>,
    isActionBusy: Boolean,
    onDismiss: () -> Unit,
    onSendFriendRequest: (String, String) -> Unit,
    onRespondFriendRequest: (String, Boolean) -> Unit,
    onOpenDirectChatWithUser: (String, String) -> Unit,
    onOpenCreatorProfile: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onReportUserClick: (String, String, String) -> Unit,
    onSetMemberVoiceMuted: (String, String, Boolean) -> Unit,
    onUnmuteGlobalUser: (String) -> Unit,
    onLowerMemberHand: (String, String) -> Unit,
    onBounceUser: (String, String, Long) -> Unit,
    onMuteUser: (String, String, Long) -> Unit,
    onSuspendUser: (String, String, Long, String) -> Unit,
    onBanUser: (String, String) -> Unit,
    onBanAccountUser: (String, String, String) -> Unit,
    onWarnUser: (String, String) -> Unit = { _, _ -> }
) {
    val isStaff = staffTier != EffectiveStaffTier.NONE
    val isAdminActor = staffTier == EffectiveStaffTier.OWNER || staffTier == EffectiveStaffTier.ADMIN
    val isSupportActor = staffTier == EffectiveStaffTier.SUPPORT

    val livePresence = roomPresence.firstOrNull { it.userId == target.userId }
    val isGloballyMuted = globallyMutedUserIds.contains(target.userId)
    val liveVoiceMuted = (livePresence?.isVoiceMuted ?: target.isVoiceMuted) || isGloballyMuted
    val liveHandRaised = livePresence?.handRaised ?: target.handRaised
    val liveSpeaking = (livePresence?.isSpeaking ?: target.isSpeaking) && !liveVoiceMuted

    val targetNormalizedRole = normalizeStaffRole(target.role)
    val isTargetRegularUser = targetNormalizedRole == null
    val isTargetAdmin = targetNormalizedRole == UserProfile.ROLE_ADMIN

    val canModerateAccount = isStaff &&
        target.userId != currentUserId &&
        (isAdminActor || isTargetRegularUser)

    val canMonitorModerateRoom = (isCurrentUserMonitor || isStaff) &&
        target.userId != currentUserId &&
        (isAdminActor || (isSupportActor && !isTargetAdmin) || (!isStaff && isTargetRegularUser))

    val formattedTargetName = formatDisplayNameWithRole(target.displayName, target.role)
    val targetColor = roleBadgeForegroundColor(target.role) ?: MaterialTheme.colorScheme.onSurface
    val friendRel = friendRelationships.firstOrNull { it.otherUserId(currentUserId) == target.userId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (liveVoiceMuted) {
                    MutedRedDot(testTag = "dialog_muted_red_dot_${target.userId}")
                }
                Text(
                    text = formattedTargetName,
                    color = targetColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (liveSpeaking) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ON MIC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (target.userId != currentUserId) {
                    Text(
                        text = "Connect & View",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Add Friend / Friend Status
                    when {
                        friendRel?.status == FriendRelationship.STATUS_ACCEPTED -> {
                            FilledTonalButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("user_action_add_friend")
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Already Friends")
                            }
                        }
                        friendRel?.status == FriendRelationship.STATUS_PENDING &&
                            friendRel.receiverId == currentUserId -> {
                            Button(
                                onClick = {
                                    onRespondFriendRequest(friendRel.pairId, true)
                                    onDismiss()
                                },
                                enabled = !isActionBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("user_action_add_friend")
                            ) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Accept Friend Request")
                            }
                        }
                        friendRel?.status == FriendRelationship.STATUS_PENDING -> {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("user_action_add_friend")
                            ) {
                                Text("Friend Request Sent")
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = {
                                    onSendFriendRequest(target.userId, target.displayName)
                                    onDismiss()
                                },
                                enabled = !isActionBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("user_action_add_friend")
                            ) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Friend")
                            }
                        }
                    }

                    // Private Message
                    Button(
                        onClick = {
                            val uid = target.userId
                            val name = target.displayName
                            onDismiss()
                            onOpenDirectChatWithUser(uid, name)
                        },
                        enabled = !isActionBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("user_action_private_msg")
                    ) {
                        Icon(Icons.Rounded.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Private Message")
                    }

                    // View Profile
                    OutlinedButton(
                        onClick = {
                            val uid = target.userId
                            onDismiss()
                            onOpenCreatorProfile(uid)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("user_action_view_profile")
                    ) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Profile")
                    }

                    // Report User
                    OutlinedButton(
                        onClick = {
                            val uid = target.userId
                            val name = target.displayName
                            onDismiss()
                            onReportUserClick(uid, name, UserReport.CONTEXT_CHAT_ROOM)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("user_action_report_user")
                    ) {
                        Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Report ${target.displayName}")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val uid = target.userId
                            onDismiss()
                            onOpenCreatorProfile(uid)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("user_action_view_profile")
                    ) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View My Profile")
                    }
                }

                if (target.messageId != null && (isStaff || target.userId == currentUserId)) {
                    Button(
                        onClick = {
                            onDeleteMessage(target.messageId)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("user_action_delete_message")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Message")
                    }
                }

                if (canMonitorModerateRoom || canModerateAccount) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                isAdminActor -> "Admin & Room Controls"
                                isSupportActor -> "Support & Room Controls"
                                else -> "Room Monitor Controls"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (canMonitorModerateRoom) {
                    if (livePresence != null || (isStaff && isGloballyMuted)) {
                        OutlinedButton(
                            onClick = {
                                if (liveVoiceMuted) {
                                    if (isStaff && isGloballyMuted) {
                                        onUnmuteGlobalUser(target.userId)
                                    } else {
                                        onSetMemberVoiceMuted(target.userId, target.displayName, false)
                                    }
                                } else {
                                    onSetMemberVoiceMuted(target.userId, target.displayName, true)
                                }
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("dialog_toggle_voice_mute_button")
                        ) {
                            Icon(
                                imageVector = if (liveVoiceMuted) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (liveVoiceMuted) "Unmute User (Mic & Chat)" else "Mute User (No Mic/Chat)")
                        }
                    }

                    if (liveHandRaised) {
                        OutlinedButton(
                            onClick = {
                                onLowerMemberHand(target.userId, target.displayName)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("dialog_lower_hand_button")
                        ) {
                            Icon(Icons.Rounded.PanTool, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lower Hand")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onBounceUser(target.userId, target.displayName, 5L)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_bounce_user_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bounce from Room (5 min)")
                    }
                }

                if (canModerateAccount) {
                    OutlinedButton(
                        onClick = {
                            val uid = target.userId
                            val name = target.displayName
                            onDismiss()
                            onWarnUser(uid, name)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_warn_user_button")
                    ) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Warning Popup")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onMuteUser(target.userId, target.displayName, 15L)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dialog_mute_15m_button")
                        ) {
                            Text("Mute 15m")
                        }
                        OutlinedButton(
                            onClick = {
                                onMuteUser(target.userId, target.displayName, 60L)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dialog_mute_1h_button")
                        ) {
                            Text("Mute 1h")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onSuspendUser(target.userId, target.displayName, 24L, "Suspended from chat room")
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Suspend 24h")
                        }
                        OutlinedButton(
                            onClick = {
                                onSuspendUser(target.userId, target.displayName, 168L, "Suspended from chat room")
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Suspend 7d")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            onBanUser(target.userId, target.displayName)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_ban_chat_button")
                    ) {
                        Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isSupportActor) "Request Chat Ban (Suspends -> Admin)"
                            else "Ban from Chat Rooms"
                        )
                    }
                    Button(
                        onClick = {
                            onBanAccountUser(target.userId, target.displayName, "Account banned by chat moderator")
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_ban_account_button")
                    ) {
                        Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isSupportActor) "Suspend 72h & Request Account Ban"
                            else "Ban User Account"
                        )
                    }
                } else if ((isStaff || isCurrentUserMonitor) && target.userId != currentUserId && !canMonitorModerateRoom) {
                    Text(
                        text = if (isStaff) {
                            "Support moderators cannot mute, bounce, suspend, or ban Admin accounts."
                        } else {
                            "Room monitors cannot mute or bounce Support or Admin staff."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
