package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CreatorPayout
import com.example.data.model.CreatorSummary
import com.example.data.model.EffectiveStaffTier
import com.example.data.model.PhoneTokenPurchase
import com.example.data.model.ReferralCashout
import com.example.data.model.ReferralClaim
import com.example.data.model.ReferralRecord
import com.example.data.model.SupportPayrollPayout
import com.example.data.model.SupportTicket
import com.example.data.model.UserProfile
import com.example.data.model.VideoTokenPurchase
import com.example.data.model.VotePurchase
import com.example.ui.components.ReferralProgramPanel
import com.example.ui.theme.formatDisplayNameWithRole
import com.example.ui.theme.roleBadgeContainerColor
import com.example.ui.theme.roleBadgeForegroundColor
import com.example.ui.theme.roleBadgeOnContainerColor
import com.example.ui.theme.staffTierToRoleString
import com.example.ui.viewmodel.UiState
import java.text.DateFormat
import java.util.Locale

private val sexOptions = listOf(
    UserProfile.SEX_FEMALE to "Female",
    UserProfile.SEX_MALE to "Male",
    UserProfile.SEX_NON_BINARY to "Non-binary",
    UserProfile.SEX_UNSPECIFIED to "Prefer not to say"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    userProfile: UserProfile?,
    userEmail: String?,
    staffTier: EffectiveStaffTier,
    currentUserId: String = "",
    unlockedCreators: List<CreatorSummary>,
    purchaseHistory: List<VotePurchase>,
    phonePurchaseHistory: List<PhoneTokenPurchase>,
    videoPurchaseHistory: List<VideoTokenPurchase> = emptyList(),
    creatorPayouts: List<CreatorPayout>,
    mySupportPayroll: List<SupportPayrollPayout>,
    mySupportTicketsState: UiState<List<SupportTicket>>,
    myReferralRecord: ReferralRecord? = null,
    referralsMade: List<ReferralRecord> = emptyList(),
    referralClaims: List<ReferralClaim> = emptyList(),
    referralCashouts: List<ReferralCashout> = emptyList(),
    isActionBusy: Boolean,
    myPostsCount: Int,
    onBuyVotesClick: () -> Unit,
    onBuyPhoneTokensClick: () -> Unit,
    onBuyVideoTokensClick: () -> Unit = {},
    onBuyChatPassClick: () -> Unit,
    onBuyPrivatePassClick: () -> Unit,
    onOpenMyGallery: () -> Unit,
    onOpenCreatorGallery: (String) -> Unit,
    onOpenCreatorAnalytics: () -> Unit,
    onOpenAdminConsole: () -> Unit,
    onOpenSupportTickets: () -> Unit,
    onClaimSupportWeeklyPay: () -> Unit,
    onApplyReferralCode: (String) -> Unit = {},
    onClaimReferralReward: () -> Unit = {},
    onCashInReferralTokens: (Long, String, String, () -> Unit) -> Unit = { _, _, _, _ -> },
    onReferralFeedback: (String) -> Unit = {},
    onUpdateProfile: (String, String, Int?, String?, () -> Unit) -> Unit,
    blockedUsersCount: Int = 0,
    onOpenBlockedUsers: () -> Unit = {},
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }

    val displayName = userProfile?.displayName ?: "Creator"
    val bio = userProfile?.bio.orEmpty()
    val balance = userProfile?.voteBalance ?: 0L
    val paidBalance = userProfile?.paidVoteBalance ?: 0L
    val freeBalance = userProfile?.freeTokensAvailable() ?: 0L
    val phoneBalance = userProfile?.phoneTokenBalance ?: 0L
    val videoBalance = userProfile?.videoTokenBalance ?: 0L
    val isStaff = staffTier != EffectiveStaffTier.NONE
    val nowMillis = remember { System.currentTimeMillis() }
    val hasFreeTrial = userProfile?.hasActiveFreeTrial(nowMillis) ?: true
    val hasPaidPass = userProfile?.hasActivePaidChatPass(nowMillis) ?: false
    val hasPrivatePass = userProfile?.hasActivePrivateChatPass(nowMillis) ?: false
    val totalEarnedCents = remember(creatorPayouts) { creatorPayouts.sumOf { it.creatorShareCents } }
    val totalEarnedUsd = remember(totalEarnedCents) {
        String.format(Locale.US, "$%.2f", totalEarnedCents / 100.0)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "profile_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    val roleStr = staffTierToRoleString(staffTier)
                    val formattedDisplayName = formatDisplayNameWithRole(displayName, roleStr, userEmail)
                    val roleFgColor = roleBadgeForegroundColor(roleStr, userEmail)
                    val roleBgColor = roleBadgeContainerColor(roleStr, userEmail)
                    val roleOnBgColor = roleBadgeOnContainerColor(roleStr, userEmail)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    if (roleFgColor != null) {
                                        Brush.linearGradient(
                                            listOf(
                                                roleFgColor,
                                                roleBgColor ?: roleFgColor.copy(alpha = 0.7f)
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.take(1).uppercase(Locale.ROOT),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formattedDisplayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = roleFgColor ?: MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isStaff) FontWeight.Bold else FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (staffTier != EffectiveStaffTier.NONE) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = roleBgColor ?: MaterialTheme.colorScheme.primaryContainer,
                                        border = BorderStroke(1.dp, (roleFgColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.65f)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (staffTier == EffectiveStaffTier.SUPPORT) "SUPPORT" else "ADMIN",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = roleOnBgColor ?: MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (!userEmail.isNullOrBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val demoLine = buildList {
                                userProfile?.age?.let { add("Age $it") }
                                UserProfile.sexDisplayLabel(userProfile?.sex).takeIf { it.isNotEmpty() }?.let { add(it) }
                            }.joinToString(" • ")
                            if (demoLine.isNotEmpty()) {
                                Text(
                                    text = demoLine,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (bio.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = bio,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("edit_profile_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit profile")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onOpenMyGallery,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("my_gallery_button")
                        ) {
                            Text("My Gallery ($myPostsCount)")
                        }
                        if (isStaff) {
                            Button(
                                onClick = onOpenAdminConsole,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = roleFgColor ?: MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_staff_console_button")
                            ) {
                                Icon(
                                    Icons.Rounded.AdminPanelSettings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (staffTier == EffectiveStaffTier.OWNER || staffTier == EffectiveStaffTier.ADMIN) {
                                        "Admin Dashboard"
                                    } else {
                                        "Support Console"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Support 6-Month Weekly Stipend Card (100 Tokens/Week)
        if (staffTier == EffectiveStaffTier.SUPPORT) {
            item(key = "support_stipend_profile_card") {
                SupportStipendCard(
                    userProfile = userProfile,
                    fallbackStartTimestamp = null,
                    payrollHistory = mySupportPayroll,
                    isActionBusy = isActionBusy,
                    onClaimWeeklyPay = onClaimSupportWeeklyPay
                )
            }
        }

        // Vote & Token Wallet Card
        item(key = "wallet_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tokens & Vote Wallet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "$balance Tokens Available",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.HowToVote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Paid (Creator Payouts): $paidBalance",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Free Starter/Bonus: $freeBalance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Votes Cast: ${userProfile?.totalVotesCast ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "Purchased: ${userProfile?.totalVotesPurchased ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Text(
                        text = "Free sign-up tokens are for voting & chat passes only. Paid & referral-earned tokens pay creators.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                    )

                    Button(
                        onClick = onBuyVotesClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_buy_votes_button")
                    ) {
                        Icon(Icons.Rounded.ShoppingBag, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buy Token / Vote Packs")
                    }
                }
            }
        }

        // Free Social & Calls Summary Card
        item(key = "free_social_calls_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Free Chat, Mic & Live Video Calls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Public chat rooms, private 1:1 DMs & snaps, private group chats, 1-on-1 mic calls, and live video calls are all 100% free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Referral Program & Rewards Card (Refer 10 buyers -> +100 creator or cashable tokens)
        item(key = "referral_program_card") {
            ReferralProgramPanel(
                userProfile = userProfile,
                currentUserId = currentUserId.ifBlank { userProfile?.userId.orEmpty() },
                myReferralRecord = myReferralRecord,
                referralsMade = referralsMade,
                referralClaims = referralClaims,
                referralCashouts = referralCashouts,
                isActionBusy = isActionBusy,
                onApplyReferralCode = onApplyReferralCode,
                onClaimReferralReward = onClaimReferralReward,
                onCashInReferralTokens = onCashInReferralTokens,
                onCopiedCodeFeedback = onReferralFeedback
            )
        }

        // Creator 70% Revenue Share Earnings Card
        item(key = "creator_earnings_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Creator Earnings (40% Content Share)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = totalEarnedUsd,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.MonetizationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Text(
                        text = "You earn 40% of token value when fans unlock your extra photos & videos (${creatorPayouts.size} payouts).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    creatorPayouts.take(5).forEach { payout ->
                        val sourceLabel = when (payout.sourceType) {
                            CreatorPayout.SOURCE_VIDEO_CALL -> "Video Call • ${payout.payerName}"
                            CreatorPayout.SOURCE_MIC_CALL -> "Mic Call • ${payout.payerName}"
                            else -> "Extra Content Unlock • ${payout.payerName}"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sourceLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "+${payout.creatorShareUsdLabel()} (${payout.creatorSharePercent}%)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Button(
                        onClick = onOpenCreatorAnalytics,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_creator_analytics_button")
                    ) {
                        Icon(Icons.Rounded.AutoGraph, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Creator Analytics & Payout Eligibility")
                    }
                }
            }
        }


        // Unlocked Creators Section
        item(key = "unlocked_header") {
            Text(
                text = "Unlocked Creator Galleries (${unlockedCreators.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (unlockedCreators.isEmpty()) {
            item(key = "no_unlocked") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "When you spend votes to unlock a creator's extra photos & videos, they'll appear here for quick access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(unlockedCreators, key = { it.creatorId }) { creator ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCreatorGallery(creator.creatorId) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = creator.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${creator.postCount} posts • ${creator.totalVotesReceived} votes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Purchase History Section
        if (purchaseHistory.isNotEmpty() || phonePurchaseHistory.isNotEmpty() || videoPurchaseHistory.isNotEmpty()) {
            item(key = "purchases_header") {
                Text(
                    text = "Token Purchase History",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(videoPurchaseHistory, key = { "video_${it.id}" }) { purchase ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${purchase.packTitle} (+${purchase.tokensAdded} video tokens)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = purchase.priceLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            items(phonePurchaseHistory, key = { "phone_${it.id}" }) { purchase ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneInTalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${purchase.packTitle} (+${purchase.tokensAdded} phone tokens)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = purchase.priceLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            items(purchaseHistory, key = { "vote_${it.id}" }) { purchase ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${purchase.packTitle} (+${purchase.votesAdded} tokens)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = purchase.priceLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // User Settings & Privacy (Blocked Users) Card
        item(key = "user_settings_blocked_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_settings_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Privacy & User Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenBlockedUsers)
                            .testTag("profile_blocked_users_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Blocked Users",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (blockedUsersCount == 0) {
                                        "No accounts blocked • Tap to manage"
                                    } else {
                                        "$blockedUsersCount restricted account${if (blockedUsersCount == 1) "" else "s"} • View or unblock"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (blockedUsersCount > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "$blockedUsersCount",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Open Blocked Users"
                            )
                        }
                    }
                }
            }
        }

        // Help & Support Tickets Card
        item(key = "support_tickets_card") {
            val myTickets = (mySupportTicketsState as? UiState.Success)?.data.orEmpty()
            val openCount = myTickets.count {
                it.status == SupportTicket.STATUS_OPEN || it.status == SupportTicket.STATUS_IN_PROGRESS
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Help & Support Tickets",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = if (myTickets.isEmpty()) {
                            "Need help with tokens, mic sessions, or safety concerns? Open a support ticket anytime."
                        } else {
                            "You have ${myTickets.size} ticket(s) ($openCount active). Tap below to view replies or create a new ticket."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onOpenSupportTickets,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_support_tickets_button")
                    ) {
                        Text("Create or View Support Tickets")
                    }
                }
            }
        }

        // Sign Out Button
        item(key = "sign_out") {
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }

    if (showEditDialog) {
        var editName by remember { mutableStateOf(displayName) }
        var editBio by remember { mutableStateOf(bio) }
        var editAgeText by remember { mutableStateOf(userProfile?.age?.toString().orEmpty()) }
        var editSex by remember { mutableStateOf(userProfile?.sex) }
        val parsedAge = editAgeText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val ageError = editAgeText.isNotBlank() && (parsedAge == null || parsedAge !in 18..110)

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { if (it.length <= 60) editName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_name_input")
                    )
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { if (it.length <= 240) editBio = it },
                        label = { Text("Creator Bio") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_bio_input")
                    )
                    OutlinedTextField(
                        value = editAgeText,
                        onValueChange = { input ->
                            if (input.length <= 3 && input.all { it.isDigit() }) {
                                editAgeText = input
                            }
                        },
                        label = { Text("Age (18–110, optional)") },
                        placeholder = { Text("25") },
                        singleLine = true,
                        isError = ageError,
                        supportingText = if (ageError) {
                            { Text("Enter an age between 18 and 110") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_age_input")
                    )
                    Text(
                        text = "Sex (for User Discovery filters)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        sexOptions.forEach { (value, label) ->
                            FilterChip(
                                selected = editSex == value,
                                onClick = { editSex = if (editSex == value) null else value },
                                label = { Text(label) },
                                modifier = Modifier.testTag("edit_sex_$value")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateProfile(editName, editBio, parsedAge, editSex) {
                            showEditDialog = false
                        }
                    },
                    enabled = editName.isNotBlank() && !ageError,
                    modifier = Modifier.testTag("save_profile_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
