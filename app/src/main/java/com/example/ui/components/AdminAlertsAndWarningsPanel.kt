package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AdminAnnouncement
import com.example.data.model.UserWarning

data class WarningPopupTarget(
    val userId: String,
    val displayName: String
)

@Composable
fun AdminBroadcastsSectionCard(
    announcements: List<AdminAnnouncement>,
    onOpenComposeDialog: () -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Global Alerts & News",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Broadcast popup message boxes to every user in the app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = onOpenComposeDialog,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_admin_broadcast_button")
                ) {
                    Text("Send All")
                }
            }

            if (announcements.isEmpty()) {
                Text(
                    text = "No active broadcasts yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                announcements.take(5).forEach { ann ->
                    Surface(
                        color = if (ann.isAlert) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${if (ann.isAlert) "ALERT" else "NEWS"} • ${ann.title}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ann.isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = ann.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { onDeleteAnnouncement(ann.announcementId.ifBlank { ann.id }) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete broadcast",
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

@Composable
fun AdminBroadcastDialog(
    isBusy: Boolean,
    onSendBroadcast: (category: String, title: String, message: String) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf(AdminAnnouncement.CATEGORY_ALERT) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        icon = {
            Icon(
                imageVector = if (category == AdminAnnouncement.CATEGORY_ALERT) {
                    Icons.Rounded.NotificationsActive
                } else {
                    Icons.Rounded.Newspaper
                },
                contentDescription = null,
                tint = if (category == AdminAnnouncement.CATEGORY_ALERT) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        },
        title = { Text("Send Alert / News to Everyone") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Every signed-in user will see a popup message box automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = category == AdminAnnouncement.CATEGORY_ALERT,
                        onClick = { category = AdminAnnouncement.CATEGORY_ALERT },
                        label = { Text("Urgent Alert") },
                        modifier = Modifier.testTag("broadcast_category_alert")
                    )
                    FilterChip(
                        selected = category == AdminAnnouncement.CATEGORY_NEWS,
                        onClick = { category = AdminAnnouncement.CATEGORY_NEWS },
                        label = { Text("Platform News") },
                        modifier = Modifier.testTag("broadcast_category_news")
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 100) title = it },
                    label = { Text("Headline / Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("broadcast_title_input")
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 600) message = it },
                    label = { Text("Popup Message") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("broadcast_message_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendBroadcast(category, title, message) },
                enabled = !isBusy && title.isNotBlank() && message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (category == AdminAnnouncement.CATEGORY_ALERT) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ),
                modifier = Modifier.testTag("send_broadcast_confirm_button")
            ) {
                Text(if (isBusy) "Sending..." else "Broadcast Popup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SendUserWarningDialog(
    target: WarningPopupTarget,
    isBusy: Boolean,
    onSendWarning: (severity: String, title: String, message: String) -> Unit,
    onDismiss: () -> Unit
) {
    var severity by remember { mutableStateOf(UserWarning.SEVERITY_WARNING) }
    var title by remember { mutableStateOf("Community Guidelines Warning") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Warn ${target.displayName}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "This immediately pops up a warning dialog on ${target.displayName}'s screen that they must acknowledge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    UserWarning.severities.forEach { (key, label) ->
                        FilterChip(
                            selected = severity == key,
                            onClick = { severity = key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("warning_severity_$key")
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 100) title = it },
                    label = { Text("Warning Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warning_title_input")
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 600) message = it },
                    label = { Text("Warning Message") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warning_message_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendWarning(severity, title, message) },
                enabled = !isBusy && title.isNotBlank() && message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("send_warning_confirm_button")
            ) {
                Text(if (isBusy) "Sending..." else "Send Warning Popup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GlobalAnnouncementPopupDialog(
    announcement: AdminAnnouncement,
    onDismiss: () -> Unit
) {
    val accent = if (announcement.isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (announcement.isAlert) Icons.Rounded.NotificationsActive else Icons.Rounded.Campaign,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(34.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (announcement.isAlert) "ADMIN ALERT" else "SNAPVOTE NEWS",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = announcement.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sent by ${announcement.senderName} (${announcement.senderRole.uppercase()})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("dismiss_announcement_popup_button")
            ) {
                Text("Got It")
            }
        }
    )
}

@Composable
fun UserWarningPopupDialog(
    warning: UserWarning,
    onAcknowledge: () -> Unit
) {
    val severityLabel = when (warning.severity) {
        UserWarning.SEVERITY_FINAL_WARNING -> "FINAL WARNING"
        UserWarning.SEVERITY_REMINDER -> "STAFF REMINDER"
        else -> "OFFICIAL WARNING"
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = {
            Icon(
                imageVector = Icons.Rounded.ReportProblem,
                contentDescription = "Staff Warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(38.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = severityLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = warning.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = warning.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "From ${warning.senderName} (${warning.senderRole.uppercase()})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("acknowledge_warning_popup_button")
            ) {
                Text("I Understand / Acknowledge")
            }
        }
    )
}
