package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CreatorSummary
import com.example.data.model.FriendRelationship
import com.example.data.model.MediaPost
import com.example.ui.components.CreatorGalleryLockCard
import com.example.ui.components.GalleryLockBadge
import com.example.ui.components.MediaPostCard
import com.example.ui.components.OnlineStatusDot
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorGalleryScreen(
    creator: CreatorSummary?,
    creatorPosts: List<MediaPost>,
    currentUserId: String,
    currentVoteBalance: Long,
    currentPaidVoteBalance: Long = 0L,
    isUnlocked: Boolean,
    isStaff: Boolean,
    friendRelationship: FriendRelationship?,
    onBack: () -> Unit,
    onVoteClick: (MediaPost) -> Unit,
    onUnlockCreatorClick: (CreatorSummary) -> Unit,
    onBuyVotesClick: (CreatorSummary?) -> Unit,
    onOpenMediaClick: (MediaPost) -> Unit,
    onReportClick: (MediaPost) -> Unit,
    onReportUserClick: (String, String, String) -> Unit,
    onDeleteClick: (MediaPost) -> Unit,
    onSendFriendRequest: (String, String) -> Unit,
    onMessageUser: (String, String) -> Unit,
    onScheduleMicCall: (CreatorSummary) -> Unit,
    onWarnUserClick: ((String, String) -> Unit)? = null,
    isBlocked: Boolean = false,
    onBlockUserClick: ((String, String) -> Unit)? = null,
    onUnblockUserClick: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isOwner = creator?.creatorId == currentUserId
    val orderedPosts = remember(creatorPosts) {
        creatorPosts.sortedBy { it.postIndex }
    }
    val freeLimit = creator?.freePreviewLimit ?: CreatorSummary.DEFAULT_FREE_PREVIEW_LIMIT
    val unlockCost = creator?.unlockCostVotes ?: CreatorSummary.DEFAULT_UNLOCK_COST_VOTES
    val lockedCount = orderedPosts.count { !isOwner && !isUnlocked && it.postIndex >= freeLimit }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = creator?.displayName ?: "Creator Gallery",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("creator_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                if (creator != null && !isOwner) {
                    if (!isBlocked && onBlockUserClick != null) {
                        IconButton(
                            onClick = { onBlockUserClick(creator.creatorId, creator.displayName) },
                            modifier = Modifier.testTag("creator_block_user_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = "Block ${creator.displayName}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onReportUserClick(
                                creator.creatorId,
                                creator.displayName,
                                com.example.data.model.UserReport.CONTEXT_PROFILE
                            )
                        },
                        modifier = Modifier.testTag("creator_report_user_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Flag,
                            contentDescription = "Report user"
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (creator != null && !isOwner && isBlocked) {
                item(key = "blocked_creator_banner") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("creator_blocked_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "You have blocked ${creator.displayName}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (onUnblockUserClick != null) {
                                OutlinedButton(
                                    onClick = { onUnblockUserClick(creator.creatorId, creator.displayName) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("creator_unblock_button")
                                ) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            }
            if (creator != null) {
                item(key = "creator_header") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
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
                                        text = creator.displayName.take(1).uppercase(Locale.ROOT),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = creator.displayName,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        OnlineStatusDot(isOnline = creator.isCurrentlyOnline(), showLabel = true)
                                    }
                                    if (!creator.bio.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = creator.bio,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (!isOwner && (lockedCount > 0 || isUnlocked)) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    GalleryLockBadge(
                                        isUnlocked = isUnlocked,
                                        unlockCostTokens = unlockCost,
                                        onClick = { onUnlockCreatorClick(creator) },
                                        compact = false,
                                        modifier = Modifier.testTag("gallery_lock_badge_${creator.creatorId}")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatPill(
                                    label = "Posts",
                                    value = "${orderedPosts.size}",
                                    modifier = Modifier.weight(1f)
                                )
                                StatPill(
                                    label = "Total Votes",
                                    value = "${creator.totalVotesReceived}",
                                    modifier = Modifier.weight(1f)
                                )
                                StatPill(
                                    label = "Gallery",
                                    value = when {
                                        isOwner -> "Yours"
                                        isUnlocked || lockedCount == 0 -> "Open"
                                        else -> "Locked"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (!isOwner) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val friendLabel = when (friendRelationship?.status) {
                                        FriendRelationship.STATUS_ACCEPTED -> "Friends"
                                        FriendRelationship.STATUS_PENDING -> "Pending"
                                        else -> "Add Friend"
                                    }
                                    val canAdd = friendRelationship == null || friendRelationship.status == FriendRelationship.STATUS_DECLINED
                                    OutlinedButton(
                                        onClick = { onSendFriendRequest(creator.creatorId, creator.displayName) },
                                        enabled = canAdd,
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(friendLabel, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { onMessageUser(creator.creatorId, creator.displayName) },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("DM", maxLines = 1)
                                    }
                                    Button(
                                        onClick = { onScheduleMicCall(creator) },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("creator_schedule_mic_button")
                                    ) {
                                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Mic (Free)", maxLines = 1)
                                    }
                                }
                                if (isStaff && onWarnUserClick != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { onWarnUserClick(creator.creatorId, creator.displayName) },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("creator_staff_warn_button")
                                    ) {
                                        Icon(
                                            Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Send Warning Popup to ${creator.displayName}",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (lockedCount > 0 && !isOwner && !isUnlocked) {
                    item(key = "unlock_vault_banner") {
                        CreatorGalleryLockCard(
                            creator = creator,
                            lockedCount = lockedCount,
                            freeLimit = freeLimit,
                            unlockCostTokens = unlockCost,
                            currentPaidBalance = currentPaidVoteBalance,
                            onOpenUnlockDialog = { onUnlockCreatorClick(creator) }
                        )
                    }
                } else if (!isOwner && isUnlocked) {
                    item(key = "unlocked_banner") {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LockOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Full gallery unlocked — enjoy every photo & video!",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            if (orderedPosts.isEmpty()) {
                item(key = "no_creator_posts") {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("This creator hasn't published any posts yet. Go back")
                    }
                }
            } else {
                items(orderedPosts, key = { it.id }) { post ->
                    MediaPostCard(
                        post = post,
                        creatorSummary = creator,
                        isOwner = isOwner,
                        isCreatorUnlocked = isUnlocked,
                        isStaff = isStaff,
                        onCreatorClick = {},
                        onVoteClick = onVoteClick,
                        onUnlockCreatorClick = onUnlockCreatorClick,
                        onOpenMediaClick = onOpenMediaClick,
                        onReportClick = onReportClick,
                        onDeleteClick = onDeleteClick
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
