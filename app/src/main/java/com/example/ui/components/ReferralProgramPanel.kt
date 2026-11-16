package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ReferralCashout
import com.example.data.model.ReferralClaim
import com.example.data.model.ReferralRecord
import com.example.data.model.UserProfile
import java.util.Locale

@Composable
fun ReferralProgramPanel(
    userProfile: UserProfile?,
    currentUserId: String,
    myReferralRecord: ReferralRecord?,
    referralsMade: List<ReferralRecord>,
    referralClaims: List<ReferralClaim>,
    referralCashouts: List<ReferralCashout>,
    isActionBusy: Boolean,
    onApplyReferralCode: (String) -> Unit,
    onClaimReferralReward: () -> Unit,
    onCashInReferralTokens: (Long, String, String, () -> Unit) -> Unit,
    onCopiedCodeFeedback: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var codeInput by remember { mutableStateOf("") }
    var showCashInDialog by remember { mutableStateOf(false) }

    val referralCode = userProfile?.effectiveReferralCode(currentUserId)
        ?: UserProfile.defaultReferralCodeForUid(currentUserId)
    val cashableTokens = userProfile?.referralCashableBalance ?: 0L
    val totalEarnedTokens = userProfile?.totalReferralTokensEarned ?: 0L
    val totalCashedInTokens = userProfile?.totalReferralTokensCashedIn ?: 0L
    val cashableUsdLabel = userProfile?.cashableUsdLabel() ?: "$0.00"

    val unclaimedQualified = remember(referralsMade) {
        referralsMade.filter { it.isQualified && !it.rewardClaimed }
    }
    val unclaimedCount = unclaimedQualified.size
    val requiredCount = UserProfile.REFERRALS_REQUIRED_PER_REWARD
    val progressFraction = (unclaimedCount.toFloat() / requiredCount.toFloat()).coerceIn(0f, 1f)
    val canClaimReward = unclaimedCount >= requiredCount && !isActionBusy

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("referral_program_card")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Referral Program & Rewards",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Refer 10 users who buy tokens to earn +100 tokens usable on content creators or cash in ($10.00).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.CardGiftcard,
                    contentDescription = "Referral rewards",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Your Invite Code Row
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "YOUR REFERRAL CODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = referralCode,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("my_referral_code_text")
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(referralCode))
                            onCopiedCodeFeedback("Copied referral code $referralCode")
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("copy_referral_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy referral code",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            val shareText = "Join me on SnapVote! Enter my referral code $referralCode in your Profile tab before buying tokens."
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Referral Code"))
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("share_referral_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share referral code",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Milestone Progress (10 paying referrals -> +100 tokens)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Paying Referrals Progress",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${unclaimedCount.coerceAtMost(requiredCount)} / $requiredCount Ready",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("referral_progress_count")
                    )
                }
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                if (unclaimedCount > requiredCount) {
                    Text(
                        text = "$unclaimedCount total unclaimed paying referrals queued (${unclaimedCount / requiredCount} reward batch ready).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Button(
                onClick = onClaimReferralReward,
                enabled = canClaimReward,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("claim_referral_reward_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CardGiftcard,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (unclaimedCount >= requiredCount) {
                        "Claim +100 Creator/Cashable Tokens ($10)"
                    } else {
                        "Refer ${requiredCount - unclaimedCount} More Buyer(s) to Unlock +100"
                    }
                )
            }

            // Cashable Referral Token Balance & Cash-In Action
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Cashable Referral Balance",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "$cashableTokens Tokens ($cashableUsdLabel)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.testTag("cashable_referral_balance_text")
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = "Spend these earned tokens on content creators OR cash them out at $0.10/token. Lifetime earned: $totalEarnedTokens • Cashed in: $totalCashedInTokens.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                    OutlinedButton(
                        onClick = { showCashInDialog = true },
                        enabled = cashableTokens >= 1L && !isActionBusy,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_cash_in_referral_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Paid,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (cashableTokens >= 1L) "Cash In Referral Tokens ($cashableUsdLabel)"
                            else "Earn 100 Referral Tokens to Cash In"
                        )
                    }
                }
            }

            HorizontalDivider()

            // Enter a friend's referral code (or show linked referrer status)
            if (myReferralRecord == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Were you referred by a friend?",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { input ->
                                val filtered = input.uppercase(Locale.ROOT).filter { it in 'A'..'Z' || it in '0'..'9' }
                                if (filtered.length <= 16) codeInput = filtered
                            },
                            label = { Text("Enter Referral Code") },
                            placeholder = { Text("e.g. SVABC123") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("apply_referral_code_input")
                        )
                        Button(
                            onClick = { onApplyReferralCode(codeInput) },
                            enabled = codeInput.trim().length >= 6 && !isActionBusy,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("apply_referral_code_button")
                        ) {
                            Text("Apply")
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("linked_referral_status_card")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (myReferralRecord.isQualified) Icons.Rounded.CheckCircle else Icons.Rounded.HourglassTop,
                            contentDescription = null,
                            tint = if (myReferralRecord.isQualified) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Referred by ${myReferralRecord.referrerName} (${myReferralRecord.referralCode})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (myReferralRecord.isQualified) {
                                    "Qualified! Your token purchase counted toward their reward."
                                } else {
                                    "Pending — buy any token pack (vote, phone, or video) to qualify your referrer."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Recent Referrals Made List
            if (referralsMade.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Your Referrals (${referralsMade.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    referralsMade.take(10).forEach { ref ->
                        val statusLabel = when {
                            ref.rewardClaimed -> "Rewarded"
                            ref.isQualified -> "Qualified"
                            else -> "Pending buy"
                        }
                        val statusColor = when {
                            ref.rewardClaimed -> MaterialTheme.colorScheme.secondary
                            ref.isQualified -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ref.referredUserName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                    }
                }
            }

            // Recent Cash-In Receipts
            if (referralCashouts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Recent Cash-Ins (${referralClaims.size} milestone claims • ${referralCashouts.size} cash-outs)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    referralCashouts.take(3).forEach { cashout ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${cashout.tokensCashedIn} tokens • ${cashout.payoutMethod}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "${cashout.usdLabel()} (${cashout.status})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCashInDialog) {
        CashInReferralTokensDialog(
            maxCashableTokens = cashableTokens,
            isBusy = isActionBusy,
            onConfirm = { amount, method, destination ->
                onCashInReferralTokens(amount, method, destination) {
                    showCashInDialog = false
                }
            },
            onDismiss = { showCashInDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashInReferralTokensDialog(
    maxCashableTokens: Long,
    isBusy: Boolean,
    onConfirm: (Long, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var tokensText by remember { mutableStateOf(maxCashableTokens.toString()) }
    var selectedMethod by remember { mutableStateOf(ReferralCashout.METHOD_INSTANT_PAYOUT) }
    var destination by remember { mutableStateOf("") }

    val parsedAmount = tokensText.trim().toLongOrNull() ?: 0L
    val isAmountValid = parsedAmount in 1L..maxCashableTokens
    val isDestValid = destination.trim().length in 3..120
    val estimatedUsd = String.format(
        Locale.US,
        "$%.2f",
        (parsedAmount.coerceAtLeast(0L) * UserProfile.CENTS_PER_TOKEN) / 100.0
    )

    val methodOptions = listOf(
        ReferralCashout.METHOD_INSTANT_PAYOUT to "Instant Payout",
        ReferralCashout.METHOD_PAYPAL to "PayPal",
        ReferralCashout.METHOD_BANK_ACH to "Bank ACH"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cash In Referral Tokens") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Available to cash in: $maxCashableTokens tokens ($0.10 per token). Cashed-in tokens are deducted from your spendable balance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tokensText,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) {
                            tokensText = input
                        }
                    },
                    label = { Text("Tokens to Cash In (Max $maxCashableTokens)") },
                    supportingText = {
                        Text(
                            if (isAmountValid) "Payout value: $estimatedUsd"
                            else "Enter an amount from 1 to $maxCashableTokens"
                        )
                    },
                    isError = tokensText.isNotEmpty() && !isAmountValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cash_in_amount_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    methodOptions.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedMethod == key,
                            onClick = { selectedMethod = key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("cash_in_method_$key")
                        )
                    }
                }
                OutlinedTextField(
                    value = destination,
                    onValueChange = { if (it.length <= 120) destination = it },
                    label = {
                        Text(
                            when (selectedMethod) {
                                ReferralCashout.METHOD_PAYPAL -> "PayPal Email"
                                ReferralCashout.METHOD_BANK_ACH -> "Routing / Account Label"
                                else -> "Debit Card / Instant Handle"
                            }
                        )
                    },
                    placeholder = { Text("payout@example.com") },
                    singleLine = true,
                    isError = destination.isNotEmpty() && !isDestValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cash_in_destination_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(parsedAmount, selectedMethod, destination.trim()) },
                enabled = isAmountValid && isDestValid && !isBusy,
                modifier = Modifier.testTag("confirm_cash_in_button")
            ) {
                Text("Cash Out $estimatedUsd")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
