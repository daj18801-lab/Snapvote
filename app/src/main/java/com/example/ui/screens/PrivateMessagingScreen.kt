package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.CreatorSummary
import com.example.data.model.DirectChatThread
import com.example.data.model.DirectMessage
import com.example.data.model.GroupChatMessage
import com.example.data.model.PrivateGroupChat
import com.example.data.model.UserProfile
import com.example.data.model.VideoSession
import com.example.media.MediaProcessor
import com.example.media.ProcessedMedia
import com.example.ui.components.CameraXCaptureView
import com.example.ui.components.OnlineStatusDot
import com.example.ui.components.PrivateChatVideoSessionCard
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DirectChatRoomView(
    thread: DirectChatThread?,
    currentUserId: String,
    userProfile: UserProfile?,
    creators: List<CreatorSummary>,
    messagesState: UiState<List<DirectMessage>>,
    threadVideoSessions: List<VideoSession> = emptyList(),
    isActionBusy: Boolean = false,
    uploadProgress: Float? = null,
    onBack: () -> Unit,
    onSendMessage: (String, () -> Unit) -> Unit,
    onSendDirectSnap: (threadId: String, caption: String, media: ProcessedMedia, durationSeconds: Long, onSent: () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onMarkDirectSnapViewed: (threadId: String, messageId: String) -> Unit = { _, _ -> },
    onBuyPrivatePass: () -> Unit,
    onReportUserClick: (String, String, String) -> Unit,
    onBlockUserClick: ((String, String) -> Unit)? = null,
    onOpenRequestVideoDialog: (threadId: String, creatorId: String, creatorName: String, isOnline: Boolean) -> Unit = { _, _, _, _ -> },
    onRespondVideoSession: (VideoSession, Boolean) -> Unit = { _, _ -> },
    onCancelVideoSession: (String) -> Unit = {},
    onJoinVideoCall: (VideoSession) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val otherId = thread?.otherUserId(currentUserId).orEmpty()
    val otherCreator = remember(creators, otherId) { creators.firstOrNull { it.creatorId == otherId } }
    val otherName = otherCreator?.displayName ?: thread?.otherUserName(currentUserId) ?: "Direct Message"
    val hasPass = true

    var draft by remember { mutableStateOf("") }
    var showSnapComposer by remember { mutableStateOf(false) }
    var activeSnapMessage by remember { mutableStateOf<DirectMessage?>(null) }
    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                    onClick = onBack,
                    modifier = Modifier.testTag("close_dm_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to messages")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = otherName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    OnlineStatusDot(
                        isOnline = otherCreator?.isCurrentlyOnline() == true,
                        showLabel = true
                    )
                }
                if (otherId.isNotBlank()) {
                    val effectiveThreadId = thread?.threadId?.takeIf { it.isNotBlank() }
                        ?: DirectChatThread.threadIdFor(currentUserId, otherId)
                    OutlinedButton(
                        onClick = {
                            onOpenRequestVideoDialog(
                                effectiveThreadId,
                                otherId,
                                otherName,
                                otherCreator?.isCurrentlyOnline() == true
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("dm_request_video_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Video", style = MaterialTheme.typography.labelLarge)
                    }
                    if (onBlockUserClick != null) {
                        IconButton(
                            onClick = { onBlockUserClick(otherId, otherName) },
                            modifier = Modifier.testTag("dm_block_user_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = "Block $otherName",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onReportUserClick(
                                otherId,
                                otherName,
                                com.example.data.model.UserReport.CONTEXT_DIRECT_CHAT
                            )
                        },
                        modifier = Modifier.testTag("dm_report_user_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Flag,
                            contentDescription = "Report $otherName"
                        )
                    }
                }
            }
        }


        if (threadVideoSessions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                threadVideoSessions.take(2).forEach { session ->
                    PrivateChatVideoSessionCard(
                        session = session,
                        currentUserId = currentUserId,
                        isBusy = isActionBusy,
                        onRespond = onRespondVideoSession,
                        onCancel = onCancelVideoSession,
                        onJoinCall = onJoinVideoCall
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (messagesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(text = messagesState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    if (messages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Say hello to $otherName!",
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
                            items(messages, key = { it.id }) { msg ->
                                val timeLabel = msg.createdAt?.toDate()?.let {
                                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
                                }.orEmpty()
                                val isMine = msg.senderId == currentUserId
                                if (msg.isSnap) {
                                    val viewedByOther = msg.viewedByIds.any { it != msg.senderId }
                                    val viewedByMe = msg.hasBeenViewedBy(currentUserId)
                                    DirectSnapMessageCard(
                                        message = msg,
                                        timeLabel = timeLabel,
                                        isMine = isMine,
                                        isViewed = if (isMine) viewedByOther else viewedByMe,
                                        onClick = {
                                            activeSnapMessage = msg
                                            if (!isMine && !viewedByMe && thread != null) {
                                                onMarkDirectSnapViewed(thread.threadId, msg.id)
                                            }
                                        }
                                    )
                                } else {
                                    PrivateMessageBubble(
                                        senderName = msg.senderName,
                                        text = msg.text,
                                        timeLabel = timeLabel,
                                        isMine = isMine
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

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
                IconButton(
                    onClick = { showSnapComposer = true },
                    enabled = hasPass && thread != null,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dm_send_snap_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = "Send Snap",
                        tint = if (hasPass && thread != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 500) draft = it },
                    enabled = hasPass,
                    placeholder = {
                        Text(if (hasPass) "Message or send a snap to $otherName..." else "Unlock pass (5 tokens/wk) to message")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dm_message_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendMessage(trimmed) { draft = "" }
                        }
                    },
                    enabled = hasPass && draft.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dm_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send private message",
                        tint = if (hasPass && draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSnapComposer && thread != null) {
        SendDirectSnapDialog(
            recipientName = otherName,
            isBusy = isActionBusy,
            uploadProgress = uploadProgress,
            onSendSnap = { caption, media, duration ->
                onSendDirectSnap(thread.threadId, caption, media, duration) {
                    showSnapComposer = false
                }
            },
            onDismiss = { if (!isActionBusy) showSnapComposer = false }
        )
    }

    activeSnapMessage?.let { snap ->
        DirectSnapViewerDialog(
            snap = snap,
            onDismiss = { activeSnapMessage = null }
        )
    }
}

@Composable
fun GroupChatRoomView(
    group: PrivateGroupChat?,
    currentUserId: String,
    userProfile: UserProfile?,
    creators: List<CreatorSummary>,
    messagesState: UiState<List<GroupChatMessage>>,
    isActionBusy: Boolean,
    onBack: () -> Unit,
    onSendMessage: (String, () -> Unit) -> Unit,
    onBuyPrivatePass: () -> Unit,
    onUpdateSettings: (String, String, String, String, Boolean, () -> Unit) -> Unit,
    onAddMembers: (PrivateGroupChat, Map<String, String>) -> Unit,
    onRemoveOrLeave: (PrivateGroupChat, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val hasPass = true
    var draft by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                    onClick = onBack,
                    modifier = Modifier.testTag("close_group_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to private chats")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group?.name ?: "Private Group",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${group?.memberIds?.size ?: 0} members • ${if (group?.privacyMode == PrivateGroupChat.PRIVACY_INVITE_ONLY) "Invite-only" else "Friends can invite"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (group != null) {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("group_settings_button")
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Private group chat settings")
                    }
                }
            }
        }


        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (messagesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(text = messagesState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    if (messages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Private group created. Start the conversation!",
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
                            items(messages, key = { it.id }) { msg ->
                                PrivateMessageBubble(
                                    senderName = msg.senderName,
                                    text = msg.text,
                                    timeLabel = msg.createdAt?.toDate()?.let {
                                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
                                    }.orEmpty(),
                                    isMine = msg.senderId == currentUserId
                                )
                            }
                        }
                    }
                }
            }
        }

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
                    value = draft,
                    onValueChange = { if (it.length <= 500) draft = it },
                    enabled = hasPass,
                    placeholder = {
                        Text(if (hasPass) "Message group..." else "Unlock pass (5 tokens/wk) to message")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("group_message_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendMessage(trimmed) { draft = "" }
                        }
                    },
                    enabled = hasPass && draft.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("group_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send group message",
                        tint = if (hasPass && draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSettingsDialog && group != null) {
        GroupSettingsDialog(
            group = group,
            currentUserId = currentUserId,
            hasPass = hasPass,
            creators = creators,
            isBusy = isActionBusy,
            onSaveSettings = { name, desc, privacy, allowInvites ->
                onUpdateSettings(group.groupId, name, desc, privacy, allowInvites) {
                    showSettingsDialog = false
                }
            },
            onAddMembers = { members ->
                onAddMembers(group, members)
            },
            onRemoveOrLeave = { targetUid ->
                onRemoveOrLeave(group, targetUid)
                if (targetUid == currentUserId) {
                    showSettingsDialog = false
                }
            },
            onDeleteGroup = {
                showSettingsDialog = false
                onDeleteGroup(group.groupId)
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupSettingsDialog(
    group: PrivateGroupChat,
    currentUserId: String,
    hasPass: Boolean,
    creators: List<CreatorSummary>,
    isBusy: Boolean,
    onSaveSettings: (name: String, desc: String, privacy: String, allowInvites: Boolean) -> Unit,
    onAddMembers: (Map<String, String>) -> Unit,
    onRemoveOrLeave: (String) -> Unit,
    onDeleteGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    val isOwner = group.ownerId == currentUserId
    val canInvite = hasPass && (isOwner || group.allowMemberInvites || group.privacyMode == PrivateGroupChat.PRIVACY_FRIENDS_CAN_INVITE)

    var name by remember(group.name) { mutableStateOf(group.name) }
    var desc by remember(group.description) { mutableStateOf(group.description) }
    var privacy by remember(group.privacyMode) { mutableStateOf(group.privacyMode) }
    var allowInvites by remember(group.allowMemberInvites) { mutableStateOf(group.allowMemberInvites) }
    val pendingNewMembers = remember { mutableStateMapOf<String, String>() }

    val nonMembers = remember(creators, group.memberIds) {
        creators.filter { !group.memberIds.contains(it.creatorId) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Private Group Chat Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Group Name") },
                    enabled = isOwner && hasPass,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_group_name_input")
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { if (it.length <= 200) desc = it },
                    label = { Text("Description") },
                    enabled = isOwner && hasPass,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Privacy Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = privacy == PrivateGroupChat.PRIVACY_INVITE_ONLY,
                        onClick = { if (isOwner && hasPass) privacy = PrivateGroupChat.PRIVACY_INVITE_ONLY },
                        label = { Text("Invite Only") }
                    )
                    FilterChip(
                        selected = privacy == PrivateGroupChat.PRIVACY_FRIENDS_CAN_INVITE,
                        onClick = { if (isOwner && hasPass) privacy = PrivateGroupChat.PRIVACY_FRIENDS_CAN_INVITE },
                        label = { Text("Friends Can Invite") }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Allow member invites",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allowInvites,
                        enabled = isOwner && hasPass,
                        onCheckedChange = { allowInvites = it }
                    )
                }

                if (isOwner) {
                    Button(
                        onClick = { onSaveSettings(name, desc, privacy, allowInvites) },
                        enabled = !isBusy && hasPass && name.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_group_settings_button")
                    ) {
                        Text("Save Group Settings")
                    }
                }

                Text(
                    text = "Members (${group.memberIds.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                group.memberIds.forEach { memberId ->
                    val memberLabel = group.memberNames[memberId] ?: memberId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (memberId == group.ownerId) "$memberLabel (Owner)" else memberLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (isOwner && memberId != currentUserId) {
                            IconButton(
                                onClick = { onRemoveOrLeave(memberId) },
                                enabled = hasPass && !isBusy
                            ) {
                                Icon(
                                    Icons.Rounded.PersonRemove,
                                    contentDescription = "Remove member",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (canInvite && nonMembers.isNotEmpty()) {
                    Text(
                        text = "Add Members",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    nonMembers.take(15).forEach { creator ->
                        val checked = pendingNewMembers.containsKey(creator.creatorId)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) pendingNewMembers.remove(creator.creatorId)
                                    else pendingNewMembers[creator.creatorId] = creator.displayName
                                }
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { now ->
                                    if (now) pendingNewMembers[creator.creatorId] = creator.displayName
                                    else pendingNewMembers.remove(creator.creatorId)
                                }
                            )
                            Text(text = creator.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (pendingNewMembers.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                onAddMembers(pendingNewMembers.toMap())
                                pendingNewMembers.clear()
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Invite Selected (${pendingNewMembers.size})")
                        }
                    }
                }

                if (!isOwner) {
                    OutlinedButton(
                        onClick = { onRemoveOrLeave(currentUserId) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Leave Group")
                    }
                } else {
                    Button(
                        onClick = onDeleteGroup,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Private Group")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun PrivateMessageBubble(
    senderName: String,
    text: String,
    timeLabel: String,
    isMine: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
            border = if (isMine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(0.86f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (timeLabel.isNotEmpty()) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DirectSnapMessageCard(
    message: DirectMessage,
    timeLabel: String,
    isMine: Boolean,
    isViewed: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            onClick = onClick,
            color = if (isViewed) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (isViewed) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .testTag("dm_snap_card_${message.id}")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isViewed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isViewed) Icons.Rounded.Visibility else Icons.Rounded.AutoAwesome,
                            contentDescription = "Photo Snap",
                            tint = if (isViewed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isMine && isViewed -> "Snap Opened"
                            isMine -> "Snap Sent (${message.snapDurationSeconds}s)"
                            isViewed -> "Opened Snap — Tap to Replay"
                            else -> "New Snap from ${message.senderName} (${message.snapDurationSeconds}s)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isViewed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendDirectSnapDialog(
    recipientName: String,
    isBusy: Boolean,
    uploadProgress: Float?,
    onSendSnap: (caption: String, media: ProcessedMedia, durationSeconds: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var useCameraX by remember { mutableStateOf(true) }
    var capturedMedia by remember { mutableStateOf<ProcessedMedia?>(null) }
    var caption by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableIntStateOf(5) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MediaProcessor.processPickedUri(context, uri)
                    .onSuccess { processed ->
                        capturedMedia = processed
                        errorMsg = null
                    }
                    .onFailure { err ->
                        errorMsg = err.localizedMessage ?: "Failed to load snap photo"
                    }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Snap to $recipientName") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val preview = capturedMedia
                if (preview != null) {
                    Image(
                        bitmap = preview.previewBitmap.asImageBitmap(),
                        contentDescription = "Snap preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                    OutlinedButton(
                        onClick = { capturedMedia = null },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retake / Choose Another")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = useCameraX,
                            onClick = { useCameraX = true },
                            label = { Text("CameraX") }
                        )
                        FilterChip(
                            selected = !useCameraX,
                            onClick = { useCameraX = false },
                            label = { Text("Photo Picker") }
                        )
                    }
                    if (useCameraX) {
                        CameraXCaptureView(
                            allowVideo = false,
                            onMediaCaptured = {
                                capturedMedia = it
                                errorMsg = null
                            },
                            onCaptureError = { errorMsg = it }
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("dm_snap_pick_photo_button")
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Photo")
                        }
                    }
                }

                Text(
                    text = "Snap view timer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10).forEach { secs ->
                        FilterChip(
                            selected = durationSeconds == secs,
                            onClick = { durationSeconds = secs },
                            label = { Text("${secs}s") }
                        )
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 160) caption = it },
                    label = { Text("Optional caption") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dm_snap_caption_input")
                )

                if (uploadProgress != null && isBusy) {
                    LinearProgressIndicator(
                        progress = { uploadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val media = capturedMedia ?: return@Button
                    onSendSnap(caption, media, durationSeconds.toLong())
                },
                enabled = !isBusy && capturedMedia != null,
                modifier = Modifier.testTag("dm_snap_send_confirm_button")
            ) {
                Text(if (isBusy) "Sending..." else "Send Snap")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DirectSnapViewerDialog(
    snap: DirectMessage,
    onDismiss: () -> Unit
) {
    val bitmap = remember(snap.id, snap.snapBase64) {
        snap.snapBase64?.let { MediaProcessor.decodeBase64ToBitmap(it) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Direct Snap from ${snap.senderName}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (!snap.snapDownloadUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = snap.snapDownloadUrl,
                        contentDescription = "Direct Snap from ${snap.senderName}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${snap.senderName} • Snap",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${snap.snapDurationSeconds}s view timer",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_dm_snap_viewer")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close snap",
                            tint = Color.White
                        )
                    }
                }
                if (snap.text.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = snap.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

