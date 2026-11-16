package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatBounce
import com.example.data.model.ChatMessage
import com.example.data.model.ChatModerationStatus
import com.example.data.model.ChatPresence
import com.example.data.model.ChatRoomCatalog
import com.example.data.model.ChatRoomInfo
import com.example.data.model.ChatRoomState
import com.example.data.model.CreatorSummary
import com.example.data.model.DirectChatThread
import com.example.data.model.DirectMessage
import com.example.data.model.EffectiveStaffTier
import com.example.data.model.FriendRelationship
import com.example.data.model.GroupChatMessage
import com.example.data.model.MicAudioChunk
import com.example.data.model.MicSession
import com.example.data.model.PrivateGroupChat
import com.example.data.model.UserProfile
import com.example.data.model.VideoFrameChunk
import com.example.data.model.VideoSession
import com.example.media.ProcessedMedia
import com.example.ui.components.ChatRoomUsersTabContent
import com.example.ui.components.ChatRoomVoiceControlsBar
import com.example.ui.components.ChatRoomVoiceStageBanner
import com.example.ui.components.DirectAndGroupChatsPanel
import com.example.ui.components.FriendsListPanel
import com.example.ui.components.MicSessionsPanel
import com.example.ui.components.MutedRedDot
import com.example.ui.components.RoomUserActionsDialog
import com.example.ui.components.RoomUserRoleBadgeChip
import com.example.ui.components.RoomUserTarget
import com.example.ui.theme.formatDisplayNameWithRole
import com.example.ui.theme.normalizeStaffRole
import com.example.ui.theme.roleBadgeForegroundColor
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.CoroutineScope
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomsScreen(
    currentUserId: String,
    staffTier: EffectiveStaffTier,
    userProfile: UserProfile?,
    creators: List<CreatorSummary>,
    myChatModeration: ChatModerationStatus?,
    activeRoomId: String?,
    activeDmThreadId: String?,
    activeGroupId: String?,
    activeMicSessionId: String?,
    isStaffInvisible: Boolean,
    roomState: ChatRoomState?,
    roomMessagesState: UiState<List<ChatMessage>>,
    roomPresence: List<ChatPresence>,
    myRoomBounce: ChatBounce?,
    isRoomAudioMutedLocally: Boolean,
    globallyMutedUserIds: Set<String> = emptySet(),
    friendRelationships: List<FriendRelationship>,
    directThreads: List<DirectChatThread>,
    dmMessagesState: UiState<List<DirectMessage>>,
    groupChats: List<PrivateGroupChat>,
    groupMessagesState: UiState<List<GroupChatMessage>>,
    micSessions: List<MicSession>,
    micLevel: Float,
    isMicCapturing: Boolean,
    micAudioChunks: List<MicAudioChunk>,
    videoSessions: List<VideoSession> = emptyList(),
    activeVideoSessionId: String? = null,
    videoFrameChunks: List<VideoFrameChunk> = emptyList(),
    videoAudioChunks: List<MicAudioChunk> = emptyList(),
    isActionBusy: Boolean,
    uploadProgress: Float? = null,
    onToggleStaffInvisible: (Boolean) -> Unit,
    onEnterRoom: (String) -> Unit,
    onLeaveRoom: () -> Unit,
    onClaimMonitorIfVacant: () -> Unit,
    onStartRoomVoiceTalk: (CoroutineScope) -> Unit,
    onStopRoomVoiceTalk: () -> Unit,
    onToggleRaiseHand: () -> Unit,
    onToggleLocalListenMute: () -> Unit,
    onSetMemberVoiceMuted: (String, String, Boolean) -> Unit,
    onLowerMemberHand: (String, String) -> Unit,
    onSendMessage: (String, () -> Unit) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onBounceUser: (String, String, Long) -> Unit,
    onMuteUser: (String, String, Long) -> Unit,
    onUnmuteUser: (String) -> Unit,
    onBanUser: (String, String) -> Unit,
    onSuspendUser: (String, String, Long, String) -> Unit,
    onBanAccountUser: (String, String, String) -> Unit,
    onReportUserClick: (String, String, String) -> Unit,
    onBuyChatPass: () -> Unit,
    onBuyVotesClick: () -> Unit,
    onBuyPrivatePass: () -> Unit,
    onBuyPhoneTokensClick: () -> Unit,
    onSendFriendRequest: (String, String) -> Unit,
    onRespondFriendRequest: (String, Boolean) -> Unit,
    onRemoveFriend: (String, String) -> Unit,
    onOpenDirectChatWithUser: (String, String) -> Unit,
    onOpenDirectThread: (String) -> Unit,
    onCloseDirectChat: () -> Unit,
    onSendDirectMessage: (String, () -> Unit) -> Unit,
    onSendDirectSnap: (String, String, ProcessedMedia, Long, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onMarkDirectSnapViewed: (String, String) -> Unit = { _, _ -> },
    onWarnUserClick: (String, String) -> Unit = { _, _ -> },
    onCreateGroupChat: (String, String, String, Boolean, Map<String, String>, () -> Unit) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onCloseGroupChat: () -> Unit,
    onSendGroupMessage: (String, () -> Unit) -> Unit,
    onUpdateGroupSettings: (String, String, String, String, Boolean, () -> Unit) -> Unit,
    onAddMembersToGroup: (PrivateGroupChat, Map<String, String>) -> Unit,
    onLeaveOrRemoveFromGroup: (PrivateGroupChat, String) -> Unit,
    onDeleteGroupChat: (String) -> Unit,
    onOpenScheduleMicDialog: (CreatorSummary) -> Unit,
    onRespondMicSession: (MicSession, Boolean) -> Unit,
    onCancelMicSession: (String) -> Unit,
    onJoinMicCall: (MicSession) -> Unit,
    onStartMicCapture: (CoroutineScope, String) -> Unit,
    onStopMicCapture: () -> Unit,
    onLeaveMicCall: () -> Unit,
    onEndMicCall: (String) -> Unit,
    onOpenRequestVideoDialog: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onRespondVideoSession: (VideoSession, Boolean) -> Unit = { _, _ -> },
    onCancelVideoSession: (String) -> Unit = {},
    onJoinVideoCall: (VideoSession) -> Unit = {},
    onSendVideoFrame: (String, String, Int, Int, Long) -> Unit = { _, _, _, _, _ -> },
    onStartVideoAudioCapture: (CoroutineScope, String) -> Unit = { _, _ -> },
    onStopVideoAudioCapture: () -> Unit = {},
    onLeaveVideoCall: () -> Unit = {},
    onEndVideoCall: (String) -> Unit = {},
    onOpenCreatorProfile: (String) -> Unit,
    onBlockUserClick: ((String, String) -> Unit)? = null,
    initialSubTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val isStaff = staffTier != EffectiveStaffTier.NONE
    var selectedSubTab by remember { mutableIntStateOf(initialSubTab) }

    val activeVideoSession = remember(videoSessions, activeVideoSessionId) {
        activeVideoSessionId?.let { id -> videoSessions.firstOrNull { it.sessionId == id } }
    }
    val activeMicSession = remember(micSessions, activeMicSessionId) {
        activeMicSessionId?.let { id -> micSessions.firstOrNull { it.sessionId == id } }
    }
    val activeDmThread = remember(directThreads, activeDmThreadId) {
        activeDmThreadId?.let { id -> directThreads.firstOrNull { it.threadId == id } }
    }
    val activeGroup = remember(groupChats, activeGroupId) {
        activeGroupId?.let { id -> groupChats.firstOrNull { it.groupId == id } }
    }

    when {
        activeVideoSession != null -> {
            PrivateVideoCallScreen(
                session = activeVideoSession,
                currentUserId = currentUserId,
                micLevel = micLevel,
                isMicCapturing = isMicCapturing,
                videoFrames = videoFrameChunks,
                audioChunks = videoAudioChunks,
                onSendVideoFrame = onSendVideoFrame,
                onStartAudioCapture = onStartVideoAudioCapture,
                onStopAudioCapture = onStopVideoAudioCapture,
                onLeaveRoom = onLeaveVideoCall,
                onEndCall = onEndVideoCall,
                modifier = modifier
            )
        }
        activeMicSession != null -> {
            MicCallRoomScreen(
                session = activeMicSession,
                currentUserId = currentUserId,
                micLevel = micLevel,
                isMicCapturing = isMicCapturing,
                audioChunks = micAudioChunks,
                onStartCapture = onStartMicCapture,
                onStopCapture = onStopMicCapture,
                onLeaveRoom = onLeaveMicCall,
                onEndCall = onEndMicCall,
                modifier = modifier
            )
        }
        activeDmThreadId != null -> {
            val threadVideoSessions = remember(videoSessions, activeDmThreadId) {
                videoSessions.filter { it.threadId == activeDmThreadId }
            }
            DirectChatRoomView(
                thread = activeDmThread,
                currentUserId = currentUserId,
                userProfile = userProfile,
                creators = creators,
                messagesState = dmMessagesState,
                threadVideoSessions = threadVideoSessions,
                isActionBusy = isActionBusy,
                uploadProgress = uploadProgress,
                onBack = onCloseDirectChat,
                onSendMessage = onSendDirectMessage,
                onSendDirectSnap = onSendDirectSnap,
                onMarkDirectSnapViewed = onMarkDirectSnapViewed,
                onBuyPrivatePass = onBuyPrivatePass,
                onReportUserClick = onReportUserClick,
                onBlockUserClick = onBlockUserClick,
                onOpenRequestVideoDialog = onOpenRequestVideoDialog,
                onRespondVideoSession = onRespondVideoSession,
                onCancelVideoSession = onCancelVideoSession,
                onJoinVideoCall = onJoinVideoCall,
                modifier = modifier
            )
        }
        activeGroupId != null -> {
            GroupChatRoomView(
                group = activeGroup,
                currentUserId = currentUserId,
                userProfile = userProfile,
                creators = creators,
                messagesState = groupMessagesState,
                isActionBusy = isActionBusy,
                onBack = onCloseGroupChat,
                onSendMessage = onSendGroupMessage,
                onBuyPrivatePass = onBuyPrivatePass,
                onUpdateSettings = onUpdateGroupSettings,
                onAddMembers = onAddMembersToGroup,
                onRemoveOrLeave = onLeaveOrRemoveFromGroup,
                onDeleteGroup = onDeleteGroupChat,
                modifier = modifier
            )
        }
        activeRoomId != null -> {
            val roomInfo = remember(activeRoomId) {
                ChatRoomCatalog.rooms.firstOrNull { it.id == activeRoomId }
                    ?: ChatRoomInfo(activeRoomId, "Chat Room", "", "Chat")
            }
            ActiveChatRoomView(
                roomInfo = roomInfo,
                currentUserId = currentUserId,
                staffTier = staffTier,
                myChatModeration = myChatModeration,
                isStaffInvisible = isStaffInvisible,
                roomState = roomState,
                roomMessagesState = roomMessagesState,
                roomPresence = roomPresence,
                myRoomBounce = myRoomBounce,
                micLevel = micLevel,
                isMicCapturing = isMicCapturing,
                isRoomAudioMutedLocally = isRoomAudioMutedLocally,
                globallyMutedUserIds = globallyMutedUserIds,
                friendRelationships = friendRelationships,
                isActionBusy = isActionBusy,
                onToggleStaffInvisible = onToggleStaffInvisible,
                onLeaveRoom = onLeaveRoom,
                onClaimMonitorIfVacant = onClaimMonitorIfVacant,
                onStartRoomVoiceTalk = onStartRoomVoiceTalk,
                onStopRoomVoiceTalk = onStopRoomVoiceTalk,
                onToggleRaiseHand = onToggleRaiseHand,
                onToggleLocalListenMute = onToggleLocalListenMute,
                onSetMemberVoiceMuted = onSetMemberVoiceMuted,
                onLowerMemberHand = onLowerMemberHand,
                onSendMessage = onSendMessage,
                onDeleteMessage = onDeleteMessage,
                onBounceUser = onBounceUser,
                onMuteUser = onMuteUser,
                onUnmuteUser = onUnmuteUser,
                onBanUser = onBanUser,
                onSuspendUser = onSuspendUser,
                onBanAccountUser = onBanAccountUser,
                onReportUserClick = onReportUserClick,
                onWarnUserClick = onWarnUserClick,
                onSendFriendRequest = onSendFriendRequest,
                onRespondFriendRequest = onRespondFriendRequest,
                onOpenDirectChatWithUser = onOpenDirectChatWithUser,
                onOpenCreatorProfile = onOpenCreatorProfile,
                modifier = modifier
            )
        }
        else -> {
            val pendingFriendCount = remember(friendRelationships, currentUserId) {
                friendRelationships.count {
                    it.status == FriendRelationship.STATUS_PENDING && it.receiverId == currentUserId
                }
            }
            val pendingMicCount = remember(micSessions, currentUserId) {
                micSessions.count { it.creatorId == currentUserId && it.status == MicSession.STATUS_PENDING }
            }
            val pendingVideoCount = remember(videoSessions, currentUserId) {
                videoSessions.count { it.creatorId == currentUserId && it.status == VideoSession.STATUS_PENDING }
            }

            Column(modifier = modifier.fillMaxSize()) {
                PrimaryScrollableTabRow(selectedTabIndex = selectedSubTab, edgePadding = 8.dp) {
                    Tab(
                        selected = selectedSubTab == 0,
                        onClick = { selectedSubTab = 0 },
                        text = {
                            val badge = when {
                                pendingVideoCount > 0 -> " ($pendingVideoCount)"
                                directThreads.isNotEmpty() -> " (${directThreads.size})"
                                else -> ""
                            }
                            Text("Messages$badge")
                        },
                        icon = { Icon(Icons.Rounded.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("social_tab_dms")
                    )
                    Tab(
                        selected = selectedSubTab == 1,
                        onClick = { selectedSubTab = 1 },
                        text = { Text("Rooms") },
                        icon = { Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("social_tab_rooms")
                    )
                    Tab(
                        selected = selectedSubTab == 2,
                        onClick = { selectedSubTab = 2 },
                        text = {
                            Text(if (pendingFriendCount > 0) "Friends ($pendingFriendCount)" else "Friends")
                        },
                        icon = { Icon(Icons.Rounded.People, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("social_tab_friends")
                    )
                    Tab(
                        selected = selectedSubTab == 3,
                        onClick = { selectedSubTab = 3 },
                        text = {
                            Text(if (pendingMicCount > 0) "Mic Calls ($pendingMicCount)" else "Mic Calls")
                        },
                        icon = { Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("social_tab_mic_calls")
                    )
                }

                when (selectedSubTab) {
                    0 -> DirectAndGroupChatsPanel(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        creators = creators,
                        relationships = friendRelationships,
                        directThreads = directThreads,
                        groupChats = groupChats,
                        isActionBusy = isActionBusy,
                        onBuyPrivatePass = onBuyPrivatePass,
                        onBuyVotesClick = onBuyVotesClick,
                        onOpenDirectThread = onOpenDirectThread,
                        onStartDirectChat = onOpenDirectChatWithUser,
                        onOpenGroupChat = onOpenGroupChat,
                        onCreateGroupChat = onCreateGroupChat,
                        modifier = Modifier.weight(1f)
                    )
                    1 -> ChatRoomLobby(
                        isStaff = isStaff,
                        userProfile = userProfile,
                        myChatModeration = myChatModeration,
                        isStaffInvisible = isStaffInvisible,
                        isActionBusy = isActionBusy,
                        onToggleStaffInvisible = onToggleStaffInvisible,
                        onEnterRoom = onEnterRoom,
                        onBuyChatPass = onBuyChatPass,
                        onBuyVotesClick = onBuyVotesClick,
                        modifier = Modifier.weight(1f)
                    )
                    2 -> FriendsListPanel(
                        currentUserId = currentUserId,
                        creators = creators,
                        relationships = friendRelationships,
                        isActionBusy = isActionBusy,
                        onSendFriendRequest = onSendFriendRequest,
                        onRespondFriendRequest = onRespondFriendRequest,
                        onRemoveFriend = onRemoveFriend,
                        onMessageUser = { uid, name -> onOpenDirectChatWithUser(uid, name) },
                        onScheduleMicCall = onOpenScheduleMicDialog,
                        onOpenCreatorProfile = onOpenCreatorProfile,
                        modifier = Modifier.weight(1f)
                    )
                    3 -> MicSessionsPanel(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        creators = creators,
                        micSessions = micSessions,
                        isActionBusy = isActionBusy,
                        onBuyPhoneTokensClick = onBuyPhoneTokensClick,
                        onOpenScheduleMicDialog = onOpenScheduleMicDialog,
                        onRespondMicSession = onRespondMicSession,
                        onCancelMicSession = onCancelMicSession,
                        onJoinMicCall = onJoinMicCall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRoomLobby(
    isStaff: Boolean,
    userProfile: UserProfile?,
    myChatModeration: ChatModerationStatus?,
    isStaffInvisible: Boolean,
    isActionBusy: Boolean,
    onToggleStaffInvisible: (Boolean) -> Unit,
    onEnterRoom: (String) -> Unit,
    onBuyChatPass: () -> Unit,
    onBuyVotesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMillis = remember { System.currentTimeMillis() }
    val isBanned = !isStaff && myChatModeration?.banned == true
    val hasFreeTrial = userProfile?.hasActiveFreeTrial(nowMillis) ?: true
    val hasPaidPass = userProfile?.hasActivePaidChatPass(nowMillis) ?: false
    val hasAccess = !isBanned && (userProfile?.hasChatAccess(isStaff, nowMillis) ?: true)
    val voteBalance = userProfile?.voteBalance ?: 0L

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Free Chat Rooms / Ban Status Card
        item(key = "chat_access_card") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isBanned) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    }
                ),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(
                    1.dp,
                    if (isBanned) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isBanned) Icons.Rounded.Block else Icons.Rounded.LockOpen,
                            contentDescription = null,
                            tint = if (isBanned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isBanned -> "Chat Access Banned"
                                isStaff -> "Staff Unlimited Chat Access"
                                else -> "Free Public Chat Rooms"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = when {
                            isBanned -> myChatModeration?.banReason?.takeIf { it.isNotBlank() }
                                ?: "A moderator banned your account from entering chat rooms."
                            isStaff -> "Admins & Support monitors have free access to all 5 chat rooms, invisible entry, and moderation tools."
                            else -> "All 5 public text & voice chat rooms are 100% free to join and chat with creators and fans anytime."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Staff Invisible Mode Toggle
        if (isStaff) {
            item(key = "staff_invisible_card") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isStaffInvisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enter Rooms Invisibly",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isStaffInvisible) {
                                    "Invisible mode ON: hidden from regular users (visible to Admins)"
                                } else {
                                    "Visible mode: your name shows in room member lists"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isStaffInvisible,
                            onCheckedChange = onToggleStaffInvisible,
                            modifier = Modifier.testTag("staff_invisible_switch")
                        )
                    }
                }
            }
        }

        item(key = "rooms_header") {
            Text(
                text = "Social Chat Rooms (${ChatRoomCatalog.rooms.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(ChatRoomCatalog.rooms, key = { it.id }) { room ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_room_card_${room.id}")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = room.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = room.topicBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = room.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onEnterRoom(room.id) },
                            enabled = hasAccess && !isActionBusy,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("enter_room_${room.id}")
                        ) {
                            Icon(
                                imageVector = if (isStaff && isStaffInvisible) Icons.Rounded.VisibilityOff else Icons.Rounded.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                when {
                                    isBanned -> "Banned"
                                    !hasAccess -> "Pass Required"
                                    isStaff && isStaffInvisible -> "Enter Invisible"
                                    else -> "Join Room"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveChatRoomView(
    roomInfo: ChatRoomInfo,
    currentUserId: String,
    staffTier: EffectiveStaffTier,
    myChatModeration: ChatModerationStatus?,
    isStaffInvisible: Boolean,
    roomState: ChatRoomState?,
    roomMessagesState: UiState<List<ChatMessage>>,
    roomPresence: List<ChatPresence>,
    myRoomBounce: ChatBounce?,
    micLevel: Float,
    isMicCapturing: Boolean,
    isRoomAudioMutedLocally: Boolean,
    globallyMutedUserIds: Set<String>,
    friendRelationships: List<FriendRelationship>,
    isActionBusy: Boolean,
    onToggleStaffInvisible: (Boolean) -> Unit,
    onLeaveRoom: () -> Unit,
    onClaimMonitorIfVacant: () -> Unit,
    onStartRoomVoiceTalk: (CoroutineScope) -> Unit,
    onStopRoomVoiceTalk: () -> Unit,
    onToggleRaiseHand: () -> Unit,
    onToggleLocalListenMute: () -> Unit,
    onSetMemberVoiceMuted: (String, String, Boolean) -> Unit,
    onLowerMemberHand: (String, String) -> Unit,
    onSendMessage: (String, () -> Unit) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onBounceUser: (String, String, Long) -> Unit,
    onMuteUser: (String, String, Long) -> Unit,
    onUnmuteUser: (String) -> Unit,
    onBanUser: (String, String) -> Unit,
    onSuspendUser: (String, String, Long, String) -> Unit,
    onBanAccountUser: (String, String, String) -> Unit,
    onReportUserClick: (String, String, String) -> Unit,
    onWarnUserClick: (String, String) -> Unit = { _, _ -> },
    onSendFriendRequest: (String, String) -> Unit,
    onRespondFriendRequest: (String, Boolean) -> Unit,
    onOpenDirectChatWithUser: (String, String) -> Unit,
    onOpenCreatorProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isStaff = staffTier != EffectiveStaffTier.NONE
    val isAdmin = staffTier == EffectiveStaffTier.OWNER || staffTier == EffectiveStaffTier.ADMIN
    var activeRoomTab by remember(roomInfo.id) { mutableIntStateOf(0) }
    var messageDraft by remember { mutableStateOf("") }
    var selectedUserTarget by remember { mutableStateOf<RoomUserTarget?>(null) }
    val listState = rememberLazyListState()

    BackHandler {
        onStopRoomVoiceTalk()
        onLeaveRoom()
    }

    DisposableEffect(roomInfo.id) {
        onDispose { onStopRoomVoiceTalk() }
    }

    // Automatically kick bounced/banned non-staff users out of the active room
    LaunchedEffect(myRoomBounce, myChatModeration, isStaff) {
        if (!isStaff && (myRoomBounce?.isCurrentlyBounced() == true || myChatModeration?.banned == true)) {
            onStopRoomVoiceTalk()
            onLeaveRoom()
        }
    }

    val isGlobalMuted = !isStaff && myChatModeration?.isCurrentlyMuted() == true
    val messages = (roomMessagesState as? UiState.Success)?.data.orEmpty()
    val visibleMembers = remember(roomPresence) { roomPresence.filter { !it.isInvisible } }
    val visibleCount = visibleMembers.size
    val invisibleCount = remember(roomPresence) { roomPresence.count { it.isInvisible } }
    val activeMicCount = remember(visibleMembers, globallyMutedUserIds) {
        visibleMembers.count {
            it.isSpeaking && !it.isVoiceMuted && !globallyMutedUserIds.contains(it.userId)
        }
    }

    val effectiveMonitorUid = remember(roomState?.monitorUid, visibleMembers) {
        val recorded = roomState?.monitorUid
        if (!recorded.isNullOrBlank() && visibleMembers.any { it.userId == recorded }) {
            recorded
        } else {
            visibleMembers.firstOrNull()?.userId
        }
    }
    val isCurrentUserMonitor = !isStaffInvisible &&
        ((roomState?.monitorUid == currentUserId) ||
            (roomState?.monitorUid.isNullOrBlank() && visibleMembers.firstOrNull()?.userId == currentUserId))
    val myPresence = remember(roomPresence, currentUserId) {
        roomPresence.firstOrNull { it.userId == currentUserId }
    }
    val isVoiceMutedByMonitor = !isAdmin && myPresence?.isVoiceMuted == true
    val isAnyRoomMuted = isGlobalMuted || isVoiceMutedByMonitor
    val isHandRaised = myPresence?.handRaised == true

    LaunchedEffect(roomState?.monitorUid, visibleMembers, isStaffInvisible) {
        val recordedMonitor = roomState?.monitorUid
        val monitorGone = recordedMonitor.isNullOrBlank() ||
            (visibleMembers.isNotEmpty() && visibleMembers.none { it.userId == recordedMonitor })
        if (monitorGone && !isStaffInvisible && visibleMembers.firstOrNull()?.userId == currentUserId) {
            onClaimMonitorIfVacant()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Room Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onLeaveRoom,
                    modifier = Modifier.testTag("leave_room_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Leave chat room"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = roomInfo.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isStaff && isStaffInvisible) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "INVISIBLE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = buildString {
                            append(
                                if (invisibleCount > 0) {
                                    "$visibleCount visible • $invisibleCount invisible support"
                                } else {
                                    "$visibleCount online"
                                }
                            )
                            if (activeMicCount > 0) {
                                append(" • $activeMicCount on mic")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isStaff) {
                    IconButton(
                        onClick = { onToggleStaffInvisible(!isStaffInvisible) },
                        modifier = Modifier.testTag("room_invisible_toggle")
                    ) {
                        Icon(
                            imageVector = if (isStaffInvisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (isStaffInvisible) "Disable invisible mode" else "Enable invisible mode",
                            tint = if (isStaffInvisible) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { activeRoomTab = if (activeRoomTab == 1) 0 else 1 },
                    modifier = Modifier.testTag("room_members_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Group,
                        contentDescription = if (activeRoomTab == 1) "Show chat tab" else "Show users tab",
                        tint = if (activeRoomTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // In-room Chat / Users Tab Switcher
        PrimaryTabRow(
            selectedTabIndex = activeRoomTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeRoomTab == 0,
                onClick = { activeRoomTab = 0 },
                text = { Text("Chat") },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("room_tab_chat")
            )
            Tab(
                selected = activeRoomTab == 1,
                onClick = { activeRoomTab = 1 },
                text = {
                    val micSuffix = if (activeMicCount > 0) " • $activeMicCount Mic" else ""
                    Text("Users ($visibleCount)$micSuffix")
                },
                icon = {
                    Icon(
                        imageVector = if (activeMicCount > 0) Icons.Rounded.Mic else Icons.Rounded.Group,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("room_tab_users")
            )
        }

        ChatRoomVoiceStageBanner(
            roomState = roomState,
            roomPresence = roomPresence,
            currentUserId = currentUserId,
            isCurrentUserMonitor = isCurrentUserMonitor,
            isVoiceMutedByMonitor = isVoiceMutedByMonitor,
            isRoomAudioMutedLocally = isRoomAudioMutedLocally,
            onToggleLocalListenMute = onToggleLocalListenMute,
            onOpenMembersSheet = { activeRoomTab = 1 }
        )

        if (isGlobalMuted && !isVoiceMutedByMonitor) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("room_global_muted_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "You are muted (${myChatModeration?.muteReason ?: "Staff moderation"}) — you cannot use the mic or send chat messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Main Content Area (Tab 0: Chat Messages, Tab 1: Users & Mic Status List)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeRoomTab) {
                0 -> {
                    when (roomMessagesState) {
                        is UiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is UiState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = roomMessagesState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(onClick = onLeaveRoom) {
                                        Text("Back to Lobby")
                                    }
                                }
                            }
                        }
                        is UiState.Success -> {
                            val itemsList = roomMessagesState.data
                            if (itemsList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No messages yet in ${roomInfo.name}. Start the conversation!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(itemsList, key = { it.id }) { msg ->
                                        val isMine = msg.senderId == currentUserId
                                        val senderPresence = roomPresence.firstOrNull { it.userId == msg.senderId }
                                        val isSenderMuted = (senderPresence?.isVoiceMuted == true) ||
                                            globallyMutedUserIds.contains(msg.senderId) ||
                                            (isMine && isAnyRoomMuted)
                                        val isSenderSpeaking = (senderPresence?.isSpeaking == true) && !isSenderMuted

                                        ChatMessageBubble(
                                            message = msg,
                                            isMine = isMine,
                                            isSenderMuted = isSenderMuted,
                                            isSenderSpeaking = isSenderSpeaking,
                                            onUserActionClick = {
                                                selectedUserTarget = RoomUserTarget(
                                                    userId = msg.senderId,
                                                    displayName = msg.senderName,
                                                    role = msg.senderRole,
                                                    messageId = msg.id,
                                                    isVoiceMuted = isSenderMuted,
                                                    isSpeaking = isSenderSpeaking,
                                                    handRaised = senderPresence?.handRaised == true
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    ChatRoomUsersTabContent(
                        roomName = roomInfo.name,
                        roomPresence = roomPresence,
                        effectiveMonitorUid = effectiveMonitorUid,
                        currentUserId = currentUserId,
                        staffTier = staffTier,
                        globallyMutedUserIds = globallyMutedUserIds,
                        friendRelationships = friendRelationships,
                        onUserClick = { member ->
                            val isMemberMuted = member.isVoiceMuted || globallyMutedUserIds.contains(member.userId)
                            selectedUserTarget = RoomUserTarget(
                                userId = member.userId,
                                displayName = member.displayName,
                                role = member.role,
                                isVoiceMuted = isMemberMuted,
                                isSpeaking = member.isSpeaking && !isMemberMuted,
                                handRaised = member.handRaised
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Push-to-Talk & Raise Hand Bar
        ChatRoomVoiceControlsBar(
            roomId = roomInfo.id,
            currentUserId = currentUserId,
            isStaffInvisible = isStaff && isStaffInvisible,
            isGlobalMuted = isGlobalMuted,
            isVoiceMutedByMonitor = isVoiceMutedByMonitor,
            isHandRaised = isHandRaised,
            micLevel = micLevel,
            isMicCapturing = isMicCapturing,
            onStartVoiceTalk = onStartRoomVoiceTalk,
            onStopVoiceTalk = onStopRoomVoiceTalk,
            onToggleRaiseHand = onToggleRaiseHand
        )

        // Text Composer Bar (shown on Chat tab)
        if (activeRoomTab == 0) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageDraft,
                        onValueChange = { if (it.length <= 500) messageDraft = it },
                        enabled = !isAnyRoomMuted,
                        placeholder = {
                            Text(
                                if (isAnyRoomMuted) "Muted — mic & chat disabled"
                                else "Message ${roomInfo.name}..."
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val trimmed = messageDraft.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendMessage(trimmed) {
                                    messageDraft = ""
                                }
                            }
                        },
                        enabled = !isAnyRoomMuted && messageDraft.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send message",
                            tint = if (!isAnyRoomMuted && messageDraft.isNotBlank()) {
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

    selectedUserTarget?.let { target ->
        RoomUserActionsDialog(
            target = target,
            currentUserId = currentUserId,
            staffTier = staffTier,
            isCurrentUserMonitor = isCurrentUserMonitor,
            roomPresence = roomPresence,
            globallyMutedUserIds = globallyMutedUserIds,
            friendRelationships = friendRelationships,
            isActionBusy = isActionBusy,
            onDismiss = { selectedUserTarget = null },
            onSendFriendRequest = onSendFriendRequest,
            onRespondFriendRequest = onRespondFriendRequest,
            onOpenDirectChatWithUser = onOpenDirectChatWithUser,
            onOpenCreatorProfile = onOpenCreatorProfile,
            onDeleteMessage = onDeleteMessage,
            onReportUserClick = onReportUserClick,
            onSetMemberVoiceMuted = onSetMemberVoiceMuted,
            onUnmuteGlobalUser = onUnmuteUser,
            onLowerMemberHand = onLowerMemberHand,
            onBounceUser = onBounceUser,
            onMuteUser = onMuteUser,
            onSuspendUser = onSuspendUser,
            onBanUser = onBanUser,
            onBanAccountUser = onBanAccountUser,
            onWarnUser = onWarnUserClick
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    isSenderMuted: Boolean,
    isSenderSpeaking: Boolean,
    onUserActionClick: () -> Unit
) {
    val timeText = remember(message.createdAt) {
        message.createdAt?.toDate()?.let {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
        } ?: ""
    }
    val formattedSenderName = formatDisplayNameWithRole(message.senderName, message.senderRole)
    val senderNormalizedRole = normalizeStaffRole(message.senderRole)
    val senderColor = roleBadgeForegroundColor(message.senderRole) ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            shape = RoundedCornerShape(14.dp),
            border = if (isMine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onUserActionClick)
                            .padding(vertical = 2.dp, horizontal = 2.dp)
                            .testTag("message_sender_${message.id}")
                    ) {
                        if (isSenderMuted) {
                            MutedRedDot(testTag = "message_muted_dot_${message.id}")
                        }
                        Text(
                            text = formattedSenderName,
                            style = MaterialTheme.typography.labelLarge,
                            color = senderColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (senderNormalizedRole != null) {
                            RoomUserRoleBadgeChip(role = message.senderRole)
                        }
                        if (isSenderSpeaking) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Using microphone",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (timeText.isNotEmpty()) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onUserActionClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "User and message actions",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatRemainingTime(endsAtMillis: Long?): String {
    if (endsAtMillis == null) return "7 days"
    val diff = (endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    val days = diff / (24L * 60L * 60L * 1000L)
    val hours = (diff % (24L * 60L * 60L * 1000L)) / (60L * 60L * 1000L)
    return when {
        days >= 1L -> "${days}d ${hours}h"
        hours >= 1L -> "${hours}h"
        else -> "<1h"
    }
}
