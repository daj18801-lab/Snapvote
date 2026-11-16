package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.media.MediaProcessor
import com.example.media.ProcessedMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

@Composable
fun CameraXCaptureView(
    allowVideo: Boolean,
    onMediaCaptured: (ProcessedMedia) -> Unit,
    onCaptureError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            onCaptureError("Camera permission is required to use the CameraX studio.")
        }
    }

    var isVideoMode by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var torchEnabled by remember { mutableStateOf(false) }
    var isProcessingCapture by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraBindError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeRecording) {
        if (activeRecording != null) {
            recordingSeconds = 0L
            while (activeRecording != null) {
                delay(1000L)
                recordingSeconds += 1L
            }
        } else {
            recordingSeconds = 0L
        }
    }

    LaunchedEffect(boundCamera, torchEnabled) {
        runCatching {
            boundCamera?.cameraControl?.enableTorch(torchEnabled && lensFacing == CameraSelector.LENS_FACING_BACK)
        }
    }

    DisposableEffect(hasCameraPermission, lensFacing, allowVideo, isVideoMode, lifecycleOwner) {
        var providerRef: ProcessCameraProvider? = null
        if (hasCameraPermission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        val provider = future.get()
                        providerRef = provider
                        provider.unbindAll()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val imgCap = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        val vidCap = if (allowVideo && isVideoMode) {
                            val recorder = Recorder.Builder()
                                .setQualitySelector(QualitySelector.from(Quality.SD))
                                .build()
                            VideoCapture.withOutput(recorder)
                        } else {
                            null
                        }
                        val selector = if (provider.hasCamera(
                                CameraSelector.Builder().requireLensFacing(lensFacing).build()
                            )
                        ) {
                            CameraSelector.Builder().requireLensFacing(lensFacing).build()
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        val camera = if (vidCap != null) {
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, vidCap)
                        } else {
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, imgCap)
                        }
                        boundCamera = camera
                        imageCapture = imgCap
                        videoCapture = vidCap
                        cameraBindError = null
                    } catch (e: Exception) {
                        Log.w("CameraXCapture", "Failed to bind CameraX use cases", e)
                        cameraBindError = e.localizedMessage ?: "Camera unavailable on this device"
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }

        onDispose {
            runCatching { activeRecording?.stop() }
            activeRecording = null
            runCatching { providerRef?.unbindAll() }
        }
    }

    if (!hasCameraPermission) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "CameraX Live Capture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (allowVideo) {
                        "Allow Camera & Microphone access to take crisp photos and record live videos straight into Firebase Storage."
                    } else {
                        "Allow Camera access to capture a live snap photo."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val perms = if (allowVideo) {
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        } else {
                            arrayOf(Manifest.permission.CAMERA)
                        }
                        permissionLauncher.launch(perms)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("camerax_grant_permission_button")
                ) {
                    Text("Enable CameraX")
                }
            }
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (allowVideo) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !isVideoMode,
                    onClick = { if (activeRecording == null) isVideoMode = false },
                    label = { Text("Photo") },
                    leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("camerax_mode_photo")
                )
                FilterChip(
                    selected = isVideoMode,
                    onClick = {
                        if (activeRecording == null) {
                            isVideoMode = true
                            if (!hasAudioPermission) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            }
                        }
                    },
                    label = { Text("Video") },
                    leadingIcon = { Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("camerax_mode_video")
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.large)
                .background(Color.Black)
        ) {
            if (cameraBindError != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = cameraBindError ?: "Camera unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeRecording != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(50)
                    ) {
                        val mins = recordingSeconds / 60L
                        val secs = recordingSeconds % 60L
                        Text(
                            text = String.format(Locale.US, "REC %02d:%02d", mins, secs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        IconButton(
                            onClick = { torchEnabled = !torchEnabled },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .minimumInteractiveComponentSize()
                                .testTag("camerax_torch_button")
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                                contentDescription = if (torchEnabled) "Disable flash" else "Enable flash",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (activeRecording == null) {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .minimumInteractiveComponentSize()
                            .testTag("camerax_flip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = "Flip camera",
                            tint = Color.White
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            ) {
                if (isProcessingCapture) {
                    CircularProgressIndicator(color = Color.White)
                } else if (!isVideoMode) {
                    Button(
                        onClick = {
                            val cap = imageCapture ?: return@Button
                            isProcessingCapture = true
                            val cacheDir = File(context.cacheDir, "camerax_temp").apply { mkdirs() }
                            val photoFile = File(cacheDir, "snap_${UUID.randomUUID()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            cap.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        scope.launch {
                                            MediaProcessor.processCapturedPhotoFile(context, photoFile)
                                                .onSuccess { processed ->
                                                    photoFile.delete()
                                                    onMediaCaptured(processed)
                                                }
                                                .onFailure { err ->
                                                    onCaptureError(err.localizedMessage ?: "Photo capture failed")
                                                }
                                            isProcessingCapture = false
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isProcessingCapture = false
                                        onCaptureError(exception.localizedMessage ?: "CameraX capture failed")
                                    }
                                }
                            )
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .size(68.dp)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag("camerax_shutter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = "Take photo",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    val isRecordingNow = activeRecording != null
                    Button(
                        onClick = {
                            val currentRec = activeRecording
                            if (currentRec != null) {
                                currentRec.stop()
                                return@Button
                            }
                            val vidCap = videoCapture ?: return@Button
                            val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
                            val videoFile = File(videosDir, "cam_${UUID.randomUUID()}.mp4")
                            val outputOptions = FileOutputOptions.Builder(videoFile).build()
                            var pending = vidCap.output.prepareRecording(context, outputOptions)
                            if (hasAudioPermission) {
                                pending = pending.withAudioEnabled()
                            }
                            activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                                when (event) {
                                    is VideoRecordEvent.Finalize -> {
                                        activeRecording = null
                                        if (event.hasError()) {
                                            onCaptureError("Video recording error (${event.error})")
                                            videoFile.delete()
                                        } else {
                                            isProcessingCapture = true
                                            scope.launch {
                                                MediaProcessor.processCapturedVideoFile(context, videoFile)
                                                    .onSuccess { processed -> onMediaCaptured(processed) }
                                                    .onFailure { err ->
                                                        onCaptureError(err.localizedMessage ?: "Unable to process video")
                                                    }
                                                isProcessingCapture = false
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecordingNow) MaterialTheme.colorScheme.error else Color.White,
                            contentColor = if (isRecordingNow) Color.White else MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .size(68.dp)
                            .border(3.dp, MaterialTheme.colorScheme.error, CircleShape)
                            .testTag("camerax_record_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingNow) Icons.Rounded.Stop else Icons.Rounded.Videocam,
                            contentDescription = if (isRecordingNow) "Stop recording" else "Start recording",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
