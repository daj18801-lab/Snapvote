package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.CreatorPayout
import com.example.data.model.CreatorSummary
import com.example.data.model.MediaPost
import com.example.data.model.VotePack
import com.example.data.model.VotePackCatalog
import com.example.media.MediaProcessor
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun VoteBalanceChip(
    balance: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("vote_balance_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.HowToVote,
                contentDescription = "Vote balance",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$balance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "+ Buy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun PhoneTokenBalanceChip(
    balance: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("phone_token_balance_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.PhoneInTalk,
                contentDescription = "Phone token balance",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "$balance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MediaPostCard(
    post: MediaPost,
    creatorSummary: CreatorSummary?,
    isOwner: Boolean,
    isCreatorUnlocked: Boolean,
    isStaff: Boolean,
    onCreatorClick: (String) -> Unit,
    onVoteClick: (MediaPost) -> Unit,
    onUnlockCreatorClick: (CreatorSummary) -> Unit,
    onOpenMediaClick: (MediaPost) -> Unit,
    onReportClick: (MediaPost) -> Unit,
    onDeleteClick: (MediaPost) -> Unit,
    modifier: Modifier = Modifier
) {
    val freeLimit = creatorSummary?.freePreviewLimit ?: CreatorSummary.DEFAULT_FREE_PREVIEW_LIMIT
    val unlockCost = creatorSummary?.unlockCostVotes ?: CreatorSummary.DEFAULT_UNLOCK_COST_VOTES
    val isLockedForViewer = !isOwner && !isCreatorUnlocked && post.postIndex >= freeLimit

    var showMenu by remember { mutableStateOf(false) }
    var voteBounce by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (voteBounce) 1.14f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        finishedListener = { voteBounce = false },
        label = "voteScale"
    )

    val bitmap = remember(post.id, post.mediaBase64, isLockedForViewer) {
        if (isLockedForViewer) null else MediaProcessor.decodeBase64ToBitmap(post.mediaBase64)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_card_${post.id}")
    ) {
        Column {
            // Creator header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCreatorClick(post.creatorId) }
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
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
                        text = post.creatorName.take(1).uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.creatorName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isOwner) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "YOU",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (isCreatorUnlocked) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.LockOpen,
                                contentDescription = "Unlocked creator",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = if (post.isVideo) "Video • ${formatDuration(post.durationMs)}" else "Photo • #${post.postIndex + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("post_menu_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Post options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("View creator gallery") },
                            leadingIcon = {
                                Icon(Icons.Rounded.LockOpen, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onCreatorClick(post.creatorId)
                            }
                        )
                        if (!isOwner) {
                            DropdownMenuItem(
                                text = { Text("Report post") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Flag, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onReportClick(post)
                                }
                            )
                        }
                        if (isOwner || isStaff) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Delete post",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick(post)
                                }
                            )
                        }
                    }
                }
            }

            // Media viewport
            val targetCreator = remember(creatorSummary, post.creatorId, post.creatorName) {
                creatorSummary ?: CreatorSummary(
                    creatorId = post.creatorId,
                    displayName = post.creatorName
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        if (isLockedForViewer) {
                            onUnlockCreatorClick(targetCreator)
                        } else {
                            onOpenMediaClick(post)
                        }
                    }
            ) {
                if (isLockedForViewer) {
                    GalleryLockOverlay(
                        creatorName = post.creatorName,
                        isVideo = post.isVideo,
                        unlockCostTokens = unlockCost,
                        onUnlockClick = { onUnlockCreatorClick(targetCreator) },
                        ctaTestTag = "unlock_post_cta_${post.id}"
                    )
                } else {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = post.caption.ifBlank { "Creator media post" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (!post.mediaDownloadUrl.isNullOrBlank() && !post.isVideo) {
                        AsyncImage(
                            model = post.mediaDownloadUrl,
                            contentDescription = post.caption.ifBlank { "Creator media post" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (post.isVideo) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.62f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play video",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Videocam,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatDuration(post.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Caption + Vote action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isLockedForViewer) {
                        "Unlock ${post.creatorName}'s vault to view & vote on this post."
                    } else {
                        post.caption.ifBlank { "Shared on SnapVote" }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isLockedForViewer) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )

                Button(
                    onClick = {
                        voteBounce = true
                        onVoteClick(post)
                    },
                    enabled = !isLockedForViewer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .scale(scale)
                        .minimumInteractiveComponentSize()
                        .testTag("vote_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Vote up",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${post.voteCount}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyVotesBottomSheet(
    currentBalance: Long,
    currentPaidBalance: Long = 0L,
    pendingCreator: CreatorSummary?,
    isBusy: Boolean,
    onBuyPack: (VotePack) -> Unit,
    onDismiss: () -> Unit
) {
    val freeBalance = (currentBalance - currentPaidBalance).coerceAtLeast(0L)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Buy Paid Tokens",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Only paid tokens can unlock creator galleries (${CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT}% payout; calls earn ${CreatorPayout.CREATOR_SHARE_PERCENT}%). Free sign-up tokens are for voting & chat passes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "$currentPaidBalance paid",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "$freeBalance free",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                        )
                    }
                }
            }

            if (pendingCreator != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Unlocking ${pendingCreator.displayName}'s gallery requires ${pendingCreator.unlockCostVotes} paid tokens (creator earns ${CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT}%). Free starter tokens cannot pay creators.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            VotePackCatalog.packs.forEach { pack ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(
                        1.dp,
                        if (pack.badge != null) MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ShoppingBag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${pack.votes} Votes • ${pack.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (pack.badge != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = pack.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = pack.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onBuyPack(pack) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("buy_pack_${pack.id}")
                        ) {
                            Text(
                                text = pack.priceLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Text(
                text = "Demo in-app vote packs credit your account immediately without charging a real payment card.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MediaViewerDialog(
    post: MediaPost,
    isLockedForViewer: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localVideoFile = remember(post.localVideoFileName, isLockedForViewer) {
        if (isLockedForViewer) null else MediaProcessor.getLocalVideoFile(context, post.localVideoFileName)
    }
    val videoStreamUri = remember(localVideoFile?.absolutePath, post.mediaDownloadUrl, isLockedForViewer) {
        when {
            isLockedForViewer -> null
            localVideoFile != null -> Uri.fromFile(localVideoFile)
            !post.mediaDownloadUrl.isNullOrBlank() -> Uri.parse(post.mediaDownloadUrl)
            else -> null
        }
    }
    val bitmap = remember(post.id, post.mediaBase64, isLockedForViewer) {
        if (isLockedForViewer) null else MediaProcessor.decodeBase64ToBitmap(post.mediaBase64)
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
                if (isLockedForViewer) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked creator media",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Locked Creator Media",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Unlock ${post.creatorName}'s gallery with paid tokens to view restricted photos & videos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                } else if (post.isVideo && videoStreamUri != null) {
                    val exoPlayer = remember(videoStreamUri.toString()) {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(videoStreamUri))
                            prepare()
                            playWhenReady = true
                        }
                    }
                    DisposableEffect(exoPlayer) {
                        onDispose {
                            exoPlayer.release()
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = post.caption.ifBlank { "Creator media" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 56.dp)
                        )
                    }
                    if (!post.isVideo && !post.mediaDownloadUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = post.mediaDownloadUrl,
                            contentDescription = post.caption.ifBlank { "Creator photo" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 56.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .testTag("close_media_viewer")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close viewer",
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(18.dp)
                ) {
                    Text(
                        text = post.creatorName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    if (!isLockedForViewer && post.caption.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = post.caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                    if (!isLockedForViewer && post.isVideo && videoStreamUri == null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Showing synced video preview frame (${formatDuration(post.durationMs)}). Enable Firebase Storage or record locally to stream full video.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportPostDialog(
    post: MediaPost,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val reasons = listOf("Spam or misleading", "Inappropriate content", "Harassment", "Copyright issue")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var customNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select a reason for Support & Admins to review ${post.creatorName}'s post:",
                    style = MaterialTheme.typography.bodySmall
                )
                reasons.forEach { reason ->
                    FilterChip(
                        selected = selectedReason == reason,
                        onClick = { selectedReason = reason },
                        label = { Text(reason) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = customNote,
                    onValueChange = { if (it.length <= 100) customNote = it },
                    label = { Text("Extra details (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val combined = if (customNote.isBlank()) selectedReason else "$selectedReason: $customNote"
                    onSubmit(combined)
                },
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}
