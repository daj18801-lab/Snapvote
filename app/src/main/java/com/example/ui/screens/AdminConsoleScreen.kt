package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminAnnouncement
import com.example.data.model.BanRequest
import com.example.data.model.ChatModerationStatus
import com.example.data.model.ContentReport
import com.example.data.model.EffectiveStaffTier
import com.example.data.model.MediaPost
import com.example.data.model.StaffRole
import com.example.data.model.SupportPayrollPayout
import com.example.data.model.SupportTicket
import com.example.data.model.UserProfile
import com.example.data.model.UserReport
import com.example.ui.components.AdminBroadcastDialog
import com.example.ui.components.AdminBroadcastsSectionCard
import com.example.ui.components.SendUserWarningDialog
import com.example.ui.components.WarningPopupTarget
import com.example.ui.theme.AdminRed
import com.example.ui.theme.AdminRedContainer
import com.example.ui.theme.AdminRedOnContainer
import com.example.ui.theme.SupportBlue
import com.example.ui.theme.SupportBlueContainer
import com.example.ui.theme.SupportBlueOnContainer
import com.example.ui.theme.formatDisplayNameWithRole
import com.example.ui.theme.normalizeStaffRole
import com.example.ui.theme.roleBadgeContainerColor
import com.example.ui.theme.roleBadgeForegroundColor
import com.example.ui.theme.roleBadgeOnContainerColor
import com.example.ui.theme.staffTierToRoleString
import com.example.ui.viewmodel.UiState
import java.util.Locale

private enum class UserRoleFilter(val label: String) {
    ALL("All"),
    USER("Users"),
    SUPPORT("Support"),
    ADMIN("Admins")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(
    staffTier: EffectiveStaffTier,
    currentUserId: String,
    currentUserEmail: String?,
    currentUserProfile: UserProfile?,
    allUsersState: UiState<List<UserProfile>>,
    reportsState: UiState<List<ContentReport>>,
    userReportsState: UiState<List<UserReport>>,
    allSupportTicketsState: UiState<List<SupportTicket>>,
    banRequestsState: UiState<List<BanRequest>>,
    chatModerationsState: UiState<List<ChatModerationStatus>>,
    staffRolesState: UiState<List<StaffRole>>,
    mySupportPayroll: List<SupportPayrollPayout>,
    allSupportPayroll: List<SupportPayrollPayout>,
    allPublicPosts: List<MediaPost>,
    isActionBusy: Boolean,
    onAssignUserRole: (UserProfile, String) -> Unit,
    onBounceUserEverywhere: (String, String, Long, String) -> Unit,
    onMuteUser: (String, String, Long) -> Unit,
    onUnmuteUser: (String) -> Unit,
    onBanUser: (String, String) -> Unit,
    onUnbanUser: (String) -> Unit,
    onSuspendUser: (String, String, Long, String) -> Unit,
    onUnsuspendUser: (String) -> Unit,
    onBanAccountUser: (String, String, String) -> Unit,
    onUnbanAccountUser: (String) -> Unit,
    onReviewBanRequest: (BanRequest, Boolean) -> Unit,
    onResolveReport: (ContentReport, String, Boolean) -> Unit,
    onResolveUserReport: (String, String, String) -> Unit,
    onReplySupportTicket: (String, String, String, () -> Unit) -> Unit,
    onClaimSupportWeeklyPay: () -> Unit,
    onHideOrDeletePost: (MediaPost, Boolean) -> Unit,
    onSaveStaffRole: (String, String, String, Boolean, () -> Unit) -> Unit,
    onRemoveStaffRole: (String) -> Unit,
    adminAnnouncements: List<AdminAnnouncement> = emptyList(),
    onSendAdminAnnouncement: (String, String, String, () -> Unit) -> Unit = { _, _, _, _ -> },
    onDeleteAdminAnnouncement: (String) -> Unit = {},
    onSendUserWarning: (String, String, String, String, String, () -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val canManageTeam = staffTier == EffectiveStaffTier.OWNER || staffTier == EffectiveStaffTier.ADMIN
    var selectedTab by remember(canManageTeam) { mutableIntStateOf(0) }
    var activeModerationTarget by remember { mutableStateOf<StaffModerationTargetInfo?>(null) }
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var activeWarningTarget by remember { mutableStateOf<WarningPopupTarget?>(null) }

    val moderationsByUser = remember(chatModerationsState) {
        (chatModerationsState as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()
    }

    val openUserReportsCount = remember(userReportsState) {
        (userReportsState as? UiState.Success)?.data?.count { it.status == UserReport.STATUS_OPEN } ?: 0
    }
    val openTicketsCount = remember(allSupportTicketsState) {
        (allSupportTicketsState as? UiState.Success)?.data?.count {
            it.status == SupportTicket.STATUS_OPEN || it.status == SupportTicket.STATUS_IN_PROGRESS
        } ?: 0
    }
    val pendingBanRequestsCount = remember(banRequestsState, canManageTeam) {
        if (!canManageTeam) 0
        else (banRequestsState as? UiState.Success)?.data?.count { it.status == BanRequest.STATUS_PENDING } ?: 0
    }
    val inboxBadgeCount = openUserReportsCount + openTicketsCount + pendingBanRequestsCount

    val tierRoleStr = staffTierToRoleString(staffTier)
    val bannerBg = roleBadgeContainerColor(tierRoleStr, currentUserEmail)
        ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    val bannerAccent = roleBadgeForegroundColor(tierRoleStr, currentUserEmail)
        ?: MaterialTheme.colorScheme.primary
    val bannerText = roleBadgeOnContainerColor(tierRoleStr, currentUserEmail)
        ?: MaterialTheme.colorScheme.onPrimaryContainer

    Column(modifier = modifier.fillMaxSize()) {
        // Role Clearance Banner
        Surface(
            color = bannerBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (canManageTeam) Icons.Rounded.AdminPanelSettings else Icons.Rounded.SupportAgent,
                    contentDescription = null,
                    tint = bannerAccent
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val selfFormatted = currentUserProfile?.displayName?.takeIf { it.isNotBlank() }?.let {
                        formatDisplayNameWithRole(it, tierRoleStr, currentUserEmail)
                    }
                    Text(
                        text = when (staffTier) {
                            EffectiveStaffTier.OWNER -> "Owner Admin Dashboard"
                            EffectiveStaffTier.ADMIN -> "Administrator Dashboard"
                            EffectiveStaffTier.SUPPORT -> "Support Monitor Console"
                            EffectiveStaffTier.NONE -> "Staff Console"
                        } + (selfFormatted?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = bannerText
                    )
                    if (!currentUserEmail.isNullOrBlank()) {
                        Text(
                            text = currentUserEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = bannerText.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Users") },
                modifier = Modifier.testTag("staff_tab_users")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(if (inboxBadgeCount > 0) "Tickets & Reports ($inboxBadgeCount)" else "Tickets & Reports") },
                modifier = Modifier.testTag("staff_tab_tickets_reports")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Moderation") },
                modifier = Modifier.testTag("staff_tab_moderation")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text(if (canManageTeam) "Team & Payroll" else "My Stipend") },
                modifier = Modifier.testTag("staff_tab_team")
            )
        }

        when (selectedTab) {
            0 -> UserDirectorySection(
                staffTier = staffTier,
                canManageTeam = canManageTeam,
                currentUserId = currentUserId,
                allUsersState = allUsersState,
                moderationsByUser = moderationsByUser,
                adminAnnouncements = adminAnnouncements,
                isActionBusy = isActionBusy,
                onOpenComposeBroadcast = { showBroadcastDialog = true },
                onDeleteAnnouncement = onDeleteAdminAnnouncement,
                onAssignUserRole = onAssignUserRole,
                onOpenModerationDialog = { activeModerationTarget = it },
                onOpenWarnDialog = { uid, name -> activeWarningTarget = WarningPopupTarget(uid, name) }
            )
            1 -> TicketsAndReportsSection(
                canManageTeam = canManageTeam,
                banRequestsState = banRequestsState,
                userReportsState = userReportsState,
                supportTicketsState = allSupportTicketsState,
                allUsersState = allUsersState,
                isActionBusy = isActionBusy,
                onReviewBanRequest = onReviewBanRequest,
                onModerateReportedUser = { uid, name, role, email ->
                    activeModerationTarget = StaffModerationTargetInfo(uid, name, role, email)
                },
                onResolveUserReport = onResolveUserReport,
                onReplySupportTicket = onReplySupportTicket
            )
            2 -> ModerationQueueSection(
                reportsState = reportsState,
                chatModerationsState = chatModerationsState,
                allPublicPosts = allPublicPosts,
                onResolveReport = onResolveReport,
                onHideOrDeletePost = onHideOrDeletePost,
                onUnmuteUser = onUnmuteUser,
                onUnbanUser = onUnbanUser,
                onUnsuspendUser = onUnsuspendUser,
                onUnbanAccountUser = onUnbanAccountUser
            )
            else -> {
                if (canManageTeam) {
                    TeamManagementSection(
                        staffRolesState = staffRolesState,
                        allSupportPayroll = allSupportPayroll,
                        isActionBusy = isActionBusy,
                        onSaveStaffRole = onSaveStaffRole,
                        onRemoveStaffRole = onRemoveStaffRole
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "support_console_stipend") {
                            SupportStipendCard(
                                userProfile = currentUserProfile,
                                fallbackStartTimestamp = null,
                                payrollHistory = mySupportPayroll,
                                isActionBusy = isActionBusy,
                                onClaimWeeklyPay = onClaimSupportWeeklyPay
                            )
                        }
                    }
                }
            }
        }
    }

    activeModerationTarget?.let { target ->
        StaffModerationActionDialog(
            target = target,
            staffTier = staffTier,
            currentUserId = currentUserId,
            currentModeration = moderationsByUser[target.userId],
            isBusy = isActionBusy,
            onBounceEverywhere = onBounceUserEverywhere,
            onMuteUser = onMuteUser,
            onUnmuteUser = onUnmuteUser,
            onBanChatUser = onBanUser,
            onUnbanChatUser = onUnbanUser,
            onSuspendAccount = onSuspendUser,
            onUnsuspendAccount = onUnsuspendUser,
            onBanAccount = onBanAccountUser,
            onUnbanAccount = onUnbanAccountUser,
            onDismiss = { activeModerationTarget = null }
        )
    }

    if (showBroadcastDialog) {
        AdminBroadcastDialog(
            isBusy = isActionBusy,
            onSendBroadcast = { category, title, message ->
                onSendAdminAnnouncement(category, title, message) {
                    showBroadcastDialog = false
                }
            },
            onDismiss = { if (!isActionBusy) showBroadcastDialog = false }
        )
    }

    activeWarningTarget?.let { target ->
        SendUserWarningDialog(
            target = target,
            isBusy = isActionBusy,
            onSendWarning = { severity, title, message ->
                onSendUserWarning(target.userId, target.displayName, severity, title, message) {
                    activeWarningTarget = null
                }
            },
            onDismiss = { if (!isActionBusy) activeWarningTarget = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserDirectorySection(
    staffTier: EffectiveStaffTier,
    canManageTeam: Boolean,
    currentUserId: String,
    allUsersState: UiState<List<UserProfile>>,
    moderationsByUser: Map<String, ChatModerationStatus>,
    adminAnnouncements: List<AdminAnnouncement>,
    isActionBusy: Boolean,
    onOpenComposeBroadcast: () -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onAssignUserRole: (UserProfile, String) -> Unit,
    onOpenModerationDialog: (StaffModerationTargetInfo) -> Unit,
    onOpenWarnDialog: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf(UserRoleFilter.ALL) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search users by name or email...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_user_search")
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(UserRoleFilter.entries) { filter ->
                    FilterChip(
                        selected = roleFilter == filter,
                        onClick = { roleFilter = filter },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("admin_role_filter_${filter.name.lowercase(Locale.ROOT)}")
                    )
                }
            }
        }

        when (allUsersState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = allUsersState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UiState.Success -> {
                val query = searchQuery.trim().lowercase(Locale.ROOT)
                val filteredUsers = remember(allUsersState.data, query, roleFilter) {
                    allUsersState.data.filter { user ->
                        val isOwner = user.email.equals(StaffRole.OWNER_EMAIL, ignoreCase = true)
                        val matchesRole = when (roleFilter) {
                            UserRoleFilter.ALL -> true
                            UserRoleFilter.USER -> !isOwner && user.role == UserProfile.ROLE_USER
                            UserRoleFilter.SUPPORT -> !isOwner && user.role == UserProfile.ROLE_SUPPORT
                            UserRoleFilter.ADMIN -> isOwner || user.role == UserProfile.ROLE_ADMIN
                        }
                        val matchesQuery = query.isEmpty() ||
                            user.displayName.lowercase(Locale.ROOT).contains(query) ||
                            user.email.orEmpty().lowercase(Locale.ROOT).contains(query) ||
                            user.userId.lowercase(Locale.ROOT).contains(query)
                        matchesRole && matchesQuery
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (canManageTeam) {
                        item(key = "admin_broadcasts_card") {
                            AdminBroadcastsSectionCard(
                                announcements = adminAnnouncements,
                                onOpenComposeDialog = onOpenComposeBroadcast,
                                onDeleteAnnouncement = onDeleteAnnouncement
                            )
                        }
                    }

                    item(key = "user_count") {
                        Text(
                            text = "Registered Users (${filteredUsers.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(filteredUsers, key = { it.userId }) { user ->
                        val isOwnerAccount = user.email.equals(StaffRole.OWNER_EMAIL, ignoreCase = true)
                        val isSelf = user.userId == currentUserId
                        val mod = moderationsByUser[user.userId]
                        val isMuted = mod?.isCurrentlyMuted() == true
                        val isChatBanned = mod?.banned == true
                        val isSuspended = mod?.isCurrentlySuspended() == true
                        val isAccountBanned = mod?.accountBanned == true
                        val canModerateThisUser = !isOwnerAccount && !isSelf && (
                            staffTier == EffectiveStaffTier.OWNER ||
                                staffTier == EffectiveStaffTier.ADMIN ||
                                (staffTier == EffectiveStaffTier.SUPPORT && user.role == UserProfile.ROLE_USER)
                            )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_user_card_${user.userId}")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val formattedUserName = formatDisplayNameWithRole(
                                                user.displayName.ifBlank { "Creator" },
                                                user.role,
                                                user.email
                                            )
                                            val userRoleFg = roleBadgeForegroundColor(user.role, user.email)
                                            val userRoleBg = roleBadgeContainerColor(user.role, user.email)
                                            val userRoleOnBg = roleBadgeOnContainerColor(user.role, user.email)
                                            val normalizedRole = normalizeStaffRole(user.role, user.email)

                                            Text(
                                                text = formattedUserName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = userRoleFg ?: MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (normalizedRole != null) FontWeight.Bold else FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Surface(
                                                color = userRoleBg ?: MaterialTheme.colorScheme.surfaceVariant,
                                                border = userRoleFg?.let { BorderStroke(1.dp, it.copy(alpha = 0.65f)) },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = when {
                                                        isOwnerAccount -> "ADMIN • OWNER"
                                                        normalizedRole == UserProfile.ROLE_ADMIN -> "ADMIN"
                                                        normalizedRole == UserProfile.ROLE_SUPPORT -> "SUPPORT"
                                                        else -> user.role.uppercase(Locale.ROOT)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = userRoleOnBg ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = user.email?.takeIf { it.isNotBlank() } ?: "UID: ${user.userId.take(12)}...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val detailsParts = buildList {
                                            user.age?.let { add("Age $it") }
                                            UserProfile.sexDisplayLabel(user.sex).takeIf { it.isNotEmpty() }?.let { add(it) }
                                            add("${user.voteBalance} tokens")
                                            if (user.role == UserProfile.ROLE_SUPPORT) {
                                                val days = user.supportTenureDays()
                                                add("Support ${days}/${UserProfile.SUPPORT_PAY_WAIT_DAYS}d")
                                            }
                                        }
                                        Text(
                                            text = detailsParts.joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isAccountBanned || isSuspended || isChatBanned || isMuted) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isAccountBanned) {
                                            StatusPillBadge("ACCOUNT BANNED", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
                                        }
                                        if (isSuspended) {
                                            StatusPillBadge("SUSPENDED", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
                                        }
                                        if (isChatBanned) {
                                            StatusPillBadge("CHAT BANNED", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.error)
                                        }
                                        if (isMuted) {
                                            StatusPillBadge("MUTED", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                }

                                if (isOwnerAccount) {
                                    Text(
                                        text = "Permanent App Owner • Cannot be demoted or moderated",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    if (canManageTeam) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            FilterChip(
                                                selected = user.role == UserProfile.ROLE_USER,
                                                enabled = !isActionBusy && !isSelf,
                                                onClick = {
                                                    if (user.role != UserProfile.ROLE_USER) {
                                                        onAssignUserRole(user, UserProfile.ROLE_USER)
                                                    }
                                                },
                                                label = { Text("User") },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("assign_user_${user.userId}")
                                            )
                                            FilterChip(
                                                selected = user.role == UserProfile.ROLE_SUPPORT,
                                                enabled = !isActionBusy && !isSelf,
                                                onClick = {
                                                    if (user.role != UserProfile.ROLE_SUPPORT) {
                                                        onAssignUserRole(user, UserProfile.ROLE_SUPPORT)
                                                    }
                                                },
                                                label = { Text("Support") },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.SupportAgent, contentDescription = null, modifier = Modifier.size(15.dp))
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("assign_support_${user.userId}")
                                            )
                                            FilterChip(
                                                selected = user.role == UserProfile.ROLE_ADMIN,
                                                enabled = !isActionBusy && !isSelf,
                                                onClick = {
                                                    if (user.role != UserProfile.ROLE_ADMIN) {
                                                        onAssignUserRole(user, UserProfile.ROLE_ADMIN)
                                                    }
                                                },
                                                label = { Text("Admin") },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(15.dp))
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("assign_admin_${user.userId}")
                                            )
                                        }
                                    }

                                    if (canModerateThisUser) {
                                        OutlinedButton(
                                            onClick = {
                                                onOpenWarnDialog(user.userId, user.displayName.ifBlank { "User" })
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("warn_user_button_${user.userId}")
                                        ) {
                                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Send Warning Popup")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onOpenModerationDialog(
                                                    StaffModerationTargetInfo(
                                                        userId = user.userId,
                                                        displayName = user.displayName,
                                                        role = user.role,
                                                        email = user.email
                                                    )
                                                )
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("moderate_user_button_${user.userId}")
                                        ) {
                                            Icon(Icons.Rounded.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Moderate: Bounce / Mute / Suspend / Ban")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TicketsAndReportsSection(
    canManageTeam: Boolean,
    banRequestsState: UiState<List<BanRequest>>,
    userReportsState: UiState<List<UserReport>>,
    supportTicketsState: UiState<List<SupportTicket>>,
    allUsersState: UiState<List<UserProfile>>,
    isActionBusy: Boolean,
    onReviewBanRequest: (BanRequest, Boolean) -> Unit,
    onModerateReportedUser: (String, String, String, String?) -> Unit,
    onResolveUserReport: (String, String, String) -> Unit,
    onReplySupportTicket: (String, String, String, () -> Unit) -> Unit
) {
    var activeReplyTicket by remember { mutableStateOf<SupportTicket?>(null) }
    val usersById = remember(allUsersState) {
        (allUsersState as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "ban_requests_header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Gavel, contentDescription = null, tint = AdminRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (canManageTeam) "Support Ban Requests (Admin Approval)" else "Submitted Ban Requests",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        when (banRequestsState) {
            is UiState.Loading -> item { CircularProgressIndicator() }
            is UiState.Error -> item {
                Text(banRequestsState.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> {
                val requests = banRequestsState.data
                if (requests.isEmpty()) {
                    item(key = "no_ban_requests") {
                        EmptyClearBanner("No escalated ban requests right now.")
                    }
                } else {
                    items(requests, key = { "breq_${it.requestId}" }) { req ->
                        val scopeLabel = if (req.banScope == BanRequest.SCOPE_ACCOUNT) {
                            "Permanent Account Ban"
                        } else {
                            "Chat Rooms Ban"
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                1.dp,
                                if (req.status == BanRequest.STATUS_PENDING) AdminRed.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ban_request_card_${req.requestId}")
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
                                    Text(
                                        text = "${req.targetUserName} • $scopeLabel",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = AdminRed,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusPillBadge(
                                        text = req.status.uppercase(Locale.ROOT),
                                        bgColor = when (req.status) {
                                            BanRequest.STATUS_PENDING -> AdminRedContainer
                                            BanRequest.STATUS_APPROVED -> MaterialTheme.colorScheme.errorContainer
                                            else -> MaterialTheme.colorScheme.tertiaryContainer
                                        },
                                        fgColor = when (req.status) {
                                            BanRequest.STATUS_PENDING -> AdminRedOnContainer
                                            BanRequest.STATUS_APPROVED -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                                        }
                                    )
                                }
                                Text(
                                    text = "Requested by ${formatDisplayNameWithRole(req.requestedByName, UserProfile.ROLE_SUPPORT)} (72h suspension applied)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SupportBlue,
                                    fontWeight = FontWeight.Medium
                                )
                                if (req.reason.isNotBlank()) {
                                    Text(
                                        text = "Reason: ${req.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (req.status == BanRequest.STATUS_PENDING) {
                                    if (canManageTeam) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = { onReviewBanRequest(req, true) },
                                                enabled = !isActionBusy,
                                                colors = ButtonDefaults.buttonColors(containerColor = AdminRed),
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .testTag("approve_ban_request_${req.requestId}")
                                            ) {
                                                Text("Approve Ban")
                                            }
                                            OutlinedButton(
                                                onClick = { onReviewBanRequest(req, false) },
                                                enabled = !isActionBusy,
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .testTag("reject_ban_request_${req.requestId}")
                                            ) {
                                                Text("Reject & Unsuspend")
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Pending Admin review.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else if (!req.reviewedByName.isNullOrBlank()) {
                                    Text(
                                        text = "Reviewed by ${formatDisplayNameWithRole(req.reviewedByName, UserProfile.ROLE_ADMIN)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AdminRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "user_reports_header") {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "User Reports",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        when (userReportsState) {
            is UiState.Loading -> item { CircularProgressIndicator() }
            is UiState.Error -> item {
                Text(userReportsState.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> {
                val reports = userReportsState.data
                if (reports.isEmpty()) {
                    item(key = "no_user_reports") {
                        EmptyClearBanner("No user reports filed right now.")
                    }
                } else {
                    items(reports, key = { "urep_${it.reportId}" }) { report ->
                        val targetProfile = usersById[report.reportedUserId]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                    Text(
                                        text = "${report.reportedUserName} • ${report.reason}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusPillBadge(
                                        text = report.status.replace("_", " ").uppercase(Locale.ROOT),
                                        bgColor = if (report.status == UserReport.STATUS_OPEN) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        },
                                        fgColor = if (report.status == UserReport.STATUS_OPEN) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        }
                                    )
                                }
                                Text(
                                    text = "Reported by ${report.reporterName} (via ${report.sourceContext.replace("_", " ")})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (report.details.isNotBlank()) {
                                    Text(
                                        text = report.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (report.status == UserReport.STATUS_OPEN) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onModerateReportedUser(
                                                    report.reportedUserId,
                                                    report.reportedUserName,
                                                    targetProfile?.role ?: UserProfile.ROLE_USER,
                                                    targetProfile?.email
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Moderate User")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onResolveUserReport(report.reportId, UserReport.STATUS_ACTION_TAKEN, "Reviewed by staff")
                                            },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("Action Taken")
                                        }
                                        TextButton(
                                            onClick = {
                                                onResolveUserReport(report.reportId, UserReport.STATUS_DISMISSED, "No violation")
                                            },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "support_tickets_header") {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Support Tickets Inbox",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        when (supportTicketsState) {
            is UiState.Loading -> item { CircularProgressIndicator() }
            is UiState.Error -> item {
                Text(supportTicketsState.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> {
                val tickets = supportTicketsState.data
                if (tickets.isEmpty()) {
                    item(key = "no_support_tickets") {
                        EmptyClearBanner("No support tickets in the inbox yet.")
                    }
                } else {
                    items(tickets, key = { "st_${it.ticketId}" }) { ticket ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                    Text(
                                        text = ticket.subject,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusPillBadge(
                                        text = ticket.status.replace("_", " ").uppercase(Locale.ROOT),
                                        bgColor = when (ticket.status) {
                                            SupportTicket.STATUS_RESOLVED, SupportTicket.STATUS_CLOSED ->
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            SupportTicket.STATUS_IN_PROGRESS ->
                                                MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        fgColor = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "${ticket.userName} (${ticket.userEmail ?: ticket.userId.take(10)}) • ${SupportTicket.categoryLabel(ticket.category)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = ticket.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!ticket.staffReply.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Reply (${ticket.respondedByName ?: "Staff"}): ${ticket.staffReply}",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { activeReplyTicket = ticket },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("reply_ticket_button_${ticket.ticketId}")
                                ) {
                                    Icon(Icons.Rounded.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (ticket.staffReply.isNullOrBlank()) "Reply / Resolve Ticket" else "Update Reply / Status")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    activeReplyTicket?.let { ticket ->
        var replyDraft by remember(ticket.ticketId) { mutableStateOf(ticket.staffReply.orEmpty()) }
        var nextStatus by remember(ticket.ticketId) {
            mutableStateOf(
                if (ticket.status == SupportTicket.STATUS_OPEN) SupportTicket.STATUS_RESOLVED else ticket.status
            )
        }

        AlertDialog(
            onDismissRequest = { activeReplyTicket = null },
            title = { Text("Respond to Ticket") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${ticket.userName}: ${ticket.subject}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            SupportTicket.STATUS_IN_PROGRESS to "In Progress",
                            SupportTicket.STATUS_RESOLVED to "Resolved",
                            SupportTicket.STATUS_CLOSED to "Closed"
                        ).forEach { (statusKey, label) ->
                            FilterChip(
                                selected = nextStatus == statusKey,
                                onClick = { nextStatus = statusKey },
                                label = { Text(label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = replyDraft,
                        onValueChange = { if (it.length <= 1200) replyDraft = it },
                        label = { Text("Staff Reply") },
                        placeholder = { Text("Write a clear response...") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("staff_ticket_reply_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReplySupportTicket(ticket.ticketId, nextStatus, replyDraft) {
                            activeReplyTicket = null
                        }
                    },
                    enabled = !isActionBusy,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("save_ticket_reply_button")
                ) {
                    Text("Save Reply")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeReplyTicket = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModerationQueueSection(
    reportsState: UiState<List<ContentReport>>,
    chatModerationsState: UiState<List<ChatModerationStatus>>,
    allPublicPosts: List<MediaPost>,
    onResolveReport: (ContentReport, String, Boolean) -> Unit,
    onHideOrDeletePost: (MediaPost, Boolean) -> Unit,
    onUnmuteUser: (String) -> Unit,
    onUnbanUser: (String) -> Unit,
    onUnsuspendUser: (String) -> Unit,
    onUnbanAccountUser: (String) -> Unit
) {
    val activeModerations = remember(chatModerationsState) {
        (chatModerationsState as? UiState.Success)?.data
            ?.filter {
                it.banned || it.isCurrentlyMuted() || it.isCurrentlySuspended() || it.accountBanned
            }
            .orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activeModerations.isNotEmpty()) {
            item(key = "chat_mods_header") {
                Text(
                    text = "Active Mutes, Suspensions & Bans (${activeModerations.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(activeModerations, key = { "cmod_${it.userId}" }) { mod ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = mod.displayName.ifBlank { mod.userId },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = buildList {
                                if (mod.accountBanned) add("Account Banned")
                                if (mod.isCurrentlySuspended()) add("Suspended")
                                if (mod.banned) add("Chat Banned")
                                if (mod.isCurrentlyMuted()) add("Muted")
                            }.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (mod.isCurrentlyMuted()) {
                                OutlinedButton(onClick = { onUnmuteUser(mod.userId) }) {
                                    Text("Unmute")
                                }
                            }
                            if (mod.isCurrentlySuspended()) {
                                OutlinedButton(onClick = { onUnsuspendUser(mod.userId) }) {
                                    Text("Unsuspend")
                                }
                            }
                            if (mod.banned && !mod.accountBanned) {
                                OutlinedButton(onClick = { onUnbanUser(mod.userId) }) {
                                    Text("Unban Chat")
                                }
                            }
                            if (mod.accountBanned) {
                                Button(onClick = { onUnbanAccountUser(mod.userId) }) {
                                    Text("Unban Account")
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "reports_header") {
            Text(
                text = "Reported Content Queue",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        when (reportsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = reportsState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UiState.Success -> {
                val reports = reportsState.data
                if (reports.isEmpty()) {
                    item(key = "no_reports") {
                        EmptyClearBanner("All clear! No post reports in the queue right now.")
                    }
                } else {
                    items(reports, key = { it.id }) { report ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                    Text(
                                        text = report.reason,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    StatusPillBadge(
                                        text = report.status.uppercase(),
                                        bgColor = if (report.status == ContentReport.STATUS_OPEN) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        },
                                        fgColor = if (report.status == ContentReport.STATUS_OPEN) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        }
                                    )
                                }

                                Text(
                                    text = "Caption: ${report.postCaption.ifBlank { "(no caption)" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (report.status == ContentReport.STATUS_OPEN) {
                                    val matchedPost = allPublicPosts.firstOrNull { it.id == report.postId }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onResolveReport(report, ContentReport.STATUS_RESOLVED, true)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                            ),
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Hide & Resolve")
                                        }
                                        Button(
                                            onClick = {
                                                val postToDelete = matchedPost ?: MediaPost(
                                                    id = report.postId,
                                                    creatorId = report.postCreatorId
                                                )
                                                onHideOrDeletePost(postToDelete, true)
                                                onResolveReport(report, ContentReport.STATUS_RESOLVED, false)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("delete_reported_post_${report.id}")
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete Media")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onResolveReport(report, ContentReport.STATUS_DISMISSED, false)
                                            },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "live_posts_monitor_header") {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Live Posts Monitor (${allPublicPosts.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(allPublicPosts, key = { "mon_${it.id}" }) { post ->
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
                            text = "${post.creatorName} • ${post.mediaType.uppercase()} (${post.voteCount} votes)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = post.caption.ifBlank { "(no caption)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { onHideOrDeletePost(post, false) }) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "Hide post",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = { onHideOrDeletePost(post, true) }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete post",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamManagementSection(
    staffRolesState: UiState<List<StaffRole>>,
    allSupportPayroll: List<SupportPayrollPayout>,
    isActionBusy: Boolean,
    onSaveStaffRole: (String, String, String, Boolean, () -> Unit) -> Unit,
    onRemoveStaffRole: (String) -> Unit
) {
    var emailInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(StaffRole.ROLE_SUPPORT) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "add_staff_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pre-Appoint Staff by Email",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Invite someone before they first sign in, or manage existing users directly from the Users tab. Support users earn 100 tokens/week after 6 months.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Google Account Email") },
                        placeholder = { Text("moderator@gmail.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("staff_email_input")
                    )

                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { if (it.length <= 60) labelInput = it },
                        label = { Text("Name / Note (optional)") },
                        placeholder = { Text("Night Shift Support") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("staff_label_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = selectedRole == StaffRole.ROLE_SUPPORT,
                            onClick = { selectedRole = StaffRole.ROLE_SUPPORT },
                            label = { Text("Support Monitor") },
                            leadingIcon = {
                                Icon(Icons.Rounded.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_chip_support")
                        )
                        FilterChip(
                            selected = selectedRole == StaffRole.ROLE_ADMIN,
                            onClick = { selectedRole = StaffRole.ROLE_ADMIN },
                            label = { Text("Admin") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_chip_admin")
                        )
                    }

                    Button(
                        onClick = {
                            onSaveStaffRole(emailInput, labelInput, selectedRole, true) {
                                emailInput = ""
                                labelInput = ""
                            }
                        },
                        enabled = !isActionBusy && emailInput.contains("@"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("appoint_staff_button")
                    ) {
                        Text("Save Staff Role")
                    }
                }
            }
        }

        item(key = "root_owner_card") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, AdminRed.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = AdminRed)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatDisplayNameWithRole("Root Owner", UserProfile.ROLE_ADMIN, StaffRole.OWNER_EMAIL),
                            style = MaterialTheme.typography.titleSmall,
                            color = AdminRed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = StaffRole.OWNER_EMAIL,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "team_list_header") {
            Text(
                text = "Email-Based Staff Team",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        when (staffRolesState) {
            is UiState.Loading -> item { CircularProgressIndicator() }
            is UiState.Error -> item {
                Text(text = staffRolesState.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> {
                val staffList = staffRolesState.data
                if (staffList.isEmpty()) {
                    item(key = "no_staff") {
                        Text(
                            text = "No email-based admins or support monitors added yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(staffList, key = { it.email }) { member ->
                        val memberRoleFg = roleBadgeForegroundColor(member.role) ?: MaterialTheme.colorScheme.onSurface
                        val memberRoleBg = roleBadgeContainerColor(member.role) ?: MaterialTheme.colorScheme.surfaceVariant
                        val memberRoleOnBg = roleBadgeOnContainerColor(member.role) ?: MaterialTheme.colorScheme.onSurface
                        val baseLabel = member.displayLabel?.takeIf { it.isNotBlank() } ?: member.email
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, memberRoleFg.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = formatDisplayNameWithRole(baseLabel, member.role),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = memberRoleFg,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = member.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = member.active,
                                        onCheckedChange = { newActive ->
                                            onSaveStaffRole(
                                                member.email,
                                                member.displayLabel.orEmpty(),
                                                member.role,
                                                newActive
                                            ) {}
                                        }
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val toggledRole = if (member.role == StaffRole.ROLE_ADMIN) {
                                        StaffRole.ROLE_SUPPORT
                                    } else {
                                        StaffRole.ROLE_ADMIN
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onSaveStaffRole(
                                                member.email,
                                                member.displayLabel.orEmpty(),
                                                toggledRole,
                                                member.active
                                            ) {}
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = memberRoleBg.copy(alpha = 0.55f),
                                            contentColor = memberRoleOnBg
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (member.role == StaffRole.ROLE_ADMIN) {
                                                "Role: ADMIN (Make Support)"
                                            } else {
                                                "Role: SUPPORT (Make Admin)"
                                            },
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    IconButton(onClick = { onRemoveStaffRole(member.email) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Remove staff member",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "support_payroll_audit_header") {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Support 6-Month Weekly Payroll (${allSupportPayroll.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (allSupportPayroll.isEmpty()) {
            item(key = "no_support_payroll") {
                EmptyClearBanner("No 100-token Support weekly stipends claimed yet (unlocks at 180 days tenure).")
            }
        } else {
            items(allSupportPayroll, key = { "spay_${it.payoutId}" }) { payout ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatDisplayNameWithRole(payout.staffName, UserProfile.ROLE_SUPPORT),
                                style = MaterialTheme.typography.titleSmall,
                                color = SupportBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${payout.staffEmail ?: payout.staffUserId} • ${payout.weekLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "+${payout.tokensAwarded} tokens (${payout.usdValueLabel()})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPillBadge(
    text: String,
    bgColor: androidx.compose.ui.graphics.Color,
    fgColor: androidx.compose.ui.graphics.Color
) {
    Surface(color = bgColor, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fgColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyClearBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
