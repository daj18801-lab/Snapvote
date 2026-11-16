package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.UserCheckIn
import com.example.data.model.UserStory
import com.example.media.MediaProcessor
import com.example.media.ProcessedMedia
import kotlinx.coroutines.launch

@Composable
fun storyStyleBrush(style: String): Brush = when (style) {
    UserStory.STYLE_OCEAN -> Brush.linearGradient(listOf(Color(0xFF0077B6), Color(0xFF00B4D8)))
    UserStory.STYLE_MIDNIGHT -> Brush.linearGradient(listOf(Color(0xFF1A1B2E), Color(0xFF4A1942)))
    UserStory.STYLE_NEON -> Brush.linearGradient(listOf(Color(0xFF7B2CBF), Color(0xFFFF006E)))
    else -> Brush.linearGradient(listOf(Color(0xFFE63946), Color(0xFFF77F00)))
}

@Composable
fun StoriesStripSection(
    stories: List<UserStory>,
    currentUserId: String,
    onAddStoryClick: () -> Unit,
    onOpenStoryClick: (UserStory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "24h Stories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${stories.size} active",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item(key = "add_story_tile") {
                Surface(
                    onClick = onAddStoryClick,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .width(96.dp)
                        .height(126.dp)
                        .testTag("add_story_button")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add story",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add Story",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Snap or Note",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            maxLines = 1
                        )
                    }
                }
            }

            items(stories, key = { it.storyId.ifBlank { it.id } }) { story ->
                val storyBitmap = remember(story.id, story.mediaBase64) {
                    story.mediaBase64?.let { MediaProcessor.decodeBase64ToBitmap(it) }
                }
                val isMine = story.userId == currentUserId
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(126.dp)
                        .clip(MaterialTheme.shapes.large)
                        .border(
                            width = 2.dp,
                            color = if (isMine) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                            shape = MaterialTheme.shapes.large
                        )
                        .clickable { onOpenStoryClick(story) }
                        .testTag("story_card_${story.storyId.ifBlank { story.id }}")
                ) {
                    if (story.isPhoto && !story.mediaDownloadUrl.isNullOrBlank()) {
                        if (storyBitmap != null) {
                            Image(
                                bitmap = storyBitmap.asImageBitmap(),
                                contentDescription = "${story.userName}'s story",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        AsyncImage(
                            model = story.mediaDownloadUrl,
                            contentDescription = "${story.userName}'s story",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (story.isPhoto && storyBitmap != null) {
                        Image(
                            bitmap = storyBitmap.asImageBitmap(),
                            contentDescription = "${story.userName}'s story",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(storyStyleBrush(story.backgroundStyle))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = story.text,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isMine) "You" else story.userName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionalCheckInSection(
    myCheckIn: UserCheckIn?,
    activeCheckIns: List<UserCheckIn>,
    onOpenCheckInDialog: () -> Unit,
    onClearCheckIn: () -> Unit,
    onCreatorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Optional Check-In",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (myCheckIn != null) {
                                "${myCheckIn.vibeTag} • ${myCheckIn.placeLabel}"
                            } else {
                                "Share your hangout spot or studio vibe (optional)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (myCheckIn != null) {
                        TextButton(
                            onClick = onClearCheckIn,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_checkin_button")
                        ) {
                            Text("Clear")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenCheckInDialog,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_checkin_button")
                    ) {
                        Text(if (myCheckIn != null) "Update" else "Check In")
                    }
                }
            }

            if (activeCheckIns.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activeCheckIns, key = { it.userId }) { checkIn ->
                        Surface(
                            onClick = { onCreatorClick(checkIn.userId) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = "${checkIn.userName} • ${checkIn.vibeTag}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    val detail = listOf(checkIn.placeLabel, checkIn.statusNote)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" — ")
                                    if (detail.isNotBlank()) {
                                        Text(
                                            text = detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
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

@Composable
fun CreateStoryDialog(
    isBusy: Boolean,
    onPostStory: (storyType: String, text: String, style: String, media: ProcessedMedia?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var storyType by remember { mutableStateOf(UserStory.TYPE_PHOTO) }
    var useCameraX by remember { mutableStateOf(true) }
    var capturedMedia by remember { mutableStateOf<ProcessedMedia?>(null) }
    var captionOrMessage by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(UserStory.STYLE_SUNSET) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MediaProcessor.processPickedUri(context, uri)
                    .onSuccess { processed ->
                        capturedMedia = processed
                        errorMsg = null
                    }
                    .onFailure { err ->
                        errorMsg = err.localizedMessage ?: "Could not load photo"
                    }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Update Your 24h Story") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = storyType == UserStory.TYPE_PHOTO,
                        onClick = { storyType = UserStory.TYPE_PHOTO },
                        label = { Text("Snap Photo") },
                        leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("story_type_photo")
                    )
                    FilterChip(
                        selected = storyType == UserStory.TYPE_MESSAGE,
                        onClick = { storyType = UserStory.TYPE_MESSAGE },
                        label = { Text("Message Story") },
                        leadingIcon = { Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("story_type_message")
                    )
                }

                if (storyType == UserStory.TYPE_PHOTO) {
                    if (capturedMedia != null) {
                        Image(
                            bitmap = capturedMedia!!.previewBitmap.asImageBitmap(),
                            contentDescription = "Story photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                        OutlinedButton(
                            onClick = { capturedMedia = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Retake / Choose Another Photo")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = useCameraX,
                                onClick = { useCameraX = true },
                                label = { Text("CameraX") }
                            )
                            FilterChip(
                                selected = !useCameraX,
                                onClick = { useCameraX = false },
                                label = { Text("Photo Picker") }
                            )
                        }
                        if (useCameraX) {
                            CameraXCaptureView(
                                allowVideo = false,
                                onMediaCaptured = {
                                    capturedMedia = it
                                    errorMsg = null
                                },
                                onCaptureError = { errorMsg = it }
                            )
                        } else {
                            OutlinedButton(
                                onClick = {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("story_pick_photo_button")
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Photo for Story")
                            }
                        }
                    }
                    OutlinedTextField(
                        value = captionOrMessage,
                        onValueChange = { if (it.length <= 280) captionOrMessage = it },
                        label = { Text("Optional caption") },
                        singleLine = false,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("story_text_input")
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(storyStyleBrush(selectedStyle))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = captionOrMessage.ifBlank { "Type your 24h status message below..." },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UserStory.styles.forEach { style ->
                            FilterChip(
                                selected = selectedStyle == style,
                                onClick = { selectedStyle = style },
                                label = { Text(style.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = captionOrMessage,
                        onValueChange = { if (it.length <= 280) captionOrMessage = it },
                        label = { Text("Story message") },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("story_text_input")
                    )
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val media = capturedMedia
                    if (storyType == UserStory.TYPE_PHOTO && media == null) {
                        errorMsg = "Capture or choose a photo first."
                    } else if (storyType == UserStory.TYPE_MESSAGE && captionOrMessage.isBlank()) {
                        errorMsg = "Write a story message first."
                    } else {
                        onPostStory(storyType, captionOrMessage, selectedStyle, media)
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("publish_story_button")
            ) {
                Text(if (isBusy) "Posting..." else "Share Story")
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
fun StoryViewerDialog(
    story: UserStory,
    currentUserId: String,
    isStaff: Boolean,
    onViewed: (UserStory) -> Unit,
    onDelete: (UserStory) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(story.storyId) {
        onViewed(story)
    }
    val bitmap = remember(story.id, story.mediaBase64) {
        story.mediaBase64?.let { MediaProcessor.decodeBase64ToBitmap(it) }
    }
    val canDelete = story.userId == currentUserId || isStaff

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (story.isPhoto) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "${story.userName}'s story",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (!story.mediaDownloadUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = story.mediaDownloadUrl,
                            contentDescription = "${story.userName}'s story",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(storyStyleBrush(story.backgroundStyle))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = story.text,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = story.userName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${story.viewCount} views • 24h story",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Row {
                        if (canDelete) {
                            IconButton(onClick = {
                                onDelete(story)
                                onDismiss()
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete story",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_story_viewer")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close story",
                                tint = Color.White
                            )
                        }
                    }
                }

                if (story.isPhoto && story.text.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = story.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInDialog(
    existingCheckIn: UserCheckIn?,
    isBusy: Boolean,
    onSaveCheckIn: (placeLabel: String, statusNote: String, vibeTag: String) -> Unit,
    onDismiss: () -> Unit
) {
    var placeLabel by remember { mutableStateOf(existingCheckIn?.placeLabel ?: "") }
    var statusNote by remember { mutableStateOf(existingCheckIn?.statusNote ?: "") }
    var vibeTag by remember { mutableStateOf(existingCheckIn?.vibeTag ?: UserCheckIn.VIBE_CREATING) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Optional Check-In (12h)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Check in whenever you want to let friends know what you're up to. Completely optional — no device GPS is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Pick your vibe",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UserCheckIn.presetVibes.forEach { vibe ->
                        FilterChip(
                            selected = vibeTag == vibe,
                            onClick = { vibeTag = vibe },
                            label = { Text(vibe) }
                        )
                    }
                }
                Text(
                    text = "Quick place presets",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UserCheckIn.presetPlaces.forEach { preset ->
                        FilterChip(
                            selected = placeLabel == preset,
                            onClick = { placeLabel = preset },
                            label = { Text(preset) }
                        )
                    }
                }
                OutlinedTextField(
                    value = placeLabel,
                    onValueChange = { if (it.length <= 80) placeLabel = it },
                    label = { Text("Spot / Place label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkin_place_input")
                )
                OutlinedTextField(
                    value = statusNote,
                    onValueChange = { if (it.length <= 160) statusNote = it },
                    label = { Text("Optional note") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkin_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveCheckIn(
                        placeLabel.ifBlank { vibeTag },
                        statusNote,
                        vibeTag
                    )
                },
                enabled = !isBusy && (placeLabel.isNotBlank() || statusNote.isNotBlank()),
                modifier = Modifier.testTag("confirm_checkin_button")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isBusy) "Saving..." else "Check In")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel")
            }
        }
    )
}
