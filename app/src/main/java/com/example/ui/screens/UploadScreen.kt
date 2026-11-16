package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CreatorPayout
import com.example.data.model.CreatorSummary
import com.example.data.model.MediaPost
import com.example.media.MediaProcessor
import com.example.media.ProcessedMedia
import com.example.ui.components.CameraXCaptureView
import com.example.ui.components.formatDuration
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun UploadScreen(
    myCurrentPostCount: Long,
    isActionBusy: Boolean,
    uploadProgress: Float? = null,
    onPublish: (ProcessedMedia, String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var useCameraXMode by remember { mutableStateOf(true) }
    var processedMedia by remember { mutableStateOf<ProcessedMedia?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        errorMessage = null
        isProcessing = true
        scope.launch {
            MediaProcessor.processPickedUri(context, uri)
                .onSuccess { processed ->
                    processedMedia = processed
                }
                .onFailure { err ->
                    errorMessage = err.localizedMessage ?: "Could not load selected media"
                }
            isProcessing = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Creator Studio Upload",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        val willBeLockedPreview = myCurrentPostCount >= CreatorSummary.DEFAULT_FREE_PREVIEW_LIMIT
        Surface(
            color = if (willBeLockedPreview) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = if (willBeLockedPreview) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (willBeLockedPreview) {
                        "Upload #${myCurrentPostCount + 1}: fans spend ${CreatorSummary.DEFAULT_UNLOCK_COST_VOTES} paid tokens to unlock your gallery — creators earn ${CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT}%!"
                    } else {
                        "Your first ${CreatorSummary.DEFAULT_FREE_PREVIEW_LIMIT} posts are free previews. Upload #3+ unlocks with paid tokens — creators earn ${CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT}% on unlocked content!"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (willBeLockedPreview) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Capture / Library Mode Switcher
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = useCameraXMode,
                onClick = { if (!isActionBusy) useCameraXMode = true },
                label = { Text("CameraX Live") },
                leadingIcon = {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("upload_mode_camerax")
            )
            FilterChip(
                selected = !useCameraXMode,
                onClick = { if (!isActionBusy) useCameraXMode = false },
                label = { Text("Photo / Video Picker") },
                leadingIcon = {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("upload_mode_picker")
            )
        }

        val selected = processedMedia
        when {
            selected != null -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = selected.previewBitmap.asImageBitmap(),
                            contentDescription = "Captured media preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (selected.mediaType == MediaPost.MEDIA_TYPE_VIDEO) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Videocam,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = formatDuration(selected.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        processedMedia = null
                        errorMessage = null
                    },
                    enabled = !isActionBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("upload_retake_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retake / Choose Different Media")
                }
            }
            useCameraXMode -> {
                CameraXCaptureView(
                    allowVideo = true,
                    onMediaCaptured = { media ->
                        processedMedia = media
                        errorMessage = null
                    },
                    onCaptureError = { msg -> errorMessage = msg }
                )
            }
            else -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Preparing media...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Pick a photo or video",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Uses Android Photo Picker with zero storage permissions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!useCameraXMode || selected == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isProcessing && !isActionBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_photo_button")
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Photo")
                }

                OutlinedButton(
                    onClick = {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    enabled = !isProcessing && !isActionBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_video_button")
                ) {
                    Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Video")
                }
            }
        }

        OutlinedTextField(
            value = caption,
            onValueChange = { if (it.length <= 240) caption = it },
            label = { Text("Caption") },
            placeholder = { Text("Describe this shot or clip...") },
            supportingText = { Text("${caption.length}/240") },
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("upload_caption_input")
        )

        if (uploadProgress != null && isActionBusy) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { uploadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_progress_bar")
                )
                Text(
                    text = "Uploading to Firebase Storage (${(uploadProgress * 100f).roundToInt().coerceIn(0, 100)}%)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = {
                val media = processedMedia ?: return@Button
                onPublish(media, caption) {
                    processedMedia = null
                    caption = ""
                }
            },
            enabled = processedMedia != null && !isProcessing && !isActionBusy,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("publish_post_button")
        ) {
            if (isActionBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Uploading...")
            } else {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload to Storage & Feed")
            }
        }
    }
}

