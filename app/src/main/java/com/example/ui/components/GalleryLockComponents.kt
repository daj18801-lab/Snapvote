package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CreatorPayout
import com.example.data.model.CreatorSummary

/**
 * Compact clickable Lock UI badge for creator galleries and discovery cards.
 * Tapping a locked badge opens the "Unlock for X tokens" dialog.
 */
@Composable
fun GalleryLockBadge(
    isUnlocked: Boolean,
    unlockCostTokens: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val containerColor = if (isUnlocked) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isUnlocked) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val iconColor = if (isUnlocked) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Surface(
        onClick = onClick,
        enabled = !isUnlocked,
        color = containerColor,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.45f)),
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (isUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                contentDescription = if (isUnlocked) "Gallery unlocked" else "Gallery locked — tap to unlock for $unlockCostTokens tokens",
                tint = iconColor,
                modifier = Modifier.size(15.dp)
            )
            if (!compact) {
                Text(
                    text = if (isUnlocked) "Unlocked" else "Lock • $unlockCostTokens tokens",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Restricted-media Lock UI overlay. Never draws underlying photo/video pixels so
 * non-paying viewers cannot see restricted media on any Android API level.
 */
@Composable
fun GalleryLockOverlay(
    creatorName: String,
    isVideo: Boolean,
    unlockCostTokens: Long,
    onUnlockClick: () -> Unit,
    ctaTestTag: String,
    modifier: Modifier = Modifier
) {
    val gradientStart = MaterialTheme.colorScheme.surfaceContainerHighest
    val gradientMid = MaterialTheme.colorScheme.surfaceContainerHigh
    val accentPrimary = MaterialTheme.colorScheme.secondary
    val accentSecondary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF10131C),
                        Color(0xFF1B1630),
                        gradientStart.copy(alpha = 0.92f)
                    )
                )
            )
            .clickable(onClick = onUnlockClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.44f)
            drawCircle(
                color = accentPrimary.copy(alpha = 0.10f),
                radius = size.minDimension * 0.46f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = accentSecondary.copy(alpha = 0.12f),
                radius = size.minDimension * 0.31f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = gradientMid.copy(alpha = 0.14f),
                radius = size.minDimension * 0.18f,
                center = center
            )
        }

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, accentPrimary.copy(alpha = 0.4f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = accentPrimary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isVideo) "LOCKED VIDEO" else "LOCKED PHOTO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (isVideo) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = "Locked video",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(6.dp)
                        .size(16.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accentPrimary.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Locked gallery post",
                    tint = accentPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Locked Creator Media",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap Lock to unlock all of $creatorName's gallery",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.80f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onUnlockClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentPrimary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(ctaTestTag)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Unlock for $unlockCostTokens tokens",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Creator Gallery Lock Card displayed above locked media items inside a creator's gallery.
 * Clicking the card or its button opens the "Unlock for X tokens" dialog.
 */
@Composable
fun CreatorGalleryLockCard(
    creator: CreatorSummary,
    lockedCount: Int,
    freeLimit: Long,
    unlockCostTokens: Long,
    currentPaidBalance: Long,
    onOpenUnlockDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasEnoughPaid = currentPaidBalance >= unlockCostTokens
    Card(
        onClick = onOpenUnlockDialog,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("creator_lock_component")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked gallery",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "$lockedCount Restricted Posts Locked",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Paid Balance: $currentPaidBalance / $unlockCostTokens tokens",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasEnoughPaid) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }

            Text(
                text = "You're viewing ${creator.displayName}'s $freeLimit free previews. Tap below to unlock all restricted photos & videos (${CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT}% goes to ${creator.displayName}). Free starter tokens cannot unlock creator media.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.88f)
            )

            Button(
                onClick = onOpenUnlockDialog,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("unlock_gallery_button")
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock for $unlockCostTokens tokens",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Confirmation dialog titled "Unlock for X tokens" triggered by any Lock UI component.
 */
@Composable
fun UnlockGalleryDialog(
    creator: CreatorSummary,
    lockedPostCount: Int,
    currentTotalBalance: Long,
    currentPaidBalance: Long,
    isBusy: Boolean,
    onConfirmUnlock: (CreatorSummary) -> Unit,
    onBuyPaidTokens: (CreatorSummary) -> Unit,
    onDismiss: () -> Unit
) {
    val unlockCost = creator.unlockCostVotes
    val hasEnoughPaid = currentPaidBalance >= unlockCost
    val freeBalance = (currentTotalBalance - currentPaidBalance).coerceAtLeast(0L)
    val neededPaid = (unlockCost - currentPaidBalance).coerceAtLeast(0L)

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Lock dialog",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Unlock for $unlockCost tokens",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("unlock_dialog_title")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Unlock ${creator.displayName}'s complete gallery" +
                        if (lockedPostCount > 0) " ($lockedPostCount restricted photos & videos)." else ".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Gallery Unlock Cost", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "$unlockCost paid tokens",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Your Paid Tokens", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "$currentPaidBalance tokens",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (hasEnoughPaid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Creator Payout Share", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT}% to creator",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!hasEnoughPaid) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "You need $neededPaid more paid token(s) to view restricted media. Your $freeBalance free sign-up tokens cannot be awarded to creators.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (hasEnoughPaid) {
                Button(
                    onClick = { onConfirmUnlock(creator) },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_unlock_gallery_button")
                ) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unlock for $unlockCost tokens")
                }
            } else {
                Button(
                    onClick = { onBuyPaidTokens(creator) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dialog_buy_paid_tokens_button")
                ) {
                    Icon(Icons.Rounded.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buy Paid Tokens ($neededPaid needed)")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("cancel_unlock_gallery_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
