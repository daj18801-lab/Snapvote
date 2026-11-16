package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatModerationStatus
import com.example.data.model.EffectiveStaffTier
import com.example.data.model.StaffRole
import com.example.data.model.SupportPayrollPayout
import com.example.data.model.SupportTicket
import com.example.data.model.UserProfile
import com.example.data.model.UserReport
import com.example.ui.viewmodel.ReportUserTarget
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

import com.example.ui.theme.formatDisplayNameWithRole
import com.example.ui.theme.roleBadgeForegroundColor

data class StaffModerationTargetInfo(
    val userId: String,
    val displayName: String,
    val role: String = UserProfile.ROLE_USER,
    val email: String? = null
)

@Composable
fun StaffModerationActionDialog(
    target: StaffModerationTargetInfo,
    staffTier: EffectiveStaffTier,
    currentUserId: String,
    currentModeration: ChatModerationStatus?,
    isBusy: Boolean,
    onBounceEverywhere: (targetUserId: String, targetName: String, durationMinutes: Long, reason: String) -> Unit,
    onMuteUser: (targetUserId: String, targetName: String, durationMinutes: Long) -> Unit,
    onUnmuteUser: (targetUserId: String) -> Unit,
    onBanChatUser: (targetUserId: String, targetName: String) -> Unit,
    onUnbanChatUser: (targetUserId: String) -> Unit,
    onSuspendAccount: (targetUserId: String, targetName: String, durationHours: Long, reason: String) -> Unit,
    onUnsuspendAccount: (targetUserId: String) -> Unit,
    onBanAccount: (targetUserId: String, targetName: String, reason: String) -> Unit,
    onUnbanAccount: (targetUserId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val isOwnerTarget = target.email.equals(StaffRole.OWNER_EMAIL, ignoreCase = true)
    val isSelf = target.userId == currentUserId
    val isSupportActor = staffTier == EffectiveStaffTier.SUPPORT
    val canModerateTarget = !isOwnerTarget && !isSelf && (
        staffTier == EffectiveStaffTier.OWNER ||
            staffTier == EffectiveStaffTier.ADMIN ||
            (isSupportActor && target.role == UserProfile.ROLE_USER)
        )

    var reasonInput by remember(target.userId) { mutableStateOf("") }
    val resolvedReason = reasonInput.trim().ifBlank { "Community guidelines enforcement" }

    val isMuted = currentModeration?.isCurrentlyMuted() == true
    val isChatBanned = currentModeration?.banned == true
    val isSuspended = currentModeration?.isCurrentlySuspended() == true
    val isAccountBanned = currentModeration?.accountBanned == true

    val formattedTargetName = formatDisplayNameWithRole(target.displayName, target.role, target.email)
    val targetRoleColor = roleBadgeForegroundColor(target.role, target.email)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Gavel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Moderate $formattedTargetName",
                color = targetRoleColor ?: MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!canModerateTarget) {
                    Text(
                        text = when {
                            isOwnerTarget -> "The App Owner account cannot be moderated."
                            isSelf -> "You cannot apply moderation actions to your own account."
                            else -> "Support monitors can only moderate regular User accounts (not Admins)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { if (it.length <= 160) reasonInput = it },
                        label = { Text("Reason (optional)") },
                        placeholder = { Text("Community guidelines enforcement") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("moderation_reason_input")
                    )

                    Text(
                        text = "1. Bounce (Kick from all 5 chat rooms)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onBounceEverywhere(target.userId, target.displayName, 15L, resolvedReason)
                                onDismiss()
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("mod_bounce_15m")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bounce 15m")
                        }
                        OutlinedButton(
                            onClick = {
                                onBounceEverywhere(target.userId, target.displayName, 60L, resolvedReason)
                                onDismiss()
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("mod_bounce_60m")
                        ) {
                            Text("Bounce 1h")
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "2. Chat Mute (Read-only in public chat rooms)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isMuted) {
                        Button(
                            onClick = {
                                onUnmuteUser(target.userId)
                                onDismiss()
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("mod_unmute_button")
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unmute User")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onMuteUser(target.userId, target.displayName, 15L)
                                    onDismiss()
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mod_mute_15m")
                            ) {
                                Text("15m")
                            }
                            OutlinedButton(
                                onClick = {
                                    onMuteUser(target.userId, target.displayName, 60L)
                                    onDismiss()
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mod_mute_1h")
                            ) {
                                Text("1h")
                            }
                            OutlinedButton(
                                onClick = {
                                    onMuteUser(target.userId, target.displayName, 24L * 60L)
                                    onDismiss()
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mod_mute_24h")
                            ) {
                                Text("24h")
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "3. Suspend Account (Timed full-app suspension)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (isSuspended) {
                        val suspText = currentModeration?.suspendedUntil?.toDate()?.let {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it)
                        } ?: "soon"
                        Text(
                            text = "Currently suspended until $suspText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = {
                                onUnsuspendAccount(target.userId)
                                onDismiss()
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("mod_unsuspend_button")
                        ) {
                            Text("Lift Suspension Now")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onSuspendAccount(target.userId, target.displayName, 24L, resolvedReason)
                                    onDismiss()
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mod_suspend_24h")
                            ) {
                                Text("24h")
                            }
                            OutlinedButton(
                                onClick = {
                                    onSuspendAccount(target.userId, target.displayName, 72L, resolvedReason)
                                    onDismiss()
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mod_suspend_3d")
                            ) {
                                Text("3 Days")
                            }
                            OutlinedButton(
                                onClick = {
                                    onSuspendAccount(target.userId, target.displayName, 168L, resolvedReason)
                                    onDismiss()
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mod_suspend_7d")
                            ) {
                                Text("7 Days")
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = if (isSupportActor) "4. Ban Escalation (Admin Approval)" else "4. Bans",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (isSupportActor) {
                        Text(
                            text = "Bans triggered by Support immediately suspend the user for 72h and send the ban request to Admins for approval.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (isChatBanned) {
                                onUnbanChatUser(target.userId)
                            } else {
                                onBanChatUser(target.userId, target.displayName)
                            }
                            onDismiss()
                        },
                        enabled = !isBusy && (!isSupportActor || !isChatBanned),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isChatBanned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("mod_chat_ban_toggle")
                    ) {
                        Icon(Icons.Rounded.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when {
                                isChatBanned && isSupportActor -> "Chat Banned (Admin Unban Only)"
                                isChatBanned -> "Unban from Chat Rooms"
                                isSupportActor -> "Request Chat Ban (Suspends + Sends to Admin)"
                                else -> "Ban from Chat Rooms Only"
                            }
                        )
                    }

                    Button(
                        onClick = {
                            if (isAccountBanned) {
                                onUnbanAccount(target.userId)
                            } else {
                                onBanAccount(target.userId, target.displayName, resolvedReason)
                            }
                            onDismiss()
                        },
                        enabled = !isBusy && (!isSupportActor || !isAccountBanned),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAccountBanned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("mod_account_ban_toggle")
                    ) {
                        Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when {
                                isAccountBanned && isSupportActor -> "Account Banned (Admin Unban Only)"
                                isAccountBanned -> "Unban Account"
                                isSupportActor -> "Suspend 72h & Send Account Ban Request"
                                else -> "Permanent Account Ban"
                            }
                        )
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
fun ReportUserDialog(
    target: ReportUserTarget,
    isBusy: Boolean,
    onSubmit: (reportedUserId: String, reportedUserName: String, reason: String, details: String, sourceContext: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReason by remember { mutableStateOf(UserReport.reportReasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Report ${target.displayName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select why you are reporting this user. Admin & Support moderators will review it promptly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UserReport.reportReasons.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            label = { Text(reason) },
                            modifier = Modifier.testTag("report_reason_${reason.take(12).lowercase(Locale.ROOT).replace(" ", "_")}")
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 400) details = it },
                    label = { Text("Additional details (optional)") },
                    placeholder = { Text("Describe what happened...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_user_details_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(target.userId, target.displayName, selectedReason, details, target.sourceContext)
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("submit_user_report_button")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportTicketsDialog(
    ticketsState: UiState<List<SupportTicket>>,
    isBusy: Boolean,
    initialCategory: String = SupportTicket.CATEGORY_OTHER,
    onCreateTicket: (category: String, subject: String, message: String, onCreated: () -> Unit) -> Unit,
    onCloseOwnTicket: (ticketId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Help & Support Tickets") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Need help with tokens, mic calls, safety, or an account appeal? Send a ticket directly to our Support & Admin team.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SupportTicket.categories.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("ticket_cat_$key")
                        )
                    }
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { if (it.length <= 120) subject = it },
                    label = { Text("Subject") },
                    placeholder = { Text("Briefly summarize your request") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_ticket_subject_input")
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 1200) message = it },
                    label = { Text("Message") },
                    placeholder = { Text("Explain the issue in detail...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_ticket_message_input")
                )

                Button(
                    onClick = {
                        onCreateTicket(selectedCategory, subject, message) {
                            subject = ""
                            message = ""
                        }
                    },
                    enabled = !isBusy && subject.isNotBlank() && message.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_support_ticket_button")
                ) {
                    Text("Submit Ticket")
                }

                HorizontalDivider()

                Text(
                    text = "My Tickets",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                when (ticketsState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = ticketsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is UiState.Success -> {
                        val tickets = ticketsState.data
                        if (tickets.isEmpty()) {
                            Text(
                                text = "You haven't created any support tickets yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            tickets.forEach { ticket ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = ticket.subject,
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = when (ticket.status) {
                                                    SupportTicket.STATUS_RESOLVED, SupportTicket.STATUS_CLOSED ->
                                                        MaterialTheme.colorScheme.tertiaryContainer
                                                    SupportTicket.STATUS_IN_PROGRESS ->
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = ticket.status.replace("_", " ").uppercase(Locale.ROOT),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = SupportTicket.categoryLabel(ticket.category),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = ticket.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!ticket.staffReply.isNullOrBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = "Staff Reply (${ticket.respondedByName ?: "Support"}):",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = ticket.staffReply,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                        if (ticket.status != SupportTicket.STATUS_CLOSED) {
                                            TextButton(
                                                onClick = { onCloseOwnTicket(ticket.ticketId) },
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Text("Close Ticket")
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
fun AccountRestrictedScreen(
    moderation: ChatModerationStatus,
    myTicketsState: UiState<List<SupportTicket>>,
    onOpenSupportAppeal: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBanned = moderation.accountBanned
    val title = if (isBanned) "Account Banned" else "Account Temporarily Suspended"
    val reason = if (isBanned) {
        moderation.accountBanReason?.takeIf { it.isNotBlank() } ?: "Repeated community guideline violations."
    } else {
        moderation.suspendReason?.takeIf { it.isNotBlank() } ?: "Your account has been temporarily suspended."
    }
    val expiresText = moderation.suspendedUntil?.toDate()?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
    }

    val openOrRepliedTicket = (myTicketsState as? UiState.Success)?.data?.firstOrNull()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isBanned) Icons.Rounded.Block else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Reason: $reason",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                if (!isBanned && expiresText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ends at $expiresText",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Text(
                    text = "You can still contact Support & Admin moderators to submit an appeal or review ticket updates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (openOrRepliedTicket != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Latest Ticket: ${openOrRepliedTicket.subject} (${openOrRepliedTicket.status.uppercase(Locale.ROOT)})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!openOrRepliedTicket.staffReply.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Staff Reply: ${openOrRepliedTicket.staffReply}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onOpenSupportAppeal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("appeal_support_tickets_button")
                ) {
                    Icon(Icons.Rounded.SupportAgent, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Appeal / Support Tickets")
                }

                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("restricted_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

@Composable
fun SupportStipendCard(
    userProfile: UserProfile?,
    fallbackStartTimestamp: Timestamp?,
    payrollHistory: List<SupportPayrollPayout>,
    isActionBusy: Boolean,
    onClaimWeeklyPay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMillis = remember { System.currentTimeMillis() }
    val tenureDays = userProfile?.supportTenureDays(fallbackStartTimestamp, nowMillis) ?: 0L
    val isSixMonthsEligible = userProfile?.isSupportSixMonthsEligible(fallbackStartTimestamp, nowMillis) ?: false
    val canClaimNow = userProfile?.canClaimWeeklySupportPay(fallbackStartTimestamp, nowMillis) ?: false
    val nextClaimMillis = userProfile?.nextSupportPayClaimAtMillis()
    val progress = (tenureDays.toFloat() / UserProfile.SUPPORT_PAY_WAIT_DAYS.toFloat()).coerceIn(0f, 1f)
    val totalTokensEarned = userProfile?.totalSupportPayTokensEarned ?: 0L

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("support_stipend_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Support Weekly Stipend",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "100 Tokens / Week ($10.00 value) after 6 months",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Paid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Support Tenure: $tenureDays / ${UserProfile.SUPPORT_PAY_WAIT_DAYS} days",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "Earned: $totalTokensEarned tokens",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
            }

            when {
                canClaimNow -> {
                    Button(
                        onClick = onClaimWeeklyPay,
                        enabled = !isActionBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("claim_support_pay_button")
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Claim 100 Weekly Tokens ($10.00)")
                    }
                }
                isSixMonthsEligible && nextClaimMillis != null -> {
                    val nextDate = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(nextClaimMillis))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Weekly payout claimed! Next 100-token claim unlocks on $nextDate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                else -> {
                    val remaining = (UserProfile.SUPPORT_PAY_WAIT_DAYS - tenureDays).coerceAtLeast(0L)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$remaining days remaining until your 6-month (180-day) milestone unlocks 100 tokens/week.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (payrollHistory.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Recent Support Payouts (${payrollHistory.size})",
                    style = MaterialTheme.typography.labelLarge
                )
                payrollHistory.take(4).forEach { payout ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Weekly Support Pay (${payout.weekLabel})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "+${payout.tokensAwarded} tokens (${payout.usdValueLabel()})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
