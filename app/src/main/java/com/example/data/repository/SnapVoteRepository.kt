package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AdminAnnouncement
import com.example.data.model.BanRequest
import com.example.data.model.BlockedUser
import com.example.data.model.ChatBounce
import com.example.data.model.ChatMessage
import com.example.data.model.ChatModerationStatus
import com.example.data.model.ChatPresence
import com.example.data.model.ChatRoomCatalog
import com.example.data.model.ChatRoomState
import com.example.data.model.ChatRoomVoiceChunk
import com.example.data.model.ContentReport
import com.example.data.model.CreatorPayout
import com.example.data.model.CreatorSummary
import com.example.data.model.CreatorUnlock
import com.example.data.model.DirectChatThread
import com.example.data.model.DirectMessage
import com.example.data.model.EffectiveStaffTier
import com.example.data.model.FriendRelationship
import com.example.data.model.GroupChatMessage
import com.example.data.model.MediaPost
import com.example.data.model.MicAudioChunk
import com.example.data.model.MicSession
import com.example.data.model.PhoneTokenPack
import com.example.data.model.PhoneTokenPurchase
import com.example.data.model.PrivateGroupChat
import com.example.data.model.ReferralCashout
import com.example.data.model.ReferralClaim
import com.example.data.model.ReferralRecord
import com.example.data.model.StaffRole
import com.example.data.model.SupportPayrollPayout
import com.example.data.model.SupportTicket
import com.example.data.model.UserCheckIn
import com.example.data.model.UserProfile
import com.example.data.model.UserReport
import com.example.data.model.UserStory
import com.example.data.model.UserWarning
import com.example.data.model.VideoFrameChunk
import com.example.data.model.VideoSession
import com.example.data.model.VideoTokenPack
import com.example.data.model.VideoTokenPurchase
import com.example.data.model.VotePack
import com.example.data.model.VotePurchase
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale
import java.util.UUID

class InsufficientVotesException(
    val requiredVotes: Long,
    val availableVotes: Long,
    val paidTokensOnly: Boolean = false
) : IllegalStateException(
    if (paidTokensOnly) {
        "Need $requiredVotes paid tokens (you have $availableVotes paid; free sign-up tokens cannot pay creators)"
    } else {
        "Need $requiredVotes tokens (you have $availableVotes)"
    }
)

class InsufficientPhoneTokensException(
    val requiredTokens: Long,
    val availableTokens: Long
) : IllegalStateException("Need $requiredTokens phone tokens (you have $availableTokens)")

class InsufficientVideoTokensException(
    val requiredTokens: Long,
    val availableTokens: Long
) : IllegalStateException("Need $requiredTokens video tokens (you have $availableTokens)")

class PrivateChatPassRequiredException :
    IllegalStateException("Private Messaging Pass required (5 tokens per week)")

private fun safeFirebaseStorage(): FirebaseStorage? =
    try {
        Firebase.storage
    } catch (_: Throwable) {
        null
    }

class SnapVoteRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val storage: FirebaseStorage? = null
) {
    private val social by lazy { SocialFeaturesDataSource(db, auth, storage) }

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        storage = safeFirebaseStorage()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun verifiedLowerEmail(): String? {
        val user = auth.currentUser ?: return null
        if (!user.isEmailVerified) return null
        return user.email?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
    }

    suspend fun ensureUserAccountInitialized(defaultDisplayName: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val safeName = defaultDisplayName.trim().ifBlank { "Creator" }.take(60)
            val verifiedEmail = verifiedLowerEmail()
            val userRef = db.collection("users").document(uid)
            val creatorRef = db.collection("creators").document(uid)

            val userSnap = userRef.get().await()
            val creatorSnap = creatorRef.get().await()
            val existingCode = userSnap.getString("referralCode")?.takeIf { it.isNotBlank() }
            val candidateCode = existingCode ?: UserProfile.defaultReferralCodeForUid(uid)
            var resolvedCode = candidateCode
            var codeRef = db.collection("referral_codes").document(resolvedCode)
            var codeSnap = codeRef.get().await()
            if (codeSnap.exists() && codeSnap.getString("ownerId") != uid) {
                resolvedCode = "SV" + UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT).take(8)
                codeRef = db.collection("referral_codes").document(resolvedCode)
                codeSnap = codeRef.get().await()
            }

            var pendingReferralBackfill = false

            if (!userSnap.exists()) {
                val userPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "bio" to "",
                    "role" to UserProfile.ROLE_USER,
                    "voteBalance" to STARTER_VOTES,
                    "paidVoteBalance" to 0L,
                    "totalVotesCast" to 0L,
                    "totalVotesPurchased" to 0L,
                    "phoneTokenBalance" to 0L,
                    "totalPhoneTokensPurchased" to 0L,
                    "videoTokenBalance" to 0L,
                    "totalVideoTokensPurchased" to 0L,
                    "referralCode" to resolvedCode,
                    "referralCashableBalance" to 0L,
                    "totalReferralTokensEarned" to 0L,
                    "totalReferralTokensCashedIn" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (verifiedEmail != null) {
                    userPayload["email"] = verifiedEmail
                }
                userRef.set(userPayload).await()
            } else {
                val needsEmail = verifiedEmail != null && userSnap.getString("email").isNullOrBlank()
                val needsCode = userSnap.getString("referralCode").isNullOrBlank()
                if (needsEmail) {
                    userRef.update(
                        mapOf(
                            "email" to verifiedEmail!!,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    pendingReferralBackfill = needsCode
                } else if (needsCode) {
                    userRef.update(
                        mapOf(
                            "referralCode" to resolvedCode,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            if (pendingReferralBackfill) {
                userRef.update(
                    mapOf(
                        "referralCode" to resolvedCode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (!codeSnap.exists()) {
                codeRef.set(
                    mapOf(
                        "code" to resolvedCode,
                        "ownerId" to uid,
                        "ownerName" to safeName,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (!creatorSnap.exists()) {
                val creatorPayload = mapOf(
                    "creatorId" to uid,
                    "displayName" to safeName,
                    "bio" to "",
                    "visibility" to "public",
                    "postCount" to 0L,
                    "totalVotesReceived" to 0L,
                    "freePreviewLimit" to CreatorSummary.DEFAULT_FREE_PREVIEW_LIMIT,
                    "unlockCostVotes" to CreatorSummary.DEFAULT_UNLOCK_COST_VOTES,
                    "isOnline" to true,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                creatorRef.set(creatorPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/init", auth)
            Result.failure(e)
        }
    }

    fun observeEffectiveStaffTier(): Flow<EffectiveStaffTier> = flow {
        val user = auth.currentUser
        if (user == null) {
            emit(EffectiveStaffTier.NONE)
            return@flow
        }
        val email = user.email?.trim()?.lowercase(Locale.ROOT)
        if (!email.isNullOrBlank() && user.isEmailVerified && email == StaffRole.OWNER_EMAIL) {
            emit(EffectiveStaffTier.OWNER)
            return@flow
        }

        val userDocTierFlow: Flow<EffectiveStaffTier> = db.collection("users").document(user.uid)
            .snapshots()
            .map { snapshot ->
                when (snapshot.getString("role")) {
                    UserProfile.ROLE_ADMIN -> EffectiveStaffTier.ADMIN
                    UserProfile.ROLE_SUPPORT -> EffectiveStaffTier.SUPPORT
                    else -> EffectiveStaffTier.NONE
                }
            }
            .catch { error ->
                logStreamError(error, OperationType.GET, "users/${user.uid}", user.uid)
                emit(EffectiveStaffTier.NONE)
            }

        val staffDocTierFlow: Flow<EffectiveStaffTier> = if (!email.isNullOrBlank() && user.isEmailVerified) {
            db.collection("staff_roles").document(email)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) {
                        EffectiveStaffTier.NONE
                    } else {
                        val staff = snapshot.toObject(
                            StaffRole::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        if (staff?.active == true) {
                            when (staff.role) {
                                StaffRole.ROLE_ADMIN -> EffectiveStaffTier.ADMIN
                                StaffRole.ROLE_SUPPORT -> EffectiveStaffTier.SUPPORT
                                else -> EffectiveStaffTier.NONE
                            }
                        } else {
                            EffectiveStaffTier.NONE
                        }
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, "staff_roles/$email", user.uid)
                    emit(EffectiveStaffTier.NONE)
                }
        } else {
            flow { emit(EffectiveStaffTier.NONE) }
        }

        emitAll(
            combine(userDocTierFlow, staffDocTierFlow) { fromUser, fromStaff ->
                maxOf(fromUser, fromStaff)
            }
        )
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<UserProfile> {
                            when {
                                it.email.equals(StaffRole.OWNER_EMAIL, ignoreCase = true) -> 3
                                it.role == UserProfile.ROLE_ADMIN -> 2
                                it.role == UserProfile.ROLE_SUPPORT -> 1
                                else -> 0
                            }
                        }.thenBy { it.displayName.lowercase(Locale.ROOT) }
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun assignUserRole(targetUser: UserProfile, newRole: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val callerEmail = auth.currentUser?.email?.trim()?.lowercase(Locale.ROOT) ?: "admin"
            require(
                newRole == UserProfile.ROLE_USER ||
                    newRole == UserProfile.ROLE_SUPPORT ||
                    newRole == UserProfile.ROLE_ADMIN
            ) { "Invalid role: $newRole" }
            val targetEmail = targetUser.email?.trim()?.lowercase(Locale.ROOT)
            require(targetEmail != StaffRole.OWNER_EMAIL) { "Owner account cannot be modified" }

            val batch = db.batch()
            val userRef = db.collection("users").document(targetUser.userId)
            val userRoleUpdate = mutableMapOf<String, Any?>(
                "role" to newRole,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newRole == UserProfile.ROLE_SUPPORT) {
                if (targetUser.supportStartedAt == null) {
                    userRoleUpdate["supportStartedAt"] = FieldValue.serverTimestamp()
                }
            } else if (newRole == UserProfile.ROLE_USER) {
                userRoleUpdate["supportStartedAt"] = null
            }
            batch.update(userRef, userRoleUpdate)

            if (!targetEmail.isNullOrBlank() && EMAIL_REGEX.matches(targetEmail)) {
                val staffRef = db.collection("staff_roles").document(targetEmail)
                if (newRole == UserProfile.ROLE_USER) {
                    batch.delete(staffRef)
                } else {
                    val existingStaff = staffRef.get().await()
                    if (!existingStaff.exists()) {
                        batch.set(
                            staffRef,
                            mapOf(
                                "email" to targetEmail,
                                "displayLabel" to targetUser.displayName.take(60),
                                "role" to newRole,
                                "active" to true,
                                "assignedByUid" to uid,
                                "assignedByEmail" to callerEmail,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        batch.update(
                            staffRef,
                            mapOf(
                                "displayLabel" to targetUser.displayName.take(60),
                                "role" to newRole,
                                "active" to true,
                                "assignedByUid" to uid,
                                "assignedByEmail" to callerEmail,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${targetUser.userId}/role", auth)
            Result.failure(e)
        }
    }

    fun observeAllStaffRoles(): Flow<List<StaffRole>> = flow {
        val uid = requireUserId()
        val path = "staff_roles"
        emitAll(
            db.collection("staff_roles")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StaffRole::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.email }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun upsertStaffRole(
        targetEmail: String,
        displayLabel: String,
        role: String,
        active: Boolean = true
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val callerEmail = auth.currentUser?.email?.trim()?.lowercase(Locale.ROOT)
                ?: throw IllegalStateException("Verified email required")
            val normalizedTarget = targetEmail.trim().lowercase(Locale.ROOT)
            require(EMAIL_REGEX.matches(normalizedTarget)) { "Please enter a valid email address" }
            require(normalizedTarget != StaffRole.OWNER_EMAIL) { "Owner account cannot be modified" }
            require(role == StaffRole.ROLE_ADMIN || role == StaffRole.ROLE_SUPPORT) { "Invalid role" }

            val docRef = db.collection("staff_roles").document(normalizedTarget)
            val existingSnap = docRef.get().await()
            val safeLabel = displayLabel.trim().take(60)

            val batch = db.batch()
            if (!existingSnap.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "email" to normalizedTarget,
                    "role" to role,
                    "active" to active,
                    "assignedByUid" to uid,
                    "assignedByEmail" to callerEmail,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeLabel.isNotEmpty()) {
                    payload["displayLabel"] = safeLabel
                }
                batch.set(docRef, payload)
            } else {
                val payload = mutableMapOf<String, Any>(
                    "role" to role,
                    "active" to active,
                    "assignedByUid" to uid,
                    "assignedByEmail" to callerEmail,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeLabel.isNotEmpty()) {
                    payload["displayLabel"] = safeLabel
                }
                batch.update(docRef, payload)
            }

            // Sync matching user document role if that user already signed in
            val matchingUsers = runCatching {
                db.collection("users").whereEqualTo("email", normalizedTarget).get().await()
            }.getOrNull()
            val effectiveUserRole = if (active) role else UserProfile.ROLE_USER
            matchingUsers?.documents?.forEach { userDoc ->
                val updates = mutableMapOf<String, Any?>(
                    "role" to effectiveUserRole,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (active && role == StaffRole.ROLE_SUPPORT) {
                    if (userDoc.getTimestamp("supportStartedAt") == null) {
                        updates["supportStartedAt"] = FieldValue.serverTimestamp()
                    }
                } else if (!active || role != StaffRole.ROLE_SUPPORT) {
                    updates["supportStartedAt"] = null
                }
                batch.update(userDoc.reference, updates)
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "staff_roles", auth)
            Result.failure(e)
        }
    }

    suspend fun removeStaffRole(targetEmail: String): Result<Unit> {
        return try {
            requireUserId()
            val normalizedTarget = targetEmail.trim().lowercase(Locale.ROOT)
            require(normalizedTarget != StaffRole.OWNER_EMAIL) { "Owner account cannot be removed" }
            val batch = db.batch()
            batch.delete(db.collection("staff_roles").document(normalizedTarget))

            val matchingUsers = runCatching {
                db.collection("users").whereEqualTo("email", normalizedTarget).get().await()
            }.getOrNull()
            matchingUsers?.documents?.forEach { userDoc ->
                batch.update(
                    userDoc.reference,
                    mapOf(
                        "role" to UserProfile.ROLE_USER,
                        "supportStartedAt" to null,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "staff_roles/$targetEmail", auth)
            Result.failure(e)
        }
    }

    fun observeModerationReports(): Flow<List<ContentReport>> = flow {
        val uid = requireUserId()
        val path = "reports"
        emitAll(
            db.collection("reports")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ContentReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun submitReport(post: MediaPost, reason: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val safeReason = reason.trim().ifBlank { "Inappropriate content" }.take(120)
            val reportId = UUID.randomUUID().toString()
            db.collection("reports").document(reportId).set(
                mapOf(
                    "postId" to post.id,
                    "postCreatorId" to post.creatorId,
                    "postCaption" to post.caption.take(240),
                    "reporterId" to uid,
                    "reason" to safeReason,
                    "status" to ContentReport.STATUS_OPEN,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reports", auth)
            Result.failure(e)
        }
    }

    suspend fun resolveReport(
        report: ContentReport,
        newStatus: String,
        hidePost: Boolean,
        note: String = ""
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            val reportRef = db.collection("reports").document(report.id)
            val reportUpdate = mutableMapOf<String, Any>(
                "status" to newStatus,
                "reviewedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val safeNote = note.trim().take(240)
            if (safeNote.isNotEmpty()) {
                reportUpdate["resolutionNote"] = safeNote
            }
            batch.update(reportRef, reportUpdate)
            if (hidePost && report.postId.isNotBlank()) {
                batch.update(
                    db.collection("posts").document(report.postId),
                    mapOf(
                        "visibility" to "hidden",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reports/${report.id}", auth)
            Result.failure(e)
        }
    }

    suspend fun setPostVisibility(postId: String, visibility: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("posts").document(postId).update(
                mapOf(
                    "visibility" to visibility,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "posts/$postId", auth)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeUnlockedCreatorIds(userId: String): Flow<Set<String>> = flow {
        val path = "users/$userId/unlocks"
        emitAll(
            db.collection("users").document(userId).collection("unlocks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CreatorUnlock::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.creatorId }.toSet()
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observePurchaseHistory(userId: String): Flow<List<VotePurchase>> = flow {
        val path = "users/$userId/purchases"
        emitAll(
            db.collection("users").document(userId).collection("purchases")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VotePurchase::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.purchasedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observePublicCreators(): Flow<List<CreatorSummary>> = flow {
        val uid = requireUserId()
        val path = "creators"
        emitAll(
            db.collection("creators")
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CreatorSummary::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.totalVotesReceived }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observePublicPosts(): Flow<List<MediaPost>> = flow {
        val uid = requireUserId()
        val path = "posts"
        emitAll(
            db.collection("posts")
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MediaPost::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun uploadPost(
        creatorName: String,
        mediaType: String,
        caption: String,
        mediaBase64: String,
        localVideoFileName: String? = null,
        durationMs: Long = 0L,
        uploadPhotoFilePath: String? = null,
        context: Context? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = social.uploadMediaPost(
        context = context,
        creatorName = creatorName,
        mediaType = mediaType,
        caption = caption,
        mediaBase64 = mediaBase64,
        localVideoFileName = localVideoFileName,
        uploadPhotoFilePath = uploadPhotoFilePath,
        durationMs = durationMs,
        onProgress = onProgress
    )

    suspend fun castVoteOnPost(post: MediaPost): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(post.id.isNotBlank()) { "Post ID must not be blank" }
            require(post.creatorId.isNotBlank()) { "Creator ID must not be blank" }

            val userRef = db.collection("users").document(uid)
            val postRef = db.collection("posts").document(post.id)
            val creatorRef = db.collection("creators").document(post.creatorId)
            val voteRef = postRef.collection("votes").document(uid)

            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val postSnap = tx.get(postRef)
                val creatorSnap = tx.get(creatorRef)
                val voteSnap = tx.get(voteRef)

                val balance = userSnap.getLong("voteBalance") ?: 0L
                if (balance < 1L) {
                    throw InsufficientVotesException(requiredVotes = 1L, availableVotes = balance)
                }
                if (!postSnap.exists() || !creatorSnap.exists()) {
                    throw IllegalStateException("Post or creator no longer exists")
                }

                val paidBalance = (userSnap.getLong("paidVoteBalance") ?: 0L).coerceIn(0L, balance)
                val freeAvailable = (balance - paidBalance).coerceAtLeast(0L)
                val newPaidBalance = if (freeAvailable >= 1L) paidBalance else (balance - 1L).coerceAtLeast(0L)
                val existingCashable = (userSnap.getLong("referralCashableBalance") ?: 0L).coerceIn(0L, paidBalance)
                val newCashable = minOf(existingCashable, newPaidBalance)
                val totalCast = userSnap.getLong("totalVotesCast") ?: 0L
                val postVotes = postSnap.getLong("voteCount") ?: 0L
                val creatorVotes = creatorSnap.getLong("totalVotesReceived") ?: 0L

                tx.update(
                    userRef,
                    mapOf(
                        "voteBalance" to balance - 1L,
                        "paidVoteBalance" to newPaidBalance,
                        "referralCashableBalance" to newCashable,
                        "totalVotesCast" to totalCast + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    postRef,
                    mapOf(
                        "voteCount" to postVotes + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    creatorRef,
                    mapOf(
                        "totalVotesReceived" to creatorVotes + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                if (!voteSnap.exists()) {
                    tx.set(
                        voteRef,
                        mapOf(
                            "postId" to post.id,
                            "voterId" to uid,
                            "votesCast" to 1L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val existingVotes = voteSnap.getLong("votesCast") ?: 0L
                    tx.update(
                        voteRef,
                        mapOf(
                            "votesCast" to existingVotes + 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is InsufficientVotesException) {
                handleFirestoreError(e, OperationType.UPDATE, "posts/${post.id}", auth)
            }
            Result.failure(e)
        }
    }

    suspend fun buyVotePack(pack: VotePack): Result<Unit> {
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            val referralRef = db.collection("referrals").document(uid)
            val purchaseId = UUID.randomUUID().toString()
            val purchaseRef = userRef.collection("purchases").document(purchaseId)

            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val referralSnap = tx.get(referralRef)
                if (!userSnap.exists()) {
                    throw IllegalStateException("User profile is not initialized")
                }
                val balance = userSnap.getLong("voteBalance") ?: 0L
                val paidBalance = userSnap.getLong("paidVoteBalance") ?: 0L
                val purchased = userSnap.getLong("totalVotesPurchased") ?: 0L

                tx.update(
                    userRef,
                    mapOf(
                        "voteBalance" to balance + pack.votes,
                        "paidVoteBalance" to paidBalance + pack.votes,
                        "totalVotesPurchased" to purchased + pack.votes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    purchaseRef,
                    mapOf(
                        "userId" to uid,
                        "packId" to pack.id,
                        "packTitle" to pack.title,
                        "votesAdded" to pack.votes,
                        "priceLabel" to pack.priceLabel,
                        "purchasedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (referralSnap.exists() && referralSnap.getString("status") == ReferralRecord.STATUS_PENDING) {
                    tx.update(
                        referralRef,
                        mapOf(
                            "status" to ReferralRecord.STATUS_QUALIFIED,
                            "qualifyingPurchaseType" to ReferralRecord.PURCHASE_TYPE_VOTE_PACK,
                            "qualifyingPurchaseId" to purchaseId,
                            "qualifiedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/purchases", auth)
            Result.failure(e)
        }
    }

    suspend fun buyChatAccessPass(): Result<Timestamp> {
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)

            val newExpiry = db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                if (!userSnap.exists()) {
                    throw IllegalStateException("User profile is not initialized")
                }
                val balance = userSnap.getLong("voteBalance") ?: 0L
                val cost = UserProfile.CHAT_PASS_COST_VOTES
                if (balance < cost) {
                    throw InsufficientVotesException(requiredVotes = cost, availableVotes = balance)
                }
                val paidBalance = (userSnap.getLong("paidVoteBalance") ?: 0L).coerceIn(0L, balance)
                val freeAvailable = (balance - paidBalance).coerceAtLeast(0L)
                val newPaidBalance = if (freeAvailable >= cost) paidBalance else (balance - cost).coerceAtLeast(0L)
                val existingCashable = (userSnap.getLong("referralCashableBalance") ?: 0L).coerceIn(0L, paidBalance)
                val newCashable = minOf(existingCashable, newPaidBalance)

                val nowMillis = System.currentTimeMillis()
                val existingExpiryMillis = userSnap.getTimestamp("chatAccessExpiresAt")?.toDate()?.time ?: 0L
                val baseMillis = maxOf(nowMillis, existingExpiryMillis)
                val nextExpiry = Timestamp(Date(baseMillis + UserProfile.CHAT_PASS_MILLIS))

                tx.update(
                    userRef,
                    mapOf(
                        "voteBalance" to balance - cost,
                        "paidVoteBalance" to newPaidBalance,
                        "referralCashableBalance" to newCashable,
                        "chatAccessExpiresAt" to nextExpiry,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                nextExpiry
            }.await()

            Result.success(newExpiry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is InsufficientVotesException) {
                handleFirestoreError(e, OperationType.UPDATE, "users/chat_pass", auth)
            }
            Result.failure(e)
        }
    }

    suspend fun unlockCreatorGallery(creatorId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            if (uid == creatorId) return Result.success(Unit)

            val userRef = db.collection("users").document(uid)
            val creatorRef = db.collection("creators").document(creatorId)
            val unlockRef = userRef.collection("unlocks").document(creatorId)
            val payoutId = UUID.randomUUID().toString()
            val payoutRef = db.collection("creator_payouts").document(payoutId)

            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val creatorSnap = tx.get(creatorRef)
                val unlockSnap = tx.get(unlockRef)

                if (unlockSnap.exists()) {
                    return@runTransaction
                }
                if (!userSnap.exists() || !creatorSnap.exists()) {
                    throw IllegalStateException("User or creator profile not found")
                }

                val cost = creatorSnap.getLong("unlockCostVotes") ?: CreatorSummary.DEFAULT_UNLOCK_COST_VOTES
                val balance = userSnap.getLong("voteBalance") ?: 0L
                val paidBalance = userSnap.getLong("paidVoteBalance") ?: 0L
                if (paidBalance < cost || balance < cost) {
                    throw InsufficientVotesException(
                        requiredVotes = cost,
                        availableVotes = paidBalance,
                        paidTokensOnly = true
                    )
                }

                val payerName = (userSnap.getString("displayName") ?: "Viewer").trim().ifBlank { "Viewer" }.take(60)
                val shareCents = (cost * CreatorPayout.CENTS_PER_TOKEN * CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT) / 100L
                val newPaidBalance = paidBalance - cost
                val existingCashable = (userSnap.getLong("referralCashableBalance") ?: 0L).coerceIn(0L, paidBalance)
                val newCashable = minOf(existingCashable, newPaidBalance)

                tx.update(
                    userRef,
                    mapOf(
                        "voteBalance" to balance - cost,
                        "paidVoteBalance" to newPaidBalance,
                        "referralCashableBalance" to newCashable,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    unlockRef,
                    mapOf(
                        "userId" to uid,
                        "creatorId" to creatorId,
                        "votesSpent" to cost,
                        "unlockedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    payoutRef,
                    mapOf(
                        "payoutId" to payoutId,
                        "creatorId" to creatorId,
                        "payerId" to uid,
                        "payerName" to payerName,
                        "sourceType" to CreatorPayout.SOURCE_GALLERY_UNLOCK,
                        "sourceId" to creatorId,
                        "grossTokens" to cost,
                        "creatorSharePercent" to CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT,
                        "creatorShareCents" to shareCents,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is InsufficientVotesException) {
                handleFirestoreError(e, OperationType.CREATE, "users/unlocks/$creatorId", auth)
            }
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        age: Int? = null,
        sex: String? = null
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val safeName = displayName.trim().ifBlank { "Creator" }.take(60)
            val safeBio = bio.trim().take(240)
            val validAge = age?.takeIf { it in 18..110 }
            val validSex = sex?.takeIf {
                it in listOf(
                    UserProfile.SEX_FEMALE,
                    UserProfile.SEX_MALE,
                    UserProfile.SEX_NON_BINARY,
                    UserProfile.SEX_UNSPECIFIED
                )
            }

            val userUpdates = mutableMapOf<String, Any?>(
                "displayName" to safeName,
                "bio" to safeBio,
                "age" to validAge,
                "sex" to validSex,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val creatorUpdates = mutableMapOf<String, Any?>(
                "displayName" to safeName,
                "bio" to safeBio,
                "age" to validAge,
                "sex" to validSex,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.update(db.collection("users").document(uid), userUpdates)
            batch.update(db.collection("creators").document(uid), creatorUpdates)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/profile", auth)
            Result.failure(e)
        }
    }

    suspend fun deletePost(post: MediaPost): Result<Unit> {
        return try {
            requireUserId()
            require(post.id.isNotBlank()) { "Post ID must not be blank" }
            db.collection("posts").document(post.id).delete().await()
            social.deleteStoragePath(post.storagePath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "posts/${post.id}", auth)
            Result.failure(e)
        }
    }

    // --- CHAT ROOMS, INVISIBLE PRESENCE & MODERATION (BOUNCE / MUTE / BAN) ---

    fun observeMyChatModeration(userId: String): Flow<ChatModerationStatus?> = flow {
        val path = "chat_moderation/$userId"
        emitAll(
            db.collection("chat_moderation").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            ChatModerationStatus::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, userId)
                    emit(null)
                }
        )
    }

    fun observeAllChatModerations(): Flow<List<ChatModerationStatus>> = flow {
        val uid = requireUserId()
        val path = "chat_moderation"
        emitAll(
            db.collection("chat_moderation")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatModerationStatus::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeMyRoomBounce(roomId: String, userId: String): Flow<ChatBounce?> = flow {
        val path = "chat_rooms/$roomId/bounces/$userId"
        emitAll(
            db.collection("chat_rooms").document(roomId)
                .collection("bounces").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            ChatBounce::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, userId)
                    emit(null)
                }
        )
    }

    fun observeRoomMessages(roomId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "chat_rooms/$roomId/messages"
        emitAll(
            db.collection("chat_rooms").document(roomId).collection("messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeRoomState(roomId: String): Flow<ChatRoomState?> = flow {
        val uid = requireUserId()
        val path = "chat_rooms/$roomId"
        emitAll(
            db.collection("chat_rooms").document(roomId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        ChatRoomState::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeRoomVoiceChunks(roomId: String, enteredAtMillis: Long): Flow<List<ChatRoomVoiceChunk>> = flow {
        val uid = requireUserId()
        val path = "chat_rooms/$roomId/voice_chunks"
        val cutoffMillis = (enteredAtMillis - 3_000L).coerceAtLeast(0L)
        emitAll(
            db.collection("chat_rooms").document(roomId)
                .collection("voice_chunks")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatRoomVoiceChunk::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { chunk ->
                        val ts = chunk.createdAt?.toDate()?.time
                        ts == null || ts >= cutoffMillis
                    }.sortedWith(
                        compareBy<ChatRoomVoiceChunk> { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                            .thenBy { it.sequenceNumber }
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeRoomPresence(roomId: String, isAdmin: Boolean): Flow<List<ChatPresence>> = flow {
        val uid = requireUserId()
        val path = "chat_rooms/$roomId/presence"
        val baseCollection = db.collection("chat_rooms").document(roomId).collection("presence")
        val query = if (isAdmin) baseCollection else baseCollection.whereEqualTo("isInvisible", false)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatPresence::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).let { list ->
                        if (isAdmin) list else list.filter { !it.isInvisible }
                    }.sortedWith(
                        compareBy<ChatPresence> {
                            if (it.joinedAtMillis > 0L) it.joinedAtMillis
                            else it.joinedAt?.toDate()?.time ?: Long.MAX_VALUE
                        }.thenBy { it.userId }
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    private suspend fun findNextVisibleMonitorCandidate(
        roomId: String,
        excludingUid: String
    ): Pair<String, String>? {
        return runCatching {
            val snap = db.collection("chat_rooms").document(roomId)
                .collection("presence")
                .whereEqualTo("isInvisible", false)
                .get()
                .await()
            val next = snap.toObjects(
                ChatPresence::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { it.userId.isNotBlank() && it.userId != excludingUid && !it.isInvisible }
                .minWithOrNull(
                    compareBy<ChatPresence> {
                        if (it.joinedAtMillis > 0L) it.joinedAtMillis
                        else it.joinedAt?.toDate()?.time ?: Long.MAX_VALUE
                    }.thenBy { it.userId }
                )
            next?.let { it.userId to it.displayName.ifBlank { "Member" } }
        }.getOrNull()
    }

    suspend fun enterChatRoom(
        roomId: String,
        displayName: String,
        roleLabel: String,
        invisible: Boolean,
        initialVoiceMuted: Boolean = false
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("chat_rooms").document(roomId)
            val presenceRef = roomRef.collection("presence").document(uid)
            val safeName = displayName.trim().ifBlank { "Creator" }.take(60)
            val nextCandidateIfSteppingDown = if (invisible) {
                findNextVisibleMonitorCandidate(roomId, excludingUid = uid)
            } else {
                null
            }
            val nowMillis = System.currentTimeMillis()

            db.runTransaction { tx ->
                val roomSnap = tx.get(roomRef)
                val myPresenceSnap = tx.get(presenceRef)
                val existingMonitorUid = roomSnap.getString("monitorUid")
                val existingMonitorPresence = if (!existingMonitorUid.isNullOrBlank() && existingMonitorUid != uid) {
                    tx.get(roomRef.collection("presence").document(existingMonitorUid))
                } else {
                    null
                }
                val candidatePresence = if (invisible && existingMonitorUid == uid && nextCandidateIfSteppingDown != null) {
                    tx.get(roomRef.collection("presence").document(nextCandidateIfSteppingDown.first))
                } else {
                    null
                }

                val existingHandRaised = myPresenceSnap.getBoolean("handRaised") ?: false
                val existingHandRaisedAt = myPresenceSnap.getLong("handRaisedAt") ?: 0L
                val existingMuted = (myPresenceSnap.getBoolean("isVoiceMuted") ?: false) || initialVoiceMuted
                val existingMutedBy = myPresenceSnap.getString("voiceMutedByUid")
                    ?: if (initialVoiceMuted) "staff_mute" else null
                val existingJoinedAtMillis = myPresenceSnap.getLong("joinedAtMillis")?.takeIf { it > 0L } ?: nowMillis
                val existingJoinedAt = myPresenceSnap.getTimestamp("joinedAt")

                // Keep presence document even when invisible=true so Admins can see invisible Support monitors
                // while regular users and non-admin Support only query isInvisible == false.
                tx.set(
                    presenceRef,
                    mapOf(
                        "roomId" to roomId,
                        "userId" to uid,
                        "displayName" to safeName,
                        "role" to roleLabel,
                        "isInvisible" to invisible,
                        "handRaised" to existingHandRaised,
                        "handRaisedAt" to existingHandRaisedAt,
                        "isVoiceMuted" to existingMuted,
                        "voiceMutedByUid" to existingMutedBy,
                        "isSpeaking" to false,
                        "joinedAtMillis" to existingJoinedAtMillis,
                        "joinedAt" to (existingJoinedAt ?: FieldValue.serverTimestamp()),
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                )

                if (!invisible) {
                    val isVacant = !roomSnap.exists() ||
                        existingMonitorUid.isNullOrBlank() ||
                        existingMonitorUid == uid ||
                        existingMonitorPresence == null ||
                        !existingMonitorPresence.exists() ||
                        (existingMonitorPresence.getBoolean("isInvisible") == true)
                    if (isVacant) {
                        tx.set(
                            roomRef,
                            mapOf(
                                "roomId" to roomId,
                                "monitorUid" to uid,
                                "monitorName" to safeName,
                                "monitorAssignedAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                } else if (roomSnap.exists() && existingMonitorUid == uid) {
                    val validNext = if (nextCandidateIfSteppingDown != null &&
                        candidatePresence?.exists() == true &&
                        candidatePresence.getBoolean("isInvisible") != true
                    ) {
                        nextCandidateIfSteppingDown
                    } else {
                        null
                    }
                    tx.set(
                        roomRef,
                        mapOf(
                            "roomId" to roomId,
                            "monitorUid" to validNext?.first,
                            "monitorName" to validNext?.second,
                            "monitorAssignedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chat_rooms/$roomId/presence", auth)
            Result.failure(e)
        }
    }

    suspend fun claimRoomMonitorIfVacant(roomId: String, displayName: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("chat_rooms").document(roomId)
            val presenceRef = roomRef.collection("presence").document(uid)
            val safeName = displayName.trim().ifBlank { "Creator" }.take(60)
            db.runTransaction { tx ->
                val roomSnap = tx.get(roomRef)
                val myPresence = tx.get(presenceRef)
                if (!myPresence.exists() || myPresence.getBoolean("isInvisible") == true) return@runTransaction
                val existingMonitorUid = roomSnap.getString("monitorUid")
                val oldPresence = if (!existingMonitorUid.isNullOrBlank() && existingMonitorUid != uid) {
                    tx.get(roomRef.collection("presence").document(existingMonitorUid))
                } else {
                    null
                }
                val isVacant = !roomSnap.exists() ||
                    existingMonitorUid.isNullOrBlank() ||
                    (existingMonitorUid != uid && (oldPresence == null || !oldPresence.exists() || oldPresence.getBoolean("isInvisible") == true))
                if (isVacant) {
                    tx.set(
                        roomRef,
                        mapOf(
                            "roomId" to roomId,
                            "monitorUid" to uid,
                            "monitorName" to safeName,
                            "monitorAssignedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveChatRoom(roomId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("chat_rooms").document(roomId)
            val presenceRef = roomRef.collection("presence").document(uid)
            val nextCandidate = findNextVisibleMonitorCandidate(roomId, excludingUid = uid)

            db.runTransaction { tx ->
                val roomSnap = tx.get(roomRef)
                val nextPresenceSnap = if (nextCandidate != null) {
                    tx.get(roomRef.collection("presence").document(nextCandidate.first))
                } else {
                    null
                }

                tx.delete(presenceRef)

                if (roomSnap.exists() && roomSnap.getString("monitorUid") == uid) {
                    val validNext = if (nextCandidate != null &&
                        nextPresenceSnap?.exists() == true &&
                        nextPresenceSnap.getBoolean("isInvisible") != true
                    ) {
                        nextCandidate
                    } else {
                        null
                    }
                    tx.set(
                        roomRef,
                        mapOf(
                            "roomId" to roomId,
                            "monitorUid" to validNext?.first,
                            "monitorName" to validNext?.second,
                            "monitorAssignedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setRoomHandRaised(roomId: String, raised: Boolean): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("chat_rooms").document(roomId)
                .collection("presence").document(uid)
                .update(
                    mapOf(
                        "handRaised" to raised,
                        "handRaisedAt" to if (raised) System.currentTimeMillis() else 0L,
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chat_rooms/$roomId/presence/$currentUidOrEmpty", auth)
            Result.failure(e)
        }
    }

    suspend fun setRoomSpeaking(roomId: String, speaking: Boolean): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("chat_rooms").document(roomId)
                .collection("presence").document(uid)
                .update(
                    mapOf(
                        "isSpeaking" to speaking,
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setRoomMemberVoiceMuted(
        roomId: String,
        targetUserId: String,
        muted: Boolean
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(targetUserId != uid) { "Cannot mute yourself via monitor action" }
            val updates = mutableMapOf<String, Any?>(
                "isVoiceMuted" to muted,
                "voiceMutedByUid" to if (muted) uid else null,
                "lastActiveAt" to FieldValue.serverTimestamp()
            )
            if (muted) {
                updates["isSpeaking"] = false
                updates["handRaised"] = false
                updates["handRaisedAt"] = 0L
            }
            db.collection("chat_rooms").document(roomId)
                .collection("presence").document(targetUserId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chat_rooms/$roomId/presence/$targetUserId", auth)
            Result.failure(e)
        }
    }

    suspend fun lowerRoomMemberHand(roomId: String, targetUserId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("chat_rooms").document(roomId)
                .collection("presence").document(targetUserId)
                .update(
                    mapOf(
                        "handRaised" to false,
                        "handRaisedAt" to 0L,
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chat_rooms/$roomId/presence/$targetUserId", auth)
            Result.failure(e)
        }
    }

    suspend fun sendRoomVoiceChunk(
        roomId: String,
        senderName: String,
        audioBase64: String,
        durationMs: Long,
        sequenceNumber: Long
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val chunkId = UUID.randomUUID().toString()
            val safeName = senderName.trim().ifBlank { "Creator" }.take(60)
            db.collection("chat_rooms").document(roomId)
                .collection("voice_chunks").document(chunkId)
                .set(
                    mapOf(
                        "chunkId" to chunkId,
                        "roomId" to roomId,
                        "senderId" to uid,
                        "senderName" to safeName,
                        "sampleRate" to 8000,
                        "audioBase64" to audioBase64,
                        "durationMs" to durationMs.coerceIn(50L, 5000L),
                        "sequenceNumber" to sequenceNumber,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val currentUidOrEmpty: String
        get() = auth.currentUser?.uid.orEmpty()

    suspend fun sendChatMessage(
        roomId: String,
        senderName: String,
        senderRole: String,
        text: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val safeText = text.trim().take(500)
            require(safeText.isNotBlank()) { "Message cannot be empty" }
            val safeName = senderName.trim().ifBlank { "Creator" }.take(60)
            val msgId = UUID.randomUUID().toString()
            db.collection("chat_rooms").document(roomId)
                .collection("messages").document(msgId)
                .set(
                    mapOf(
                        "roomId" to roomId,
                        "senderId" to uid,
                        "senderName" to safeName,
                        "senderRole" to senderRole,
                        "text" to safeText,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chat_rooms/$roomId/messages", auth)
            Result.failure(e)
        }
    }

    suspend fun deleteChatMessage(roomId: String, messageId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("chat_rooms").document(roomId)
                .collection("messages").document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "chat_rooms/$roomId/messages/$messageId", auth)
            Result.failure(e)
        }
    }

    suspend fun bounceUserFromRoom(
        roomId: String,
        targetUserId: String,
        reason: String = "Bounced by room moderator",
        durationMinutes: Long = 5L
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val safeReason = reason.trim().ifBlank { "Bounced by room moderator" }.take(160)
            val until = Timestamp(Date(System.currentTimeMillis() + durationMinutes * 60_000L))
            val roomRef = db.collection("chat_rooms").document(roomId)
            val nextCandidate = findNextVisibleMonitorCandidate(roomId, excludingUid = targetUserId)

            db.runTransaction { tx ->
                val roomSnap = tx.get(roomRef)
                val nextPresenceSnap = if (nextCandidate != null) {
                    tx.get(roomRef.collection("presence").document(nextCandidate.first))
                } else {
                    null
                }

                tx.set(
                    roomRef.collection("bounces").document(targetUserId),
                    mapOf(
                        "roomId" to roomId,
                        "userId" to targetUserId,
                        "reason" to safeReason,
                        "bouncedByUid" to uid,
                        "bouncedUntil" to until,
                        "bouncedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.delete(roomRef.collection("presence").document(targetUserId))

                if (roomSnap.exists() && roomSnap.getString("monitorUid") == targetUserId) {
                    val validNext = if (nextCandidate != null &&
                        nextPresenceSnap?.exists() == true &&
                        nextPresenceSnap.getBoolean("isInvisible") != true
                    ) {
                        nextCandidate
                    } else {
                        null
                    }
                    tx.set(
                        roomRef,
                        mapOf(
                            "roomId" to roomId,
                            "monitorUid" to validNext?.first,
                            "monitorName" to validNext?.second,
                            "monitorAssignedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chat_rooms/$roomId/bounces/$targetUserId", auth)
            Result.failure(e)
        }
    }

    suspend fun bounceUserFromAllRooms(
        targetUserId: String,
        durationMinutes: Long = 15L,
        reason: String = "Bounced from chat rooms by staff"
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val safeReason = reason.trim().ifBlank { "Bounced from chat rooms by staff" }.take(160)
            val until = Timestamp(Date(System.currentTimeMillis() + durationMinutes * 60_000L))
            val batch = db.batch()
            ChatRoomCatalog.rooms.forEach { room ->
                val roomRef = db.collection("chat_rooms").document(room.id)
                batch.set(
                    roomRef.collection("bounces").document(targetUserId),
                    mapOf(
                        "roomId" to room.id,
                        "userId" to targetUserId,
                        "reason" to safeReason,
                        "bouncedByUid" to uid,
                        "bouncedUntil" to until,
                        "bouncedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.delete(roomRef.collection("presence").document(targetUserId))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chat_rooms/bounces/$targetUserId", auth)
            Result.failure(e)
        }
    }

    private suspend fun updateModerationRecord(
        targetUserId: String,
        targetDisplayName: String?,
        mutate: (MutableMap<String, Any?>, DocumentSnapshot) -> Unit
    ) {
        val uid = requireUserId()
        val callerEmail = auth.currentUser?.email?.trim()?.lowercase(Locale.ROOT)
        val modRef = db.collection("chat_moderation").document(targetUserId)
        val existingSnap = modRef.get().await()
        val safeName = (targetDisplayName ?: existingSnap.getString("displayName") ?: "User")
            .trim().ifBlank { "User" }.take(60)
        val payload = mutableMapOf<String, Any?>(
            "userId" to targetUserId,
            "displayName" to safeName,
            "mutedUntil" to existingSnap.getTimestamp("mutedUntil"),
            "muteReason" to existingSnap.getString("muteReason"),
            "banned" to (existingSnap.getBoolean("banned") ?: false),
            "banReason" to existingSnap.getString("banReason"),
            "suspendedUntil" to existingSnap.getTimestamp("suspendedUntil"),
            "suspendReason" to existingSnap.getString("suspendReason"),
            "accountBanned" to (existingSnap.getBoolean("accountBanned") ?: false),
            "accountBanReason" to existingSnap.getString("accountBanReason"),
            "moderatedByUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (callerEmail != null) {
            payload["moderatedByEmail"] = callerEmail
        }
        mutate(payload, existingSnap)

        val now = System.currentTimeMillis()
        val stillMuted = ((payload["mutedUntil"] as? Timestamp)?.toDate()?.time ?: 0L) > now
        val chatBanned = (payload["banned"] as? Boolean) == true
        val stillSuspended = ((payload["suspendedUntil"] as? Timestamp)?.toDate()?.time ?: 0L) > now
        val accBanned = (payload["accountBanned"] as? Boolean) == true

        if (!stillMuted && !chatBanned && !stillSuspended && !accBanned) {
            if (existingSnap.exists()) {
                modRef.delete().await()
            }
        } else {
            modRef.set(payload).await()
        }
    }

    suspend fun muteUserInChat(
        targetUserId: String,
        targetDisplayName: String,
        durationMinutes: Long,
        reason: String = "Muted by staff"
    ): Result<Unit> {
        return try {
            val mutedUntil = Timestamp(Date(System.currentTimeMillis() + durationMinutes * 60_000L))
            updateModerationRecord(targetUserId, targetDisplayName) { payload, _ ->
                payload["mutedUntil"] = mutedUntil
                payload["muteReason"] = reason.trim().ifBlank { "Muted by staff" }.take(160)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chat_moderation/$targetUserId", auth)
            Result.failure(e)
        }
    }

    suspend fun unmuteUserInChat(targetUserId: String): Result<Unit> {
        return try {
            updateModerationRecord(targetUserId, null) { payload, _ ->
                payload["mutedUntil"] = null
                payload["muteReason"] = null
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chat_moderation/$targetUserId", auth)
            Result.failure(e)
        }
    }

    suspend fun banUserFromChat(
        targetUserId: String,
        targetDisplayName: String,
        reason: String = "Banned from chat by staff"
    ): Result<Unit> {
        return try {
            updateModerationRecord(targetUserId, targetDisplayName) { payload, _ ->
                payload["banned"] = true
                payload["banReason"] = reason.trim().ifBlank { "Banned from chat by staff" }.take(160)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chat_moderation/$targetUserId", auth)
            Result.failure(e)
        }
    }

    suspend fun unbanUserFromChat(targetUserId: String): Result<Unit> {
        return try {
            updateModerationRecord(targetUserId, null) { payload, _ ->
                payload["banned"] = false
                payload["banReason"] = null
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chat_moderation/$targetUserId", auth)
            Result.failure(e)
        }
    }

    suspend fun suspendUserAccount(
        targetUserId: String,
        targetDisplayName: String,
        durationHours: Long,
        reason: String = "Suspended by moderation team"
    ): Result<Unit> {
        return try {
            val until = Timestamp(Date(System.currentTimeMillis() + durationHours * 3_600_000L))
            updateModerationRecord(targetUserId, targetDisplayName) { payload, _ ->
                payload["suspendedUntil"] = until
                payload["suspendReason"] = reason.trim().ifBlank { "Suspended by moderation team" }.take(160)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chat_moderation/$targetUserId/suspend", auth)
            Result.failure(e)
        }
    }

    suspend fun unsuspendUserAccount(targetUserId: String): Result<Unit> {
        return try {
            updateModerationRecord(targetUserId, null) { payload, _ ->
                payload["suspendedUntil"] = null
                payload["suspendReason"] = null
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chat_moderation/$targetUserId/unsuspend", auth)
            Result.failure(e)
        }
    }

    suspend fun banUserAccount(
        targetUserId: String,
        targetDisplayName: String,
        reason: String = "Account banned for policy violations"
    ): Result<Unit> {
        return try {
            updateModerationRecord(targetUserId, targetDisplayName) { payload, _ ->
                payload["accountBanned"] = true
                payload["accountBanReason"] = reason.trim().ifBlank { "Account banned for policy violations" }.take(160)
                payload["banned"] = true
                if (payload["banReason"] == null) {
                    payload["banReason"] = payload["accountBanReason"]
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chat_moderation/$targetUserId/accountBan", auth)
            Result.failure(e)
        }
    }

    suspend fun unbanUserAccount(targetUserId: String): Result<Unit> {
        return try {
            updateModerationRecord(targetUserId, null) { payload, _ ->
                payload["accountBanned"] = false
                payload["accountBanReason"] = null
                payload["banned"] = false
                payload["banReason"] = null
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chat_moderation/$targetUserId/accountUnban", auth)
            Result.failure(e)
        }
    }

    // --- ONLINE PRESENCE, FRIENDS, PRIVATE DMs & PRIVATE GROUP CHATS (5 TOKENS / WEEK) ---

    suspend fun setUserOnlineStatus(isOnline: Boolean): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.success(Unit)
            val creatorRef = db.collection("creators").document(uid)
            val snap = creatorRef.get().await()
            if (snap.exists()) {
                creatorRef.update(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Presence heartbeat failure should never crash or disrupt user actions
            Result.failure(e)
        }
    }

    suspend fun buyPrivateChatPass(): Result<Timestamp> {
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)

            val newExpiry = db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                if (!userSnap.exists()) {
                    throw IllegalStateException("User profile is not initialized")
                }
                val balance = userSnap.getLong("voteBalance") ?: 0L
                val cost = UserProfile.PRIVATE_CHAT_PASS_COST_TOKENS
                if (balance < cost) {
                    throw InsufficientVotesException(requiredVotes = cost, availableVotes = balance)
                }
                val paidBalance = (userSnap.getLong("paidVoteBalance") ?: 0L).coerceIn(0L, balance)
                val freeAvailable = (balance - paidBalance).coerceAtLeast(0L)
                val newPaidBalance = if (freeAvailable >= cost) paidBalance else (balance - cost).coerceAtLeast(0L)
                val existingCashable = (userSnap.getLong("referralCashableBalance") ?: 0L).coerceIn(0L, paidBalance)
                val newCashable = minOf(existingCashable, newPaidBalance)

                val nowMillis = System.currentTimeMillis()
                val existingExpiryMillis = userSnap.getTimestamp("privateChatAccessExpiresAt")?.toDate()?.time ?: 0L
                val baseMillis = maxOf(nowMillis, existingExpiryMillis)
                val nextExpiry = Timestamp(Date(baseMillis + UserProfile.PRIVATE_CHAT_PASS_MILLIS))

                tx.update(
                    userRef,
                    mapOf(
                        "voteBalance" to balance - cost,
                        "paidVoteBalance" to newPaidBalance,
                        "referralCashableBalance" to newCashable,
                        "privateChatAccessExpiresAt" to nextExpiry,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                nextExpiry
            }.await()

            Result.success(newExpiry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is InsufficientVotesException) {
                handleFirestoreError(e, OperationType.UPDATE, "users/private_chat_pass", auth)
            }
            Result.failure(e)
        }
    }

    private fun ensureFreePrivateMessagingEnabled() {
        // Private DMs, snaps, and group chats are free for all signed-in users.
    }

    fun observeFriendRelationships(userId: String): Flow<List<FriendRelationship>> = flow {
        val path = "friend_requests"
        emitAll(
            db.collection("friend_requests")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FriendRelationship::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun sendFriendRequest(
        targetUserId: String,
        targetName: String,
        myName: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(targetUserId.isNotBlank() && targetUserId != uid) { "Invalid friend target" }
            val pairId = FriendRelationship.pairIdFor(uid, targetUserId)
            val docRef = db.collection("friend_requests").document(pairId)
            val existingSnap = docRef.get().await()

            val safeMyName = myName.trim().ifBlank { "Creator" }.take(60)
            val safeTargetName = targetName.trim().ifBlank { "Creator" }.take(60)

            if (!existingSnap.exists()) {
                docRef.set(
                    mapOf(
                        "pairId" to pairId,
                        "senderId" to uid,
                        "senderName" to safeMyName,
                        "receiverId" to targetUserId,
                        "receiverName" to safeTargetName,
                        "participantIds" to listOf(uid, targetUserId).sorted(),
                        "status" to FriendRelationship.STATUS_PENDING,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val status = existingSnap.getString("status")
                if (status == FriendRelationship.STATUS_DECLINED) {
                    docRef.update(
                        mapOf(
                            "senderId" to uid,
                            "senderName" to safeMyName,
                            "receiverId" to targetUserId,
                            "receiverName" to safeTargetName,
                            "status" to FriendRelationship.STATUS_PENDING,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "friend_requests", auth)
            Result.failure(e)
        }
    }

    suspend fun respondToFriendRequest(pairId: String, accept: Boolean): Result<Unit> {
        return try {
            requireUserId()
            val nextStatus = if (accept) {
                FriendRelationship.STATUS_ACCEPTED
            } else {
                FriendRelationship.STATUS_DECLINED
            }
            db.collection("friend_requests").document(pairId).update(
                mapOf(
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "friend_requests/$pairId", auth)
            Result.failure(e)
        }
    }

    suspend fun removeFriendRelationship(pairId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("friend_requests").document(pairId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "friend_requests/$pairId", auth)
            Result.failure(e)
        }
    }

    fun observeDirectChatThreads(userId: String): Flow<List<DirectChatThread>> = flow {
        val path = "direct_chats"
        emitAll(
            db.collection("direct_chats")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DirectChatThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeDirectMessages(threadId: String): Flow<List<DirectMessage>> = flow {
        val uid = requireUserId()
        val path = "direct_chats/$threadId/messages"
        emitAll(
            db.collection("direct_chats").document(threadId).collection("messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DirectMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun openOrCreateDirectThread(
        otherUserId: String,
        otherUserName: String,
        myName: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            require(otherUserId.isNotBlank() && otherUserId != uid) { "Invalid recipient" }
            val threadId = DirectChatThread.threadIdFor(uid, otherUserId)
            val threadRef = db.collection("direct_chats").document(threadId)
            val existingSnap = threadRef.get().await()
            if (existingSnap.exists()) {
                return Result.success(threadId)
            }

            ensureFreePrivateMessagingEnabled()

            val safeMyName = myName.trim().ifBlank { "Creator" }.take(60)
            val safeOtherName = otherUserName.trim().ifBlank { "Creator" }.take(60)
            val participantIds = listOf(uid, otherUserId).sorted()
            val participantNames = mapOf(
                uid to safeMyName,
                otherUserId to safeOtherName
            )

            threadRef.set(
                mapOf(
                    "threadId" to threadId,
                    "participantIds" to participantIds,
                    "participantNames" to participantNames,
                    "lastMessageText" to "",
                    "lastMessageSenderId" to null,
                    "lastMessageAt" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(threadId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is PrivateChatPassRequiredException) {
                handleFirestoreError(e, OperationType.WRITE, "direct_chats", auth)
            }
            Result.failure(e)
        }
    }

    suspend fun sendDirectMessage(
        threadId: String,
        senderName: String,
        text: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            ensureFreePrivateMessagingEnabled()

            val safeText = text.trim().take(500)
            require(safeText.isNotBlank()) { "Message cannot be empty" }
            val safeSenderName = senderName.trim().ifBlank { "Creator" }.take(60)
            val msgId = UUID.randomUUID().toString()

            val threadRef = db.collection("direct_chats").document(threadId)
            val msgRef = threadRef.collection("messages").document(msgId)
            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "threadId" to threadId,
                    "senderId" to uid,
                    "senderName" to safeSenderName,
                    "text" to safeText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                threadRef,
                mapOf(
                    "lastMessageText" to safeText,
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
            if (e !is PrivateChatPassRequiredException) {
                handleFirestoreError(e, OperationType.CREATE, "direct_chats/$threadId/messages", auth)
            }
            Result.failure(e)
        }
    }

    fun observeGroupChats(userId: String): Flow<List<PrivateGroupChat>> = flow {
        val path = "group_chats"
        emitAll(
            db.collection("group_chats")
                .whereArrayContains("memberIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PrivateGroupChat::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeGroupMessages(groupId: String): Flow<List<GroupChatMessage>> = flow {
        val uid = requireUserId()
        val path = "group_chats/$groupId/messages"
        emitAll(
            db.collection("group_chats").document(groupId).collection("messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GroupChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createPrivateGroupChat(
        name: String,
        description: String,
        privacyMode: String,
        allowMemberInvites: Boolean,
        invitedMembers: Map<String, String>,
        myName: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            ensureFreePrivateMessagingEnabled()

            val safeName = name.trim().take(60)
            require(safeName.isNotBlank()) { "Group name cannot be empty" }
            val safeDesc = description.trim().take(200)
            val validPrivacy = if (privacyMode == PrivateGroupChat.PRIVACY_FRIENDS_CAN_INVITE) {
                PrivateGroupChat.PRIVACY_FRIENDS_CAN_INVITE
            } else {
                PrivateGroupChat.PRIVACY_INVITE_ONLY
            }

            val groupId = UUID.randomUUID().toString()
            val memberNames = mutableMapOf<String, String>()
            memberNames[uid] = myName.trim().ifBlank { "Creator" }.take(60)
            invitedMembers.forEach { (memberId, memberName) ->
                if (memberId.isNotBlank() && memberId != uid && memberNames.size < 50) {
                    memberNames[memberId] = memberName.trim().ifBlank { "Creator" }.take(60)
                }
            }
            val memberIds = memberNames.keys.toList()

            db.collection("group_chats").document(groupId).set(
                mapOf(
                    "groupId" to groupId,
                    "name" to safeName,
                    "description" to safeDesc,
                    "ownerId" to uid,
                    "memberIds" to memberIds,
                    "memberNames" to memberNames,
                    "privacyMode" to validPrivacy,
                    "allowMemberInvites" to allowMemberInvites,
                    "lastMessageText" to "",
                    "lastMessageAt" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(groupId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is PrivateChatPassRequiredException) {
                handleFirestoreError(e, OperationType.CREATE, "group_chats", auth)
            }
            Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(
        groupId: String,
        senderName: String,
        text: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            ensureFreePrivateMessagingEnabled()

            val safeText = text.trim().take(500)
            require(safeText.isNotBlank()) { "Message cannot be empty" }
            val safeSenderName = senderName.trim().ifBlank { "Creator" }.take(60)
            val msgId = UUID.randomUUID().toString()

            val groupRef = db.collection("group_chats").document(groupId)
            val msgRef = groupRef.collection("messages").document(msgId)
            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "groupId" to groupId,
                    "senderId" to uid,
                    "senderName" to safeSenderName,
                    "text" to safeText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                groupRef,
                mapOf(
                    "lastMessageText" to "$safeSenderName: $safeText".take(500),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is PrivateChatPassRequiredException) {
                handleFirestoreError(e, OperationType.CREATE, "group_chats/$groupId/messages", auth)
            }
            Result.failure(e)
        }
    }

    suspend fun updateGroupChatSettings(
        groupId: String,
        name: String,
        description: String,
        privacyMode: String,
        allowMemberInvites: Boolean
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            ensureFreePrivateMessagingEnabled()

            val safeName = name.trim().take(60)
            require(safeName.isNotBlank()) { "Group name cannot be empty" }
            val safeDesc = description.trim().take(200)
            val validPrivacy = if (privacyMode == PrivateGroupChat.PRIVACY_FRIENDS_CAN_INVITE) {
                PrivateGroupChat.PRIVACY_FRIENDS_CAN_INVITE
            } else {
                PrivateGroupChat.PRIVACY_INVITE_ONLY
            }

            db.collection("group_chats").document(groupId).update(
                mapOf(
                    "name" to safeName,
                    "description" to safeDesc,
                    "privacyMode" to validPrivacy,
                    "allowMemberInvites" to allowMemberInvites,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is PrivateChatPassRequiredException) {
                handleFirestoreError(e, OperationType.UPDATE, "group_chats/$groupId", auth)
            }
            Result.failure(e)
        }
    }

    suspend fun addMembersToGroupChat(
        group: PrivateGroupChat,
        newMembers: Map<String, String>
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            ensureFreePrivateMessagingEnabled()

            val updatedNames = group.memberNames.toMutableMap()
            val updatedIds = group.memberIds.toMutableList()
            newMembers.forEach { (memberId, memberName) ->
                if (memberId.isNotBlank() && !updatedIds.contains(memberId) && updatedIds.size < 50) {
                    updatedIds.add(memberId)
                    updatedNames[memberId] = memberName.trim().ifBlank { "Creator" }.take(60)
                }
            }

            db.collection("group_chats").document(group.groupId).update(
                mapOf(
                    "memberIds" to updatedIds,
                    "memberNames" to updatedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is PrivateChatPassRequiredException) {
                handleFirestoreError(e, OperationType.UPDATE, "group_chats/${group.groupId}/members", auth)
            }
            Result.failure(e)
        }
    }

    suspend fun removeOrLeaveGroupChat(
        group: PrivateGroupChat,
        targetUserId: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(targetUserId != group.ownerId) { "Owner cannot leave without deleting the group" }
            val isOwnerRemoving = uid == group.ownerId
            val isSelfLeaving = uid == targetUserId
            require(isOwnerRemoving || isSelfLeaving) { "Not permitted to remove member" }

            val updatedIds = group.memberIds.filter { it != targetUserId }
            val updatedNames = group.memberNames.filterKeys { it != targetUserId }

            db.collection("group_chats").document(group.groupId).update(
                mapOf(
                    "memberIds" to updatedIds,
                    "memberNames" to updatedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is PrivateChatPassRequiredException) {
                handleFirestoreError(e, OperationType.UPDATE, "group_chats/${group.groupId}/leave", auth)
            }
            Result.failure(e)
        }
    }

    suspend fun deleteGroupChat(groupId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("group_chats").document(groupId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "group_chats/$groupId", auth)
            Result.failure(e)
        }
    }

    // --- PHONE TOKENS, 70% CREATOR REVENUE SHARE & SCHEDULED MIC-TO-MIC CONVERSATIONS ---

    fun observePhonePurchaseHistory(userId: String): Flow<List<PhoneTokenPurchase>> = flow {
        val path = "users/$userId/phone_purchases"
        emitAll(
            db.collection("users").document(userId).collection("phone_purchases")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PhoneTokenPurchase::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.purchasedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeCreatorPayouts(creatorId: String): Flow<List<CreatorPayout>> = flow {
        val path = "creator_payouts"
        emitAll(
            db.collection("creator_payouts")
                .whereEqualTo("creatorId", creatorId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CreatorPayout::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, creatorId)
                    throw error
                }
        )
    }

    suspend fun buyPhoneTokenPack(pack: PhoneTokenPack): Result<Unit> {
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            val referralRef = db.collection("referrals").document(uid)
            val purchaseId = UUID.randomUUID().toString()
            val purchaseRef = userRef.collection("phone_purchases").document(purchaseId)

            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val referralSnap = tx.get(referralRef)
                if (!userSnap.exists()) {
                    throw IllegalStateException("User profile is not initialized")
                }
                val balance = userSnap.getLong("phoneTokenBalance") ?: 0L
                val purchased = userSnap.getLong("totalPhoneTokensPurchased") ?: 0L

                tx.update(
                    userRef,
                    mapOf(
                        "phoneTokenBalance" to balance + pack.tokens,
                        "totalPhoneTokensPurchased" to purchased + pack.tokens,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    purchaseRef,
                    mapOf(
                        "userId" to uid,
                        "packId" to pack.id,
                        "packTitle" to pack.title,
                        "tokensAdded" to pack.tokens,
                        "priceLabel" to pack.priceLabel,
                        "purchasedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (referralSnap.exists() && referralSnap.getString("status") == ReferralRecord.STATUS_PENDING) {
                    tx.update(
                        referralRef,
                        mapOf(
                            "status" to ReferralRecord.STATUS_QUALIFIED,
                            "qualifyingPurchaseType" to ReferralRecord.PURCHASE_TYPE_PHONE_PACK,
                            "qualifyingPurchaseId" to purchaseId,
                            "qualifiedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/phone_purchases", auth)
            Result.failure(e)
        }
    }

    fun observeMicSessions(userId: String): Flow<List<MicSession>> = flow {
        val path = "mic_sessions"
        emitAll(
            db.collection("mic_sessions")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MicSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.scheduledForMillis }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeMicAudioChunks(sessionId: String): Flow<List<MicAudioChunk>> = flow {
        val uid = requireUserId()
        val path = "mic_sessions/$sessionId/audio_chunks"
        emitAll(
            db.collection("mic_sessions").document(sessionId).collection("audio_chunks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MicAudioChunk::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.sequenceNumber }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun scheduleMicSession(
        creatorId: String,
        creatorName: String,
        requesterName: String,
        topic: String,
        scheduledForMillis: Long,
        durationMinutes: Long
    ): Result<String> {
        return try {
            val uid = requireUserId()
            require(creatorId.isNotBlank() && creatorId != uid) { "Cannot schedule a mic session with yourself" }
            require(durationMinutes in 1L..60L) { "Duration must be between 1 and 60 minutes" }

            val sessionId = UUID.randomUUID().toString()
            val sessionRef = db.collection("mic_sessions").document(sessionId)

            val safeRequester = requesterName.trim().ifBlank { "Creator" }.take(60)
            val safeCreator = creatorName.trim().ifBlank { "Creator" }.take(60)
            val safeTopic = topic.trim().ifBlank { "1-on-1 Mic Conversation" }.take(160)

            sessionRef.set(
                mapOf(
                    "sessionId" to sessionId,
                    "requesterId" to uid,
                    "requesterName" to safeRequester,
                    "creatorId" to creatorId,
                    "creatorName" to safeCreator,
                    "participantIds" to listOf(uid, creatorId),
                    "topic" to safeTopic,
                    "scheduledForMillis" to scheduledForMillis,
                    "durationMinutes" to durationMinutes,
                    "tokensPerMinute" to 0L,
                    "totalTokensCost" to 0L,
                    "creatorShareTokens" to 0L,
                    "creatorShareCents" to 0L,
                    "status" to MicSession.STATUS_PENDING,
                    "escrowStatus" to MicSession.ESCROW_FREE,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "mic_sessions", auth)
            Result.failure(e)
        }
    }

    suspend fun respondToMicSession(session: MicSession, accept: Boolean): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(session.creatorId == uid) { "Only the invited creator can respond" }
            val sessionRef = db.collection("mic_sessions").document(session.sessionId)

            val nextStatus = if (accept) MicSession.STATUS_ACCEPTED else MicSession.STATUS_DECLINED
            sessionRef.update(
                mapOf(
                    "status" to nextStatus,
                    "escrowStatus" to MicSession.ESCROW_FREE,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "mic_sessions/${session.sessionId}", auth)
            Result.failure(e)
        }
    }

    suspend fun cancelMicSession(sessionId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("mic_sessions").document(sessionId).update(
                mapOf(
                    "status" to MicSession.STATUS_CANCELLED,
                    "escrowStatus" to MicSession.ESCROW_FREE,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "mic_sessions/$sessionId", auth)
            Result.failure(e)
        }
    }

    suspend fun claimMicSessionRefund(session: MicSession): Result<Unit> {
        return Result.success(Unit)
    }

    suspend fun updateMicSessionCallStatus(sessionId: String, newStatus: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("mic_sessions").document(sessionId).update(
                mapOf(
                    "status" to newStatus,
                    "escrowStatus" to MicSession.ESCROW_FREE,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "mic_sessions/$sessionId/status", auth)
            Result.failure(e)
        }
    }

    suspend fun sendMicAudioChunk(
        sessionId: String,
        senderName: String,
        audioBase64: String,
        durationMs: Long,
        sequenceNumber: Long
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val chunkId = UUID.randomUUID().toString()
            val safeName = senderName.trim().ifBlank { "Creator" }.take(60)
            db.collection("mic_sessions").document(sessionId)
                .collection("audio_chunks").document(chunkId)
                .set(
                    mapOf(
                        "chunkId" to chunkId,
                        "sessionId" to sessionId,
                        "senderId" to uid,
                        "senderName" to safeName,
                        "sampleRate" to 8000,
                        "audioBase64" to audioBase64,
                        "durationMs" to durationMs.coerceIn(50L, 5000L),
                        "sequenceNumber" to sequenceNumber,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- VIDEO TOKENS (100 TOKENS/MIN) & PRIVATE CHAT 1:1 VIDEO SESSIONS ---

    fun observeVideoPurchaseHistory(userId: String): Flow<List<VideoTokenPurchase>> = flow {
        val path = "users/$userId/video_purchases"
        emitAll(
            db.collection("users").document(userId).collection("video_purchases")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoTokenPurchase::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.purchasedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun buyVideoTokenPack(pack: VideoTokenPack): Result<Unit> {
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            val referralRef = db.collection("referrals").document(uid)
            val purchaseId = UUID.randomUUID().toString()
            val purchaseRef = userRef.collection("video_purchases").document(purchaseId)

            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val referralSnap = tx.get(referralRef)
                if (!userSnap.exists()) {
                    throw IllegalStateException("User profile is not initialized")
                }
                val balance = userSnap.getLong("videoTokenBalance") ?: 0L
                val purchased = userSnap.getLong("totalVideoTokensPurchased") ?: 0L

                tx.update(
                    userRef,
                    mapOf(
                        "videoTokenBalance" to balance + pack.tokens,
                        "totalVideoTokensPurchased" to purchased + pack.tokens,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    purchaseRef,
                    mapOf(
                        "userId" to uid,
                        "packId" to pack.id,
                        "packTitle" to pack.title,
                        "tokensAdded" to pack.tokens,
                        "priceLabel" to pack.priceLabel,
                        "purchasedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (referralSnap.exists() && referralSnap.getString("status") == ReferralRecord.STATUS_PENDING) {
                    tx.update(
                        referralRef,
                        mapOf(
                            "status" to ReferralRecord.STATUS_QUALIFIED,
                            "qualifyingPurchaseType" to ReferralRecord.PURCHASE_TYPE_VIDEO_PACK,
                            "qualifyingPurchaseId" to purchaseId,
                            "qualifiedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/video_purchases", auth)
            Result.failure(e)
        }
    }

    fun observeVideoSessions(userId: String): Flow<List<VideoSession>> = flow {
        val path = "video_sessions"
        emitAll(
            db.collection("video_sessions")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeVideoFrameChunks(sessionId: String): Flow<List<VideoFrameChunk>> = flow {
        val uid = requireUserId()
        val path = "video_sessions/$sessionId/video_frames"
        emitAll(
            db.collection("video_sessions").document(sessionId).collection("video_frames")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoFrameChunk::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.sequenceNumber }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeVideoAudioChunks(sessionId: String): Flow<List<MicAudioChunk>> = flow {
        val uid = requireUserId()
        val path = "video_sessions/$sessionId/audio_chunks"
        emitAll(
            db.collection("video_sessions").document(sessionId).collection("audio_chunks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MicAudioChunk::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.sequenceNumber }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun requestPrivateVideoSession(
        threadId: String,
        creatorId: String,
        creatorName: String,
        requesterName: String,
        note: String,
        durationMinutes: Long
    ): Result<String> {
        return try {
            val uid = requireUserId()
            require(creatorId.isNotBlank() && creatorId != uid) { "Cannot request a video session with yourself" }
            require(durationMinutes in 1L..60L) { "Duration must be between 1 and 60 minutes" }

            val effectiveThreadId = threadId.ifBlank { DirectChatThread.threadIdFor(uid, creatorId) }
            val sessionId = UUID.randomUUID().toString()
            val sessionRef = db.collection("video_sessions").document(sessionId)

            val safeRequester = requesterName.trim().ifBlank { "Creator" }.take(60)
            val safeCreator = creatorName.trim().ifBlank { "Creator" }.take(60)
            val safeNote = note.trim().ifBlank { "Private chat 1-on-1 live video request" }.take(160)

            sessionRef.set(
                mapOf(
                    "sessionId" to sessionId,
                    "threadId" to effectiveThreadId,
                    "requesterId" to uid,
                    "requesterName" to safeRequester,
                    "creatorId" to creatorId,
                    "creatorName" to safeCreator,
                    "participantIds" to listOf(uid, creatorId),
                    "note" to safeNote,
                    "durationMinutes" to durationMinutes,
                    "tokensPerMinute" to 0L,
                    "totalTokensCost" to 0L,
                    "creatorShareTokens" to 0L,
                    "creatorShareCents" to 0L,
                    "status" to VideoSession.STATUS_PENDING,
                    "escrowStatus" to VideoSession.ESCROW_FREE,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "video_sessions", auth)
            Result.failure(e)
        }
    }

    suspend fun respondToVideoSession(session: VideoSession, accept: Boolean): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(session.creatorId == uid) { "Only the creator can accept or decline this video request" }
            val sessionRef = db.collection("video_sessions").document(session.sessionId)

            val nextStatus = if (accept) VideoSession.STATUS_ACCEPTED else VideoSession.STATUS_DECLINED
            sessionRef.update(
                mapOf(
                    "status" to nextStatus,
                    "escrowStatus" to VideoSession.ESCROW_FREE,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "video_sessions/${session.sessionId}", auth)
            Result.failure(e)
        }
    }

    suspend fun cancelVideoSession(sessionId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("video_sessions").document(sessionId).update(
                mapOf(
                    "status" to VideoSession.STATUS_CANCELLED,
                    "escrowStatus" to VideoSession.ESCROW_FREE,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "video_sessions/$sessionId", auth)
            Result.failure(e)
        }
    }

    suspend fun claimVideoSessionRefund(session: VideoSession): Result<Unit> {
        return Result.success(Unit)
    }

    suspend fun updateVideoSessionStatus(sessionId: String, newStatus: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("video_sessions").document(sessionId).update(
                mapOf(
                    "status" to newStatus,
                    "escrowStatus" to VideoSession.ESCROW_FREE,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "video_sessions/$sessionId/status", auth)
            Result.failure(e)
        }
    }

    suspend fun sendVideoFrameChunk(
        sessionId: String,
        senderName: String,
        frameBase64: String,
        width: Int,
        height: Int,
        sequenceNumber: Long
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val frameId = UUID.randomUUID().toString()
            val safeName = senderName.trim().ifBlank { "Creator" }.take(60)
            db.collection("video_sessions").document(sessionId)
                .collection("video_frames").document(frameId)
                .set(
                    mapOf(
                        "frameId" to frameId,
                        "sessionId" to sessionId,
                        "senderId" to uid,
                        "senderName" to safeName,
                        "frameBase64" to frameBase64,
                        "width" to width.coerceIn(1, 1280),
                        "height" to height.coerceIn(1, 1280),
                        "sequenceNumber" to sequenceNumber,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendVideoAudioChunk(
        sessionId: String,
        senderName: String,
        audioBase64: String,
        durationMs: Long,
        sequenceNumber: Long
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val chunkId = UUID.randomUUID().toString()
            val safeName = senderName.trim().ifBlank { "Creator" }.take(60)
            db.collection("video_sessions").document(sessionId)
                .collection("audio_chunks").document(chunkId)
                .set(
                    mapOf(
                        "chunkId" to chunkId,
                        "sessionId" to sessionId,
                        "senderId" to uid,
                        "senderName" to safeName,
                        "sampleRate" to 8000,
                        "audioBase64" to audioBase64,
                        "durationMs" to durationMs.coerceIn(50L, 5000L),
                        "sequenceNumber" to sequenceNumber,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- USER REPORTS, SUPPORT TICKETS & 6-MONTH SUPPORT WEEKLY PAYROLL ---

    fun observeUserReports(): Flow<List<UserReport>> = flow {
        val uid = requireUserId()
        val path = "user_reports"
        emitAll(
            db.collection("user_reports")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun submitUserReport(
        reportedUserId: String,
        reportedUserName: String,
        reporterName: String,
        reason: String,
        details: String,
        sourceContext: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(reportedUserId != uid) { "You cannot report yourself" }
            val reportId = UUID.randomUUID().toString()
            db.collection("user_reports").document(reportId).set(
                mapOf(
                    "reportId" to reportId,
                    "reportedUserId" to reportedUserId,
                    "reportedUserName" to reportedUserName.trim().ifBlank { "User" }.take(60),
                    "reporterId" to uid,
                    "reporterName" to reporterName.trim().ifBlank { "Member" }.take(60),
                    "reason" to reason.trim().ifBlank { "Harassment or Bullying" }.take(120),
                    "details" to details.trim().take(400),
                    "sourceContext" to sourceContext,
                    "status" to UserReport.STATUS_OPEN,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "user_reports", auth)
            Result.failure(e)
        }
    }

    suspend fun resolveUserReport(
        reportId: String,
        newStatus: String,
        note: String = ""
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "reviewedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val safeNote = note.trim().take(240)
            if (safeNote.isNotEmpty()) {
                updates["resolutionNote"] = safeNote
            }
            db.collection("user_reports").document(reportId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "user_reports/$reportId", auth)
            Result.failure(e)
        }
    }

    fun observeMySupportTickets(userId: String): Flow<List<SupportTicket>> = flow {
        val path = "support_tickets/user/$userId"
        emitAll(
            db.collection("support_tickets")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SupportTicket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeAllSupportTickets(): Flow<List<SupportTicket>> = flow {
        val uid = requireUserId()
        val path = "support_tickets"
        emitAll(
            db.collection("support_tickets")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SupportTicket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createSupportTicket(
        userName: String,
        category: String,
        subject: String,
        message: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val safeSubject = subject.trim().take(120)
            val safeMessage = message.trim().take(600)
            require(safeSubject.isNotBlank()) { "Please enter a ticket subject" }
            require(safeMessage.isNotBlank()) { "Please describe your request" }
            val ticketId = UUID.randomUUID().toString()
            val email = auth.currentUser?.email?.trim()?.lowercase(Locale.ROOT)
            val payload = mutableMapOf<String, Any>(
                "ticketId" to ticketId,
                "userId" to uid,
                "userName" to userName.trim().ifBlank { "Member" }.take(60),
                "category" to category,
                "subject" to safeSubject,
                "message" to safeMessage,
                "status" to SupportTicket.STATUS_OPEN,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!email.isNullOrBlank()) {
                payload["userEmail"] = email
            }
            db.collection("support_tickets").document(ticketId).set(payload).await()
            Result.success(ticketId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "support_tickets", auth)
            Result.failure(e)
        }
    }

    suspend fun updateSupportTicketStaffReply(
        ticketId: String,
        newStatus: String,
        staffReply: String,
        staffName: String
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val safeReply = staffReply.trim().take(600)
            val updates = mutableMapOf<String, Any?>(
                "status" to newStatus,
                "respondedByUid" to uid,
                "respondedByName" to staffName.trim().ifBlank { "Support" }.take(60),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (safeReply.isNotBlank()) {
                updates["staffReply"] = safeReply
            }
            db.collection("support_tickets").document(ticketId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "support_tickets/$ticketId", auth)
            Result.failure(e)
        }
    }

    suspend fun closeOwnSupportTicket(ticketId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("support_tickets").document(ticketId).update(
                mapOf(
                    "status" to SupportTicket.STATUS_CLOSED,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "support_tickets/$ticketId/close", auth)
            Result.failure(e)
        }
    }

    fun observeMySupportPayroll(userId: String): Flow<List<SupportPayrollPayout>> = flow {
        val path = "support_payroll/user/$userId"
        emitAll(
            db.collection("support_payroll")
                .whereEqualTo("staffUserId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SupportPayrollPayout::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeAllSupportPayroll(): Flow<List<SupportPayrollPayout>> = flow {
        val uid = requireUserId()
        val path = "support_payroll"
        emitAll(
            db.collection("support_payroll")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SupportPayrollPayout::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun claimSupportWeeklyPay(): Result<SupportPayrollPayout> {
        return try {
            val uid = requireUserId()
            val email = auth.currentUser?.email?.trim()?.lowercase(Locale.ROOT)
            val userRef = db.collection("users").document(uid)
            val payoutId = UUID.randomUUID().toString()
            val payoutRef = db.collection("support_payroll").document(payoutId)
            val cal = java.util.Calendar.getInstance()
            val weekLabel = "${cal.get(java.util.Calendar.YEAR)}-W${cal.get(java.util.Calendar.WEEK_OF_YEAR)}"

            val payout = db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                if (!userSnap.exists()) {
                    throw IllegalStateException("User profile not found")
                }
                val currentBalance = userSnap.getLong("voteBalance") ?: 0L
                val currentEarned = userSnap.getLong("totalSupportPayTokensEarned") ?: 0L
                val staffName = (userSnap.getString("displayName") ?: "Support")
                    .trim().ifBlank { "Support" }.take(60)

                tx.update(
                    userRef,
                    mapOf(
                        "voteBalance" to currentBalance + UserProfile.SUPPORT_PAY_WEEKLY_TOKENS,
                        "totalSupportPayTokensEarned" to currentEarned + UserProfile.SUPPORT_PAY_WEEKLY_TOKENS,
                        "lastSupportPayClaimAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val payload = mutableMapOf<String, Any>(
                    "payoutId" to payoutId,
                    "staffUserId" to uid,
                    "staffName" to staffName,
                    "tokensAwarded" to UserProfile.SUPPORT_PAY_WEEKLY_TOKENS,
                    "usdValueCents" to UserProfile.SUPPORT_PAY_WEEKLY_CENTS,
                    "weekLabel" to weekLabel,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) {
                    payload["staffEmail"] = email
                }
                tx.set(payoutRef, payload)

                SupportPayrollPayout(
                    id = payoutId,
                    payoutId = payoutId,
                    staffUserId = uid,
                    staffName = staffName,
                    staffEmail = email,
                    tokensAwarded = UserProfile.SUPPORT_PAY_WEEKLY_TOKENS,
                    usdValueCents = UserProfile.SUPPORT_PAY_WEEKLY_CENTS,
                    weekLabel = weekLabel
                )
            }.await()

            Result.success(payout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "support_payroll/claim", auth)
            Result.failure(e)
        }
    }

    fun observeBanRequests(): Flow<List<BanRequest>> = flow {
        val uid = requireUserId()
        val path = "ban_requests"
        emitAll(
            db.collection("ban_requests")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BanRequest::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<BanRequest> { if (it.status == BanRequest.STATUS_PENDING) 0 else 1 }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun requestUserBanBySupport(
        targetUserId: String,
        targetDisplayName: String,
        requesterName: String,
        banScope: String,
        reason: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val callerEmail = auth.currentUser?.email?.trim()?.lowercase(Locale.ROOT)
            val safeTargetName = targetDisplayName.trim().ifBlank { "User" }.take(60)
            val safeRequesterName = requesterName.trim().ifBlank { "Support" }.take(60)
            val safeReason = reason.trim().ifBlank { "Submitted by Support for Admin ban review" }.take(240)
            val safeScope = if (banScope == BanRequest.SCOPE_CHAT) BanRequest.SCOPE_CHAT else BanRequest.SCOPE_ACCOUNT

            // 1. Immediately suspend the user for 72h & bounce from all rooms pending Admin decision
            suspendUserAccount(
                targetUserId = targetUserId,
                targetDisplayName = safeTargetName,
                durationHours = 72L,
                reason = "Suspended pending Admin ban approval: $safeReason".take(160)
            ).getOrThrow()
            bounceUserFromAllRooms(targetUserId, 60L, safeReason.take(160))

            // 2. Create Admin approval request
            val requestId = UUID.randomUUID().toString()
            val payload = mutableMapOf<String, Any>(
                "requestId" to requestId,
                "targetUserId" to targetUserId,
                "targetUserName" to safeTargetName,
                "requestedByUid" to uid,
                "requestedByName" to safeRequesterName,
                "banScope" to safeScope,
                "reason" to safeReason,
                "status" to BanRequest.STATUS_PENDING,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!callerEmail.isNullOrBlank()) {
                payload["requestedByEmail"] = callerEmail
            }
            db.collection("ban_requests").document(requestId).set(payload).await()
            Result.success(requestId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "ban_requests", auth)
            Result.failure(e)
        }
    }

    suspend fun reviewBanRequest(
        banRequest: BanRequest,
        approve: Boolean,
        reviewerName: String,
        reviewNote: String = ""
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            val nextStatus = if (approve) BanRequest.STATUS_APPROVED else BanRequest.STATUS_REJECTED
            val safeReviewerName = reviewerName.trim().ifBlank { "Admin" }.take(60)
            val defaultNote = if (approve) "Ban approved by Admin" else "Ban request rejected; suspension lifted"
            val safeNote = reviewNote.trim().ifBlank { defaultNote }.take(240)

            db.collection("ban_requests").document(banRequest.requestId).update(
                mapOf(
                    "status" to nextStatus,
                    "reviewedByUid" to uid,
                    "reviewedByName" to safeReviewerName,
                    "reviewNote" to safeNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (approve) {
                if (banRequest.banScope == BanRequest.SCOPE_ACCOUNT) {
                    banUserAccount(banRequest.targetUserId, banRequest.targetUserName, banRequest.reason).getOrThrow()
                } else {
                    banUserFromChat(banRequest.targetUserId, banRequest.targetUserName, banRequest.reason).getOrThrow()
                    unsuspendUserAccount(banRequest.targetUserId)
                }
                bounceUserFromAllRooms(banRequest.targetUserId, 60L, banRequest.reason.take(160))
            } else {
                unsuspendUserAccount(banRequest.targetUserId)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "ban_requests/${banRequest.requestId}", auth)
            Result.failure(e)
        }
    }

    // --- REFERRAL PROGRAM: CODES, PAYING QUALIFICATION, 10 -> 100 REWARDS & CASH-IN ---

    fun observeMyReferralRecord(userId: String): Flow<ReferralRecord?> = flow {
        val path = "referrals/$userId"
        emitAll(
            db.collection("referrals").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        ReferralRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, userId)
                    emit(null)
                }
        )
    }

    fun observeReferralsMadeBy(userId: String): Flow<List<ReferralRecord>> = flow {
        val path = "referrals"
        emitAll(
            db.collection("referrals")
                .whereEqualTo("referrerId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReferralRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.qualifiedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    emit(emptyList())
                }
        )
    }

    fun observeMyReferralClaims(userId: String): Flow<List<ReferralClaim>> = flow {
        val path = "referral_claims"
        emitAll(
            db.collection("referral_claims")
                .whereEqualTo("referrerId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReferralClaim::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    emit(emptyList())
                }
        )
    }

    fun observeMyReferralCashouts(userId: String): Flow<List<ReferralCashout>> = flow {
        val path = "referral_cashouts"
        emitAll(
            db.collection("referral_cashouts")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReferralCashout::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    emit(emptyList())
                }
        )
    }

    suspend fun applyReferralCode(
        rawCode: String,
        myDisplayName: String
    ): Result<ReferralRecord> {
        return try {
            val uid = requireUserId()
            val normalizedCode = rawCode.trim().uppercase(Locale.ROOT)
            if (!REFERRAL_CODE_REGEX.matches(normalizedCode)) {
                return Result.failure(
                    IllegalArgumentException("Referral codes must be 6–16 letters or numbers.")
                )
            }
            val safeName = myDisplayName.trim().ifBlank { "Creator" }.take(60)
            val referralRef = db.collection("referrals").document(uid)
            val existingRefSnap = referralRef.get().await()
            if (existingRefSnap.exists()) {
                return Result.failure(
                    IllegalStateException("You have already applied a referral code on this account.")
                )
            }

            val codeSnap = db.collection("referral_codes").document(normalizedCode).get().await()
            if (!codeSnap.exists()) {
                return Result.failure(
                    IllegalArgumentException("Referral code \"$normalizedCode\" was not found.")
                )
            }
            val referrerId = codeSnap.getString("ownerId").orEmpty()
            val referrerName = (codeSnap.getString("ownerName") ?: "Creator").trim().ifBlank { "Creator" }.take(60)
            if (referrerId.isBlank() || referrerId == uid) {
                return Result.failure(
                    IllegalArgumentException("You cannot apply your own referral code.")
                )
            }

            val payload = mapOf(
                "referredUserId" to uid,
                "referredUserName" to safeName,
                "referrerId" to referrerId,
                "referrerName" to referrerName,
                "referralCode" to normalizedCode,
                "status" to ReferralRecord.STATUS_PENDING,
                "rewardClaimed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            referralRef.set(payload).await()
            Result.success(
                ReferralRecord(
                    id = uid,
                    referredUserId = uid,
                    referredUserName = safeName,
                    referrerId = referrerId,
                    referrerName = referrerName,
                    referralCode = normalizedCode,
                    status = ReferralRecord.STATUS_PENDING,
                    rewardClaimed = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "referrals/apply", auth)
            Result.failure(e)
        }
    }

    suspend fun claimReferralMilestoneReward(referrerDisplayName: String): Result<ReferralClaim> {
        return try {
            val uid = requireUserId()
            val safeName = referrerDisplayName.trim().ifBlank { "Creator" }.take(60)
            val eligibleSnapshot = db.collection("referrals")
                .whereEqualTo("referrerId", uid)
                .whereEqualTo("status", ReferralRecord.STATUS_QUALIFIED)
                .whereEqualTo("rewardClaimed", false)
                .limit(UserProfile.REFERRALS_REQUIRED_PER_REWARD.toLong())
                .get()
                .await()

            val targetIds = eligibleSnapshot.documents
                .map { it.id }
                .distinct()
                .take(UserProfile.REFERRALS_REQUIRED_PER_REWARD)
            if (targetIds.size < UserProfile.REFERRALS_REQUIRED_PER_REWARD) {
                return Result.failure(
                    IllegalStateException(
                        "You need ${UserProfile.REFERRALS_REQUIRED_PER_REWARD} qualified paying referrals to claim +100 tokens (currently ${targetIds.size}/10)."
                    )
                )
            }

            val claimId = UUID.randomUUID().toString()
            val userRef = db.collection("users").document(uid)
            val claimRef = db.collection("referral_claims").document(claimId)
            val referralRefs = targetIds.map { db.collection("referrals").document(it) }

            db.runTransaction { tx ->
                // All reads MUST come before any writes
                val userSnap = tx.get(userRef)
                if (!userSnap.exists()) {
                    throw IllegalStateException("User profile is not initialized")
                }
                val referralSnaps = referralRefs.map { tx.get(it) }
                referralSnaps.forEach { snap ->
                    if (!snap.exists() ||
                        snap.getString("referrerId") != uid ||
                        snap.getString("status") != ReferralRecord.STATUS_QUALIFIED ||
                        snap.getBoolean("rewardClaimed") != false
                    ) {
                        throw IllegalStateException("One or more referrals changed status; please try again.")
                    }
                }

                val voteBalance = userSnap.getLong("voteBalance") ?: 0L
                val paidBalance = userSnap.getLong("paidVoteBalance") ?: 0L
                val cashableBalance = userSnap.getLong("referralCashableBalance") ?: 0L
                val totalEarned = userSnap.getLong("totalReferralTokensEarned") ?: 0L

                tx.update(
                    userRef,
                    mapOf(
                        "voteBalance" to voteBalance + UserProfile.REFERRAL_REWARD_TOKENS,
                        "paidVoteBalance" to paidBalance + UserProfile.REFERRAL_REWARD_TOKENS,
                        "referralCashableBalance" to cashableBalance + UserProfile.REFERRAL_REWARD_TOKENS,
                        "totalReferralTokensEarned" to totalEarned + UserProfile.REFERRAL_REWARD_TOKENS,
                        "lastReferralClaimId" to claimId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                referralRefs.forEach { ref ->
                    tx.update(
                        ref,
                        mapOf(
                            "rewardClaimed" to true,
                            "rewardClaimId" to claimId,
                            "claimedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                tx.set(
                    claimRef,
                    mapOf(
                        "claimId" to claimId,
                        "referrerId" to uid,
                        "referrerName" to safeName,
                        "qualifiedReferralIds" to targetIds,
                        "referralCountRequired" to UserProfile.REFERRALS_REQUIRED_PER_REWARD,
                        "tokensAwarded" to UserProfile.REFERRAL_REWARD_TOKENS,
                        "cashValueCents" to UserProfile.REFERRAL_REWARD_CENTS,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(
                ReferralClaim(
                    id = claimId,
                    claimId = claimId,
                    referrerId = uid,
                    referrerName = safeName,
                    qualifiedReferralIds = targetIds,
                    referralCountRequired = UserProfile.REFERRALS_REQUIRED_PER_REWARD,
                    tokensAwarded = UserProfile.REFERRAL_REWARD_TOKENS,
                    cashValueCents = UserProfile.REFERRAL_REWARD_CENTS
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "referral_claims/claim", auth)
            Result.failure(e)
        }
    }

    suspend fun cashInReferralTokens(
        tokensToCashIn: Long,
        payoutMethod: String,
        payoutDestination: String,
        userName: String
    ): Result<ReferralCashout> {
        return try {
            val uid = requireUserId()
            if (tokensToCashIn < 1L || tokensToCashIn > 100000L) {
                return Result.failure(IllegalArgumentException("Enter between 1 and 100,000 tokens to cash in."))
            }
            if (payoutMethod !in setOf(
                    ReferralCashout.METHOD_INSTANT_PAYOUT,
                    ReferralCashout.METHOD_PAYPAL,
                    ReferralCashout.METHOD_BANK_ACH
                )
            ) {
                return Result.failure(IllegalArgumentException("Unsupported payout method."))
            }
            val cleanDestination = payoutDestination.trim()
            if (cleanDestination.length !in 3..120) {
                return Result.failure(IllegalArgumentException("Enter a valid payout destination (3–120 characters)."))
            }
            val safeName = userName.trim().ifBlank { "Creator" }.take(60)
            val usdCents = tokensToCashIn * UserProfile.CENTS_PER_TOKEN
            val cashoutId = UUID.randomUUID().toString()
            val userRef = db.collection("users").document(uid)
            val cashoutRef = db.collection("referral_cashouts").document(cashoutId)

            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                if (!userSnap.exists()) {
                    throw IllegalStateException("User profile is not initialized")
                }
                val voteBalance = userSnap.getLong("voteBalance") ?: 0L
                val paidBalance = userSnap.getLong("paidVoteBalance") ?: 0L
                val cashableBalance = userSnap.getLong("referralCashableBalance") ?: 0L
                val totalCashedIn = userSnap.getLong("totalReferralTokensCashedIn") ?: 0L

                if (cashableBalance < tokensToCashIn || paidBalance < tokensToCashIn || voteBalance < tokensToCashIn) {
                    throw IllegalStateException(
                        "You only have $cashableBalance cashable referral tokens available."
                    )
                }

                tx.update(
                    userRef,
                    mapOf(
                        "voteBalance" to voteBalance - tokensToCashIn,
                        "paidVoteBalance" to paidBalance - tokensToCashIn,
                        "referralCashableBalance" to cashableBalance - tokensToCashIn,
                        "totalReferralTokensCashedIn" to totalCashedIn + tokensToCashIn,
                        "lastReferralCashoutId" to cashoutId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    cashoutRef,
                    mapOf(
                        "cashoutId" to cashoutId,
                        "userId" to uid,
                        "userName" to safeName,
                        "tokensCashedIn" to tokensToCashIn,
                        "usdCents" to usdCents,
                        "payoutMethod" to payoutMethod,
                        "payoutDestination" to cleanDestination,
                        "status" to ReferralCashout.STATUS_SUBMITTED,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(
                ReferralCashout(
                    id = cashoutId,
                    cashoutId = cashoutId,
                    userId = uid,
                    userName = safeName,
                    tokensCashedIn = tokensToCashIn,
                    usdCents = usdCents,
                    payoutMethod = payoutMethod,
                    payoutDestination = cleanDestination,
                    status = ReferralCashout.STATUS_SUBMITTED
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "referral_cashouts/cashin", auth)
            Result.failure(e)
        }
    }

    // --- DM SNAPS, 24H STORIES, OPTIONAL CHECK-INS, ADMIN ALERTS & USER WARNINGS ---

    suspend fun sendDirectSnap(
        threadId: String,
        senderName: String,
        caption: String,
        snapBase64: String,
        uploadPhotoFilePath: String? = null,
        durationSeconds: Long = 5L,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        return try {
            requireUserId()
            social.sendDirectSnap(
                threadId = threadId,
                senderName = senderName,
                caption = caption,
                snapBase64 = snapBase64,
                uploadPhotoFilePath = uploadPhotoFilePath,
                durationSeconds = durationSeconds,
                onProgress = onProgress
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "direct_chats/$threadId/messages", auth)
            Result.failure(e)
        }
    }

    suspend fun markDirectSnapViewed(threadId: String, messageId: String): Result<Unit> =
        social.markDirectSnapViewed(threadId, messageId)

    fun observeActiveStories(): Flow<List<UserStory>> = social.observeActiveStories()

    suspend fun postStory(
        userName: String,
        storyType: String,
        text: String,
        backgroundStyle: String,
        mediaBase64: String? = null,
        uploadPhotoFilePath: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = social.postStory(
        userName = userName,
        storyType = storyType,
        text = text,
        backgroundStyle = backgroundStyle,
        mediaBase64 = mediaBase64,
        uploadPhotoFilePath = uploadPhotoFilePath,
        onProgress = onProgress
    )

    suspend fun incrementStoryView(storyId: String): Result<Unit> =
        social.incrementStoryView(storyId)

    suspend fun deleteStory(storyId: String, storagePath: String? = null): Result<Unit> =
        social.deleteStory(storyId, storagePath)

    fun observeActiveCheckIns(): Flow<List<UserCheckIn>> = social.observeActiveCheckIns()

    suspend fun setCheckIn(
        userName: String,
        placeLabel: String,
        statusNote: String,
        vibeTag: String
    ): Result<Unit> = social.setCheckIn(userName, placeLabel, statusNote, vibeTag)

    suspend fun clearCheckIn(): Result<Unit> = social.clearCheckIn()

    fun observeAdminAnnouncements(): Flow<List<AdminAnnouncement>> = social.observeAdminAnnouncements()

    suspend fun sendAdminAnnouncement(
        category: String,
        title: String,
        message: String,
        senderName: String,
        senderRole: String
    ): Result<String> = social.sendAdminAnnouncement(category, title, message, senderName, senderRole)

    suspend fun deleteAdminAnnouncement(announcementId: String): Result<Unit> =
        social.deleteAdminAnnouncement(announcementId)

    fun observeMyWarnings(userId: String): Flow<List<UserWarning>> = social.observeMyWarnings(userId)

    suspend fun sendUserWarning(
        targetUserId: String,
        targetUserName: String,
        senderName: String,
        senderRole: String,
        severity: String,
        title: String,
        message: String
    ): Result<String> = social.sendUserWarning(
        targetUserId = targetUserId,
        targetUserName = targetUserName,
        senderName = senderName,
        senderRole = senderRole,
        severity = severity,
        title = title,
        message = message
    )

    suspend fun acknowledgeUserWarning(warningId: String): Result<Unit> =
        social.acknowledgeUserWarning(warningId)

    fun observeBlockedUsers(userId: String): Flow<List<BlockedUser>> =
        social.observeBlockedUsers(userId)

    suspend fun blockUser(
        blockedUserId: String,
        blockedUserName: String,
        reason: String? = null
    ): Result<Unit> = social.blockUser(blockedUserId, blockedUserName, reason)

    suspend fun unblockUser(blockedUserId: String): Result<Unit> =
        social.unblockUser(blockedUserId)

    private fun logStreamError(
        error: Throwable,
        op: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception) ?: return
        if (auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path, auth)
        }
    }

    companion object {
        const val STARTER_VOTES = 100L
        private val EMAIL_REGEX = Regex("^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$")
        private val REFERRAL_CODE_REGEX = Regex("^[A-Z0-9]{6,16}$")
    }
}
