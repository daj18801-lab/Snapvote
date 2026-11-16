package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AdminAnnouncement
import com.example.data.model.BlockedUser
import com.example.data.model.CreatorSummary
import com.example.data.model.MediaPost
import com.example.data.model.UserCheckIn
import com.example.data.model.UserStory
import com.example.data.model.UserWarning
import com.example.media.MediaProcessor
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Date
import java.util.UUID

internal class SocialFeaturesDataSource(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage?
) {
    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Please sign in first")

    suspend fun uploadFileOrBytesToStorage(
        storagePath: String,
        localFile: File?,
        fallbackBase64: String?,
        contentType: String,
        onProgress: (Float) -> Unit = {}
    ): Pair<String?, String?> {
        val activeStorage = storage ?: return null to null
        return try {
            val ref = activeStorage.reference.child(storagePath)
            val metadata = storageMetadata { this.contentType = contentType }
            val task = when {
                localFile != null && localFile.exists() && localFile.length() > 0L ->
                    ref.putFile(Uri.fromFile(localFile), metadata)
                !fallbackBase64.isNullOrBlank() -> {
                    val bytes = Base64.decode(fallbackBase64, Base64.DEFAULT)
                    ref.putBytes(bytes, metadata)
                }
                else -> return null to null
            }
            task.addOnProgressListener { snap ->
                val total = snap.totalByteCount
                if (total > 0L) {
                    val progress = (snap.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0.05f, 0.98f)
                    onProgress(progress)
                }
            }
            task.await()
            val url = ref.downloadUrl.await().toString()
            onProgress(1f)
            url to storagePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageException) {
            if (e.httpResultCode == 404) {
                Log.w(TAG, "Firebase Storage bucket not initialized yet; storing preview fallback", e)
                null to null
            } else {
                throw e
            }
        }
    }

    suspend fun deleteStoragePath(storagePath: String?) {
        val activeStorage = storage ?: return
        val cleanPath = storagePath?.trim().orEmpty()
        if (cleanPath.isBlank()) return
        runCatching {
            activeStorage.reference.child(cleanPath).delete().await()
        }.onFailure { err ->
            Log.w(TAG, "Unable to delete storage object $cleanPath", err)
        }
    }

    suspend fun uploadMediaPost(
        context: Context?,
        creatorName: String,
        mediaType: String,
        caption: String,
        mediaBase64: String,
        localVideoFileName: String?,
        uploadPhotoFilePath: String?,
        durationMs: Long,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            val uid = requireUid()
            val safeName = creatorName.trim().ifBlank { "Creator" }.take(60)
            val safeCaption = caption.trim().take(240)
            val postId = UUID.randomUUID().toString()
            val ext = if (mediaType == MediaPost.MEDIA_TYPE_VIDEO) "mp4" else "jpg"
            val contentType = if (mediaType == MediaPost.MEDIA_TYPE_VIDEO) "video/mp4" else "image/jpeg"
            val candidateFile = when {
                mediaType == MediaPost.MEDIA_TYPE_VIDEO && context != null ->
                    MediaProcessor.getLocalVideoFile(context, localVideoFileName)
                else -> MediaProcessor.getPhotoUploadFile(uploadPhotoFilePath)
            }

            onProgress(0.05f)
            val (downloadUrl, storagePath) = uploadFileOrBytesToStorage(
                storagePath = "posts/$uid/$postId.$ext",
                localFile = candidateFile,
                fallbackBase64 = mediaBase64,
                contentType = contentType,
                onProgress = onProgress
            )

            val postRef = db.collection("posts").document(postId)
            val creatorRef = db.collection("creators").document(uid)

            db.runTransaction { tx ->
                val creatorSnap = tx.get(creatorRef)
                val currentPostCount = if (creatorSnap.exists()) {
                    creatorSnap.getLong("postCount") ?: 0L
                } else {
                    0L
                }

                if (!creatorSnap.exists()) {
                    tx.set(
                        creatorRef,
                        mapOf(
                            "creatorId" to uid,
                            "displayName" to safeName,
                            "bio" to "",
                            "visibility" to "public",
                            "postCount" to 1L,
                            "totalVotesReceived" to 0L,
                            "freePreviewLimit" to CreatorSummary.DEFAULT_FREE_PREVIEW_LIMIT,
                            "unlockCostVotes" to CreatorSummary.DEFAULT_UNLOCK_COST_VOTES,
                            "isOnline" to true,
                            "lastSeenAt" to FieldValue.serverTimestamp(),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.update(
                        creatorRef,
                        mapOf(
                            "postCount" to currentPostCount + 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val postPayload = mutableMapOf<String, Any>(
                    "creatorId" to uid,
                    "creatorName" to safeName,
                    "mediaType" to mediaType,
                    "caption" to safeCaption,
                    "mediaBase64" to mediaBase64,
                    "durationMs" to durationMs,
                    "postIndex" to currentPostCount,
                    "voteCount" to 0L,
                    "visibility" to "public",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!localVideoFileName.isNullOrBlank()) {
                    postPayload["localVideoFileName"] = localVideoFileName.take(120)
                }
                if (!downloadUrl.isNullOrBlank()) {
                    postPayload["mediaDownloadUrl"] = downloadUrl.take(1000)
                }
                if (!storagePath.isNullOrBlank()) {
                    postPayload["storagePath"] = storagePath.take(260)
                }
                tx.set(postRef, postPayload)
                postId
            }.await()

            Result.success(postId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "posts", auth)
            Result.failure(e)
        }
    }

    suspend fun sendDirectSnap(
        threadId: String,
        senderName: String,
        caption: String,
        snapBase64: String,
        uploadPhotoFilePath: String?,
        durationSeconds: Long,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            val uid = requireUid()
            require(snapBase64.isNotBlank()) { "Snap image cannot be empty" }
            val safeSenderName = senderName.trim().ifBlank { "Creator" }.take(60)
            val safeCaption = caption.trim().take(200)
            val clampedDuration = durationSeconds.coerceIn(1L, 30L)
            val msgId = UUID.randomUUID().toString()
            val candidateFile = MediaProcessor.getPhotoUploadFile(uploadPhotoFilePath)

            val (downloadUrl, storagePath) = uploadFileOrBytesToStorage(
                storagePath = "dm_snaps/$threadId/$uid/$msgId.jpg",
                localFile = candidateFile,
                fallbackBase64 = snapBase64,
                contentType = "image/jpeg",
                onProgress = onProgress
            )

            val threadRef = db.collection("direct_chats").document(threadId)
            val msgRef = threadRef.collection("messages").document(msgId)
            val msgPayload = mutableMapOf<String, Any>(
                "threadId" to threadId,
                "senderId" to uid,
                "senderName" to safeSenderName,
                "text" to safeCaption,
                "messageType" to "snap",
                "snapBase64" to snapBase64,
                "snapDurationSeconds" to clampedDuration,
                "viewedByIds" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!downloadUrl.isNullOrBlank()) {
                msgPayload["snapDownloadUrl"] = downloadUrl.take(1000)
            }
            if (!storagePath.isNullOrBlank()) {
                msgPayload["snapStoragePath"] = storagePath.take(260)
            }

            val summaryText = if (safeCaption.isNotBlank()) "📷 Snap: $safeCaption" else "📷 Sent a Snap"
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                threadRef,
                mapOf(
                    "lastMessageText" to summaryText.take(500),
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "direct_chats/$threadId/messages", auth)
            Result.failure(e)
        }
    }

    suspend fun markDirectSnapViewed(threadId: String, messageId: String): Result<Unit> {
        return try {
            val uid = requireUid()
            val msgRef = db.collection("direct_chats")
                .document(threadId)
                .collection("messages")
                .document(messageId)
            db.runTransaction { tx ->
                val snap = tx.get(msgRef)
                if (!snap.exists()) return@runTransaction
                @Suppress("UNCHECKED_CAST")
                val existing = (snap.get("viewedByIds") as? List<String>).orEmpty()
                if (uid in existing || existing.size >= 2) return@runTransaction
                tx.update(msgRef, "viewedByIds", existing + uid)
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "direct_chats/$threadId/messages/$messageId", auth)
            Result.failure(e)
        }
    }

    fun observeActiveStories(): Flow<List<UserStory>> = flow {
        val path = "stories"
        emitAll(
            db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    val now = System.currentTimeMillis()
                    snapshot.documents
                        .mapNotNull { it.toObject(UserStory::class.java) }
                        .filter { it.isActive(now) }
                }
                .catch { error ->
                    handleFirestoreError(error, OperationType.LIST, path, auth)
                    throw error
                }
        )
    }

    suspend fun postStory(
        userName: String,
        storyType: String,
        text: String,
        backgroundStyle: String,
        mediaBase64: String? = null,
        uploadPhotoFilePath: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        return try {
            val uid = requireUid()
            val safeName = userName.trim().ifBlank { "Creator" }.take(60)
            val safeText = text.trim().take(280)
            val safeType = if (storyType == UserStory.TYPE_PHOTO) UserStory.TYPE_PHOTO else UserStory.TYPE_MESSAGE
            val safeStyle = if (backgroundStyle in UserStory.styles) backgroundStyle else UserStory.STYLE_SUNSET
            if (safeType == UserStory.TYPE_MESSAGE) {
                require(safeText.isNotBlank()) { "Story message cannot be empty" }
            } else {
                require(!mediaBase64.isNullOrBlank()) { "Story photo is required" }
            }

            val storyId = UUID.randomUUID().toString()
            val (downloadUrl, storagePath) = if (safeType == UserStory.TYPE_PHOTO) {
                uploadFileOrBytesToStorage(
                    storagePath = "stories/$uid/$storyId.jpg",
                    localFile = MediaProcessor.getPhotoUploadFile(uploadPhotoFilePath),
                    fallbackBase64 = mediaBase64,
                    contentType = "image/jpeg",
                    onProgress = onProgress
                )
            } else {
                null to null
            }

            // 23h 55m stays strictly inside <= 24h + 5m security rule despite client clock skew
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + (23L * 60L + 55L) * 60L * 1000L))
            val payload = mutableMapOf<String, Any>(
                "storyId" to storyId,
                "userId" to uid,
                "userName" to safeName,
                "storyType" to safeType,
                "text" to safeText,
                "backgroundStyle" to safeStyle,
                "viewCount" to 0L,
                "expiresAt" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (safeType == UserStory.TYPE_PHOTO && !mediaBase64.isNullOrBlank()) {
                payload["mediaBase64"] = mediaBase64
            }
            if (!downloadUrl.isNullOrBlank()) {
                payload["mediaDownloadUrl"] = downloadUrl.take(1000)
            }
            if (!storagePath.isNullOrBlank()) {
                payload["storagePath"] = storagePath.take(260)
            }

            db.collection("stories").document(storyId).set(payload).await()
            Result.success(storyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "stories", auth)
            Result.failure(e)
        }
    }

    suspend fun incrementStoryView(storyId: String): Result<Unit> {
        return try {
            val uid = requireUid()
            val storyRef = db.collection("stories").document(storyId)
            db.runTransaction { tx ->
                val snap = tx.get(storyRef)
                if (!snap.exists()) return@runTransaction
                if (snap.getString("userId") == uid) return@runTransaction
                val currentCount = snap.getLong("viewCount") ?: 0L
                tx.update(
                    storyRef,
                    mapOf(
                        "viewCount" to currentCount + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "stories/$storyId", auth)
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String, storagePath: String?): Result<Unit> {
        return try {
            requireUid()
            db.collection("stories").document(storyId).delete().await()
            deleteStoragePath(storagePath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "stories/$storyId", auth)
            Result.failure(e)
        }
    }

    fun observeActiveCheckIns(): Flow<List<UserCheckIn>> = flow {
        val path = "check_ins"
        emitAll(
            db.collection(path)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(60)
                .snapshots()
                .map { snapshot ->
                    val now = System.currentTimeMillis()
                    snapshot.documents
                        .mapNotNull { it.toObject(UserCheckIn::class.java) }
                        .filter { it.isActive(now) }
                }
                .catch { error ->
                    handleFirestoreError(error, OperationType.LIST, path, auth)
                    throw error
                }
        )
    }

    suspend fun setCheckIn(
        userName: String,
        placeLabel: String,
        statusNote: String,
        vibeTag: String
    ): Result<Unit> {
        return try {
            val uid = requireUid()
            val safeName = userName.trim().ifBlank { "Creator" }.take(60)
            val safePlace = placeLabel.trim().ifBlank { "Checked in" }.take(60)
            val safeNote = statusNote.trim().take(120)
            val safeVibe = if (vibeTag in UserCheckIn.presetVibes) vibeTag else UserCheckIn.VIBE_CREATING
            require(safePlace.isNotBlank() || safeNote.isNotBlank()) { "Add a place label or note to check in" }

            val expiresAt = Timestamp(Date(System.currentTimeMillis() + UserCheckIn.CHECKIN_DURATION_MILLIS))
            val docRef = db.collection("check_ins").document(uid)
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val createdAtValue: Any = snap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
                tx.set(
                    docRef,
                    mapOf(
                        "userId" to uid,
                        "userName" to safeName,
                        "placeLabel" to safePlace,
                        "statusNote" to safeNote,
                        "vibeTag" to safeVibe,
                        "expiresAt" to expiresAt,
                        "createdAt" to createdAtValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "check_ins", auth)
            Result.failure(e)
        }
    }

    suspend fun clearCheckIn(): Result<Unit> {
        return try {
            val uid = requireUid()
            db.collection("check_ins").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "check_ins", auth)
            Result.failure(e)
        }
    }

    // --- ADMIN BROADCAST ALERTS & NEWS + STAFF TARGETED USER WARNING POPUPS ---

    fun observeAdminAnnouncements(): Flow<List<AdminAnnouncement>> = flow {
        val path = "admin_announcements"
        emitAll(
            db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { it.toObject(AdminAnnouncement::class.java) }
                }
                .catch { error ->
                    handleFirestoreError(error, OperationType.LIST, path, auth)
                    throw error
                }
        )
    }

    suspend fun sendAdminAnnouncement(
        category: String,
        title: String,
        message: String,
        senderName: String,
        senderRole: String
    ): Result<String> {
        return try {
            val uid = requireUid()
            val safeCategory = if (category == AdminAnnouncement.CATEGORY_ALERT) {
                AdminAnnouncement.CATEGORY_ALERT
            } else {
                AdminAnnouncement.CATEGORY_NEWS
            }
            val safeTitle = title.trim().take(100)
            val safeMessage = message.trim().take(600)
            require(safeTitle.isNotBlank()) { "Announcement title cannot be empty" }
            require(safeMessage.isNotBlank()) { "Announcement message cannot be empty" }
            val safeSenderName = senderName.trim().ifBlank { "Admin" }.take(60)
            val safeRole = if (senderRole == "owner") "owner" else "admin"
            val announcementId = UUID.randomUUID().toString()

            db.collection("admin_announcements").document(announcementId).set(
                mapOf(
                    "announcementId" to announcementId,
                    "category" to safeCategory,
                    "title" to safeTitle,
                    "message" to safeMessage,
                    "senderId" to uid,
                    "senderName" to safeSenderName,
                    "senderRole" to safeRole,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(announcementId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "admin_announcements", auth)
            Result.failure(e)
        }
    }

    suspend fun deleteAdminAnnouncement(announcementId: String): Result<Unit> {
        return try {
            requireUid()
            db.collection("admin_announcements").document(announcementId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "admin_announcements/$announcementId", auth)
            Result.failure(e)
        }
    }

    fun observeMyWarnings(userId: String): Flow<List<UserWarning>> = flow {
        val path = "user_warnings"
        emitAll(
            db.collection(path)
                .whereEqualTo("targetUserId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { it.toObject(UserWarning::class.java) }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    handleFirestoreError(error, OperationType.LIST, path, auth)
                    throw error
                }
        )
    }

    suspend fun sendUserWarning(
        targetUserId: String,
        targetUserName: String,
        senderName: String,
        senderRole: String,
        severity: String,
        title: String,
        message: String
    ): Result<String> {
        return try {
            val uid = requireUid()
            require(targetUserId.isNotBlank()) { "Target user is required" }
            val safeSeverity = when (severity) {
                UserWarning.SEVERITY_REMINDER,
                UserWarning.SEVERITY_WARNING,
                UserWarning.SEVERITY_FINAL_WARNING -> severity
                else -> UserWarning.SEVERITY_WARNING
            }
            val safeTitle = title.trim().ifBlank { "Community Guidelines Notice" }.take(100)
            val safeMessage = message.trim().take(600)
            require(safeMessage.isNotBlank()) { "Warning message cannot be empty" }

            val safeRole = when (senderRole) {
                "owner", "admin", "support" -> senderRole
                else -> "support"
            }
            val warningId = UUID.randomUUID().toString()
            db.collection("user_warnings").document(warningId).set(
                mapOf(
                    "warningId" to warningId,
                    "targetUserId" to targetUserId,
                    "targetUserName" to targetUserName.trim().ifBlank { "User" }.take(60),
                    "senderId" to uid,
                    "senderName" to senderName.trim().ifBlank { "Staff" }.take(60),
                    "senderRole" to safeRole,
                    "severity" to safeSeverity,
                    "title" to safeTitle,
                    "message" to safeMessage,
                    "acknowledged" to false,
                    "acknowledgedAt" to null,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(warningId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "user_warnings", auth)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeUserWarning(warningId: String): Result<Unit> {
        return try {
            requireUid()
            db.collection("user_warnings").document(warningId).update(
                mapOf(
                    "acknowledged" to true,
                    "acknowledgedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "user_warnings/$warningId", auth)
            Result.failure(e)
        }
    }

    // --- USER-MANAGED BLOCKED USERS ---

    fun observeBlockedUsers(userId: String): Flow<List<BlockedUser>> = flow {
        val path = "users/$userId/blocked_users"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("blocked_users")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { it.toObject(BlockedUser::class.java) }
                }
                .catch { error ->
                    handleFirestoreError(error, OperationType.LIST, path, auth)
                    throw error
                }
        )
    }

    suspend fun blockUser(
        blockedUserId: String,
        blockedUserName: String,
        reason: String? = null
    ): Result<Unit> {
        return try {
            val uid = requireUid()
            val cleanBlockedId = blockedUserId.trim()
            require(cleanBlockedId.isNotBlank()) { "Target user ID cannot be blank" }
            require(cleanBlockedId != uid) { "You cannot block your own account" }

            val safeName = blockedUserName.trim().ifBlank { "User" }.take(60)
            val cleanReason = reason?.trim()?.take(160)?.takeIf { it.isNotEmpty() }
            val docRef = db.collection("users")
                .document(uid)
                .collection("blocked_users")
                .document(cleanBlockedId)

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "blockedUserId" to cleanBlockedId,
                "blockedUserName" to safeName,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (cleanReason != null) {
                payload["reason"] = cleanReason
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/blocked_users", auth)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(blockedUserId: String): Result<Unit> {
        return try {
            val uid = requireUid()
            val cleanBlockedId = blockedUserId.trim()
            require(cleanBlockedId.isNotBlank()) { "Target user ID cannot be blank" }
            db.collection("users")
                .document(uid)
                .collection("blocked_users")
                .document(cleanBlockedId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/blocked_users/$blockedUserId", auth)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "SocialFeaturesDS"
    }
}
