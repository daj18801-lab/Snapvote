package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminAnnouncement
import com.example.data.model.BanRequest
import com.example.data.model.BlockedUser
import com.example.data.model.ChatBounce
import com.example.data.model.ChatMessage
import com.example.data.model.ChatModerationStatus
import com.example.data.model.ChatPresence
import com.example.data.model.ChatRoomState
import com.example.data.model.ContentReport
import com.example.data.model.CreatorPayout
import com.example.data.model.CreatorSummary
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
import com.example.data.repository.InsufficientPhoneTokensException
import com.example.data.repository.InsufficientVideoTokensException
import com.example.data.repository.InsufficientVotesException
import com.example.data.repository.PrivateChatPassRequiredException
import com.example.data.repository.SnapVoteRepository
import com.example.media.MicAudioEngine
import com.example.media.ProcessedMedia
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ReportUserTarget(
    val userId: String,
    val displayName: String,
    val sourceContext: String
)

data class VideoRequestTarget(
    val threadId: String,
    val creatorId: String,
    val creatorName: String,
    val isOnline: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class SnapVoteViewModel(
    private val repository: SnapVoteRepository,
    val currentUserId: String,
    val currentDisplayName: String,
    val currentUserEmail: String?
) : ViewModel() {

    private val _isActionBusy = MutableStateFlow(false)
    val isActionBusy: StateFlow<Boolean> = _isActionBusy.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _showBuyVotesSheet = MutableStateFlow(false)
    val showBuyVotesSheet: StateFlow<Boolean> = _showBuyVotesSheet.asStateFlow()

    private val _pendingUnlockCreator = MutableStateFlow<CreatorSummary?>(null)
    val pendingUnlockCreator: StateFlow<CreatorSummary?> = _pendingUnlockCreator.asStateFlow()

    private val _pendingUnlockDialogCreator = MutableStateFlow<CreatorSummary?>(null)
    val pendingUnlockDialogCreator: StateFlow<CreatorSummary?> = _pendingUnlockDialogCreator.asStateFlow()

    private val _activeRoomId = MutableStateFlow<String?>(null)
    val activeRoomId: StateFlow<String?> = _activeRoomId.asStateFlow()

    private val roomEnteredAtMillis = MutableStateFlow(0L)

    private val _isRoomAudioMutedLocally = MutableStateFlow(false)
    val isRoomAudioMutedLocally: StateFlow<Boolean> = _isRoomAudioMutedLocally.asStateFlow()

    private val _isStaffInvisible = MutableStateFlow(false)
    val isStaffInvisible: StateFlow<Boolean> = _isStaffInvisible.asStateFlow()

    private val micAudioEngine = MicAudioEngine()

    private val _showBuyPhoneTokensSheet = MutableStateFlow(false)
    val showBuyPhoneTokensSheet: StateFlow<Boolean> = _showBuyPhoneTokensSheet.asStateFlow()

    private val _pendingMicScheduleCreator = MutableStateFlow<CreatorSummary?>(null)
    val pendingMicScheduleCreator: StateFlow<CreatorSummary?> = _pendingMicScheduleCreator.asStateFlow()

    private val _activeDmThreadId = MutableStateFlow<String?>(null)
    val activeDmThreadId: StateFlow<String?> = _activeDmThreadId.asStateFlow()

    private val _activeGroupId = MutableStateFlow<String?>(null)
    val activeGroupId: StateFlow<String?> = _activeGroupId.asStateFlow()

    private val _activeMicSessionId = MutableStateFlow<String?>(null)
    val activeMicSessionId: StateFlow<String?> = _activeMicSessionId.asStateFlow()

    private val _showBuyVideoTokensSheet = MutableStateFlow(false)
    val showBuyVideoTokensSheet: StateFlow<Boolean> = _showBuyVideoTokensSheet.asStateFlow()

    private val _pendingVideoRequestTarget = MutableStateFlow<VideoRequestTarget?>(null)
    val pendingVideoRequestTarget: StateFlow<VideoRequestTarget?> = _pendingVideoRequestTarget.asStateFlow()

    private val _activeVideoSessionId = MutableStateFlow<String?>(null)
    val activeVideoSessionId: StateFlow<String?> = _activeVideoSessionId.asStateFlow()

    private val _pendingReportTarget = MutableStateFlow<ReportUserTarget?>(null)
    val pendingReportTarget: StateFlow<ReportUserTarget?> = _pendingReportTarget.asStateFlow()

    private val _showSupportTicketsSheet = MutableStateFlow(false)
    val showSupportTicketsSheet: StateFlow<Boolean> = _showSupportTicketsSheet.asStateFlow()

    val micLevel: StateFlow<Float> = micAudioEngine.micLevel
    val isMicCapturing: StateFlow<Boolean> = micAudioEngine.isCapturing

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

    private val claimedRefundSessionIds = mutableSetOf<String>()
    private val claimedRefundVideoSessionIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            repository.ensureUserAccountInitialized(currentDisplayName)
                .onSuccess {
                    repository.setUserOnlineStatus(true)
                }
                .onFailure { error ->
                    Log.w(TAG, "Account init failed", error)
                    _toastMessages.tryEmit(error.localizedMessage ?: "Failed to sync profile")
                }
        }
        // Automatically reclaim escrowed phone tokens whenever a mic session is declined or cancelled
        viewModelScope.launch {
            repository.observeMicSessions(currentUserId)
                .catch { error -> Log.w(TAG, "Mic refund observer error", error) }
                .collect { sessions ->
                    sessions.forEach { session ->
                        if (session.requesterId == currentUserId &&
                            session.escrowStatus == MicSession.ESCROW_REFUND_PENDING &&
                            claimedRefundSessionIds.add(session.sessionId)
                        ) {
                            repository.claimMicSessionRefund(session)
                                .onSuccess {
                                    _toastMessages.tryEmit(
                                        "Refunded +${session.totalTokensCost} phone tokens from ${session.creatorName} call"
                                    )
                                }
                                .onFailure {
                                    claimedRefundSessionIds.remove(session.sessionId)
                                }
                        }
                    }
                }
        }
        // Feed remote mic audio chunks to AudioTrack when inside an active mic call room
        viewModelScope.launch {
            _activeMicSessionId.flatMapLatest { sessionId ->
                if (sessionId == null) flowOf(emptyList())
                else repository.observeMicAudioChunks(sessionId).catch { emit(emptyList()) }
            }.collect { chunks ->
                chunks.forEach { chunk ->
                    if (chunk.senderId != currentUserId && chunk.audioBase64.isNotBlank()) {
                        micAudioEngine.playIncomingChunk(chunk.chunkId.ifBlank { chunk.id }, chunk.audioBase64)
                    }
                }
            }
        }
        // Feed remote chat room voice chunks to AudioTrack when inside an active chat room
        viewModelScope.launch {
            combine(_activeRoomId, roomEnteredAtMillis) { roomId, enteredAt ->
                roomId to enteredAt
            }.flatMapLatest { (roomId, enteredAt) ->
                if (roomId == null || enteredAt <= 0L) flowOf(emptyList())
                else repository.observeRoomVoiceChunks(roomId, enteredAt).catch { emit(emptyList()) }
            }.collect { chunks ->
                if (!_isRoomAudioMutedLocally.value) {
                    chunks.forEach { chunk ->
                        if (chunk.senderId != currentUserId && chunk.audioBase64.isNotBlank()) {
                            micAudioEngine.playIncomingChunk(chunk.chunkId.ifBlank { chunk.id }, chunk.audioBase64)
                        }
                    }
                }
            }
        }
        // Automatically reclaim escrowed video tokens whenever a private video request is declined or cancelled
        viewModelScope.launch {
            repository.observeVideoSessions(currentUserId)
                .catch { error -> Log.w(TAG, "Video refund observer error", error) }
                .collect { sessions ->
                    sessions.forEach { session ->
                        if (session.requesterId == currentUserId &&
                            session.escrowStatus == VideoSession.ESCROW_REFUND_PENDING &&
                            claimedRefundVideoSessionIds.add(session.sessionId)
                        ) {
                            repository.claimVideoSessionRefund(session)
                                .onSuccess {
                                    _toastMessages.tryEmit(
                                        "Refunded +${session.totalTokensCost} video tokens from ${session.creatorName} video request"
                                    )
                                }
                                .onFailure {
                                    claimedRefundVideoSessionIds.remove(session.sessionId)
                                }
                        }
                    }
                }
        }
        // Feed remote video call audio chunks to AudioTrack when inside an active private video call
        viewModelScope.launch {
            _activeVideoSessionId.flatMapLatest { sessionId ->
                if (sessionId == null) flowOf(emptyList())
                else repository.observeVideoAudioChunks(sessionId).catch { emit(emptyList()) }
            }.collect { chunks ->
                chunks.forEach { chunk ->
                    if (chunk.senderId != currentUserId && chunk.audioBase64.isNotBlank()) {
                        micAudioEngine.playIncomingChunk(chunk.chunkId.ifBlank { chunk.id }, chunk.audioBase64)
                    }
                }
            }
        }
    }

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load wallet"))) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val postsState: StateFlow<UiState<List<MediaPost>>> =
        repository.observePublicPosts()
            .map<List<MediaPost>, UiState<List<MediaPost>>> { UiState.Success(it) }
            .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load feed"))) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val creatorsState: StateFlow<UiState<List<CreatorSummary>>> =
        repository.observePublicCreators()
            .map<List<CreatorSummary>, UiState<List<CreatorSummary>>> { UiState.Success(it) }
            .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load creators"))) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val unlockedCreatorIds: StateFlow<Set<String>> =
        repository.observeUnlockedCreatorIds(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing unlocked creators", error)
                emit(emptySet())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptySet()
            )

    val purchaseHistory: StateFlow<List<VotePurchase>> =
        repository.observePurchaseHistory(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing purchases", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val myReferralRecord: StateFlow<ReferralRecord?> =
        repository.observeMyReferralRecord(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing my referral record", error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null
            )

    val referralsMade: StateFlow<List<ReferralRecord>> =
        repository.observeReferralsMadeBy(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing referrals made", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val referralClaims: StateFlow<List<ReferralClaim>> =
        repository.observeMyReferralClaims(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing referral claims", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val referralCashouts: StateFlow<List<ReferralCashout>> =
        repository.observeMyReferralCashouts(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing referral cashouts", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val staffTier: StateFlow<EffectiveStaffTier> =
        repository.observeEffectiveStaffTier()
            .catch { error ->
                Log.w(TAG, "Error observing staff tier", error)
                emit(EffectiveStaffTier.NONE)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = EffectiveStaffTier.NONE
            )

    val allUsersState: StateFlow<UiState<List<UserProfile>>> =
        staffTier.flatMapLatest { tier ->
            if (tier != EffectiveStaffTier.NONE) {
                repository.observeAllUsers()
                    .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
                    .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load user directory"))) }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val staffRolesState: StateFlow<UiState<List<StaffRole>>> =
        staffTier.flatMapLatest { tier ->
            if (tier == EffectiveStaffTier.OWNER || tier == EffectiveStaffTier.ADMIN) {
                repository.observeAllStaffRoles()
                    .map<List<StaffRole>, UiState<List<StaffRole>>> { UiState.Success(it) }
                    .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load staff team"))) }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val moderationReportsState: StateFlow<UiState<List<ContentReport>>> =
        staffTier.flatMapLatest { tier ->
            if (tier != EffectiveStaffTier.NONE) {
                repository.observeModerationReports()
                    .map<List<ContentReport>, UiState<List<ContentReport>>> { UiState.Success(it) }
                    .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load moderation queue"))) }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val userReportsState: StateFlow<UiState<List<UserReport>>> =
        staffTier.flatMapLatest { tier ->
            if (tier != EffectiveStaffTier.NONE) {
                repository.observeUserReports()
                    .map<List<UserReport>, UiState<List<UserReport>>> { UiState.Success(it) }
                    .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load user reports"))) }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val mySupportTicketsState: StateFlow<UiState<List<SupportTicket>>> =
        repository.observeMySupportTickets(currentUserId)
            .map<List<SupportTicket>, UiState<List<SupportTicket>>> { UiState.Success(it) }
            .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load your support tickets"))) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val allSupportTicketsState: StateFlow<UiState<List<SupportTicket>>> =
        staffTier.flatMapLatest { tier ->
            if (tier != EffectiveStaffTier.NONE) {
                repository.observeAllSupportTickets()
                    .map<List<SupportTicket>, UiState<List<SupportTicket>>> { UiState.Success(it) }
                    .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load support tickets"))) }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val mySupportPayrollState: StateFlow<List<SupportPayrollPayout>> =
        repository.observeMySupportPayroll(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val allSupportPayrollState: StateFlow<List<SupportPayrollPayout>> =
        staffTier.flatMapLatest { tier ->
            if (tier == EffectiveStaffTier.OWNER || tier == EffectiveStaffTier.ADMIN) {
                repository.observeAllSupportPayroll().catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val banRequestsState: StateFlow<UiState<List<BanRequest>>> =
        staffTier.flatMapLatest { tier ->
            if (tier != EffectiveStaffTier.NONE) {
                repository.observeBanRequests()
                    .map<List<BanRequest>, UiState<List<BanRequest>>> { UiState.Success(it) }
                    .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load ban requests"))) }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val allChatModerationsState: StateFlow<UiState<List<ChatModerationStatus>>> =
        staffTier.flatMapLatest { tier ->
            if (tier != EffectiveStaffTier.NONE) {
                repository.observeAllChatModerations()
                    .map<List<ChatModerationStatus>, UiState<List<ChatModerationStatus>>> { UiState.Success(it) }
                    .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load chat moderation list"))) }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val myChatModeration: StateFlow<ChatModerationStatus?> =
        repository.observeMyChatModeration(currentUserId)
            .catch { emit(null) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null
            )

    val myRoomBounce: StateFlow<ChatBounce?> =
        _activeRoomId.flatMapLatest { roomId ->
            if (roomId == null) {
                flowOf(null)
            } else {
                repository.observeMyRoomBounce(roomId, currentUserId)
                    .catch { emit(null) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val roomMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        _activeRoomId.flatMapLatest { roomId ->
            if (roomId == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeRoomMessages(roomId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Unable to read room messages"))) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val roomState: StateFlow<ChatRoomState?> =
        _activeRoomId.flatMapLatest { roomId ->
            if (roomId == null) {
                flowOf(null)
            } else {
                repository.observeRoomState(roomId)
                    .catch { error ->
                        Log.w(TAG, "Error observing room state in $roomId", error)
                        emit(null)
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val roomPresenceState: StateFlow<List<ChatPresence>> =
        combine(_activeRoomId, staffTier) { roomId, tier ->
            roomId to (tier == EffectiveStaffTier.OWNER || tier == EffectiveStaffTier.ADMIN)
        }.flatMapLatest { (roomId, isAdmin) ->
            if (roomId == null) {
                flowOf(emptyList())
            } else {
                repository.observeRoomPresence(roomId, isAdmin)
                    .catch { error ->
                        Log.w(TAG, "Error observing presence in $roomId", error)
                        emit(emptyList())
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val phonePurchaseHistory: StateFlow<List<PhoneTokenPurchase>> =
        repository.observePhonePurchaseHistory(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing phone purchases", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val creatorPayouts: StateFlow<List<CreatorPayout>> =
        repository.observeCreatorPayouts(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing creator payouts", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val friendRelationships: StateFlow<List<FriendRelationship>> =
        repository.observeFriendRelationships(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing friends", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val directChatThreads: StateFlow<List<DirectChatThread>> =
        repository.observeDirectChatThreads(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing DM threads", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val dmMessagesState: StateFlow<UiState<List<DirectMessage>>> =
        _activeDmThreadId.flatMapLatest { threadId ->
            if (threadId == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeDirectMessages(threadId)
                    .map<List<DirectMessage>, UiState<List<DirectMessage>>> { UiState.Success(it) }
                    .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Unable to load messages"))) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val groupChats: StateFlow<List<PrivateGroupChat>> =
        repository.observeGroupChats(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing group chats", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val groupMessagesState: StateFlow<UiState<List<GroupChatMessage>>> =
        _activeGroupId.flatMapLatest { groupId ->
            if (groupId == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeGroupMessages(groupId)
                    .map<List<GroupChatMessage>, UiState<List<GroupChatMessage>>> { UiState.Success(it) }
                    .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Unable to load group chat"))) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Success(emptyList())
        )

    val micSessions: StateFlow<List<MicSession>> =
        repository.observeMicSessions(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing mic sessions", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val micAudioChunks: StateFlow<List<MicAudioChunk>> =
        _activeMicSessionId.flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(emptyList())
            } else {
                repository.observeMicAudioChunks(sessionId)
                    .catch { error ->
                        Log.w(TAG, "Error observing audio chunks", error)
                        emit(emptyList())
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val videoPurchaseHistory: StateFlow<List<VideoTokenPurchase>> =
        repository.observeVideoPurchaseHistory(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing video token purchases", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val videoSessions: StateFlow<List<VideoSession>> =
        repository.observeVideoSessions(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing video sessions", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val videoFrameChunks: StateFlow<List<VideoFrameChunk>> =
        _activeVideoSessionId.flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(emptyList())
            } else {
                repository.observeVideoFrameChunks(sessionId)
                    .catch { error ->
                        Log.w(TAG, "Error observing video frames", error)
                        emit(emptyList())
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val videoAudioChunks: StateFlow<List<MicAudioChunk>> =
        _activeVideoSessionId.flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(emptyList())
            } else {
                repository.observeVideoAudioChunks(sessionId)
                    .catch { error ->
                        Log.w(TAG, "Error observing video audio chunks", error)
                        emit(emptyList())
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val activeStories: StateFlow<List<UserStory>> =
        repository.observeActiveStories()
            .catch { error ->
                Log.w(TAG, "Error observing active stories", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val activeCheckIns: StateFlow<List<UserCheckIn>> =
        repository.observeActiveCheckIns()
            .catch { error ->
                Log.w(TAG, "Error observing active check-ins", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val adminAnnouncements: StateFlow<List<AdminAnnouncement>> =
        repository.observeAdminAnnouncements()
            .catch { error ->
                Log.w(TAG, "Error observing admin announcements", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val myWarnings: StateFlow<List<UserWarning>> =
        repository.observeMyWarnings(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing user warnings", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val blockedUsersState: StateFlow<UiState<List<BlockedUser>>> =
        repository.observeBlockedUsers(currentUserId)
            .map<List<BlockedUser>, UiState<List<BlockedUser>>> { UiState.Success(it) }
            .catch { error -> emit(UiState.Error(unwrapErrorMessage(error, "Failed to load blocked users"))) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val blockedUsers: StateFlow<List<BlockedUser>> =
        blockedUsersState
            .map { (it as? UiState.Success)?.data.orEmpty() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val blockedUserIds: StateFlow<Set<String>> =
        blockedUsers
            .map { list -> list.mapNotNull { it.blockedUserId.takeIf(String::isNotBlank) }.toSet() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptySet()
            )

    fun openUnlockGalleryDialog(creator: CreatorSummary) {
        _pendingUnlockDialogCreator.value = creator
    }

    fun dismissUnlockGalleryDialog() {
        _pendingUnlockDialogCreator.value = null
    }

    fun openBuyVotesSheet(creatorToUnlock: CreatorSummary? = null) {
        _pendingUnlockDialogCreator.value = null
        _pendingUnlockCreator.value = creatorToUnlock
        _showBuyVotesSheet.value = true
    }

    fun dismissBuyVotesSheet() {
        _showBuyVotesSheet.value = false
    }

    fun castVote(post: MediaPost) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.castVoteOnPost(post)
                .onSuccess {
                    _toastMessages.tryEmit("Voted +1 for ${post.creatorName}!")
                }
                .onFailure { error ->
                    if (error is InsufficientVotesException) {
                        _toastMessages.tryEmit("Out of votes! Buy a vote pack to keep voting.")
                        openBuyVotesSheet()
                    } else {
                        _toastMessages.tryEmit(error.localizedMessage ?: "Vote failed")
                    }
                }
            _isActionBusy.value = false
        }
    }

    fun unlockCreatorGallery(creator: CreatorSummary) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.unlockCreatorGallery(creator.creatorId)
                .onSuccess {
                    _pendingUnlockCreator.value = null
                    _pendingUnlockDialogCreator.value = null
                    _toastMessages.tryEmit("Unlocked ${creator.displayName}'s full gallery!")
                }
                .onFailure { error ->
                    if (error is InsufficientVotesException) {
                        _toastMessages.tryEmit(
                            if (error.paidTokensOnly) {
                                "Need ${error.requiredVotes} paid tokens to unlock ${creator.displayName} (free 100 sign-up tokens can't pay creators)."
                            } else {
                                "Need ${error.requiredVotes} votes to unlock ${creator.displayName}'s gallery."
                            }
                        )
                        openBuyVotesSheet(creator)
                    } else {
                        _toastMessages.tryEmit(error.localizedMessage ?: "Unlock failed")
                    }
                }
            _isActionBusy.value = false
        }
    }

    fun buyVotePack(pack: VotePack) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.buyVotePack(pack)
                .onSuccess {
                    val pending = _pendingUnlockCreator.value
                    _showBuyVotesSheet.value = false
                    if (pending != null) {
                        _pendingUnlockDialogCreator.value = pending
                        _toastMessages.tryEmit("+${pack.votes} paid tokens added! Confirm to unlock ${pending.displayName}'s gallery.")
                    } else {
                        _toastMessages.tryEmit("Added +${pack.votes} votes to your wallet!")
                    }
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Purchase failed")
                }
            _isActionBusy.value = false
        }
    }

    fun buyChatAccessPass() {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.buyChatAccessPass()
                .onSuccess {
                    _toastMessages.tryEmit("5-day Chat Room Pass unlocked for 5 vote coins!")
                }
                .onFailure { error ->
                    if (error is InsufficientVotesException) {
                        _toastMessages.tryEmit("Need ${error.requiredVotes} vote coins to unlock 5 days of chat rooms.")
                        openBuyVotesSheet()
                    } else {
                        _toastMessages.tryEmit(error.localizedMessage ?: "Could not unlock chat pass")
                    }
                }
            _isActionBusy.value = false
        }
    }

    fun uploadPost(
        media: ProcessedMedia,
        caption: String,
        context: Context? = null,
        onComplete: () -> Unit
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            _uploadProgress.value = 0.05f
            val profileName = (userProfileState.value as? UiState.Success)?.data?.displayName
                ?: currentDisplayName
            repository.uploadPost(
                creatorName = profileName,
                mediaType = media.mediaType,
                caption = caption,
                mediaBase64 = media.mediaBase64,
                localVideoFileName = media.localVideoFileName,
                durationMs = media.durationMs,
                uploadPhotoFilePath = media.uploadPhotoFilePath,
                context = context,
                onProgress = { progress -> _uploadProgress.value = progress }
            ).onSuccess {
                _toastMessages.tryEmit("Published to SnapVote!")
                onComplete()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Upload failed")
            }
            _uploadProgress.value = null
            _isActionBusy.value = false
        }
    }

    fun deletePost(post: MediaPost) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.deletePost(post)
                .onSuccess { _toastMessages.tryEmit("Post removed") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Delete failed") }
            _isActionBusy.value = false
        }
    }

    fun updateProfile(
        displayName: String,
        bio: String,
        age: Int?,
        sex: String?,
        onComplete: () -> Unit
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.updateProfile(displayName, bio, age, sex)
                .onSuccess {
                    _toastMessages.tryEmit("Profile updated")
                    onComplete()
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Update failed")
                }
            _isActionBusy.value = false
        }
    }

    fun submitReport(post: MediaPost, reason: String) {
        viewModelScope.launch {
            repository.submitReport(post, reason)
                .onSuccess { _toastMessages.tryEmit("Report sent to Support & Admins") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Report failed") }
        }
    }

    fun resolveReport(
        report: ContentReport,
        status: String,
        hidePost: Boolean,
        note: String = ""
    ) {
        viewModelScope.launch {
            repository.resolveReport(report, status, hidePost, note)
                .onSuccess {
                    val msg = if (hidePost) "Post hidden & report resolved" else "Report marked $status"
                    _toastMessages.tryEmit(msg)
                }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Action failed") }
        }
    }

    fun hideOrRestorePost(postId: String, visibility: String) {
        viewModelScope.launch {
            repository.setPostVisibility(postId, visibility)
                .onSuccess {
                    _toastMessages.tryEmit(if (visibility == "hidden") "Post hidden" else "Post restored")
                }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Action failed") }
        }
    }

    fun assignUserRole(targetUser: UserProfile, newRole: String) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.assignUserRole(targetUser, newRole)
                .onSuccess {
                    val label = when (newRole) {
                        UserProfile.ROLE_ADMIN -> "Admin"
                        UserProfile.ROLE_SUPPORT -> "Support"
                        else -> "User"
                    }
                    _toastMessages.tryEmit("${targetUser.displayName} is now $label")
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Role update failed")
                }
            _isActionBusy.value = false
        }
    }

    fun appointOrUpdateStaff(
        email: String,
        displayLabel: String,
        role: String,
        active: Boolean,
        onComplete: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.upsertStaffRole(email, displayLabel, role, active)
                .onSuccess {
                    _toastMessages.tryEmit("Staff role saved for $email")
                    onComplete()
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Could not save staff role")
                }
            _isActionBusy.value = false
        }
    }

    fun removeStaff(email: String) {
        viewModelScope.launch {
            repository.removeStaffRole(email)
                .onSuccess { _toastMessages.tryEmit("Removed $email from staff") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Remove failed") }
        }
    }

    // --- CHAT ROOMS & MODERATION ---

    private fun currentChatRoleLabel(): String = when (staffTier.value) {
        EffectiveStaffTier.OWNER, EffectiveStaffTier.ADMIN -> UserProfile.ROLE_ADMIN
        EffectiveStaffTier.SUPPORT -> UserProfile.ROLE_SUPPORT
        EffectiveStaffTier.NONE -> UserProfile.ROLE_USER
    }

    private fun currentProfileDisplayName(): String =
        (userProfileState.value as? UiState.Success)?.data?.displayName?.takeIf { it.isNotBlank() }
            ?: currentDisplayName

    fun setStaffInvisible(invisible: Boolean) {
        val isStaff = staffTier.value != EffectiveStaffTier.NONE
        if (!isStaff) {
            _isStaffInvisible.value = false
            return
        }
        _isStaffInvisible.value = invisible
        if (invisible) {
            micAudioEngine.stopMicCapture()
        }
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.enterChatRoom(
                roomId = roomId,
                displayName = currentProfileDisplayName(),
                roleLabel = currentChatRoleLabel(),
                invisible = invisible
            ).onSuccess {
                _toastMessages.tryEmit(
                    if (invisible) "Switched to Invisible Monitor mode" else "Now visible in room members"
                )
            }
        }
    }

    fun enterChatRoom(roomId: String) {
        val tier = staffTier.value
        val isStaff = tier != EffectiveStaffTier.NONE
        if (myChatModeration.value?.banned == true && !isStaff) {
            _toastMessages.tryEmit("You are banned from chat rooms.")
            return
        }
        val profile = (userProfileState.value as? UiState.Success)?.data
        if (profile != null && !profile.hasChatAccess(isStaff)) {
            _toastMessages.tryEmit("7-day free trial ended. Unlock 5 days of chat for 5 vote coins!")
            return
        }
        val invisible = isStaff && _isStaffInvisible.value
        val initialVoiceMuted = !isStaff && myChatModeration.value?.isCurrentlyMuted() == true
        viewModelScope.launch {
            val result = repository.enterChatRoom(
                roomId = roomId,
                displayName = currentProfileDisplayName(),
                roleLabel = currentChatRoleLabel(),
                invisible = invisible,
                initialVoiceMuted = initialVoiceMuted
            )
            result.onSuccess {
                roomEnteredAtMillis.value = System.currentTimeMillis()
                _isRoomAudioMutedLocally.value = false
                _activeVideoSessionId.value = null
                _activeRoomId.value = roomId
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Cannot enter room right now")
            }
        }
    }

    fun leaveChatRoom() {
        val roomId = _activeRoomId.value ?: return
        micAudioEngine.stopMicCapture()
        _activeRoomId.value = null
        roomEnteredAtMillis.value = 0L
        viewModelScope.launch {
            repository.leaveChatRoom(roomId)
        }
    }

    fun claimRoomMonitorIfVacant() {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.claimRoomMonitorIfVacant(roomId, currentProfileDisplayName())
        }
    }

    fun startRoomVoiceTalk(captureScope: CoroutineScope) {
        val roomId = _activeRoomId.value ?: return
        val isStaff = staffTier.value != EffectiveStaffTier.NONE
        if (isStaff && _isStaffInvisible.value) {
            _toastMessages.tryEmit("Disable Invisible mode to talk on room voice.")
            return
        }
        val myPresence = roomPresenceState.value.firstOrNull { it.userId == currentUserId }
        if (myPresence?.isVoiceMuted == true || (!isStaff && myChatModeration.value?.isCurrentlyMuted() == true)) {
            _toastMessages.tryEmit("You are muted and cannot use the mic or send chat messages.")
            return
        }
        val senderName = currentProfileDisplayName()
        viewModelScope.launch {
            repository.setRoomSpeaking(roomId, true)
        }
        micAudioEngine.startMicCapture(captureScope) { b64, durationMs, seq ->
            repository.sendRoomVoiceChunk(
                roomId = roomId,
                senderName = senderName,
                audioBase64 = b64,
                durationMs = durationMs,
                sequenceNumber = seq
            )
        }
    }

    fun stopRoomVoiceTalk() {
        micAudioEngine.stopMicCapture()
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.setRoomSpeaking(roomId, false)
        }
    }

    fun toggleRoomHandRaised() {
        val roomId = _activeRoomId.value ?: return
        val myPresence = roomPresenceState.value.firstOrNull { it.userId == currentUserId }
        val nextRaised = !(myPresence?.handRaised ?: false)
        viewModelScope.launch {
            repository.setRoomHandRaised(roomId, nextRaised)
                .onSuccess {
                    _toastMessages.tryEmit(if (nextRaised) "Hand raised" else "Hand lowered")
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Could not update hand state")
                }
        }
    }

    fun toggleRoomLocalListenMute() {
        val next = !_isRoomAudioMutedLocally.value
        _isRoomAudioMutedLocally.value = next
        _toastMessages.tryEmit(if (next) "Room audio muted locally" else "Room audio unmuted")
    }

    fun setRoomMemberVoiceMuted(targetUserId: String, targetName: String, muted: Boolean) {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.setRoomMemberVoiceMuted(roomId, targetUserId, muted)
                .onSuccess {
                    _toastMessages.tryEmit(
                        if (muted) "Muted $targetName's mic" else "Unmuted $targetName's mic"
                    )
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Could not update mic mute")
                }
        }
    }

    fun lowerRoomMemberHand(targetUserId: String, targetName: String) {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.lowerRoomMemberHand(roomId, targetUserId)
                .onSuccess {
                    _toastMessages.tryEmit("Lowered $targetName's hand")
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Could not lower hand")
                }
        }
    }

    fun sendChatMessage(text: String, onSent: () -> Unit) {
        val roomId = _activeRoomId.value ?: return
        val isAdmin = staffTier.value == EffectiveStaffTier.OWNER || staffTier.value == EffectiveStaffTier.ADMIN
        val myPresenceMuted = roomPresenceState.value.firstOrNull { it.userId == currentUserId }?.isVoiceMuted == true
        if (!isAdmin && (myPresenceMuted || myChatModeration.value?.isCurrentlyMuted() == true)) {
            _toastMessages.tryEmit("You are muted and cannot use the mic or send chat messages.")
            return
        }
        viewModelScope.launch {
            repository.sendChatMessage(
                roomId = roomId,
                senderName = currentProfileDisplayName(),
                senderRole = currentChatRoleLabel(),
                text = text
            ).onSuccess {
                onSent()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Message failed to send")
            }
        }
    }

    fun deleteChatMessage(messageId: String) {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.deleteChatMessage(roomId, messageId)
                .onSuccess { _toastMessages.tryEmit("Message deleted") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Delete failed") }
        }
    }

    fun bounceUserFromActiveRoom(targetUserId: String, targetName: String, durationMinutes: Long = 5L) {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.bounceUserFromRoom(
                roomId = roomId,
                targetUserId = targetUserId,
                reason = "Bounced from room by moderator",
                durationMinutes = durationMinutes
            ).onSuccess {
                _toastMessages.tryEmit("Bounced $targetName from room for ${durationMinutes}m")
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Bounce failed")
            }
        }
    }

    fun muteUser(targetUserId: String, targetName: String, durationMinutes: Long) {
        viewModelScope.launch {
            repository.muteUserInChat(
                targetUserId = targetUserId,
                targetDisplayName = targetName,
                durationMinutes = durationMinutes,
                reason = "Muted by staff"
            ).onSuccess {
                val activeRoom = _activeRoomId.value
                if (activeRoom != null && targetUserId != currentUserId) {
                    repository.setRoomMemberVoiceMuted(activeRoom, targetUserId, true)
                }
                val label = when {
                    durationMinutes >= 60L -> "${durationMinutes / 60L}h"
                    else -> "${durationMinutes}m"
                }
                _toastMessages.tryEmit("Muted $targetName for $label")
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Mute failed")
            }
        }
    }

    fun unmuteUser(targetUserId: String) {
        viewModelScope.launch {
            repository.unmuteUserInChat(targetUserId)
                .onSuccess {
                    val activeRoom = _activeRoomId.value
                    if (activeRoom != null && targetUserId != currentUserId) {
                        repository.setRoomMemberVoiceMuted(activeRoom, targetUserId, false)
                    }
                    _toastMessages.tryEmit("User unmuted")
                }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Unmute failed") }
        }
    }

    fun banUserFromChat(targetUserId: String, targetName: String) {
        viewModelScope.launch {
            if (staffTier.value == EffectiveStaffTier.SUPPORT) {
                repository.requestUserBanBySupport(
                    targetUserId = targetUserId,
                    targetDisplayName = targetName,
                    requesterName = currentProfileDisplayName(),
                    banScope = BanRequest.SCOPE_CHAT,
                    reason = "Chat ban requested by Support"
                ).onSuccess {
                    _toastMessages.tryEmit("Suspended $targetName & sent chat ban request to Admins for approval")
                }.onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Ban request failed")
                }
                return@launch
            }
            repository.banUserFromChat(
                targetUserId = targetUserId,
                targetDisplayName = targetName,
                reason = "Banned from chat rooms by staff"
            ).onSuccess {
                _activeRoomId.value?.let { roomId ->
                    repository.bounceUserFromRoom(
                        roomId = roomId,
                        targetUserId = targetUserId,
                        reason = "Banned from chat",
                        durationMinutes = 60L
                    )
                }
                _toastMessages.tryEmit("Banned $targetName from chat rooms")
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Ban failed")
            }
        }
    }

    fun unbanUserFromChat(targetUserId: String) {
        viewModelScope.launch {
            repository.unbanUserFromChat(targetUserId)
                .onSuccess { _toastMessages.tryEmit("User unbanned from chat") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Unban failed") }
        }
    }

    fun bounceUserEverywhere(
        targetUserId: String,
        targetName: String,
        durationMinutes: Long = 15L,
        reason: String = "Bounced from chat rooms by staff"
    ) {
        viewModelScope.launch {
            repository.bounceUserFromAllRooms(targetUserId, durationMinutes, reason)
                .onSuccess {
                    _toastMessages.tryEmit("Bounced $targetName from all chat rooms for ${durationMinutes}m")
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Bounce failed")
                }
        }
    }

    fun suspendUser(
        targetUserId: String,
        targetName: String,
        durationHours: Long,
        reason: String = "Suspended by moderation team"
    ) {
        viewModelScope.launch {
            repository.suspendUserAccount(targetUserId, targetName, durationHours, reason)
                .onSuccess {
                    val label = if (durationHours >= 24L) "${durationHours / 24L}d" else "${durationHours}h"
                    _toastMessages.tryEmit("Suspended $targetName for $label")
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Suspend failed")
                }
        }
    }

    fun unsuspendUser(targetUserId: String) {
        viewModelScope.launch {
            repository.unsuspendUserAccount(targetUserId)
                .onSuccess { _toastMessages.tryEmit("Account suspension lifted") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Unsuspend failed") }
        }
    }

    fun banUserAccount(
        targetUserId: String,
        targetName: String,
        reason: String = "Account banned for policy violations"
    ) {
        viewModelScope.launch {
            if (staffTier.value == EffectiveStaffTier.SUPPORT) {
                repository.requestUserBanBySupport(
                    targetUserId = targetUserId,
                    targetDisplayName = targetName,
                    requesterName = currentProfileDisplayName(),
                    banScope = BanRequest.SCOPE_ACCOUNT,
                    reason = reason
                ).onSuccess {
                    _toastMessages.tryEmit("Suspended $targetName & sent account ban request to Admins for approval")
                }.onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Ban request failed")
                }
                return@launch
            }
            repository.banUserAccount(targetUserId, targetName, reason)
                .onSuccess {
                    repository.bounceUserFromAllRooms(targetUserId, 60L, reason)
                    _toastMessages.tryEmit("Account permanently banned for $targetName")
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Account ban failed")
                }
        }
    }

    fun unbanUserAccount(targetUserId: String) {
        viewModelScope.launch {
            repository.unbanUserAccount(targetUserId)
                .onSuccess { _toastMessages.tryEmit("Account ban lifted") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Unban failed") }
        }
    }

    fun reviewBanRequest(banRequest: BanRequest, approve: Boolean, note: String = "") {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.reviewBanRequest(
                banRequest = banRequest,
                approve = approve,
                reviewerName = currentProfileDisplayName(),
                reviewNote = note
            ).onSuccess {
                _toastMessages.tryEmit(
                    if (approve) "Approved ban for ${banRequest.targetUserName}"
                    else "Rejected ban request & lifted suspension for ${banRequest.targetUserName}"
                )
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Could not review ban request")
            }
            _isActionBusy.value = false
        }
    }

    // --- USER REPORTS, SUPPORT TICKETS & 6-MONTH SUPPORT WEEKLY PAYROLL ---

    fun openReportUserDialog(userId: String, displayName: String, sourceContext: String) {
        if (userId == currentUserId) return
        _pendingReportTarget.value = ReportUserTarget(userId, displayName, sourceContext)
    }

    fun dismissReportUserDialog() {
        _pendingReportTarget.value = null
    }

    fun submitUserReport(
        reportedUserId: String,
        reportedUserName: String,
        reason: String,
        details: String,
        sourceContext: String,
        onSubmitted: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.submitUserReport(
                reportedUserId = reportedUserId,
                reportedUserName = reportedUserName,
                reporterName = currentProfileDisplayName(),
                reason = reason,
                details = details,
                sourceContext = sourceContext
            ).onSuccess {
                _pendingReportTarget.value = null
                _toastMessages.tryEmit("Report submitted against $reportedUserName")
                onSubmitted()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Could not submit report")
            }
            _isActionBusy.value = false
        }
    }

    fun resolveUserReport(reportId: String, status: String, note: String = "") {
        viewModelScope.launch {
            repository.resolveUserReport(reportId, status, note)
                .onSuccess { _toastMessages.tryEmit("User report marked $status") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Update failed") }
        }
    }

    fun openSupportTicketsSheet() {
        _showSupportTicketsSheet.value = true
    }

    fun dismissSupportTicketsSheet() {
        _showSupportTicketsSheet.value = false
    }

    fun createSupportTicket(
        category: String,
        subject: String,
        message: String,
        onCreated: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.createSupportTicket(
                userName = currentProfileDisplayName(),
                category = category,
                subject = subject,
                message = message
            ).onSuccess {
                _toastMessages.tryEmit("Support ticket sent! Our team will respond shortly.")
                onCreated()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Could not submit ticket")
            }
            _isActionBusy.value = false
        }
    }

    fun replySupportTicket(
        ticketId: String,
        newStatus: String,
        reply: String,
        onUpdated: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.updateSupportTicketStaffReply(
                ticketId = ticketId,
                newStatus = newStatus,
                staffReply = reply,
                staffName = currentProfileDisplayName()
            ).onSuccess {
                _toastMessages.tryEmit("Support ticket updated")
                onUpdated()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Could not update ticket")
            }
            _isActionBusy.value = false
        }
    }

    fun closeOwnSupportTicket(ticketId: String) {
        viewModelScope.launch {
            repository.closeOwnSupportTicket(ticketId)
                .onSuccess { _toastMessages.tryEmit("Support ticket closed") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Close failed") }
        }
    }

    fun claimSupportWeeklyPay() {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.claimSupportWeeklyPay()
                .onSuccess { payout ->
                    _toastMessages.tryEmit(
                        "Weekly Support Stipend claimed: +${payout.tokensAwarded} tokens (${payout.usdValueLabel()} value)!"
                    )
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Not yet eligible for Support weekly pay")
                }
            _isActionBusy.value = false
        }
    }

    // --- ONLINE PRESENCE, FRIENDS, DMs & GROUP CHATS (5 TOKENS/WEEK) ---

    fun setOnlineStatus(isOnline: Boolean) {
        viewModelScope.launch {
            repository.setUserOnlineStatus(isOnline)
        }
    }

    fun buyPrivateChatPass() {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.buyPrivateChatPass()
                .onSuccess {
                    _toastMessages.tryEmit("Private DM & Group Chat Pass unlocked for 7 days (5 tokens)!")
                }
                .onFailure { error ->
                    if (error is InsufficientVotesException) {
                        _toastMessages.tryEmit("Need ${error.requiredVotes} tokens for the weekly Private Messaging Pass.")
                        openBuyVotesSheet()
                    } else {
                        _toastMessages.tryEmit(error.localizedMessage ?: "Could not unlock private pass")
                    }
                }
            _isActionBusy.value = false
        }
    }

    fun sendFriendRequest(targetUserId: String, targetName: String) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.sendFriendRequest(targetUserId, targetName, currentProfileDisplayName())
                .onSuccess { _toastMessages.tryEmit("Friend request sent to $targetName") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Friend request failed") }
            _isActionBusy.value = false
        }
    }

    fun respondToFriendRequest(pairId: String, accept: Boolean) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.respondToFriendRequest(pairId, accept)
                .onSuccess {
                    _toastMessages.tryEmit(if (accept) "Friend added!" else "Request declined")
                }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Action failed") }
            _isActionBusy.value = false
        }
    }

    fun removeFriend(pairId: String, label: String) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.removeFriendRelationship(pairId)
                .onSuccess { _toastMessages.tryEmit("Removed $label") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Remove failed") }
            _isActionBusy.value = false
        }
    }

    private fun detachActiveRoom() {
        val previousRoomId = _activeRoomId.value ?: return
        micAudioEngine.stopMicCapture()
        _activeRoomId.value = null
        roomEnteredAtMillis.value = 0L
        viewModelScope.launch {
            repository.leaveChatRoom(previousRoomId)
        }
    }

    fun openDirectChatWithUser(otherUserId: String, otherUserName: String, onOpened: () -> Unit = {}) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.openOrCreateDirectThread(otherUserId, otherUserName, currentProfileDisplayName())
                .onSuccess { threadId ->
                    detachActiveRoom()
                    _activeGroupId.value = null
                    _activeMicSessionId.value = null
                    _activeVideoSessionId.value = null
                    _activeDmThreadId.value = threadId
                    onOpened()
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Cannot open direct chat")
                }
            _isActionBusy.value = false
        }
    }

    fun openExistingDirectThread(threadId: String) {
        detachActiveRoom()
        _activeGroupId.value = null
        _activeMicSessionId.value = null
        _activeVideoSessionId.value = null
        _activeDmThreadId.value = threadId
    }

    fun closeDirectChat() {
        _activeDmThreadId.value = null
    }

    fun sendDirectMessage(text: String, onSent: () -> Unit) {
        val threadId = _activeDmThreadId.value ?: return
        viewModelScope.launch {
            repository.sendDirectMessage(threadId, currentProfileDisplayName(), text)
                .onSuccess { onSent() }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Message failed")
                }
        }
    }

    fun createGroupChat(
        name: String,
        description: String,
        privacyMode: String,
        allowMemberInvites: Boolean,
        invitedMembers: Map<String, String>,
        onCreated: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.createPrivateGroupChat(
                name = name,
                description = description,
                privacyMode = privacyMode,
                allowMemberInvites = allowMemberInvites,
                invitedMembers = invitedMembers,
                myName = currentProfileDisplayName()
            ).onSuccess { groupId ->
                detachActiveRoom()
                _activeDmThreadId.value = null
                _activeMicSessionId.value = null
                _activeVideoSessionId.value = null
                _activeGroupId.value = groupId
                _toastMessages.tryEmit("Created private group \"$name\"")
                onCreated()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Could not create group")
            }
            _isActionBusy.value = false
        }
    }

    fun openGroupChat(groupId: String) {
        detachActiveRoom()
        _activeDmThreadId.value = null
        _activeMicSessionId.value = null
        _activeVideoSessionId.value = null
        _activeGroupId.value = groupId
    }

    fun closeGroupChat() {
        _activeGroupId.value = null
    }

    fun sendGroupMessage(text: String, onSent: () -> Unit) {
        val groupId = _activeGroupId.value ?: return
        viewModelScope.launch {
            repository.sendGroupMessage(groupId, currentProfileDisplayName(), text)
                .onSuccess { onSent() }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Failed to send message")
                }
        }
    }

    fun updateGroupSettings(
        groupId: String,
        name: String,
        description: String,
        privacyMode: String,
        allowMemberInvites: Boolean,
        onSaved: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.updateGroupChatSettings(groupId, name, description, privacyMode, allowMemberInvites)
                .onSuccess {
                    _toastMessages.tryEmit("Group chat settings updated")
                    onSaved()
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Could not update settings")
                }
            _isActionBusy.value = false
        }
    }

    fun addMembersToGroup(group: PrivateGroupChat, newMembers: Map<String, String>) {
        if (newMembers.isEmpty()) return
        viewModelScope.launch {
            repository.addMembersToGroupChat(group, newMembers)
                .onSuccess { _toastMessages.tryEmit("Added ${newMembers.size} member(s) to ${group.name}") }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Could not add members")
                }
        }
    }

    fun leaveOrRemoveFromGroup(group: PrivateGroupChat, targetUserId: String) {
        viewModelScope.launch {
            repository.removeOrLeaveGroupChat(group, targetUserId)
                .onSuccess {
                    if (targetUserId == currentUserId) {
                        _activeGroupId.value = null
                        _toastMessages.tryEmit("Left ${group.name}")
                    } else {
                        _toastMessages.tryEmit("Member removed")
                    }
                }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Action failed") }
        }
    }

    fun deleteGroupChat(groupId: String) {
        viewModelScope.launch {
            repository.deleteGroupChat(groupId)
                .onSuccess {
                    _activeGroupId.value = null
                    _toastMessages.tryEmit("Private group deleted")
                }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Delete failed") }
        }
    }

    // --- PHONE TOKENS & MIC-TO-MIC CONVERSATIONS (5 TOKENS/MIN, 70% CREATOR SHARE) ---

    fun openBuyPhoneTokensSheet() {
        _showBuyPhoneTokensSheet.value = true
    }

    fun dismissBuyPhoneTokensSheet() {
        _showBuyPhoneTokensSheet.value = false
    }

    fun buyPhoneTokenPack(pack: PhoneTokenPack) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.buyPhoneTokenPack(pack)
                .onSuccess {
                    _showBuyPhoneTokensSheet.value = false
                    _toastMessages.tryEmit("Added +${pack.tokens} phone tokens to your wallet!")
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Purchase failed")
                }
            _isActionBusy.value = false
        }
    }

    fun openScheduleMicDialog(creator: CreatorSummary) {
        if (creator.creatorId == currentUserId) return
        _pendingMicScheduleCreator.value = creator
    }

    fun dismissScheduleMicDialog() {
        _pendingMicScheduleCreator.value = null
    }

    fun scheduleMicSession(
        creator: CreatorSummary,
        topic: String,
        scheduledForMillis: Long,
        durationMinutes: Long,
        onScheduled: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.scheduleMicSession(
                creatorId = creator.creatorId,
                creatorName = creator.displayName,
                requesterName = currentProfileDisplayName(),
                topic = topic,
                scheduledForMillis = scheduledForMillis,
                durationMinutes = durationMinutes
            ).onSuccess {
                _pendingMicScheduleCreator.value = null
                _toastMessages.tryEmit(
                    "Free mic call requested (${durationMinutes}m). Waiting for ${creator.displayName} to accept."
                )
                onScheduled()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Booking failed")
            }
            _isActionBusy.value = false
        }
    }

    fun respondToMicSession(session: MicSession, accept: Boolean) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.respondToMicSession(session, accept)
                .onSuccess {
                    if (accept) {
                        _toastMessages.tryEmit("Accepted mic call! Ready to join live audio.")
                    } else {
                        _toastMessages.tryEmit("Declined mic call request.")
                    }
                }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Response failed") }
            _isActionBusy.value = false
        }
    }

    fun cancelMicSession(sessionId: String) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.cancelMicSession(sessionId)
                .onSuccess { _toastMessages.tryEmit("Mic request cancelled.") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Cancel failed") }
            _isActionBusy.value = false
        }
    }

    fun enterMicCallRoom(session: MicSession) {
        detachActiveRoom()
        _activeDmThreadId.value = null
        _activeGroupId.value = null
        _activeVideoSessionId.value = null
        _activeMicSessionId.value = session.sessionId
        if (session.status == MicSession.STATUS_ACCEPTED) {
            viewModelScope.launch {
                repository.updateMicSessionCallStatus(session.sessionId, MicSession.STATUS_ACTIVE)
            }
        }
    }

    fun leaveMicCallRoom() {
        micAudioEngine.stopMicCapture()
        _activeMicSessionId.value = null
    }

    fun endMicCallSession(sessionId: String) {
        micAudioEngine.stopMicCapture()
        _activeMicSessionId.value = null
        viewModelScope.launch {
            repository.updateMicSessionCallStatus(sessionId, MicSession.STATUS_COMPLETED)
                .onSuccess { _toastMessages.tryEmit("Mic conversation completed") }
        }
    }

    fun startMicCapture(captureScope: CoroutineScope, sessionId: String) {
        val senderName = currentProfileDisplayName()
        micAudioEngine.startMicCapture(captureScope) { b64, durationMs, seq ->
            repository.sendMicAudioChunk(
                sessionId = sessionId,
                senderName = senderName,
                audioBase64 = b64,
                durationMs = durationMs,
                sequenceNumber = seq
            )
        }
    }

    fun stopMicCapture() {
        micAudioEngine.stopMicCapture()
    }

    // --- VIDEO TOKENS & PRIVATE CHAT CREATOR VIDEO SESSIONS (100 TOKENS/MIN, 70% CREATOR SHARE) ---

    fun openBuyVideoTokensSheet() {
        _showBuyVideoTokensSheet.value = true
    }

    fun dismissBuyVideoTokensSheet() {
        _showBuyVideoTokensSheet.value = false
    }

    fun buyVideoTokenPack(pack: VideoTokenPack) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.buyVideoTokenPack(pack)
                .onSuccess {
                    _showBuyVideoTokensSheet.value = false
                    _toastMessages.tryEmit("Added +${pack.tokens} video tokens to your wallet!")
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Video token purchase failed")
                }
            _isActionBusy.value = false
        }
    }

    fun openRequestVideoDialog(threadId: String, creatorId: String, creatorName: String, isOnline: Boolean) {
        if (creatorId.isBlank() || creatorId == currentUserId) return
        _pendingVideoRequestTarget.value = VideoRequestTarget(
            threadId = threadId,
            creatorId = creatorId,
            creatorName = creatorName,
            isOnline = isOnline
        )
    }

    fun dismissRequestVideoDialog() {
        _pendingVideoRequestTarget.value = null
    }

    fun requestPrivateVideoSession(
        target: VideoRequestTarget,
        note: String,
        durationMinutes: Long
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.requestPrivateVideoSession(
                threadId = target.threadId,
                creatorId = target.creatorId,
                creatorName = target.creatorName,
                requesterName = currentProfileDisplayName(),
                note = note,
                durationMinutes = durationMinutes
            ).onSuccess {
                _pendingVideoRequestTarget.value = null
                _toastMessages.tryEmit(
                    "Free live video request sent (${durationMinutes}m). Waiting for ${target.creatorName} to accept."
                )
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Video request failed")
            }
            _isActionBusy.value = false
        }
    }

    fun respondToVideoSession(session: VideoSession, accept: Boolean) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.respondToVideoSession(session, accept)
                .onSuccess {
                    if (accept) {
                        _toastMessages.tryEmit("Accepted live video! Ready to start 1-on-1 call.")
                    } else {
                        _toastMessages.tryEmit("Declined video request.")
                    }
                }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Response failed") }
            _isActionBusy.value = false
        }
    }

    fun cancelVideoSession(sessionId: String) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.cancelVideoSession(sessionId)
                .onSuccess { _toastMessages.tryEmit("Video request cancelled.") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Cancel failed") }
            _isActionBusy.value = false
        }
    }

    fun enterVideoCallRoom(session: VideoSession) {
        detachActiveRoom()
        _activeGroupId.value = null
        _activeMicSessionId.value = null
        if (_activeDmThreadId.value.isNullOrBlank() && session.threadId.isNotBlank()) {
            _activeDmThreadId.value = session.threadId
        }
        _activeVideoSessionId.value = session.sessionId
        if (session.status == VideoSession.STATUS_ACCEPTED) {
            viewModelScope.launch {
                repository.updateVideoSessionStatus(session.sessionId, VideoSession.STATUS_ACTIVE)
            }
        }
    }

    fun leaveVideoCallRoom() {
        micAudioEngine.stopMicCapture()
        _activeVideoSessionId.value = null
    }

    fun endVideoCallSession(sessionId: String) {
        micAudioEngine.stopMicCapture()
        _activeVideoSessionId.value = null
        viewModelScope.launch {
            repository.updateVideoSessionStatus(sessionId, VideoSession.STATUS_COMPLETED)
                .onSuccess { _toastMessages.tryEmit("Private video session ended") }
        }
    }

    fun sendVideoFrameChunk(
        sessionId: String,
        frameBase64: String,
        width: Int,
        height: Int,
        sequenceNumber: Long
    ) {
        val senderName = currentProfileDisplayName()
        viewModelScope.launch {
            repository.sendVideoFrameChunk(
                sessionId = sessionId,
                senderName = senderName,
                frameBase64 = frameBase64,
                width = width,
                height = height,
                sequenceNumber = sequenceNumber
            )
        }
    }

    fun startVideoAudioCapture(captureScope: CoroutineScope, sessionId: String) {
        val senderName = currentProfileDisplayName()
        micAudioEngine.startMicCapture(captureScope) { b64, durationMs, seq ->
            repository.sendVideoAudioChunk(
                sessionId = sessionId,
                senderName = senderName,
                audioBase64 = b64,
                durationMs = durationMs,
                sequenceNumber = seq
            )
        }
    }

    // --- REFERRAL PROGRAM: APPLY CODE, CLAIM 10->100 REWARD, CASH IN TOKENS ---

    fun applyReferralCode(rawCode: String) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.applyReferralCode(rawCode, currentProfileDisplayName())
                .onSuccess { record ->
                    _toastMessages.tryEmit(
                        "Referral code ${record.referralCode} linked to ${record.referrerName}! Buy any token pack to qualify."
                    )
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Failed to apply referral code")
                }
            _isActionBusy.value = false
        }
    }

    fun claimReferralReward() {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.claimReferralMilestoneReward(currentProfileDisplayName())
                .onSuccess { claim ->
                    _toastMessages.tryEmit(
                        "Claimed +${claim.tokensAwarded} tokens (${claim.cashValueUsdLabel()} cashable or usable on creators)!"
                    )
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Unable to claim referral reward")
                }
            _isActionBusy.value = false
        }
    }

    fun cashInReferralTokens(
        tokensToCashIn: Long,
        payoutMethod: String,
        payoutDestination: String,
        onSuccess: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.cashInReferralTokens(
                tokensToCashIn = tokensToCashIn,
                payoutMethod = payoutMethod,
                payoutDestination = payoutDestination,
                userName = currentProfileDisplayName()
            )
                .onSuccess { cashout ->
                    _toastMessages.tryEmit(
                        "Cashed in ${cashout.tokensCashedIn} referral tokens for ${cashout.usdLabel()}!"
                    )
                    onSuccess()
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Cash-in failed")
                }
            _isActionBusy.value = false
        }
    }

    // --- DM SNAPS, 24H STORIES, OPTIONAL CHECK-INS, ADMIN ALERTS & USER WARNINGS ---

    fun sendDirectSnap(
        threadId: String,
        caption: String,
        media: ProcessedMedia,
        durationSeconds: Long = 5L,
        onSent: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            _uploadProgress.value = 0.05f
            repository.sendDirectSnap(
                threadId = threadId,
                senderName = currentProfileDisplayName(),
                caption = caption,
                snapBase64 = media.mediaBase64,
                uploadPhotoFilePath = media.uploadPhotoFilePath,
                durationSeconds = durationSeconds,
                onProgress = { progress -> _uploadProgress.value = progress }
            ).onSuccess {
                _toastMessages.tryEmit("Snap sent!")
                onSent()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Failed to send snap")
            }
            _uploadProgress.value = null
            _isActionBusy.value = false
        }
    }

    fun markDirectSnapViewed(threadId: String, messageId: String) {
        viewModelScope.launch {
            repository.markDirectSnapViewed(threadId, messageId)
        }
    }

    fun postStory(
        storyType: String,
        text: String,
        backgroundStyle: String,
        media: ProcessedMedia? = null,
        onComplete: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            _uploadProgress.value = if (media != null) 0.05f else null
            repository.postStory(
                userName = currentProfileDisplayName(),
                storyType = storyType,
                text = text,
                backgroundStyle = backgroundStyle,
                mediaBase64 = media?.mediaBase64,
                uploadPhotoFilePath = media?.uploadPhotoFilePath,
                onProgress = { progress -> _uploadProgress.value = progress }
            ).onSuccess {
                _toastMessages.tryEmit("Story shared for 24 hours!")
                onComplete()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Could not post story")
            }
            _uploadProgress.value = null
            _isActionBusy.value = false
        }
    }

    fun markStoryViewed(story: UserStory) {
        if (story.userId == currentUserId || story.storyId.isBlank()) return
        viewModelScope.launch {
            repository.incrementStoryView(story.storyId)
        }
    }

    fun deleteStory(story: UserStory) {
        viewModelScope.launch {
            repository.deleteStory(story.storyId.ifBlank { story.id }, story.storagePath)
                .onSuccess { _toastMessages.tryEmit("Story removed") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Delete failed") }
        }
    }

    fun setCheckIn(
        placeLabel: String,
        statusNote: String,
        vibeTag: String,
        onComplete: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.setCheckIn(
                userName = currentProfileDisplayName(),
                placeLabel = placeLabel,
                statusNote = statusNote,
                vibeTag = vibeTag
            ).onSuccess {
                _toastMessages.tryEmit("Checked in as $vibeTag!")
                onComplete()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Check-in failed")
            }
            _isActionBusy.value = false
        }
    }

    fun clearCheckIn() {
        viewModelScope.launch {
            repository.clearCheckIn()
                .onSuccess { _toastMessages.tryEmit("Check-in cleared") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Clear failed") }
        }
    }

    fun sendAdminAnnouncement(
        category: String,
        title: String,
        message: String,
        onComplete: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            val role = when (staffTier.value) {
                EffectiveStaffTier.OWNER -> "owner"
                EffectiveStaffTier.ADMIN -> "admin"
                else -> "admin"
            }
            repository.sendAdminAnnouncement(
                category = category,
                title = title,
                message = message,
                senderName = currentProfileDisplayName(),
                senderRole = role
            ).onSuccess {
                val label = if (category == AdminAnnouncement.CATEGORY_ALERT) "Alert" else "News"
                _toastMessages.tryEmit("$label broadcast sent to all users!")
                onComplete()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Broadcast failed")
            }
            _isActionBusy.value = false
        }
    }

    fun deleteAdminAnnouncement(announcementId: String) {
        viewModelScope.launch {
            repository.deleteAdminAnnouncement(announcementId)
                .onSuccess { _toastMessages.tryEmit("Announcement deleted") }
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Delete failed") }
        }
    }

    fun sendUserWarning(
        targetUserId: String,
        targetUserName: String,
        severity: String,
        title: String,
        message: String,
        onComplete: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        viewModelScope.launch {
            _isActionBusy.value = true
            val role = when (staffTier.value) {
                EffectiveStaffTier.OWNER -> "owner"
                EffectiveStaffTier.ADMIN -> "admin"
                EffectiveStaffTier.SUPPORT -> "support"
                else -> "support"
            }
            repository.sendUserWarning(
                targetUserId = targetUserId,
                targetUserName = targetUserName,
                senderName = currentProfileDisplayName(),
                senderRole = role,
                severity = severity,
                title = title,
                message = message
            ).onSuccess {
                _toastMessages.tryEmit("Warning popup sent to $targetUserName")
                onComplete()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Failed to send warning")
            }
            _isActionBusy.value = false
        }
    }

    fun acknowledgeUserWarning(warningId: String) {
        viewModelScope.launch {
            repository.acknowledgeUserWarning(warningId)
                .onFailure { error -> _toastMessages.tryEmit(error.localizedMessage ?: "Could not acknowledge warning") }
        }
    }

    fun blockUser(
        targetUserId: String,
        targetName: String,
        reason: String = "",
        onBlocked: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        if (targetUserId.isBlank() || targetUserId == currentUserId) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.blockUser(
                blockedUserId = targetUserId,
                blockedUserName = targetName,
                reason = reason
            ).onSuccess {
                val activeThreadId = _activeDmThreadId.value
                val expectedThreadId = DirectChatThread.threadIdFor(currentUserId, targetUserId)
                if (activeThreadId == expectedThreadId) {
                    _activeDmThreadId.value = null
                }
                _toastMessages.tryEmit("Blocked ${targetName.ifBlank { "user" }}")
                onBlocked()
            }.onFailure { error ->
                _toastMessages.tryEmit(error.localizedMessage ?: "Could not block user")
            }
            _isActionBusy.value = false
        }
    }

    fun unblockUser(
        blockedUserId: String,
        targetName: String = "",
        onUnblocked: () -> Unit = {}
    ) {
        if (_isActionBusy.value) return
        if (blockedUserId.isBlank()) return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.unblockUser(blockedUserId)
                .onSuccess {
                    _toastMessages.tryEmit("Unblocked ${targetName.ifBlank { "user" }}")
                    onUnblocked()
                }
                .onFailure { error ->
                    _toastMessages.tryEmit(error.localizedMessage ?: "Could not unblock user")
                }
            _isActionBusy.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        micAudioEngine.release()
    }

    private fun unwrapErrorMessage(error: Throwable, fallback: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, fallback, rootError)
        return rootError.localizedMessage ?: fallback
    }

    private companion object {
        const val TAG = "SnapVoteVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
