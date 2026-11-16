package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CreatorSummary
import com.example.data.model.FriendRelationship
import com.example.data.model.MediaPost
import com.example.data.model.UserCheckIn
import com.example.data.model.UserProfile
import com.example.data.model.UserStory
import com.example.ui.components.GalleryLockBadge
import com.example.ui.components.MediaPostCard
import com.example.ui.components.OnlineStatusDot
import com.example.ui.components.OptionalCheckInSection
import com.example.ui.components.StoriesStripSection
import com.example.ui.components.TrendingCreatorsChartCard
import com.example.ui.viewmodel.UiState
import java.util.Locale

private enum class FeedFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    TOP_VOTED("Top Voted")
}

private enum class SexFilter(val label: String, val value: String?) {
    ALL("All", null),
    FEMALE("Female", UserProfile.SEX_FEMALE),
    MALE("Male", UserProfile.SEX_MALE),
    NON_BINARY("Non-binary", UserProfile.SEX_NON_BINARY)
}

private enum class AgeBracketFilter(val label: String, val range: IntRange?) {
    ALL("All Ages", null),
    AGE_18_24("18–24", 18..24),
    AGE_25_34("25–34", 25..34),
    AGE_35_44("35–44", 35..44),
    AGE_45_PLUS("45+", 45..150)
}

@Composable
fun DiscoverScreen(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    isStaff: Boolean,
    postsState: UiState<List<MediaPost>>,
    creatorsState: UiState<List<CreatorSummary>>,
    unlockedCreatorIds: Set<String>,
    friendRelationships: List<FriendRelationship>,
    onNavigateToUpload: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCreator: (String) -> Unit,
    onVoteClick: (MediaPost) -> Unit,
    onUnlockCreatorClick: (CreatorSummary) -> Unit,
    onOpenMediaClick: (MediaPost) -> Unit,
    onReportClick: (MediaPost) -> Unit,
    onReportUserClick: (String, String, String) -> Unit,
    onDeleteClick: (MediaPost) -> Unit,
    onSendFriendRequest: (String, String) -> Unit,
    onMessageUser: (String, String) -> Unit,
    onScheduleMicCall: (CreatorSummary) -> Unit,
    activeStories: List<UserStory> = emptyList(),
    activeCheckIns: List<UserCheckIn> = emptyList(),
    onAddStoryClick: () -> Unit = {},
    onOpenStoryClick: (UserStory) -> Unit = {},
    onOpenCheckInDialog: () -> Unit = {},
    onClearCheckIn: () -> Unit = {},
    onWarnUserClick: (String, String) -> Unit = { _, _ -> },
    onBlockUserClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf(FeedFilter.ALL) }

    val creatorsList = (creatorsState as? UiState.Success)?.data.orEmpty()
    val creatorsById = remember(creatorsList) { creatorsList.associateBy { it.creatorId } }
    val myCheckIn = remember(activeCheckIns, currentUserId) {
        activeCheckIns.firstOrNull { it.userId == currentUserId }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Posts Feed") },
                icon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("discover_tab_posts")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Users") },
                icon = { Icon(Icons.Rounded.People, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("discover_tab_users")
            )
        }

        if (selectedTab == 0) {
            when (postsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = postsState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val allPosts = postsState.data
                    val filteredPosts = remember(allPosts, selectedFilter) {
                        when (selectedFilter) {
                            FeedFilter.ALL -> allPosts
                            FeedFilter.PHOTOS -> allPosts.filter { !it.isVideo }
                            FeedFilter.VIDEOS -> allPosts.filter { it.isVideo }
                            FeedFilter.TOP_VOTED -> allPosts.sortedByDescending { it.voteCount }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item(key = "stories_strip") {
                            StoriesStripSection(
                                stories = activeStories,
                                currentUserId = currentUserId,
                                onAddStoryClick = onAddStoryClick,
                                onOpenStoryClick = onOpenStoryClick,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                            )
                        }

                        item(key = "optional_checkin_card") {
                            OptionalCheckInSection(
                                myCheckIn = myCheckIn,
                                activeCheckIns = activeCheckIns,
                                onOpenCheckInDialog = onOpenCheckInDialog,
                                onClearCheckIn = onClearCheckIn,
                                onCreatorClick = onNavigateToCreator,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item(key = "trending_creators_chart") {
                            TrendingCreatorsChartCard(
                                creators = creatorsList,
                                onCreatorClick = onNavigateToCreator,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        val activeCreators = creatorsList.filter { it.postCount > 0 || it.creatorId == currentUserId }
                        if (activeCreators.isNotEmpty()) {
                            item(key = "creators_header") {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    Text(
                                        text = "Creators Spotlight",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(activeCreators, key = { it.creatorId }) { creator ->
                                            CreatorSpotlightCard(
                                                creator = creator,
                                                isOwner = creator.creatorId == currentUserId,
                                                isUnlocked = unlockedCreatorIds.contains(creator.creatorId),
                                                onClick = { onNavigateToCreator(creator.creatorId) },
                                                onLockClick = { onUnlockCreatorClick(creator) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "feed_filters") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(FeedFilter.entries) { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = { Text(filter.label) },
                                        leadingIcon = {
                                            when (filter) {
                                                FeedFilter.PHOTOS -> Icon(
                                                    Icons.Rounded.Photo,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                FeedFilter.VIDEOS -> Icon(
                                                    Icons.Rounded.Videocam,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                FeedFilter.TOP_VOTED -> Icon(
                                                    Icons.AutoMirrored.Rounded.TrendingUp,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                FeedFilter.ALL -> null
                                            }
                                        },
                                        modifier = Modifier.testTag("filter_${filter.name.lowercase(Locale.ROOT)}")
                                    )
                                }
                            }
                        }

                        if (filteredPosts.isEmpty()) {
                            item(key = "empty_feed") {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_empty_gallery),
                                            contentDescription = "No posts yet",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(170.dp)
                                                .clip(MaterialTheme.shapes.medium)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = if (allPosts.isEmpty()) "Be the first creator to post!" else "No posts match this filter",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Upload a photo or video from your device so others can vote and unlock your full gallery.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = onNavigateToUpload,
                                            modifier = Modifier.testTag("empty_upload_cta")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                                contentDescription = null
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Upload Photo or Video")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(filteredPosts, key = { it.id }) { post ->
                                MediaPostCard(
                                    post = post,
                                    creatorSummary = creatorsById[post.creatorId],
                                    isOwner = post.creatorId == currentUserId,
                                    isCreatorUnlocked = unlockedCreatorIds.contains(post.creatorId),
                                    isStaff = isStaff,
                                    onCreatorClick = onNavigateToCreator,
                                    onVoteClick = onVoteClick,
                                    onUnlockCreatorClick = onUnlockCreatorClick,
                                    onOpenMediaClick = onOpenMediaClick,
                                    onReportClick = onReportClick,
                                    onDeleteClick = onDeleteClick,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            UsersDiscoveryTab(
                currentUserId = currentUserId,
                currentUserProfile = currentUserProfile,
                isStaff = isStaff,
                creatorsState = creatorsState,
                activeCheckIns = activeCheckIns,
                unlockedCreatorIds = unlockedCreatorIds,
                friendRelationships = friendRelationships,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToCreator = onNavigateToCreator,
                onSendFriendRequest = onSendFriendRequest,
                onMessageUser = onMessageUser,
                onScheduleMicCall = onScheduleMicCall,
                onReportUserClick = onReportUserClick,
                onUnlockCreatorClick = onUnlockCreatorClick,
                onWarnUserClick = onWarnUserClick,
                onBlockUserClick = onBlockUserClick
            )
        }
    }
}

@Composable
private fun UsersDiscoveryTab(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    isStaff: Boolean,
    creatorsState: UiState<List<CreatorSummary>>,
    activeCheckIns: List<UserCheckIn>,
    unlockedCreatorIds: Set<String>,
    friendRelationships: List<FriendRelationship>,
    onNavigateToProfile: () -> Unit,
    onNavigateToCreator: (String) -> Unit,
    onSendFriendRequest: (String, String) -> Unit,
    onMessageUser: (String, String) -> Unit,
    onScheduleMicCall: (CreatorSummary) -> Unit,
    onReportUserClick: (String, String, String) -> Unit,
    onUnlockCreatorClick: (CreatorSummary) -> Unit,
    onWarnUserClick: (String, String) -> Unit,
    onBlockUserClick: (String, String) -> Unit = { _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSex by remember { mutableStateOf(SexFilter.ALL) }
    var selectedAgeBracket by remember { mutableStateOf(AgeBracketFilter.ALL) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search users by name or bio...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discover_users_search")
            )

            // Sex filter row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SexFilter.entries) { sexFilter ->
                    FilterChip(
                        selected = selectedSex == sexFilter,
                        onClick = { selectedSex = sexFilter },
                        label = { Text(sexFilter.label) },
                        modifier = Modifier.testTag("sex_filter_${sexFilter.name.lowercase(Locale.ROOT)}")
                    )
                }
            }

            // Age filter row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AgeBracketFilter.entries) { bracket ->
                    FilterChip(
                        selected = selectedAgeBracket == bracket,
                        onClick = { selectedAgeBracket = bracket },
                        label = { Text(bracket.label) },
                        modifier = Modifier.testTag("age_filter_${bracket.name.lowercase(Locale.ROOT)}")
                    )
                }
            }
        }

        when (creatorsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = creatorsState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UiState.Success -> {
                val query = searchQuery.trim().lowercase(Locale.ROOT)
                val ageRange = selectedAgeBracket.range
                val sexValue = selectedSex.value
                val filteredUsers = remember(creatorsState.data, query, sexValue, ageRange) {
                    creatorsState.data.filter { creator ->
                        val matchesQuery = query.isEmpty() ||
                            creator.displayName.lowercase(Locale.ROOT).contains(query) ||
                            creator.bio.orEmpty().lowercase(Locale.ROOT).contains(query)
                        val matchesSex = sexValue == null || creator.sex == sexValue
                        val matchesAge = ageRange == null ||
                            (creator.age != null && creator.age in ageRange)
                        matchesQuery && matchesSex && matchesAge
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentUserProfile != null && (currentUserProfile.age == null || currentUserProfile.sex.isNullOrBlank())) {
                        item(key = "complete_profile_nudge") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Add your age & sex in Profile so others can find you with filters.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onNavigateToProfile) {
                                        Text("Edit")
                                    }
                                }
                            }
                        }
                    }

                    item(key = "users_results_header") {
                        Text(
                            text = "People on SnapVote (${filteredUsers.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (filteredUsers.isEmpty()) {
                        item(key = "no_matching_users") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No users match these filters",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Try clearing the age or sex filter, or ask creators to add their age & sex on their Profile.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredUsers, key = { it.creatorId }) { user ->
                            val isSelf = user.creatorId == currentUserId
                            val isUnlocked = unlockedCreatorIds.contains(user.creatorId)
                            val hasLockedPosts = user.postCount > user.freePreviewLimit
                            val demoParts = buildList {
                                user.age?.let { add("$it yrs") }
                                UserProfile.sexDisplayLabel(user.sex).takeIf { it.isNotEmpty() }?.let { add(it) }
                            }

                            val relationship = friendRelationships.firstOrNull {
                                it.otherUserId(currentUserId) == user.creatorId
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToCreator(user.creatorId) }
                                    .testTag("discover_user_card_${user.creatorId}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.colorScheme.secondary
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = user.displayName.take(1).uppercase(Locale.ROOT),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = user.displayName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                OnlineStatusDot(
                                                    isOnline = user.isCurrentlyOnline(),
                                                    showLabel = true
                                                )
                                                if (isSelf) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "YOU",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (demoParts.isNotEmpty()) {
                                                Text(
                                                    text = demoParts.joinToString(" • "),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                Text(
                                                    text = "Age & sex not listed",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (!user.bio.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = user.bio,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            val userCheckIn = activeCheckIns.firstOrNull { it.userId == user.creatorId }
                                            if (userCheckIn != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Checked in: ${userCheckIn.vibeTag} • ${userCheckIn.placeLabel}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${user.postCount} posts • ${user.totalVotesReceived} votes",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if ((hasLockedPosts || isUnlocked) && !isSelf) {
                                            GalleryLockBadge(
                                                isUnlocked = isUnlocked,
                                                unlockCostTokens = user.unlockCostVotes,
                                                onClick = { onUnlockCreatorClick(user) },
                                                compact = true,
                                                modifier = Modifier.testTag("user_lock_badge_${user.creatorId}")
                                            )
                                        }
                                        if (isStaff && !isSelf) {
                                            IconButton(
                                                onClick = { onWarnUserClick(user.creatorId, user.displayName) },
                                                modifier = Modifier.testTag("warn_user_${user.creatorId}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.WarningAmber,
                                                    contentDescription = "Send warning popup to ${user.displayName}",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        if (!isSelf) {
                                            IconButton(
                                                onClick = { onBlockUserClick(user.creatorId, user.displayName) },
                                                modifier = Modifier.testTag("block_user_${user.creatorId}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Block,
                                                    contentDescription = "Block ${user.displayName}",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    onReportUserClick(
                                                        user.creatorId,
                                                        user.displayName,
                                                        com.example.data.model.UserReport.CONTEXT_DISCOVER
                                                    )
                                                },
                                                modifier = Modifier.testTag("report_user_${user.creatorId}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Flag,
                                                    contentDescription = "Report ${user.displayName}",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (!isSelf) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val friendButtonLabel = when (relationship?.status) {
                                                FriendRelationship.STATUS_ACCEPTED -> "Friends"
                                                FriendRelationship.STATUS_PENDING -> "Pending"
                                                else -> "Add Friend"
                                            }
                                            val canSendRequest = relationship == null || relationship.status == FriendRelationship.STATUS_DECLINED
                                            OutlinedButton(
                                                onClick = { onSendFriendRequest(user.creatorId, user.displayName) },
                                                enabled = canSendRequest,
                                                contentPadding = PaddingValues(horizontal = 10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                            ) {
                                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(friendButtonLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            OutlinedButton(
                                                onClick = { onMessageUser(user.creatorId, user.displayName) },
                                                contentPadding = PaddingValues(horizontal = 10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                            ) {
                                                Icon(Icons.Rounded.MailOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("DM", maxLines = 1)
                                            }
                                            Button(
                                                onClick = { onScheduleMicCall(user) },
                                                contentPadding = PaddingValues(horizontal = 10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                            ) {
                                                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Mic", maxLines = 1)
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
    }
}

@Composable
private fun CreatorSpotlightCard(
    creator: CreatorSummary,
    isOwner: Boolean,
    isUnlocked: Boolean,
    onClick: () -> Unit,
    onLockClick: () -> Unit
) {
    val hasLockedPosts = creator.postCount > creator.freePreviewLimit
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(156.dp)
            .clickable(onClick = onClick)
            .testTag("creator_card_${creator.creatorId}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = creator.displayName.take(1).uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                if ((hasLockedPosts || isUnlocked) && !isOwner) {
                    GalleryLockBadge(
                        isUnlocked = isUnlocked,
                        unlockCostTokens = creator.unlockCostVotes,
                        onClick = onLockClick,
                        compact = true,
                        modifier = Modifier.testTag("spotlight_lock_badge_${creator.creatorId}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = creator.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.HowToVote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${creator.totalVotesReceived} • ${creator.postCount} posts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
