package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val displayName: String = "",
    val bio: String? = null,
    val age: Int? = null,
    val sex: String? = null,
    val role: String = ROLE_USER,
    val voteBalance: Long = 0L,
    val paidVoteBalance: Long = 0L,
    val totalVotesCast: Long = 0L,
    val totalVotesPurchased: Long = 0L,
    val chatAccessExpiresAt: Timestamp? = null,
    val privateChatAccessExpiresAt: Timestamp? = null,
    val phoneTokenBalance: Long = 0L,
    val totalPhoneTokensPurchased: Long = 0L,
    val lastRefundedSessionId: String? = null,
    val videoTokenBalance: Long = 0L,
    val totalVideoTokensPurchased: Long = 0L,
    val lastRefundedVideoSessionId: String? = null,
    val supportStartedAt: Timestamp? = null,
    val lastSupportPayClaimAt: Timestamp? = null,
    val totalSupportPayTokensEarned: Long = 0L,
    val referralCode: String? = null,
    val referralCashableBalance: Long = 0L,
    val totalReferralTokensEarned: Long = 0L,
    val totalReferralTokensCashedIn: Long = 0L,
    val lastReferralClaimId: String? = null,
    val lastReferralCashoutId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun freeTokensAvailable(): Long = (voteBalance - paidVoteBalance).coerceAtLeast(0L)

    fun effectiveReferralCode(fallbackUid: String = userId.ifBlank { id }): String =
        referralCode?.takeIf { it.isNotBlank() } ?: defaultReferralCodeForUid(fallbackUid)

    fun cashableUsdLabel(): String = String.format(
        java.util.Locale.US,
        "$%.2f",
        (referralCashableBalance * CENTS_PER_TOKEN) / 100.0
    )

    fun freeTrialEndsAtMillis(): Long? =
        createdAt?.toDate()?.time?.plus(FREE_CHAT_TRIAL_MILLIS)

    fun hasActiveFreeTrial(nowMillis: Long = System.currentTimeMillis()): Boolean = true

    fun hasActivePaidChatPass(nowMillis: Long = System.currentTimeMillis()): Boolean = true

    fun hasChatAccess(isStaff: Boolean, nowMillis: Long = System.currentTimeMillis()): Boolean = true

    fun hasActivePrivateChatPass(nowMillis: Long = System.currentTimeMillis()): Boolean = true

    fun supportTenureStartMillis(fallbackStart: Timestamp? = null): Long? =
        (supportStartedAt ?: fallbackStart)?.toDate()?.time

    fun supportTenureDays(
        fallbackStart: Timestamp? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val start = supportTenureStartMillis(fallbackStart) ?: return 0L
        return ((nowMillis - start).coerceAtLeast(0L)) / (24L * 60L * 60L * 1000L)
    }

    fun isSupportSixMonthsEligible(
        fallbackStart: Timestamp? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val start = supportTenureStartMillis(fallbackStart) ?: return false
        return nowMillis >= start + SUPPORT_PAY_WAIT_MILLIS
    }

    fun canClaimWeeklySupportPay(
        fallbackStart: Timestamp? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isSupportSixMonthsEligible(fallbackStart, nowMillis)) return false
        val lastClaim = lastSupportPayClaimAt?.toDate()?.time ?: return true
        return nowMillis >= lastClaim + SUPPORT_PAY_COOLDOWN_MILLIS
    }

    fun nextSupportPayClaimAtMillis(): Long? {
        val lastClaim = lastSupportPayClaimAt?.toDate()?.time ?: return null
        return lastClaim + SUPPORT_PAY_COOLDOWN_MILLIS
    }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_SUPPORT = "support"
        const val ROLE_ADMIN = "admin"

        const val SEX_FEMALE = "female"
        const val SEX_MALE = "male"
        const val SEX_NON_BINARY = "non_binary"
        const val SEX_UNSPECIFIED = "unspecified"

        const val FREE_CHAT_TRIAL_DAYS = 7L
        const val CHAT_PASS_DAYS = 5L
        const val CHAT_PASS_COST_VOTES = 5L
        const val FREE_CHAT_TRIAL_MILLIS = FREE_CHAT_TRIAL_DAYS * 24L * 60L * 60L * 1000L
        const val CHAT_PASS_MILLIS = CHAT_PASS_DAYS * 24L * 60L * 60L * 1000L

        const val PRIVATE_CHAT_PASS_DAYS = 7L
        const val PRIVATE_CHAT_PASS_COST_TOKENS = 5L
        const val PRIVATE_CHAT_PASS_MILLIS = PRIVATE_CHAT_PASS_DAYS * 24L * 60L * 60L * 1000L

        const val SUPPORT_PAY_WAIT_DAYS = 180L
        const val SUPPORT_PAY_WAIT_MILLIS = SUPPORT_PAY_WAIT_DAYS * 24L * 60L * 60L * 1000L
        const val SUPPORT_PAY_WEEKLY_TOKENS = 100L
        const val SUPPORT_PAY_WEEKLY_CENTS = 1000L
        const val SUPPORT_PAY_COOLDOWN_DAYS = 7L
        const val SUPPORT_PAY_COOLDOWN_MILLIS = SUPPORT_PAY_COOLDOWN_DAYS * 24L * 60L * 60L * 1000L

        const val REFERRALS_REQUIRED_PER_REWARD = 10
        const val REFERRAL_REWARD_TOKENS = 100L
        const val REFERRAL_REWARD_CENTS = 1000L
        const val CENTS_PER_TOKEN = 10L

        fun defaultReferralCodeForUid(uid: String): String {
            val clean = uid.uppercase(java.util.Locale.ROOT).filter { it in 'A'..'Z' || it in '0'..'9' }
            val hashSuffix = uid.hashCode().toUInt().toString(36).uppercase(java.util.Locale.ROOT).padStart(4, '0').take(4)
            return ("SV" + clean.take(4).padEnd(4, 'X') + hashSuffix).take(10)
        }

        fun sexDisplayLabel(sex: String?): String = when (sex) {
            SEX_FEMALE -> "Female"
            SEX_MALE -> "Male"
            SEX_NON_BINARY -> "Non-binary"
            SEX_UNSPECIFIED -> "Prefer not to say"
            else -> ""
        }
    }
}

@IgnoreExtraProperties
data class CreatorSummary(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val displayName: String = "",
    val bio: String? = null,
    val age: Int? = null,
    val sex: String? = null,
    val visibility: String = "public",
    val postCount: Long = 0L,
    val totalVotesReceived: Long = 0L,
    val freePreviewLimit: Long = DEFAULT_FREE_PREVIEW_LIMIT,
    val unlockCostVotes: Long = DEFAULT_UNLOCK_COST_VOTES,
    @get:PropertyName("isOnline") @set:PropertyName("isOnline")
    var isOnline: Boolean = false,
    val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isCurrentlyOnline(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!isOnline) return false
        val seen = lastSeenAt?.toDate()?.time ?: return true
        return (nowMillis - seen) <= ONLINE_STALE_MILLIS
    }

    companion object {
        const val DEFAULT_FREE_PREVIEW_LIMIT = 2L
        const val DEFAULT_UNLOCK_COST_VOTES = 3L
        const val ONLINE_STALE_MILLIS = 5L * 60L * 1000L
    }
}

@IgnoreExtraProperties
data class MediaPost(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val mediaType: String = MEDIA_TYPE_PHOTO,
    val caption: String = "",
    val mediaBase64: String = "",
    val mediaDownloadUrl: String? = null,
    val storagePath: String? = null,
    val localVideoFileName: String? = null,
    val durationMs: Long = 0L,
    val postIndex: Long = 0L,
    val voteCount: Long = 0L,
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isVideo: Boolean
        get() = mediaType == MEDIA_TYPE_VIDEO

    companion object {
        const val MEDIA_TYPE_PHOTO = "photo"
        const val MEDIA_TYPE_VIDEO = "video"
    }
}

@IgnoreExtraProperties
data class CreatorUnlock(
    @DocumentId val id: String = "",
    val userId: String = "",
    val creatorId: String = "",
    val votesSpent: Long = 0L,
    @ServerTimestamp val unlockedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VotePurchase(
    @DocumentId val id: String = "",
    val userId: String = "",
    val packId: String = "",
    val packTitle: String = "",
    val votesAdded: Long = 0L,
    val priceLabel: String = "",
    @ServerTimestamp val purchasedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PostVoteRecord(
    @DocumentId val id: String = "",
    val postId: String = "",
    val voterId: String = "",
    val votesCast: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StaffRole(
    @DocumentId val id: String = "",
    val email: String = "",
    val displayLabel: String? = null,
    val role: String = ROLE_SUPPORT,
    val active: Boolean = true,
    val assignedByUid: String = "",
    val assignedByEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_SUPPORT = "support"
        const val OWNER_EMAIL = "daj18801@gmail.com"
    }
}

enum class EffectiveStaffTier {
    NONE,
    SUPPORT,
    ADMIN,
    OWNER
}

@IgnoreExtraProperties
data class ContentReport(
    @DocumentId val id: String = "",
    val postId: String = "",
    val postCreatorId: String = "",
    val postCaption: String = "",
    val reporterId: String = "",
    val reason: String = "",
    val status: String = STATUS_OPEN,
    val reviewedByUid: String? = null,
    val resolutionNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_RESOLVED = "resolved"
        const val STATUS_DISMISSED = "dismissed"
    }
}

data class ChatRoomInfo(
    val id: String,
    val name: String,
    val subtitle: String,
    val topicBadge: String
)

object ChatRoomCatalog {
    val rooms = listOf(
        ChatRoomInfo(
            id = "lounge",
            name = "General Lounge",
            subtitle = "Hang out, say hello, and meet creators across SnapVote",
            topicBadge = "Social"
        ),
        ChatRoomInfo(
            id = "creators_hub",
            name = "Creators Hub",
            subtitle = "Share lighting setups, ideas, and tips to earn more votes",
            topicBadge = "Tips"
        ),
        ChatRoomInfo(
            id = "photo_critique",
            name = "Photo Critique",
            subtitle = "Discuss framing, color grading, and portrait techniques",
            topicBadge = "Photos"
        ),
        ChatRoomInfo(
            id = "video_showcase",
            name = "Video Showcase",
            subtitle = "Talk short clips, transitions, and behind-the-scenes stories",
            topicBadge = "Videos"
        ),
        ChatRoomInfo(
            id = "vip_hangout",
            name = "Top Voters Club",
            subtitle = "Celebrate trending creators, vote streaks, and unlocked vaults",
            topicBadge = "Trending"
        )
    )
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = UserProfile.ROLE_USER,
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatRoomState(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val monitorUid: String? = null,
    val monitorName: String? = null,
    @ServerTimestamp val monitorAssignedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatPresence(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val role: String = UserProfile.ROLE_USER,
    @get:PropertyName("isInvisible") @set:PropertyName("isInvisible")
    var isInvisible: Boolean = false,
    @get:PropertyName("handRaised") @set:PropertyName("handRaised")
    var handRaised: Boolean = false,
    val handRaisedAt: Long = 0L,
    @get:PropertyName("isVoiceMuted") @set:PropertyName("isVoiceMuted")
    var isVoiceMuted: Boolean = false,
    val voiceMutedByUid: String? = null,
    @get:PropertyName("isSpeaking") @set:PropertyName("isSpeaking")
    var isSpeaking: Boolean = false,
    val joinedAtMillis: Long = 0L,
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val lastActiveAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatRoomVoiceChunk(
    @DocumentId val id: String = "",
    val chunkId: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val sampleRate: Int = 8000,
    val audioBase64: String = "",
    val durationMs: Long = 0L,
    val sequenceNumber: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatBounce(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val userId: String = "",
    val reason: String = "",
    val bouncedByUid: String = "",
    val bouncedUntil: Timestamp? = null,
    @ServerTimestamp val bouncedAt: Timestamp? = null
) {
    fun isCurrentlyBounced(nowMillis: Long = System.currentTimeMillis()): Boolean =
        (bouncedUntil?.toDate()?.time ?: 0L) > nowMillis
}

@IgnoreExtraProperties
data class ChatModerationStatus(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val mutedUntil: Timestamp? = null,
    val muteReason: String? = null,
    val banned: Boolean = false,
    val banReason: String? = null,
    val suspendedUntil: Timestamp? = null,
    val suspendReason: String? = null,
    val accountBanned: Boolean = false,
    val accountBanReason: String? = null,
    val moderatedByUid: String = "",
    val moderatedByEmail: String? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isCurrentlyMuted(nowMillis: Long = System.currentTimeMillis()): Boolean =
        (mutedUntil?.toDate()?.time ?: 0L) > nowMillis

    fun isCurrentlySuspended(nowMillis: Long = System.currentTimeMillis()): Boolean =
        (suspendedUntil?.toDate()?.time ?: 0L) > nowMillis

    fun isAccountRestricted(nowMillis: Long = System.currentTimeMillis()): Boolean =
        accountBanned || isCurrentlySuspended(nowMillis)
}

@IgnoreExtraProperties
data class UserReport(
    @DocumentId val id: String = "",
    val reportId: String = "",
    val reportedUserId: String = "",
    val reportedUserName: String = "",
    val reporterId: String = "",
    val reporterName: String = "",
    val reason: String = "",
    val details: String = "",
    val sourceContext: String = CONTEXT_DISCOVER,
    val status: String = STATUS_OPEN,
    val reviewedByUid: String? = null,
    val resolutionNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_ACTION_TAKEN = "action_taken"
        const val STATUS_DISMISSED = "dismissed"

        const val CONTEXT_DISCOVER = "discover"
        const val CONTEXT_PROFILE = "profile"
        const val CONTEXT_CHAT_ROOM = "chat_room"
        const val CONTEXT_DIRECT_CHAT = "direct_chat"
        const val CONTEXT_GROUP_CHAT = "group_chat"
        const val CONTEXT_FRIENDS = "friends"

        val reportReasons = listOf(
            "Harassment or Bullying",
            "Spam or Scam",
            "Inappropriate Content",
            "Impersonation",
            "Underage or Safety Concern"
        )
    }
}

@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val ticketId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String? = null,
    val category: String = CATEGORY_OTHER,
    val subject: String = "",
    val message: String = "",
    val status: String = STATUS_OPEN,
    val staffReply: String? = null,
    val respondedByUid: String? = null,
    val respondedByName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_RESOLVED = "resolved"
        const val STATUS_CLOSED = "closed"

        const val CATEGORY_ACCOUNT_APPEAL = "account_appeal"
        const val CATEGORY_TOKENS_BILLING = "tokens_billing"
        const val CATEGORY_SAFETY_HARASSMENT = "safety_harassment"
        const val CATEGORY_MIC_CALLS = "mic_calls"
        const val CATEGORY_TECHNICAL_BUG = "technical_bug"
        const val CATEGORY_OTHER = "other"

        val categories = listOf(
            CATEGORY_ACCOUNT_APPEAL to "Account / Appeal",
            CATEGORY_TOKENS_BILLING to "Tokens & Billing",
            CATEGORY_SAFETY_HARASSMENT to "Safety & Harassment",
            CATEGORY_MIC_CALLS to "Mic-to-Mic Calls",
            CATEGORY_TECHNICAL_BUG to "Technical Bug",
            CATEGORY_OTHER to "General Question"
        )

        fun categoryLabel(category: String): String =
            categories.firstOrNull { it.first == category }?.second ?: "General Question"
    }
}

@IgnoreExtraProperties
data class SupportPayrollPayout(
    @DocumentId val id: String = "",
    val payoutId: String = "",
    val staffUserId: String = "",
    val staffName: String = "",
    val staffEmail: String? = null,
    val tokensAwarded: Long = UserProfile.SUPPORT_PAY_WEEKLY_TOKENS,
    val usdValueCents: Long = UserProfile.SUPPORT_PAY_WEEKLY_CENTS,
    val weekLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun usdValueLabel(): String = String.format(java.util.Locale.US, "$%.2f", usdValueCents / 100.0)
}

@IgnoreExtraProperties
data class BanRequest(
    @DocumentId val id: String = "",
    val requestId: String = "",
    val targetUserId: String = "",
    val targetUserName: String = "",
    val requestedByUid: String = "",
    val requestedByName: String = "",
    val requestedByEmail: String? = null,
    val banScope: String = SCOPE_ACCOUNT,
    val reason: String = "",
    val status: String = STATUS_PENDING,
    val reviewedByUid: String? = null,
    val reviewedByName: String? = null,
    val reviewNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val SCOPE_ACCOUNT = "account"
        const val SCOPE_CHAT = "chat"

        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
    }
}


@IgnoreExtraProperties
data class FriendRelationship(
    @DocumentId val id: String = "",
    val pairId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val participantIds: List<String> = emptyList(),
    val status: String = STATUS_PENDING,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun otherUserId(myUid: String): String =
        if (senderId == myUid) receiverId else senderId

    fun otherUserName(myUid: String): String =
        if (senderId == myUid) receiverName else senderName

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"

        fun pairIdFor(uidA: String, uidB: String): String =
            listOf(uidA, uidB).sorted().joinToString("_")
    }
}

@IgnoreExtraProperties
data class DirectChatThread(
    @DocumentId val id: String = "",
    val threadId: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageSenderId: String? = null,
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun otherUserId(myUid: String): String =
        participantIds.firstOrNull { it != myUid }.orEmpty()

    fun otherUserName(myUid: String): String {
        val otherId = otherUserId(myUid)
        return participantNames[otherId] ?: "Friend"
    }

    companion object {
        fun threadIdFor(uidA: String, uidB: String): String =
            listOf(uidA, uidB).sorted().joinToString("_")
    }
}

@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val threadId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = TYPE_TEXT,
    val snapBase64: String? = null,
    val snapDownloadUrl: String? = null,
    val snapStoragePath: String? = null,
    val snapDurationSeconds: Int = 5,
    val viewedByIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val isSnap: Boolean
        get() = messageType == TYPE_SNAP

    fun hasBeenViewedBy(uid: String): Boolean = uid in viewedByIds

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_SNAP = "snap"
    }
}

@IgnoreExtraProperties
data class PrivateGroupChat(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val ownerId: String = "",
    val memberIds: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val privacyMode: String = PRIVACY_INVITE_ONLY,
    val allowMemberInvites: Boolean = false,
    val lastMessageText: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val PRIVACY_INVITE_ONLY = "invite_only"
        const val PRIVACY_FRIENDS_CAN_INVITE = "friends_can_invite"
    }
}

@IgnoreExtraProperties
data class GroupChatMessage(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class VotePack(
    val id: String,
    val title: String,
    val subtitle: String,
    val votes: Long,
    val priceLabel: String,
    val badge: String? = null
)

object VotePackCatalog {
    val packs = listOf(
        VotePack(
            id = "starter_10",
            title = "Starter Spark",
            subtitle = "Vote on top shots, unlock 3 creators, or buy a weekly DM pass",
            votes = 10L,
            priceLabel = "$0.99"
        ),
        VotePack(
            id = "fan_30",
            title = "Gallery Pass",
            subtitle = "Unlock up to 10 creator galleries & chat privately anytime",
            votes = 30L,
            priceLabel = "$2.49",
            badge = "POPULAR"
        ),
        VotePack(
            id = "patron_100",
            title = "Studio Patron",
            subtitle = "Power-vote, unlock vaults, and host private group chats",
            votes = 100L,
            priceLabel = "$6.99",
            badge = "BEST VALUE"
        )
    )
}

@IgnoreExtraProperties
data class PhoneTokenPurchase(
    @DocumentId val id: String = "",
    val userId: String = "",
    val packId: String = "",
    val packTitle: String = "",
    val tokensAdded: Long = 0L,
    val priceLabel: String = "",
    @ServerTimestamp val purchasedAt: Timestamp? = null
)

data class PhoneTokenPack(
    val id: String,
    val title: String,
    val subtitle: String,
    val tokens: Long,
    val priceLabel: String,
    val badge: String? = null
)

object PhoneTokenPackCatalog {
    val packs = listOf(
        PhoneTokenPack(
            id = "phone_15",
            title = "Quick Call 15",
            subtitle = "3 minutes of 1-on-1 mic conversation (5 tokens/min)",
            tokens = 15L,
            priceLabel = "$1.49"
        ),
        PhoneTokenPack(
            id = "phone_50",
            title = "Studio Line 50",
            subtitle = "10 minutes of scheduled mic-to-mic sessions",
            tokens = 50L,
            priceLabel = "$4.49",
            badge = "POPULAR"
        ),
        PhoneTokenPack(
            id = "phone_150",
            title = "VIP Backstage 150",
            subtitle = "30 minutes of private creator voice time",
            tokens = 150L,
            priceLabel = "$11.99",
            badge = "BEST VALUE"
        )
    )
}

@IgnoreExtraProperties
data class CreatorPayout(
    @DocumentId val id: String = "",
    val payoutId: String = "",
    val creatorId: String = "",
    val payerId: String = "",
    val payerName: String = "",
    val sourceType: String = SOURCE_GALLERY_UNLOCK,
    val sourceId: String = "",
    val grossTokens: Long = 0L,
    val creatorSharePercent: Long = CREATOR_SHARE_PERCENT,
    val creatorShareCents: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun creatorShareUsdLabel(): String = String.format(java.util.Locale.US, "$%.2f", creatorShareCents / 100.0)

    fun creatorShareTokensLabel(): String {
        val tenths = (grossTokens * creatorSharePercent) / 10L
        val whole = tenths / 10L
        val rem = tenths % 10L
        return if (rem == 0L) "$whole" else "$whole.$rem"
    }

    companion object {
        const val SOURCE_GALLERY_UNLOCK = "gallery_unlock"
        const val SOURCE_MIC_CALL = "mic_call"
        const val SOURCE_VIDEO_CALL = "video_call"
        const val CREATOR_CONTENT_SHARE_PERCENT = 40L
        const val CREATOR_SHARE_PERCENT = 70L
        const val CENTS_PER_TOKEN = 10L

        fun sharePercentForSource(sourceType: String): Long = when (sourceType) {
            SOURCE_GALLERY_UNLOCK -> CREATOR_CONTENT_SHARE_PERCENT
            else -> CREATOR_SHARE_PERCENT
        }
    }
}

@IgnoreExtraProperties
data class MicSession(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val requesterId: String = "",
    val requesterName: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val participantIds: List<String> = emptyList(),
    val topic: String = "",
    val scheduledForMillis: Long = 0L,
    val durationMinutes: Long = 5L,
    val tokensPerMinute: Long = TOKENS_PER_MINUTE,
    val totalTokensCost: Long = 0L,
    val creatorShareTokens: Long = 0L,
    val creatorShareCents: Long = 0L,
    val status: String = STATUS_PENDING,
    val escrowStatus: String = ESCROW_FREE,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun creatorShareUsdLabel(): String = String.format(java.util.Locale.US, "$%.2f", creatorShareCents / 100.0)

    companion object {
        const val TOKENS_PER_MINUTE = 0L
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_ACTIVE = "active"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_DECLINED = "declined"
        const val STATUS_CANCELLED = "cancelled"

        const val ESCROW_FREE = "free"
        const val ESCROW_HELD = "held"
        const val ESCROW_RELEASED = "released"
        const val ESCROW_REFUND_PENDING = "refund_pending"
        const val ESCROW_REFUNDED = "refunded"
    }
}

@IgnoreExtraProperties
data class MicAudioChunk(
    @DocumentId val id: String = "",
    val chunkId: String = "",
    val sessionId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val sampleRate: Int = 8000,
    val audioBase64: String = "",
    val durationMs: Long = 0L,
    val sequenceNumber: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoTokenPurchase(
    @DocumentId val id: String = "",
    val userId: String = "",
    val packId: String = "",
    val packTitle: String = "",
    val tokensAdded: Long = 0L,
    val priceLabel: String = "",
    @ServerTimestamp val purchasedAt: Timestamp? = null
)

data class VideoTokenPack(
    val id: String,
    val title: String,
    val subtitle: String,
    val tokens: Long,
    val priceLabel: String,
    val badge: String? = null
)

object VideoTokenPackCatalog {
    val packs = listOf(
        VideoTokenPack(
            id = "video_100",
            title = "1-Min Video Preview",
            subtitle = "1 minute of live 1-on-1 creator video in private chat (100 tokens/min)",
            tokens = 100L,
            priceLabel = "$9.99"
        ),
        VideoTokenPack(
            id = "video_300",
            title = "3-Min Private Video",
            subtitle = "3 minutes of private creator video calls",
            tokens = 300L,
            priceLabel = "$27.99",
            badge = "POPULAR"
        ),
        VideoTokenPack(
            id = "video_500",
            title = "5-Min VIP Studio",
            subtitle = "5 minutes of live creator video sessions",
            tokens = 500L,
            priceLabel = "$44.99",
            badge = "BEST VALUE"
        ),
        VideoTokenPack(
            id = "video_1000",
            title = "10-Min Creator Stage",
            subtitle = "10 minutes of private face-to-face creator video",
            tokens = 1000L,
            priceLabel = "$84.99"
        )
    )
}

@IgnoreExtraProperties
data class VideoSession(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val threadId: String = "",
    val requesterId: String = "",
    val requesterName: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val participantIds: List<String> = emptyList(),
    val note: String = "",
    val durationMinutes: Long = 1L,
    val tokensPerMinute: Long = TOKENS_PER_MINUTE,
    val totalTokensCost: Long = 0L,
    val creatorShareTokens: Long = 0L,
    val creatorShareCents: Long = 0L,
    val status: String = STATUS_PENDING,
    val escrowStatus: String = ESCROW_FREE,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun creatorShareUsdLabel(): String = String.format(java.util.Locale.US, "$%.2f", creatorShareCents / 100.0)

    companion object {
        const val TOKENS_PER_MINUTE = 0L
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_ACTIVE = "active"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_DECLINED = "declined"
        const val STATUS_CANCELLED = "cancelled"

        const val ESCROW_FREE = "free"
        const val ESCROW_HELD = "held"
        const val ESCROW_RELEASED = "released"
        const val ESCROW_REFUND_PENDING = "refund_pending"
        const val ESCROW_REFUNDED = "refunded"
    }
}

@IgnoreExtraProperties
data class VideoFrameChunk(
    @DocumentId val id: String = "",
    val frameId: String = "",
    val sessionId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val frameBase64: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sequenceNumber: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReferralCodeDirectoryEntry(
    @DocumentId val id: String = "",
    val code: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReferralRecord(
    @DocumentId val id: String = "",
    val referredUserId: String = "",
    val referredUserName: String = "",
    val referrerId: String = "",
    val referrerName: String = "",
    val referralCode: String = "",
    val status: String = STATUS_PENDING,
    val qualifyingPurchaseType: String? = null,
    val qualifyingPurchaseId: String? = null,
    val rewardClaimed: Boolean = false,
    val rewardClaimId: String? = null,
    val qualifiedAt: Timestamp? = null,
    val claimedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isQualified: Boolean
        get() = status == STATUS_QUALIFIED

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_QUALIFIED = "qualified"

        const val PURCHASE_TYPE_VOTE_PACK = "vote_pack"
        const val PURCHASE_TYPE_PHONE_PACK = "phone_pack"
        const val PURCHASE_TYPE_VIDEO_PACK = "video_pack"
    }
}

@IgnoreExtraProperties
data class ReferralClaim(
    @DocumentId val id: String = "",
    val claimId: String = "",
    val referrerId: String = "",
    val referrerName: String = "",
    val qualifiedReferralIds: List<String> = emptyList(),
    val referralCountRequired: Int = UserProfile.REFERRALS_REQUIRED_PER_REWARD,
    val tokensAwarded: Long = UserProfile.REFERRAL_REWARD_TOKENS,
    val cashValueCents: Long = UserProfile.REFERRAL_REWARD_CENTS,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun cashValueUsdLabel(): String = String.format(java.util.Locale.US, "$%.2f", cashValueCents / 100.0)
}

@IgnoreExtraProperties
data class ReferralCashout(
    @DocumentId val id: String = "",
    val cashoutId: String = "",
    val userId: String = "",
    val userName: String = "",
    val tokensCashedIn: Long = 0L,
    val usdCents: Long = 0L,
    val payoutMethod: String = METHOD_INSTANT_PAYOUT,
    val payoutDestination: String = "",
    val status: String = STATUS_SUBMITTED,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun usdLabel(): String = String.format(java.util.Locale.US, "$%.2f", usdCents / 100.0)

    companion object {
        const val METHOD_INSTANT_PAYOUT = "instant_payout"
        const val METHOD_PAYPAL = "paypal"
        const val METHOD_BANK_ACH = "bank_ach"

        const val STATUS_SUBMITTED = "submitted"
        const val STATUS_COMPLETED = "completed"
    }
}

@IgnoreExtraProperties
data class UserStory(
    @DocumentId val id: String = "",
    val storyId: String = "",
    val userId: String = "",
    val userName: String = "",
    val storyType: String = TYPE_PHOTO,
    val text: String = "",
    val mediaBase64: String? = null,
    val mediaDownloadUrl: String? = null,
    val storagePath: String? = null,
    val backgroundStyle: String = STYLE_SUNSET,
    val viewCount: Long = 0L,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isPhoto: Boolean
        get() = storyType == TYPE_PHOTO

    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        (expiresAt?.toDate()?.time ?: Long.MAX_VALUE) > nowMillis

    companion object {
        const val TYPE_PHOTO = "photo"
        const val TYPE_MESSAGE = "message"

        const val STYLE_SUNSET = "sunset"
        const val STYLE_OCEAN = "ocean"
        const val STYLE_MIDNIGHT = "midnight"
        const val STYLE_NEON = "neon"

        val styles = listOf(STYLE_SUNSET, STYLE_OCEAN, STYLE_MIDNIGHT, STYLE_NEON)
        const val STORY_DURATION_MILLIS = 24L * 60L * 60L * 1000L
    }
}

@IgnoreExtraProperties
data class UserCheckIn(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val placeLabel: String = "",
    val statusNote: String = "",
    val vibeTag: String = VIBE_CREATING,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        (expiresAt?.toDate()?.time ?: Long.MAX_VALUE) > nowMillis

    companion object {
        const val VIBE_HANGING_OUT = "Hanging Out"
        const val VIBE_CREATING = "Creating"
        const val VIBE_COFFEE = "Coffee Break"
        const val VIBE_STUDIO = "In Studio"
        const val VIBE_LIVE = "Going Live"
        const val VIBE_CHILLING = "Chilling"

        val presetVibes = listOf(
            VIBE_CREATING,
            VIBE_STUDIO,
            VIBE_COFFEE,
            VIBE_HANGING_OUT,
            VIBE_LIVE,
            VIBE_CHILLING
        )
        val presetPlaces = listOf(
            "Home Studio",
            "Downtown Coffee",
            "Editing Lounge",
            "City Rooftop",
            "Creative Hub",
            "On Set"
        )
        const val CHECKIN_DURATION_MILLIS = 12L * 60L * 60L * 1000L
    }
}

@IgnoreExtraProperties
data class AdminAnnouncement(
    @DocumentId val id: String = "",
    val announcementId: String = "",
    val category: String = CATEGORY_NEWS,
    val title: String = "",
    val message: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = UserProfile.ROLE_ADMIN,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val isAlert: Boolean
        get() = category == CATEGORY_ALERT

    companion object {
        const val CATEGORY_ALERT = "alert"
        const val CATEGORY_NEWS = "news"
    }
}

@IgnoreExtraProperties
data class UserWarning(
    @DocumentId val id: String = "",
    val warningId: String = "",
    val targetUserId: String = "",
    val targetUserName: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = UserProfile.ROLE_SUPPORT,
    val severity: String = SEVERITY_WARNING,
    val title: String = "",
    val message: String = "",
    val acknowledged: Boolean = false,
    val acknowledgedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    companion object {
        const val SEVERITY_REMINDER = "reminder"
        const val SEVERITY_WARNING = "warning"
        const val SEVERITY_FINAL_WARNING = "final_warning"

        val severities = listOf(
            SEVERITY_REMINDER to "Friendly Reminder",
            SEVERITY_WARNING to "Official Warning",
            SEVERITY_FINAL_WARNING to "Final Warning"
        )
    }
}

@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val blockedUserId: String = "",
    val blockedUserName: String = "",
    val reason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)



