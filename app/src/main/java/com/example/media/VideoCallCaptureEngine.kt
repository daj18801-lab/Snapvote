package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class EncodedVideoFrame(
    val base64: String,
    val width: Int,
    val height: Int,
    val sequenceNumber: Long
)

/**
 * Lightweight CameraX binder for 1:1 creator private video calls.
 * Binds live local Preview + throttled JPEG ImageAnalysis (~2.5 fps, <= 90 KB Base64)
 * so remote participants see real live camera frames over Firestore.
 */
class VideoCallCaptureController(
    private val context: Context
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val isDisposed = AtomicBoolean(false)
    private val sequence = AtomicLong(0L)
    private var lastFrameSentAtMs: Long = 0L
    private var boundProvider: ProcessCameraProvider? = null

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFrontCamera: Boolean,
        isCameraEnabled: Boolean,
        onFrameEncoded: (EncodedVideoFrame) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isCameraEnabled) {
            unbind()
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                if (isDisposed.get()) return@addListener
                try {
                    val provider = cameraProviderFuture.get()
                    boundProvider = provider

                    val selector = if (useFrontCamera && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(320, 240),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        processImageProxy(imageProxy, useFrontCamera, onFrameEncoded)
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                } catch (t: Throwable) {
                    Log.w(TAG, "Unable to bind CameraX preview", t)
                    onError(t.localizedMessage ?: "Camera preview unavailable on this device")
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun processImageProxy(
        imageProxy: ImageProxy,
        mirrorHorizontally: Boolean,
        onFrameEncoded: (EncodedVideoFrame) -> Unit
    ) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastFrameSentAtMs < FRAME_INTERVAL_MS || isDisposed.get()) {
                return
            }
            val rawBitmap = imageProxy.toBitmap()
            val rotated = rotateAndScale(
                source = rawBitmap,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                mirrorHorizontally = mirrorHorizontally,
                maxDimension = TARGET_MAX_DIMENSION
            )
            if (rotated !== rawBitmap) {
                rawBitmap.recycle()
            }

            val baos = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            val width = rotated.width
            val height = rotated.height
            rotated.recycle()

            val bytes = baos.toByteArray()
            if (bytes.isEmpty()) return
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (encoded.length > MAX_BASE64_CHARS) return

            lastFrameSentAtMs = now
            onFrameEncoded(
                EncodedVideoFrame(
                    base64 = encoded,
                    width = width,
                    height = height,
                    sequenceNumber = sequence.incrementAndGet()
                )
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Frame encode skipped", t)
        } finally {
            imageProxy.close()
        }
    }

    fun unbind() {
        try {
            boundProvider?.unbindAll()
        } catch (t: Throwable) {
            Log.w(TAG, "Error unbinding camera provider", t)
        }
    }

    fun release() {
        if (isDisposed.compareAndSet(false, true)) {
            unbind()
            analysisExecutor.shutdownNow()
        }
    }

    companion object {
        private const val TAG = "VideoCallCapture"
        private const val FRAME_INTERVAL_MS = 380L
        private const val TARGET_MAX_DIMENSION = 320
        private const val JPEG_QUALITY = 58
        private const val MAX_BASE64_CHARS = 120_000

        fun decodeBase64Bitmap(base64: String): Bitmap? {
            if (base64.isBlank()) return null
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Throwable) {
                null
            }
        }

        private fun rotateAndScale(
            source: Bitmap,
            rotationDegrees: Int,
            mirrorHorizontally: Boolean,
            maxDimension: Int
        ): Bitmap {
            val matrix = Matrix()
            if (rotationDegrees != 0) {
                matrix.postRotate(rotationDegrees.toFloat())
            }
            if (mirrorHorizontally) {
                matrix.postScale(-1f, 1f)
            }
            val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            val longest = maxOf(oriented.width, oriented.height)
            if (longest <= maxDimension) {
                return oriented
            }
            val scale = maxDimension.toFloat() / longest.toFloat()
            val targetW = (oriented.width * scale).toInt().coerceAtLeast(1)
            val targetH = (oriented.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
            if (scaled !== oriented) {
                oriented.recycle()
            }
            return scaled
        }
    }
}
