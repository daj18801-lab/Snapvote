package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.content.Context
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.model.CreatorSummary
import com.example.data.model.EffectiveStaffTier
import com.example.data.model.MediaPost
import com.example.data.model.SupportTicket
import com.example.data.model.UserCheckIn
import com.example.data.model.UserStory
import com.example.data.repository.SnapVoteRepository
import com.example.ui.components.BuyPhoneTokensBottomSheet
import com.example.ui.components.BuyVideoTokensBottomSheet
import com.example.ui.components.BuyVotesBottomSheet
import com.example.ui.components.CheckInDialog
import com.example.ui.components.CreateStoryDialog
import com.example.ui.components.GlobalAnnouncementPopupDialog
import com.example.ui.components.MediaViewerDialog
import com.example.ui.components.PhoneTokenBalanceChip
import com.example.ui.components.ReportPostDialog
import com.example.ui.components.RequestPrivateVideoDialog
import com.example.ui.components.ScheduleMicSessionDialog
import com.example.ui.components.SendUserWarningDialog
import com.example.ui.components.StoryViewerDialog
import com.example.ui.components.UnlockGalleryDialog
import com.example.ui.components.UserWarningPopupDialog
import com.example.ui.components.VideoTokenBalanceChip
import com.example.ui.components.VoteBalanceChip
import com.example.ui.components.WarningPopupTarget
import com.example.ui.screens.AccountRestrictedScreen
import com.example.ui.screens.AdminConsoleScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BlockUserConfirmDialog
import com.example.ui.screens.BlockedUsersScreen
import com.example.ui.screens.ChatRoomsScreen
import com.example.ui.screens.CreatorAnalyticsScreen
import com.example.ui.screens.CreatorGalleryScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReportUserDialog
import com.example.ui.screens.SupportTicketsDialog
import com.example.ui.screens.UploadScreen
import com.example.ui.theme.SnapVoteTheme
import com.example.ui.viewmodel.SnapVoteViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.storage

private object Routes {
    const val DISCOVER = "discover"
    const val CHAT = "chat"
    const val UPLOAD = "upload"
    const val PROFILE = "profile"
    const val BLOCKED_USERS = "blocked_users"
    const val STAFF = "staff"
    const val CREATOR_ANALYTICS = "creator_analytics"
    const val CREATOR_PATTERN = "creator/{creatorId}"
    fun creator(creatorId: String) = "creator/$creatorId"
}

private data class TopLevelNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapVoteTheme {
                SnapVoteRoot()
            }
        }
    }
}

@Composable
fun SnapVoteRoot() {
    val context = LocalContext.current
    val firebaseReady = remember(context) {
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
        }.getOrDefault(false)
    }

    if (!firebaseReady) {
        AuthScreen(onAuthSuccess = {})
        return
    }

    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            val viewModel: SnapVoteViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        val storage = runCatching { Firebase.storage }.getOrNull()
                        val repo = SnapVoteRepository(db, auth, storage)
                        SnapVoteViewModel(
                            repository = repo,
                            currentUserId = user.uid,
                            currentDisplayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Creator",
                            currentUserEmail = user.email
                        )
                    }
                }
            )
            SnapVoteMainScaffold(viewModel = viewModel)
        }
    }
}

{
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            val viewModel: SnapVoteViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        val storage = runCatching { Firebase.storage }.getOrNull()
                        val repo = SnapVoteRepository(db, auth, storage)
                        SnapVoteViewModel(
                            repository = repo,
                            currentUserId = user.uid,
                            currentDisplayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Creator",
                            currentUserEmail = user.email
                        )
                    }
                }
            )
            SnapVoteMainScaffold(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnapVoteMainScaffold(viewModel: SnapVoteViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val postsState by viewModel.postsState.collectAsStateWithLifecycle()
    val creatorsState by viewModel.creatorsState.collectAsStateWithLifecycle()
    val unlockedCreatorIds by viewModel.unlockedCreatorIds.collectAsStateWithLifecycle()
    val purchaseHistory by viewModel.purchaseHistory.collectAsStateWithLifecycle()
    val staffTier by viewModel.staffTier.collectAsStateWithLifecycle()
    val allUsersState by viewModel.allUsersState.collectAsStateWithLifecycle()
    val staffRolesState by viewModel.staffRolesState.collectAsStateWithLifecycle()
    val moderationReportsState by viewModel.moderationReportsState.collectAsStateWithLifecycle()
    val allChatModerationsState by viewModel.allChatModerationsState.collectAsStateWithLifecycle()
    val myChatModeration by viewModel.myChatModeration.collectAsStateWithLifecycle()
    val activeRoomId by viewModel.activeRoomId.collectAsStateWithLifecycle()
    val isStaffInvisible by viewModel.isStaffInvisible.collectAsStateWithLifecycle()
    val roomState by viewModel.roomState.collectAsStateWithLifecycle()
    val roomMessagesState by viewModel.roomMessagesState.collectAsStateWithLifecycle()
    val roomPresenceState by viewModel.roomPresenceState.collectAsStateWithLifecycle()
    val myRoomBounce by viewModel.myRoomBounce.collectAsStateWithLifecycle()
    val isRoomAudioMutedLocally by viewModel.isRoomAudioMutedLocally.collectAsStateWithLifecycle()
    val isActionBusy by viewModel.isActionBusy.collectAsStateWithLifecycle()
    val showBuyVotesSheet by viewModel.showBuyVotesSheet.collectAsStateWithLifecycle()
    val pendingUnlockCreator by viewModel.pendingUnlockCreator.collectAsStateWithLifecycle()
    val pendingUnlockDialogCreator by viewModel.pendingUnlockDialogCreator.collectAsStateWithLifecycle()

    val phonePurchaseHistory by viewModel.phonePurchaseHistory.collectAsStateWithLifecycle()
    val creatorPayouts by viewModel.creatorPayouts.collectAsStateWithLifecycle()
    val friendRelationships by viewModel.friendRelationships.collectAsStateWithLifecycle()
    val directChatThreads by viewModel.directChatThreads.collectAsStateWithLifecycle()
    val dmMessagesState by viewModel.dmMessagesState.collectAsStateWithLifecycle()
    val groupChats by viewModel.groupChats.collectAsStateWithLifecycle()
    val groupMessagesState by viewModel.groupMessagesState.collectAsStateWithLifecycle()
    val micSessions by viewModel.micSessions.collectAsStateWithLifecycle()
    val micAudioChunks by viewModel.micAudioChunks.collectAsStateWithLifecycle()
    val activeDmThreadId by viewModel.activeDmThreadId.collectAsStateWithLifecycle()
    val activeGroupId by viewModel.activeGroupId.collectAsStateWithLifecycle()
    val activeMicSessionId by viewModel.activeMicSessionId.collectAsStateWithLifecycle()
    val micLevel by viewModel.micLevel.collectAsStateWithLifecycle()
    val isMicCapturing by viewModel.isMicCapturing.collectAsStateWithLifecycle()
    val showBuyPhoneTokensSheet by viewModel.showBuyPhoneTokensSheet.collectAsStateWithLifecycle()
    val pendingMicScheduleCreator by viewModel.pendingMicScheduleCreator.collectAsStateWithLifecycle()

    val videoPurchaseHistory by viewModel.videoPurchaseHistory.collectAsStateWithLifecycle()
    val videoSessions by viewModel.videoSessions.collectAsStateWithLifecycle()
    val videoFrameChunks by viewModel.videoFrameChunks.collectAsStateWithLifecycle()
    val videoAudioChunks by viewModel.videoAudioChunks.collectAsStateWithLifecycle()
    val activeVideoSessionId by viewModel.activeVideoSessionId.collectAsStateWithLifecycle()
    val showBuyVideoTokensSheet by viewModel.showBuyVideoTokensSheet.collectAsStateWithLifecycle()
    val pendingVideoRequestTarget by viewModel.pendingVideoRequestTarget.collectAsStateWithLifecycle()

    val userReportsState by viewModel.userReportsState.collectAsStateWithLifecycle()
    val mySupportTicketsState by viewModel.mySupportTicketsState.collectAsStateWithLifecycle()
    val allSupportTicketsState by viewModel.allSupportTicketsState.collectAsStateWithLifecycle()
    val mySupportPayrollState by viewModel.mySupportPayrollState.collectAsStateWithLifecycle()
    val allSupportPayrollState by viewModel.allSupportPayrollState.collectAsStateWithLifecycle()
    val banRequestsState by viewModel.banRequestsState.collectAsStateWithLifecycle()
    val pendingReportTarget by viewModel.pendingReportTarget.collectAsStateWithLifecycle()
    val showSupportTicketsSheet by viewModel.showSupportTicketsSheet.collectAsStateWithLifecycle()
    val myReferralRecord by viewModel.myReferralRecord.collectAsStateWithLifecycle()
    val referralsMade by viewModel.referralsMade.collectAsStateWithLifecycle()
    val referralClaims by viewModel.referralClaims.collectAsStateWithLifecycle()
    val referralCashouts by viewModel.referralCashouts.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val activeStories by viewModel.activeStories.collectAsStateWithLifecycle()
    val activeCheckIns by viewModel.activeCheckIns.collectAsStateWithLifecycle()
    val adminAnnouncements by viewModel.adminAnnouncements.collectAsStateWithLifecycle()
    val myWarnings by viewModel.myWarnings.collectAsStateWithLifecycle()
    val blockedUsersState by viewModel.blockedUsersState.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val blockedUserIds by viewModel.blockedUserIds.collectAsStateWithLifecycle()

    var activeViewerPost by remember { mutableStateOf<MediaPost?>(null) }
    var activeReportPost by remember { mutableStateOf<MediaPost?>(null) }
    var showCreateStoryDialog by remember { mutableStateOf(false) }
    var activeViewerStory by remember { mutableStateOf<UserStory?>(null) }
    var showCheckInDialog by remember { mutableStateOf(false) }
    var pendingWarningTarget by remember { mutableStateOf<WarningPopupTarget?>(null) }
    var pendingBlockTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val alertPrefs = remember(context, viewModel.currentUserId) {
        context.getSharedPreferences("snapvote_dismissed_alerts_${viewModel.currentUserId}", Context.MODE_PRIVATE)
    }
    var dismissedAnnouncementIds by remember(viewModel.currentUserId) {
        mutableStateOf(alertPrefs.getStringSet("dismissed_ids", emptySet())?.toSet().orEmpty())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setOnlineStatus(true)
                Lifecycle.Event.ON_STOP -> viewModel.setOnlineStatus(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val userProfile = (userProfileState as? UiState.Success)?.data
    val allCreators = (creatorsState as? UiState.Success)?.data.orEmpty()
    val allPosts = (postsState as? UiState.Success)?.data.orEmpty()

    val visiblePostsState = remember(postsState, blockedUserIds) {
        if (postsState is UiState.Success) {
            UiState.Success((postsState as UiState.Success).data.filterNot { blockedUserIds.contains(it.creatorId) })
        } else {
            postsState
        }
    }
    val visibleCreatorsState = remember(creatorsState, blockedUserIds) {
        if (creatorsState is UiState.Success) {
            UiState.Success((creatorsState as UiState.Success).data.filterNot { blockedUserIds.contains(it.creatorId) })
        } else {
            creatorsState
        }
    }
    val visibleCreators = remember(allCreators, blockedUserIds) {
        allCreators.filterNot { blockedUserIds.contains(it.creatorId) }
    }
    val visibleStories = remember(activeStories, blockedUserIds) {
        activeStories.filterNot { blockedUserIds.contains(it.userId) }
    }
    val visibleCheckIns = remember(activeCheckIns, blockedUserIds) {
        activeCheckIns.filterNot { blockedUserIds.contains(it.userId) }
    }
    val visibleDirectThreads = remember(directChatThreads, blockedUserIds, viewModel.currentUserId) {
        directChatThreads.filterNot { blockedUserIds.contains(it.otherUserId(viewModel.currentUserId)) }
    }
    val visibleRoomMessagesState = remember(roomMessagesState, blockedUserIds) {
        if (roomMessagesState is UiState.Success) {
            UiState.Success((roomMessagesState as UiState.Success).data.filterNot { blockedUserIds.contains(it.senderId) })
        } else {
            roomMessagesState
        }
    }

    val voteBalance = userProfile?.voteBalance ?: 0L
    val paidVoteBalance = userProfile?.paidVoteBalance ?: 0L
    val phoneTokenBalance = userProfile?.phoneTokenBalance ?: 0L
    val videoTokenBalance = userProfile?.videoTokenBalance ?: 0L
    val isStaff = staffTier != EffectiveStaffTier.NONE
    val isAdmin = staffTier == EffectiveStaffTier.OWNER || staffTier == EffectiveStaffTier.ADMIN

    fun canViewPost(post: MediaPost): Boolean {
        if (post.creatorId == viewModel.currentUserId || unlockedCreatorIds.contains(post.creatorId)) {
            return true
        }
        val freeLimit = allCreators.firstOrNull { it.creatorId == post.creatorId }?.freePreviewLimit
            ?: CreatorSummary.DEFAULT_FREE_PREVIEW_LIMIT
        return post.postIndex < freeLimit
    }

    fun openPostOrUnlockDialog(post: MediaPost) {
        if (canViewPost(post)) {
            activeViewerPost = post
        } else {
            val targetCreator = allCreators.firstOrNull { it.creatorId == post.creatorId }
                ?: CreatorSummary(creatorId = post.creatorId, displayName = post.creatorName)
            viewModel.openUnlockGalleryDialog(targetCreator)
        }
    }

    val navItems = remember(isStaff, isAdmin) {
        buildList {
            add(
                TopLevelNavItem(
                    route = Routes.CHAT,
                    label = "Messages",
                    selectedIcon = Icons.Rounded.Forum,
                    unselectedIcon = Icons.Outlined.Forum,
                    testTag = "nav_chat"
                )
            )
            add(
                TopLevelNavItem(
                    route = Routes.DISCOVER,
                    label = "Discover",
                    selectedIcon = Icons.Rounded.Explore,
                    unselectedIcon = Icons.Outlined.Explore,
                    testTag = "nav_discover"
                )
            )
            add(
                TopLevelNavItem(
                    route = Routes.UPLOAD,
                    label = "Upload",
                    selectedIcon = Icons.Rounded.AddCircle,
                    unselectedIcon = Icons.Outlined.AddCircleOutline,
                    testTag = "nav_upload"
                )
            )
            add(
                TopLevelNavItem(
                    route = Routes.PROFILE,
                    label = "Profile",
                    selectedIcon = Icons.Rounded.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    testTag = "nav_profile"
                )
            )
            if (isStaff) {
                add(
                    TopLevelNavItem(
                        route = Routes.STAFF,
                        label = if (isAdmin) "Admin" else "Staff",
                        selectedIcon = Icons.Rounded.AdminPanelSettings,
                        unselectedIcon = Icons.Outlined.AdminPanelSettings,
                        testTag = "nav_staff"
                    )
                )
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isCreatorRoute = currentRoute == Routes.CREATOR_PATTERN
    val isAnalyticsRoute = currentRoute == Routes.CREATOR_ANALYTICS
    val isBlockedUsersRoute = currentRoute == Routes.BLOCKED_USERS
    val hideGlobalBars = isCreatorRoute || isAnalyticsRoute || isBlockedUsersRoute

    LaunchedEffect(currentRoute, activeRoomId, activeMicSessionId, activeVideoSessionId) {
        if (currentRoute != Routes.CHAT && activeRoomId != null) {
            viewModel.leaveChatRoom()
        }
        if (currentRoute != Routes.CHAT && activeMicSessionId != null) {
            viewModel.leaveMicCallRoom()
        }
        if (currentRoute != Routes.CHAT && activeVideoSessionId != null) {
            viewModel.leaveVideoCallRoom()
        }
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isAccountRestricted = !isStaff && myChatModeration?.isAccountRestricted() == true
    val isInImmersiveChatView = currentRoute == Routes.CHAT &&
        (activeRoomId != null || activeDmThreadId != null || activeGroupId != null || activeMicSessionId != null || activeVideoSessionId != null)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                if (!isAccountRestricted && !hideGlobalBars && !isInImmersiveChatView) {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (currentRoute) {
                                    Routes.CHAT -> stringResource(R.string.private_messages_title)
                                    Routes.UPLOAD -> "Upload Media"
                                    Routes.PROFILE -> "My Profile"
                                    Routes.STAFF -> if (isAdmin) "Admin Dashboard" else "Support Console"
                                    else -> "SnapVote"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                IconButton(
                                    onClick = viewModel::openSupportTicketsSheet,
                                    modifier = Modifier.testTag("top_bar_support_tickets_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SupportAgent,
                                        contentDescription = "Help and Support Tickets",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                VoteBalanceChip(
                                    balance = voteBalance,
                                    onClick = { viewModel.openBuyVotesSheet() }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                if (!isAccountRestricted && !useRail && !hideGlobalBars) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (isAccountRestricted && myChatModeration != null) {
                AccountRestrictedScreen(
                    moderation = myChatModeration!!,
                    myTicketsState = mySupportTicketsState,
                    onOpenSupportAppeal = viewModel::openSupportTicketsSheet,
                    onSignOut = {
                        viewModel.setOnlineStatus(false)
                        signOutUser(credentialManager, scope)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail && !hideGlobalBars) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            navItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.CHAT,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 720.dp)
                        ) {
                            composable(Routes.DISCOVER) {
                                DiscoverScreen(
                                    currentUserId = viewModel.currentUserId,
                                    currentUserProfile = userProfile,
                                    isStaff = isStaff,
                                    postsState = visiblePostsState,
                                    creatorsState = visibleCreatorsState,
                                    unlockedCreatorIds = unlockedCreatorIds,
                                    friendRelationships = friendRelationships,
                                    onNavigateToUpload = { navigateTopLevel(Routes.UPLOAD) },
                                    onNavigateToProfile = { navigateTopLevel(Routes.PROFILE) },
                                    onNavigateToCreator = { creatorId ->
                                        navController.navigate(Routes.creator(creatorId))
                                    },
                                    onVoteClick = viewModel::castVote,
                                    onUnlockCreatorClick = viewModel::openUnlockGalleryDialog,
                                    onOpenMediaClick = ::openPostOrUnlockDialog,
                                    onReportClick = { post -> activeReportPost = post },
                                    onReportUserClick = viewModel::openReportUserDialog,
                                    onDeleteClick = viewModel::deletePost,
                                    onSendFriendRequest = viewModel::sendFriendRequest,
                                    onMessageUser = { otherId, otherName ->
                                        viewModel.openDirectChatWithUser(otherId, otherName) {
                                            navigateTopLevel(Routes.CHAT)
                                        }
                                    },
                                    onScheduleMicCall = viewModel::openScheduleMicDialog,
                                    activeStories = visibleStories,
                                    activeCheckIns = visibleCheckIns,
                                    onAddStoryClick = { showCreateStoryDialog = true },
                                    onOpenStoryClick = { story -> activeViewerStory = story },
                                    onOpenCheckInDialog = { showCheckInDialog = true },
                                    onClearCheckIn = viewModel::clearCheckIn,
                                    onWarnUserClick = { uid, name ->
                                        pendingWarningTarget = WarningPopupTarget(uid, name)
                                    },
                                    onBlockUserClick = { uid, name ->
                                        pendingBlockTarget = uid to name
                                    }
                                )
                            }

                            composable(Routes.CHAT) {
                                val globallyMutedUserIds = remember(allChatModerationsState) {
                                    (allChatModerationsState as? UiState.Success)?.data
                                        ?.filter { it.isCurrentlyMuted() }
                                        ?.map { it.userId }
                                        ?.toSet()
                                        .orEmpty()
                                }
                                ChatRoomsScreen(
                                    currentUserId = viewModel.currentUserId,
                                    staffTier = staffTier,
                                    userProfile = userProfile,
                                    creators = visibleCreators,
                                    myChatModeration = myChatModeration,
                                    activeRoomId = activeRoomId,
                                    activeDmThreadId = activeDmThreadId,
                                    activeGroupId = activeGroupId,
                                    activeMicSessionId = activeMicSessionId,
                                    isStaffInvisible = isStaffInvisible,
                                    roomState = roomState,
                                    roomMessagesState = visibleRoomMessagesState,
                                    roomPresence = roomPresenceState,
                                    myRoomBounce = myRoomBounce,
                                    isRoomAudioMutedLocally = isRoomAudioMutedLocally,
                                    globallyMutedUserIds = globallyMutedUserIds,
                                    friendRelationships = friendRelationships,
                                    directThreads = visibleDirectThreads,
                                    dmMessagesState = dmMessagesState,
                                    groupChats = groupChats,
                                    groupMessagesState = groupMessagesState,
                                    micSessions = micSessions,
                                    micLevel = micLevel,
                                    isMicCapturing = isMicCapturing,
                                    micAudioChunks = micAudioChunks,
                                    videoSessions = videoSessions,
                                    activeVideoSessionId = activeVideoSessionId,
                                    videoFrameChunks = videoFrameChunks,
                                    videoAudioChunks = videoAudioChunks,
                                    isActionBusy = isActionBusy,
                                    uploadProgress = uploadProgress,
                                    onToggleStaffInvisible = viewModel::setStaffInvisible,
                                    onEnterRoom = viewModel::enterChatRoom,
                                    onLeaveRoom = viewModel::leaveChatRoom,
                                    onClaimMonitorIfVacant = viewModel::claimRoomMonitorIfVacant,
                                    onStartRoomVoiceTalk = viewModel::startRoomVoiceTalk,
                                    onStopRoomVoiceTalk = viewModel::stopRoomVoiceTalk,
                                    onToggleRaiseHand = viewModel::toggleRoomHandRaised,
                                    onToggleLocalListenMute = viewModel::toggleRoomLocalListenMute,
                                    onSetMemberVoiceMuted = viewModel::setRoomMemberVoiceMuted,
                                    onLowerMemberHand = viewModel::lowerRoomMemberHand,
                                    onSendMessage = viewModel::sendChatMessage,
                                    onDeleteMessage = viewModel::deleteChatMessage,
                                    onBounceUser = viewModel::bounceUserFromActiveRoom,
                                    onMuteUser = viewModel::muteUser,
                                    onUnmuteUser = viewModel::unmuteUser,
                                    onBanUser = viewModel::banUserFromChat,
                                    onSuspendUser = viewModel::suspendUser,
                                    onBanAccountUser = viewModel::banUserAccount,
                                    onReportUserClick = viewModel::openReportUserDialog,
                                    onBuyChatPass = viewModel::buyChatAccessPass,
                                    onBuyVotesClick = { viewModel.openBuyVotesSheet() },
                                    onBuyPrivatePass = viewModel::buyPrivateChatPass,
                                    onBuyPhoneTokensClick = { viewModel.openBuyPhoneTokensSheet() },
                                    onSendFriendRequest = viewModel::sendFriendRequest,
                                    onRespondFriendRequest = viewModel::respondToFriendRequest,
                                    onRemoveFriend = viewModel::removeFriend,
                                    onOpenDirectChatWithUser = { uid, name ->
                                        viewModel.openDirectChatWithUser(uid, name)
                                    },
                                    onOpenDirectThread = viewModel::openExistingDirectThread,
                                    onCloseDirectChat = viewModel::closeDirectChat,
                                    onSendDirectMessage = viewModel::sendDirectMessage,
                                    onSendDirectSnap = viewModel::sendDirectSnap,
                                    onMarkDirectSnapViewed = viewModel::markDirectSnapViewed,
                                    onWarnUserClick = { uid, name ->
                                        pendingWarningTarget = WarningPopupTarget(uid, name)
                                    },
                                    onCreateGroupChat = viewModel::createGroupChat,
                                    onOpenGroupChat = viewModel::openGroupChat,
                                    onCloseGroupChat = viewModel::closeGroupChat,
                                    onSendGroupMessage = viewModel::sendGroupMessage,
                                    onUpdateGroupSettings = viewModel::updateGroupSettings,
                                    onAddMembersToGroup = viewModel::addMembersToGroup,
                                    onLeaveOrRemoveFromGroup = viewModel::leaveOrRemoveFromGroup,
                                    onDeleteGroupChat = viewModel::deleteGroupChat,
                                    onOpenScheduleMicDialog = viewModel::openScheduleMicDialog,
                                    onRespondMicSession = viewModel::respondToMicSession,
                                    onCancelMicSession = viewModel::cancelMicSession,
                                    onJoinMicCall = viewModel::enterMicCallRoom,
                                    onStartMicCapture = viewModel::startMicCapture,
                                    onStopMicCapture = viewModel::stopMicCapture,
                                    onLeaveMicCall = viewModel::leaveMicCallRoom,
                                    onEndMicCall = viewModel::endMicCallSession,
                                    onOpenRequestVideoDialog = viewModel::openRequestVideoDialog,
                                    onRespondVideoSession = viewModel::respondToVideoSession,
                                    onCancelVideoSession = viewModel::cancelVideoSession,
                                    onJoinVideoCall = viewModel::enterVideoCallRoom,
                                    onSendVideoFrame = viewModel::sendVideoFrameChunk,
                                    onStartVideoAudioCapture = viewModel::startVideoAudioCapture,
                                    onStopVideoAudioCapture = viewModel::stopMicCapture,
                                    onLeaveVideoCall = viewModel::leaveVideoCallRoom,
                                    onEndVideoCall = viewModel::endVideoCallSession,
                                    onOpenCreatorProfile = { creatorId ->
                                        navController.navigate(Routes.creator(creatorId))
                                    },
                                    onBlockUserClick = { uid, name ->
                                        pendingBlockTarget = uid to name
                                    },
                                    initialSubTab = 0
                                )
                            }

                            composable(Routes.UPLOAD) {
                                val myPostCount = allPosts.count { it.creatorId == viewModel.currentUserId }.toLong()
                                UploadScreen(
                                    myCurrentPostCount = myPostCount,
                                    isActionBusy = isActionBusy,
                                    uploadProgress = uploadProgress,
                                    onPublish = { media, caption, resetForm ->
                                        viewModel.uploadPost(media, caption, context = context) {
                                            resetForm()
                                            navigateTopLevel(Routes.DISCOVER)
                                        }
                                    }
                                )
                            }

                            composable(Routes.PROFILE) {
                                val unlockedCreatorsList = allCreators.filter {
                                    unlockedCreatorIds.contains(it.creatorId)
                                }
                                val myPostsCount = allPosts.count { it.creatorId == viewModel.currentUserId }
                                ProfileScreen(
                                    userProfile = userProfile,
                                    userEmail = viewModel.currentUserEmail,
                                    staffTier = staffTier,
                                    currentUserId = viewModel.currentUserId,
                                    unlockedCreators = unlockedCreatorsList,
                                    purchaseHistory = purchaseHistory,
                                    phonePurchaseHistory = phonePurchaseHistory,
                                    videoPurchaseHistory = videoPurchaseHistory,
                                    creatorPayouts = creatorPayouts,
                                    mySupportPayroll = mySupportPayrollState,
                                    mySupportTicketsState = mySupportTicketsState,
                                    myReferralRecord = myReferralRecord,
                                    referralsMade = referralsMade,
                                    referralClaims = referralClaims,
                                    referralCashouts = referralCashouts,
                                    isActionBusy = isActionBusy,
                                    myPostsCount = myPostsCount,
                                    onBuyVotesClick = { viewModel.openBuyVotesSheet() },
                                    onBuyPhoneTokensClick = { viewModel.openBuyPhoneTokensSheet() },
                                    onBuyVideoTokensClick = { viewModel.openBuyVideoTokensSheet() },
                                    onBuyChatPassClick = viewModel::buyChatAccessPass,
                                    onBuyPrivatePassClick = viewModel::buyPrivateChatPass,
                                    onOpenMyGallery = {
                                        navController.navigate(Routes.creator(viewModel.currentUserId))
                                    },
                                    onOpenCreatorGallery = { creatorId ->
                                        navController.navigate(Routes.creator(creatorId))
                                    },
                                    onOpenCreatorAnalytics = {
                                        navController.navigate(Routes.CREATOR_ANALYTICS)
                                    },
                                    onOpenAdminConsole = { navigateTopLevel(Routes.STAFF) },
                                    onOpenSupportTickets = viewModel::openSupportTicketsSheet,
                                    onClaimSupportWeeklyPay = viewModel::claimSupportWeeklyPay,
                                    onApplyReferralCode = viewModel::applyReferralCode,
                                    onClaimReferralReward = viewModel::claimReferralReward,
                                    onCashInReferralTokens = viewModel::cashInReferralTokens,
                                    onReferralFeedback = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    },
                                    onUpdateProfile = viewModel::updateProfile,
                                    blockedUsersCount = blockedUsers.size,
                                    onOpenBlockedUsers = {
                                        navController.navigate(Routes.BLOCKED_USERS)
                                    },
                                    onSignOut = {
                                        viewModel.setOnlineStatus(false)
                                        signOutUser(credentialManager, scope)
                                    }
                                )
                            }

                            composable(Routes.BLOCKED_USERS) {
                                BlockedUsersScreen(
                                    blockedUsersState = blockedUsersState,
                                    isActionBusy = isActionBusy,
                                    onBack = { navController.popBackStack() },
                                    onUnblockUser = { entry ->
                                        viewModel.unblockUser(
                                            blockedUserId = entry.blockedUserId.ifBlank { entry.id },
                                            targetName = entry.blockedUserName
                                        )
                                    }
                                )
                            }

                            composable(Routes.STAFF) {
                                AdminConsoleScreen(
                                    staffTier = staffTier,
                                    currentUserId = viewModel.currentUserId,
                                    currentUserEmail = viewModel.currentUserEmail,
                                    currentUserProfile = userProfile,
                                    allUsersState = allUsersState,
                                    reportsState = moderationReportsState,
                                    userReportsState = userReportsState,
                                    allSupportTicketsState = allSupportTicketsState,
                                    banRequestsState = banRequestsState,
                                    chatModerationsState = allChatModerationsState,
                                    staffRolesState = staffRolesState,
                                    mySupportPayroll = mySupportPayrollState,
                                    allSupportPayroll = allSupportPayrollState,
                                    allPublicPosts = allPosts,
                                    isActionBusy = isActionBusy,
                                    onAssignUserRole = viewModel::assignUserRole,
                                    onBounceUserEverywhere = viewModel::bounceUserEverywhere,
                                    onMuteUser = viewModel::muteUser,
                                    onUnmuteUser = viewModel::unmuteUser,
                                    onBanUser = viewModel::banUserFromChat,
                                    onUnbanUser = viewModel::unbanUserFromChat,
                                    onSuspendUser = viewModel::suspendUser,
                                    onUnsuspendUser = viewModel::unsuspendUser,
                                    onBanAccountUser = viewModel::banUserAccount,
                                    onUnbanAccountUser = viewModel::unbanUserAccount,
                                    onReviewBanRequest = { req, approve ->
                                        viewModel.reviewBanRequest(req, approve)
                                    },
                                    onResolveReport = { report, status, hidePost ->
                                        viewModel.resolveReport(report, status, hidePost)
                                    },
                                    onResolveUserReport = viewModel::resolveUserReport,
                                    onReplySupportTicket = viewModel::replySupportTicket,
                                    onClaimSupportWeeklyPay = viewModel::claimSupportWeeklyPay,
                                    onHideOrDeletePost = { post, delete ->
                                        if (delete) {
                                            viewModel.deletePost(post)
                                        } else {
                                            viewModel.hideOrRestorePost(post.id, "hidden")
                                        }
                                    },
                                    onSaveStaffRole = viewModel::appointOrUpdateStaff,
                                    onRemoveStaffRole = viewModel::removeStaff,
                                    adminAnnouncements = adminAnnouncements,
                                    onSendAdminAnnouncement = viewModel::sendAdminAnnouncement,
                                    onDeleteAdminAnnouncement = viewModel::deleteAdminAnnouncement,
                                    onSendUserWarning = viewModel::sendUserWarning
                                )
                            }

                            composable(
                                route = Routes.CREATOR_PATTERN,
                                arguments = listOf(navArgument("creatorId") { type = NavType.StringType })
                            ) { entry ->
                                val creatorId = entry.arguments?.getString("creatorId").orEmpty()
                                val creatorSummary = allCreators.firstOrNull { it.creatorId == creatorId }
                                val creatorPosts = allPosts.filter { it.creatorId == creatorId }
                                val friendRel = friendRelationships.firstOrNull {
                                    it.otherUserId(viewModel.currentUserId) == creatorId
                                }
                                CreatorGalleryScreen(
                                    creator = creatorSummary,
                                    creatorPosts = creatorPosts,
                                    currentUserId = viewModel.currentUserId,
                                    currentVoteBalance = voteBalance,
                                    currentPaidVoteBalance = paidVoteBalance,
                                    isUnlocked = unlockedCreatorIds.contains(creatorId),
                                    isStaff = isStaff,
                                    friendRelationship = friendRel,
                                    onBack = { navController.popBackStack() },
                                    onVoteClick = viewModel::castVote,
                                    onUnlockCreatorClick = viewModel::openUnlockGalleryDialog,
                                    onBuyVotesClick = { target -> viewModel.openBuyVotesSheet(target) },
                                    onOpenMediaClick = ::openPostOrUnlockDialog,
                                    onReportClick = { post -> activeReportPost = post },
                                    onReportUserClick = viewModel::openReportUserDialog,
                                    onDeleteClick = viewModel::deletePost,
                                    onSendFriendRequest = viewModel::sendFriendRequest,
                                    onMessageUser = { otherId, otherName ->
                                        viewModel.openDirectChatWithUser(otherId, otherName) {
                                            navigateTopLevel(Routes.CHAT)
                                        }
                                    },
                                    onScheduleMicCall = viewModel::openScheduleMicDialog,
                                    onWarnUserClick = if (isStaff) { uid, name ->
                                        pendingWarningTarget = WarningPopupTarget(uid, name)
                                    } else null,
                                    isBlocked = blockedUserIds.contains(creatorId),
                                    onBlockUserClick = { uid, name ->
                                        pendingBlockTarget = uid to name
                                    },
                                    onUnblockUserClick = { uid, name ->
                                        viewModel.unblockUser(uid, name)
                                    }
                                )
                            }

                            composable(Routes.CREATOR_ANALYTICS) {
                                val myPosts = allPosts.filter { it.creatorId == viewModel.currentUserId }
                                CreatorAnalyticsScreen(
                                    creatorDisplayName = userProfile?.displayName ?: "Creator",
                                    creatorPayouts = creatorPayouts,
                                    myPosts = myPosts,
                                    isAccountRestricted = isAccountRestricted,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBuyVotesSheet) {
        BuyVotesBottomSheet(
            currentBalance = voteBalance,
            currentPaidBalance = paidVoteBalance,
            pendingCreator = pendingUnlockCreator,
            isBusy = isActionBusy,
            onBuyPack = viewModel::buyVotePack,
            onDismiss = viewModel::dismissBuyVotesSheet
        )
    }

    if (showBuyPhoneTokensSheet) {
        BuyPhoneTokensBottomSheet(
            currentPhoneBalance = phoneTokenBalance,
            isBusy = isActionBusy,
            onBuyPack = viewModel::buyPhoneTokenPack,
            onDismiss = viewModel::dismissBuyPhoneTokensSheet
        )
    }

    if (showBuyVideoTokensSheet) {
        BuyVideoTokensBottomSheet(
            currentVideoBalance = videoTokenBalance,
            isBusy = isActionBusy,
            onBuyPack = viewModel::buyVideoTokenPack,
            onDismiss = viewModel::dismissBuyVideoTokensSheet
        )
    }

    pendingVideoRequestTarget?.let { target ->
        RequestPrivateVideoDialog(
            creatorName = target.creatorName,
            isOnline = target.isOnline,
            currentVideoBalance = videoTokenBalance,
            isBusy = isActionBusy,
            onBuyVideoTokensClick = {
                viewModel.dismissRequestVideoDialog()
                viewModel.openBuyVideoTokensSheet()
            },
            onConfirmRequest = { note, mins ->
                viewModel.requestPrivateVideoSession(target, note, mins)
            },
            onDismiss = viewModel::dismissRequestVideoDialog
        )
    }

    pendingMicScheduleCreator?.let { targetCreator ->
        ScheduleMicSessionDialog(
            creator = targetCreator,
            currentPhoneBalance = phoneTokenBalance,
            isBusy = isActionBusy,
            onBuyPhoneTokensClick = {
                viewModel.dismissScheduleMicDialog()
                viewModel.openBuyPhoneTokensSheet()
            },
            onScheduleConfirm = { topic, whenMillis, mins ->
                viewModel.scheduleMicSession(targetCreator, topic, whenMillis, mins) {
                    navigateTopLevel(Routes.CHAT)
                }
            },
            onDismiss = viewModel::dismissScheduleMicDialog
        )
    }

    pendingUnlockDialogCreator?.let { targetCreator ->
        val lockedCountForCreator = allPosts.count { post ->
            post.creatorId == targetCreator.creatorId &&
                post.creatorId != viewModel.currentUserId &&
                !unlockedCreatorIds.contains(targetCreator.creatorId) &&
                post.postIndex >= targetCreator.freePreviewLimit
        }
        UnlockGalleryDialog(
            creator = targetCreator,
            lockedPostCount = lockedCountForCreator,
            currentTotalBalance = voteBalance,
            currentPaidBalance = paidVoteBalance,
            isBusy = isActionBusy,
            onConfirmUnlock = viewModel::unlockCreatorGallery,
            onBuyPaidTokens = { creator -> viewModel.openBuyVotesSheet(creator) },
            onDismiss = viewModel::dismissUnlockGalleryDialog
        )
    }

    activeViewerPost?.let { post ->
        MediaViewerDialog(
            post = post,
            isLockedForViewer = !canViewPost(post),
            onDismiss = { activeViewerPost = null }
        )
    }

    activeReportPost?.let { post ->
        ReportPostDialog(
            post = post,
            onSubmit = { reason ->
                viewModel.submitReport(post, reason)
                activeReportPost = null
            },
            onDismiss = { activeReportPost = null }
        )
    }

    pendingReportTarget?.let { target ->
        ReportUserDialog(
            target = target,
            isBusy = isActionBusy,
            onSubmit = { reportedUserId, reportedUserName, reason, details, sourceContext ->
                viewModel.submitUserReport(reportedUserId, reportedUserName, reason, details, sourceContext)
            },
            onDismiss = viewModel::dismissReportUserDialog
        )
    }

    if (showSupportTicketsSheet) {
        SupportTicketsDialog(
            ticketsState = mySupportTicketsState,
            isBusy = isActionBusy,
            initialCategory = if (isAccountRestricted) SupportTicket.CATEGORY_ACCOUNT_APPEAL else SupportTicket.CATEGORY_OTHER,
            onCreateTicket = { category, subject, message, onCreated ->
                viewModel.createSupportTicket(category, subject, message, onCreated)
            },
            onCloseOwnTicket = viewModel::closeOwnSupportTicket,
            onDismiss = viewModel::dismissSupportTicketsSheet
        )
    }

    if (showCreateStoryDialog) {
        CreateStoryDialog(
            isBusy = isActionBusy,
            onPostStory = { storyType, text, style, media ->
                viewModel.postStory(storyType, text, style, media) {
                    showCreateStoryDialog = false
                }
            },
            onDismiss = { if (!isActionBusy) showCreateStoryDialog = false }
        )
    }

    activeViewerStory?.let { story ->
        StoryViewerDialog(
            story = story,
            currentUserId = viewModel.currentUserId,
            isStaff = isStaff,
            onViewed = viewModel::markStoryViewed,
            onDelete = viewModel::deleteStory,
            onDismiss = { activeViewerStory = null }
        )
    }

    if (showCheckInDialog) {
        val myCurrentCheckIn = activeCheckIns.firstOrNull { it.userId == viewModel.currentUserId }
        CheckInDialog(
            existingCheckIn = myCurrentCheckIn,
            isBusy = isActionBusy,
            onSaveCheckIn = { place, note, vibe ->
                viewModel.setCheckIn(place, note, vibe) {
                    showCheckInDialog = false
                }
            },
            onDismiss = { if (!isActionBusy) showCheckInDialog = false }
        )
    }

    pendingWarningTarget?.let { target ->
        SendUserWarningDialog(
            target = target,
            isBusy = isActionBusy,
            onSendWarning = { severity, title, message ->
                viewModel.sendUserWarning(target.userId, target.displayName, severity, title, message) {
                    pendingWarningTarget = null
                }
            },
            onDismiss = { if (!isActionBusy) pendingWarningTarget = null }
        )
    }

    pendingBlockTarget?.let { (targetUid, targetName) ->
        BlockUserConfirmDialog(
            targetUserName = targetName.ifBlank { "User" },
            isBusy = isActionBusy,
            onConfirmBlock = { reason ->
                viewModel.blockUser(targetUid, targetName, reason) {
                    pendingBlockTarget = null
                }
            },
            onDismiss = { if (!isActionBusy) pendingBlockTarget = null }
        )
    }

    val pendingWarning = myWarnings.firstOrNull { !it.acknowledged }
    if (pendingWarning != null) {
        UserWarningPopupDialog(
            warning = pendingWarning,
            onAcknowledge = {
                viewModel.acknowledgeUserWarning(pendingWarning.warningId.ifBlank { pendingWarning.id })
            }
        )
    } else {
        val unseenAnnouncement = adminAnnouncements.firstOrNull { ann ->
            val keyId = ann.announcementId.ifBlank { ann.id }
            keyId.isNotBlank() && !dismissedAnnouncementIds.contains(keyId)
        }
        if (unseenAnnouncement != null) {
            val annKey = unseenAnnouncement.announcementId.ifBlank { unseenAnnouncement.id }
            GlobalAnnouncementPopupDialog(
                announcement = unseenAnnouncement,
                onDismiss = {
                    val updated = dismissedAnnouncementIds + annKey
                    dismissedAnnouncementIds = updated
                    alertPrefs.edit().putStringSet("dismissed_ids", updated).apply()
                }
            )
        }
    }
}
