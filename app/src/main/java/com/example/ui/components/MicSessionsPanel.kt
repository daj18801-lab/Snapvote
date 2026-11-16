package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CreatorSummary
import com.example.data.model.MicSession
import com.example.data.model.UserProfile
import java.text.DateFormat
import java.util.Date

@Composable
fun MicSessionsPanel(
    currentUserId: String,
    userProfile: UserProfile?,
    creators: List<CreatorSummary>,
    micSessions: List<MicSession>,
    isActionBusy: Boolean,
    onBuyPhoneTokensClick: () -> Unit,
    onOpenScheduleMicDialog: (CreatorSummary) -> Unit,
    onRespondMicSession: (MicSession, Boolean) -> Unit,
    onCancelMicSession: (String) -> Unit,
    onJoinMicCall: (MicSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val phoneBalance = userProfile?.phoneTokenBalance ?: 0L
    val incomingRequests = remember(micSessions, currentUserId) {
        micSessions.filter { it.creatorId == currentUserId && it.status == MicSession.STATUS_PENDING }
    }
    val activeOrScheduled = remember(micSessions) {
        micSessions.filter {
            it.status in listOf(MicSession.STATUS_ACCEPTED, MicSession.STATUS_ACTIVE)
        }
    }
    val otherSessions = remember(micSessions, currentUserId) {
        micSessions.filter { session ->
            !(session.creatorId == currentUserId && session.status == MicSession.STATUS_PENDING) &&
                session.status !in listOf(MicSession.STATUS_ACCEPTED, MicSession.STATUS_ACTIVE)
        }
    }
    val callableCreators = remember(creators, currentUserId) {
        creators.filter { it.creatorId != currentUserId }
            .sortedByDescending { it.isCurrentlyOnline() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "phone_wallet_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Free 1-on-1 Mic Calls",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "100% Free Audio",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.PhoneInTalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "Request or schedule live mic-to-mic voice calls with any creator completely free — no phone tokens needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (incomingRequests.isNotEmpty()) {
            item(key = "incoming_mic_header") {
                Text(
                    text = "Incoming Mic Requests (${incomingRequests.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(incomingRequests, key = { "in_mic_${it.sessionId}" }) { session ->
                MicSessionCard(
                    session = session,
                    currentUserId = currentUserId,
                    isActionBusy = isActionBusy,
                    onAccept = { onRespondMicSession(session, true) },
                    onDecline = { onRespondMicSession(session, false) },
                    onCancel = { onCancelMicSession(session.sessionId) },
                    onJoinCall = { onJoinMicCall(session) }
                )
            }
        }

        if (activeOrScheduled.isNotEmpty()) {
            item(key = "ready_mic_header") {
                Text(
                    text = "Ready & Live Mic Calls (${activeOrScheduled.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(activeOrScheduled, key = { "ready_mic_${it.sessionId}" }) { session ->
                MicSessionCard(
                    session = session,
                    currentUserId = currentUserId,
                    isActionBusy = isActionBusy,
                    onAccept = {},
                    onDecline = {},
                    onCancel = {},
                    onJoinCall = { onJoinMicCall(session) }
                )
            }
        }

        if (otherSessions.isNotEmpty()) {
            item(key = "other_mic_header") {
                Text(
                    text = "Sent & Past Mic Requests (${otherSessions.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(otherSessions, key = { "hist_mic_${it.sessionId}" }) { session ->
                MicSessionCard(
                    session = session,
                    currentUserId = currentUserId,
                    isActionBusy = isActionBusy,
                    onAccept = {},
                    onDecline = {},
                    onCancel = { onCancelMicSession(session.sessionId) },
                    onJoinCall = { onJoinMicCall(session) }
                )
            }
        }

        item(key = "book_creator_header") {
            Text(
                text = "Schedule a Mic-to-Mic Call (${callableCreators.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(callableCreators, key = { "call_creator_${it.creatorId}" }) { creator ->
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
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = creator.displayName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            OnlineStatusDot(isOnline = creator.isCurrentlyOnline(), showLabel = true)
                        }
                        Text(
                            text = "Free 1-on-1 mic-to-mic voice call",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { onOpenScheduleMicDialog(creator) },
                        enabled = !isActionBusy,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("schedule_mic_with_${creator.creatorId}")
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Schedule")
                    }
                }
            }
        }
    }
}

@Composable
private fun MicSessionCard(
    session: MicSession,
    currentUserId: String,
    isActionBusy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onJoinCall: () -> Unit
) {
    val isCreator = session.creatorId == currentUserId
    val peerName = if (isCreator) session.requesterName else session.creatorName
    val scheduledLabel = remember(session.scheduledForMillis) {
        if (session.scheduledForMillis <= 0L) "Now"
        else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(session.scheduledForMillis))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mic_session_card_${session.sessionId}")
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
                    text = if (isCreator) "Call from $peerName" else "Call with $peerName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = when (session.status) {
                        MicSession.STATUS_ACCEPTED, MicSession.STATUS_ACTIVE -> MaterialTheme.colorScheme.tertiaryContainer
                        MicSession.STATUS_DECLINED, MicSession.STATUS_CANCELLED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = session.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "${session.topic} • ${session.durationMinutes} min • $scheduledLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Free 1-on-1 Mic Call",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            when {
                isCreator && session.status == MicSession.STATUS_PENDING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onAccept,
                            enabled = !isActionBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("accept_mic_${session.sessionId}")
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Accept")
                        }
                        OutlinedButton(
                            onClick = onDecline,
                            enabled = !isActionBusy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("decline_mic_${session.sessionId}")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Decline")
                        }
                    }
                }
                session.status == MicSession.STATUS_ACCEPTED || session.status == MicSession.STATUS_ACTIVE -> {
                    Button(
                        onClick = onJoinCall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("join_mic_call_${session.sessionId}")
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Join Live Mic Call")
                    }
                }
                !isCreator && session.status == MicSession.STATUS_PENDING -> {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isActionBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cancel_mic_${session.sessionId}")
                    ) {
                        Text("Cancel Request")
                    }
                }
            }
        }
    }
}
