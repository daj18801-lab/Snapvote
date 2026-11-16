package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.example.data.model.MediaPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class ProcessedMedia(
    val mediaType: String,
    val mediaBase64: String,
    val previewBitmap: Bitmap,
    val localVideoFileName: String?,
    val durationMs: Long,
    val uploadPhotoFilePath: String? = null
)

object MediaProcessor {
    private const val MAX_DIMENSION_PX = 720
    private const val MAX_STORAGE_PHOTO_PX = 1440
    private const val MAX_BASE64_LENGTH = 360_000

    suspend fun processPickedUri(context: Context, uri: Uri): Result<ProcessedMedia> = withContext(Dispatchers.IO) {
        runCatching {
            val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
            val isVideo = mimeType.startsWith("video/")
            if (isVideo) {
                processVideoUri(context, uri)
            } else {
                processPhotoUri(context, uri)
            }
        }
    }

    suspend fun processCapturedPhotoFile(
        context: Context,
        photoFile: File
    ): Result<ProcessedMedia> = withContext(Dispatchers.IO) {
        runCatching {
            require(photoFile.exists() && photoFile.length() > 0L) { "Captured photo file is missing" }
            val decoded = decodeFileWithExif(photoFile, MAX_STORAGE_PHOTO_PX)
            buildPhotoMedia(context, decoded)
        }
    }

    suspend fun processCapturedVideoFile(
        context: Context,
        videoFile: File
    ): Result<ProcessedMedia> = withContext(Dispatchers.IO) {
        runCatching {
            require(videoFile.exists() && videoFile.length() > 0L) { "Recorded video file is missing" }
            val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
            val destFile = if (videoFile.parentFile?.absolutePath == videosDir.absolutePath) {
                videoFile
            } else {
                val fileName = "cam_vid_${UUID.randomUUID()}.mp4"
                val target = File(videosDir, fileName)
                videoFile.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }
            extractVideoMedia(destFile)
        }
    }

    private fun processPhotoUri(context: Context, uri: Uri): ProcessedMedia {
        val tempDir = File(context.cacheDir, "upload_cache").apply { mkdirs() }
        val tempFile = File(tempDir, "picked_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected image")

        val decoded = decodeFileWithExif(tempFile, MAX_STORAGE_PHOTO_PX)
        val result = buildPhotoMedia(context, decoded)
        tempFile.delete()
        return result
    }

    private fun buildPhotoMedia(context: Context, fullBitmap: Bitmap): ProcessedMedia {
        val uploadDir = File(context.cacheDir, "upload_cache").apply { mkdirs() }
        val uploadFile = File(uploadDir, "photo_${UUID.randomUUID()}.jpg")
        val storageScaled = scaleBitmap(fullBitmap, MAX_STORAGE_PHOTO_PX)
        uploadFile.outputStream().use { out ->
            storageScaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }

        val previewScaled = scaleBitmap(storageScaled, MAX_DIMENSION_PX)
        val base64 = compressBitmapToBase64(previewScaled)
        return ProcessedMedia(
            mediaType = MediaPost.MEDIA_TYPE_PHOTO,
            mediaBase64 = base64,
            previewBitmap = previewScaled,
            localVideoFileName = null,
            durationMs = 0L,
            uploadPhotoFilePath = uploadFile.absolutePath
        )
    }

    private fun processVideoUri(context: Context, uri: Uri): ProcessedMedia {
        val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
        val fileName = "vid_${UUID.randomUUID()}.mp4"
        val destFile = File(videosDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to read selected video")

        return extractVideoMedia(destFile)
    }

    private fun extractVideoMedia(destFile: File): ProcessedMedia {
        val retriever = MediaMetadataRetriever()
        var frameBitmap: Bitmap? = null
        var durationMs = 0L
        try {
            retriever.setDataSource(destFile.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            frameBitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } finally {
            runCatching { retriever.release() }
        }

        val rawBitmap = frameBitmap ?: Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        val scaled = scaleBitmap(rawBitmap, MAX_DIMENSION_PX)
        val base64 = compressBitmapToBase64(scaled)

        return ProcessedMedia(
            mediaType = MediaPost.MEDIA_TYPE_VIDEO,
            mediaBase64 = base64,
            previewBitmap = scaled,
            localVideoFileName = destFile.name,
            durationMs = durationMs.coerceAtLeast(1000L),
            uploadPhotoFilePath = null
        )
    }

    private fun decodeFileWithExif(file: File, maxDim: Int): Bitmap {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val sampleSize = calculateInSampleSize(
            boundsOptions.outWidth,
            boundsOptions.outHeight,
            maxDim
        )
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: error("Unable to decode image file")

        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return decoded
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun getLocalVideoFile(context: Context, fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        val file = File(File(context.filesDir, "videos"), fileName)
        return if (file.exists() && file.length() > 0L) file else null
    }

    fun getPhotoUploadFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        return if (file.exists() && file.length() > 0L) file else null
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val largest = max(width, height)
        while (largest / inSampleSize > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest.toFloat()
        val newW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun compressBitmapToBase64(bitmap: Bitmap): String {
        var quality = 76
        var encoded: String
        do {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            quality -= 12
        } while (encoded.length > MAX_BASE64_LENGTH && quality >= 24)
        return encoded
    }
}
