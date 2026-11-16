package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CreatorSummary
import com.example.data.model.DirectChatThread
import com.example.data.model.FriendRelationship
import com.example.data.model.PrivateGroupChat
import com.example.data.model.UserProfile
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun FriendsListPanel(
    currentUserId: String,
    creators: List<CreatorSummary>,
    relationships: List<FriendRelationship>,
    isActionBusy: Boolean,
    onSendFriendRequest: (String, String) -> Unit,
    onRespondFriendRequest: (String, Boolean) -> Unit,
    onRemoveFriend: (String, String) -> Unit,
    onMessageUser: (String, String) -> Unit,
    onScheduleMicCall: (CreatorSummary) -> Unit,
    onOpenCreatorProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val creatorsById = remember(creators) { creators.associateBy { it.creatorId } }

    val incomingPending = remember(relationships, currentUserId) {
        relationships.filter {
            it.status == FriendRelationship.STATUS_PENDING && it.receiverId == currentUserId
        }
    }
    val outgoingPending = remember(relationships, currentUserId) {
        relationships.filter {
            it.status == FriendRelationship.STATUS_PENDING && it.senderId == currentUserId
        }
    }
    val acceptedFriends = remember(relationships, creatorsById) {
        relationships
            .filter { it.status == FriendRelationship.STATUS_ACCEPTED }
            .sortedByDescending { rel ->
                val otherId = rel.otherUserId(currentUserId)
                creatorsById[otherId]?.isCurrentlyOnline() == true
            }
    }
    val existingOtherIds = remember(relationships, currentUserId) {
        relationships
            .filter { it.status != FriendRelationship.STATUS_DECLINED }
            .map { it.otherUserId(currentUserId) }
            .toSet()
    }
    val discoverablePeople = remember(creators, existingOtherIds, currentUserId) {
        creators
            .filter { it.creatorId != currentUserId && !existingOtherIds.contains(it.creatorId) }
            .sortedByDescending { it.isCurrentlyOnline() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (incomingPending.isNotEmpty()) {
            item(key = "incoming_header") {
                Text(
                    text = "Friend Requests (${incomingPending.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(incomingPending, key = { "in_${it.pairId}" }) { rel ->
                val otherCreator = creatorsById[rel.senderId]
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = otherCreator?.displayName ?: rel.senderName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                OnlineStatusDot(
                                    isOnline = otherCreator?.isCurrentlyOnline() == true,
                                    showLabel = true
                                )
                            }
                            Text(
                                text = "Wants to add you as a friend",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onRespondFriendRequest(rel.pairId, true) },
                            enabled = !isActionBusy,
                            modifier = Modifier.testTag("accept_friend_${rel.pairId}")
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Accept friend request",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { onRespondFriendRequest(rel.pairId, false) },
                            enabled = !isActionBusy,
                            modifier = Modifier.testTag("decline_friend_${rel.pairId}")
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Decline friend request",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item(key = "friends_header") {
            val onlineCount = acceptedFriends.count {
                creatorsById[it.otherUserId(currentUserId)]?.isCurrentlyOnline() == true
            }
            Text(
                text = "My Friends (${acceptedFriends.size}) • $onlineCount online",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (acceptedFriends.isEmpty()) {
            item(key = "empty_friends") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No friends added yet. Add creators below to see when they are online or offline, send DMs, or start private group chats.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(acceptedFriends, key = { "friend_${it.pairId}" }) { rel ->
                val otherId = rel.otherUserId(currentUserId)
                val otherCreator = creatorsById[otherId]
                val otherName = otherCreator?.displayName ?: rel.otherUserName(currentUserId)
                val isOnline = otherCreator?.isCurrentlyOnline() == true

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("friend_card_$otherId")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onOpenCreatorProfile(otherId) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
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
                                    text = otherName.take(1).uppercase(Locale.ROOT),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = otherName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                OnlineStatusDot(isOnline = isOnline, showLabel = true)
                            }
                            TextButton(
                                onClick = { onRemoveFriend(rel.pairId, otherName) },
                                enabled = !isActionBusy
                            ) {
                                Text("Unfriend")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onMessageUser(otherId, otherName) },
                                enabled = !isActionBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("friend_dm_button_$otherId")
                            ) {
                                Icon(Icons.Rounded.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Message")
                            }
                            if (otherCreator != null) {
                                OutlinedButton(
                                    onClick = { onScheduleMicCall(otherCreator) },
                                    enabled = !isActionBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("friend_mic_button_$otherId")
                                ) {
                                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Mic Call")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (outgoingPending.isNotEmpty()) {
            item(key = "outgoing_header") {
                Text(
                    text = "Sent Requests (${outgoingPending.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(outgoingPending, key = { "out_${it.pairId}" }) { rel ->
                val otherCreator = creatorsById[rel.receiverId]
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = otherCreator?.displayName ?: rel.receiverName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Request pending",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onRemoveFriend(rel.pairId, rel.receiverName) }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        item(key = "add_friends_header") {
            Text(
                text = "People You Can Add (${discoverablePeople.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(discoverablePeople, key = { "add_${it.creatorId}" }) { creator ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenCreatorProfile(creator.creatorId) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = creator.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            OnlineStatusDot(isOnline = creator.isCurrentlyOnline(), showLabel = true)
                        }
                        Text(
                            text = "${creator.postCount} posts • ${creator.totalVotesReceived} votes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { onSendFriendRequest(creator.creatorId, creator.displayName) },
                        enabled = !isActionBusy,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("add_friend_button_${creator.creatorId}")
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Friend")
                    }
                }
            }
        }
    }
}

@Composable
fun DirectAndGroupChatsPanel(
    currentUserId: String,
    userProfile: UserProfile?,
    creators: List<CreatorSummary>,
    relationships: List<FriendRelationship>,
    directThreads: List<DirectChatThread>,
    groupChats: List<PrivateGroupChat>,
    isActionBusy: Boolean,
    onBuyPrivatePass: () -> Unit,
    onBuyVotesClick: () -> Unit,
    onOpenDirectThread: (String) -> Unit,
    onStartDirectChat: (userId: String, userName: String) -> Unit = { _, _ -> },
    onOpenGroupChat: (String) -> Unit,
    onCreateGroupChat: (
        name: String,
        description: String,
        privacyMode: String,
        allowMemberInvites: Boolean,
        invitedMembers: Map<String, String>,
        onCreated: () -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMillis = remember { System.currentTimeMillis() }
    val hasPrivatePass = userProfile?.hasActivePrivateChatPass(nowMillis) == true
    val voteBalance = userProfile?.voteBalance ?: 0L
    val creatorsById = remember(creators) { creators.associateBy { it.creatorId } }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showNewDirectMessageDialog by remember { mutableStateOf(false) }

    val friendOptions = remember(relationships, creatorsById, currentUserId) {
        val fromFriends = relationships
            .filter { it.status == FriendRelationship.STATUS_ACCEPTED }
            .map { rel ->
                val id = rel.otherUserId(currentUserId)
                id to (creatorsById[id]?.displayName ?: rel.otherUserName(currentUserId))
            }
        if (fromFriends.isNotEmpty()) {
            fromFriends
        } else {
            creators.filter { it.creatorId != currentUserId }.map { it.creatorId to it.displayName }
        }
    }

    val allMessageableUsers = remember(creators, currentUserId) {
        creators
            .filter { it.creatorId != currentUserId }
            .sortedByDescending { it.isCurrentlyOnline() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("private_message_log_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "dms_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Private Message Log (${directThreads.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("private_message_log_header")
                    )
                    Text(
                        text = "Your 1-on-1 private conversations & snaps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showNewDirectMessageDialog = true },
                    enabled = !isActionBusy && allMessageableUsers.isNotEmpty(),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("new_direct_message_button")
                ) {
                    Icon(Icons.Rounded.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New DM")
                }
            }
        }

        if (directThreads.isEmpty()) {
            item(key = "no_dms") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("private_message_log_empty")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No private messages yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Your 1-on-1 private message log will appear right here whenever you log in. Tap New DM or message anyone in Discover to start chatting for free.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (allMessageableUsers.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showNewDirectMessageDialog = true },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("empty_start_dm_button")
                            ) {
                                Icon(Icons.Rounded.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start a Private Message")
                            }
                        }
                    }
                }
            }
        } else {
            items(directThreads, key = { "dm_${it.threadId}" }) { thread ->
                val otherId = thread.otherUserId(currentUserId)
                val otherCreator = creatorsById[otherId]
                val otherName = otherCreator?.displayName ?: thread.otherUserName(currentUserId)
                val timestampDate = thread.lastMessageAt?.toDate() ?: thread.updatedAt?.toDate()
                val timeLabel = remember(timestampDate) {
                    formatDmLogTimestamp(timestampDate)
                }
                val previewText = when {
                    thread.lastMessageText.isBlank() -> "Tap to send a private message or snap"
                    thread.lastMessageSenderId == currentUserId -> "You: ${thread.lastMessageText}"
                    else -> thread.lastMessageText
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDirectThread(thread.threadId) }
                        .testTag("dm_thread_card_${thread.threadId}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = otherName.take(1).uppercase(Locale.ROOT),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = otherName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    OnlineStatusDot(
                                        isOnline = otherCreator?.isCurrentlyOnline() == true,
                                        showLabel = true
                                    )
                                }
                                if (timeLabel.isNotEmpty()) {
                                    Text(
                                        text = timeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = previewText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item(key = "groups_header_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Private Group Chats (${groupChats.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = { showCreateGroupDialog = true },
                    enabled = !isActionBusy,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("create_group_chat_button")
                ) {
                    Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Group")
                }
            }
        }

        if (groupChats.isEmpty()) {
            item(key = "no_groups") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No private groups yet. Tap New Group to create an invite-only or friends group chat for free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(groupChats, key = { "grp_${it.groupId}" }) { group ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenGroupChat(group.groupId) }
                        .testTag("group_chat_card_${group.groupId}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (group.privacyMode == PrivateGroupChat.PRIVACY_INVITE_ONLY) "Invite Only" else "Friends Invite",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = group.lastMessageText.ifBlank {
                                    "${group.memberIds.size} members • Tap to open"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item(key = "private_pass_card") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "1:1 Private DMs, Snaps, Group Chats, Chat Rooms, Mic & Video Calls are all free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showNewDirectMessageDialog) {
        NewDirectMessagePickerDialog(
            users = allMessageableUsers,
            isBusy = isActionBusy,
            onSelectUser = { user ->
                showNewDirectMessageDialog = false
                onStartDirectChat(user.creatorId, user.displayName)
            },
            onDismiss = { showNewDirectMessageDialog = false }
        )
    }

    if (showCreateGroupDialog) {
        CreatePrivateGroupDialog(
            candidateMembers = friendOptions,
            isBusy = isActionBusy,
            onCreate = { name, desc, privacyMode, allowInvites, selectedMembers ->
                onCreateGroupChat(name, desc, privacyMode, allowInvites, selectedMembers) {
                    showCreateGroupDialog = false
                }
            },
            onDismiss = { showCreateGroupDialog = false }
        )
    }
}

private fun formatDmLogTimestamp(date: Date?): String {
    if (date == null) return ""
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }
    val sameDay = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "h:mm a" else "MMM d"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}

@Composable
private fun NewDirectMessagePickerDialog(
    users: List<CreatorSummary>,
    isBusy: Boolean,
    onSelectUser: (CreatorSummary) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(users, search) {
        val q = search.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) users else users.filter { it.displayName.lowercase(Locale.ROOT).contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start Private Message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search users...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .height(260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filtered.isEmpty()) {
                        Text(
                            text = "No users found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        filtered.take(30).forEach { user ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isBusy) { onSelectUser(user) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        OnlineStatusDot(
                                            isOnline = user.isCurrentlyOnline(),
                                            showLabel = true
                                        )
                                    }
                                    Text(
                                        text = "Message",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreatePrivateGroupDialog(
    candidateMembers: List<Pair<String, String>>,
    isBusy: Boolean,
    onCreate: (
        name: String,
        description: String,
        privacyMode: String,
        allowMemberInvites: Boolean,
        selectedMembers: Map<String, String>
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var privacyMode by remember { mutableStateOf(PrivateGroupChat.PRIVACY_INVITE_ONLY) }
    var allowMemberInvites by remember { mutableStateOf(false) }
    val selectedMembers = remember { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Private Group Chat") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 200) description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Privacy Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = privacyMode == PrivateGroupChat.PRIVACY_INVITE_ONLY,
                        onClick = { privacyMode = PrivateGroupChat.PRIVACY_INVITE_ONLY },
                        label = { Text("Invite Only") }
                    )
                    FilterChip(
                        selected = privacyMode == PrivateGroupChat.PRIVACY_FRIENDS_CAN_INVITE,
                        onClick = { privacyMode = PrivateGroupChat.PRIVACY_FRIENDS_CAN_INVITE },
                        label = { Text("Members Can Invite") }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Allow member invites",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allowMemberInvites,
                        onCheckedChange = { allowMemberInvites = it }
                    )
                }

                if (candidateMembers.isNotEmpty()) {
                    Text(
                        text = "Invite Friends / Creators (${selectedMembers.size} selected)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    candidateMembers.take(20).forEach { (uid, memberName) ->
                        val checked = selectedMembers.containsKey(uid)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedMembers.remove(uid)
                                    else selectedMembers[uid] = memberName
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isNowChecked ->
                                    if (isNowChecked) selectedMembers[uid] = memberName
                                    else selectedMembers.remove(uid)
                                }
                            )
                            Text(text = memberName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(name, description, privacyMode, allowMemberInvites, selectedMembers.toMap())
                },
                enabled = !isBusy && name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_group_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
