package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BlockedUser
import com.example.ui.viewmodel.UiState
import java.text.DateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    blockedUsersState: UiState<List<BlockedUser>>,
    isActionBusy: Boolean = false,
    onBack: () -> Unit,
    onUnblockUser: (BlockedUser) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var searchQuery by remember { mutableStateOf("") }
    var pendingUnblockUser by remember { mutableStateOf<BlockedUser?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.blocked_users_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("blocked_users_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to profile settings"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        when (blockedUsersState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("blocked_users_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = blockedUsersState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
            }
            is UiState.Success -> {
                val allBlocked = blockedUsersState.data
                val query = searchQuery.trim().lowercase(Locale.ROOT)
                val filtered = remember(allBlocked, query) {
                    if (query.isEmpty()) {
                        allBlocked
                    } else {
                        allBlocked.filter { entry ->
                            entry.blockedUserName.lowercase(Locale.ROOT).contains(query) ||
                                entry.reason.orEmpty().lowercase(Locale.ROOT).contains(query)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("blocked_users_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "privacy_summary_banner") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (allBlocked.isEmpty()) {
                                            "No Restricted Accounts"
                                        } else {
                                            "${allBlocked.size} Blocked Account${if (allBlocked.size == 1) "" else "s"}"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Blocked accounts are hidden from your Discover feed, direct message log, and public chat messages until you unblock them.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (allBlocked.isNotEmpty()) {
                        item(key = "blocked_search_bar") {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search blocked accounts...") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("blocked_users_search_input")
                            )
                        }
                    }

                    if (filtered.isEmpty()) {
                        item(key = "blocked_empty_state") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("blocked_users_empty_state")
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (allBlocked.isEmpty()) {
                                            Icons.Rounded.CheckCircleOutline
                                        } else {
                                            Icons.Rounded.Block
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = if (allBlocked.isEmpty()) {
                                            "You haven't blocked anyone"
                                        } else {
                                            "No blocked accounts match \"$searchQuery\""
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (allBlocked.isEmpty()) {
                                            "You can block any user from their profile, Discover card, or direct message header. Anyone you restrict will appear here."
                                        } else {
                                            "Try another name or clear your search filter."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = filtered,
                            key = { it.blockedUserId.ifBlank { it.id } }
                        ) { entry ->
                            val targetUid = entry.blockedUserId.ifBlank { entry.id }
                            val dateLabel = remember(entry.createdAt) {
                                entry.createdAt?.toDate()?.let { date ->
                                    "Blocked ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)}"
                                } ?: "Blocked recently"
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("blocked_user_card_$targetUid")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = entry.blockedUserName.take(1).uppercase(Locale.ROOT),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.blockedUserName.ifBlank { "User" },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = dateLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!entry.reason.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = entry.reason,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    OutlinedButton(
                                        onClick = { pendingUnblockUser = entry },
                                        enabled = !isActionBusy,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("unblock_user_button_$targetUid")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LockOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Unblock")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingUnblockUser?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isActionBusy) pendingUnblockUser = null },
            title = { Text("Unblock ${target.blockedUserName.ifBlank { "User" }}?") },
            text = {
                Text(
                    "Once unblocked, you will be able to see ${target.blockedUserName.ifBlank { "this user" }}'s posts, stories, and messages again."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUnblockUser(target)
                        pendingUnblockUser = null
                    },
                    enabled = !isActionBusy,
                    modifier = Modifier.testTag("confirm_unblock_button")
                ) {
                    Text("Unblock")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingUnblockUser = null },
                    enabled = !isActionBusy
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("confirm_unblock_dialog")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockUserConfirmDialog(
    targetUserName: String,
    isBusy: Boolean = false,
    onConfirmBlock: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val presetReasons = remember {
        listOf("Unwanted messages", "Spam", "Harassment", "Prefer not to see")
    }
    var selectedReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Block $targetUserName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "You won't see their posts, stories, check-ins, or direct messages. You can unblock them anytime in Profile Settings > Blocked Users.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Reason (optional)",
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetReasons.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = {
                                selectedReason = if (selectedReason == reason) "" else reason
                            },
                            label = { Text(reason) }
                        )
                    }
                }
                OutlinedTextField(
                    value = selectedReason,
                    onValueChange = { if (it.length <= 160) selectedReason = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("block_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmBlock(selectedReason) },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_block_user_button")
            ) {
                Text("Block User")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag("block_user_dialog")
    )
}
