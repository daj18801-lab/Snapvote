package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.model.BlockedUser
import com.example.data.model.CreatorSummary
import com.example.data.model.DirectChatThread
import com.example.data.model.EffectiveStaffTier
import com.example.data.model.UserProfile
import com.example.ui.theme.SnapVoteTheme
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlockedUsersAndPrivateLogUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun blockedUsersScreen_emptyState_displaysEmptyMessage() {
        composeRule.setContent {
            SnapVoteTheme {
                BlockedUsersScreen(
                    blockedUsersState = UiState.Success(emptyList()),
                    onBack = {},
                    onUnblockUser = {}
                )
            }
        }

        composeRule.onNodeWithTag("blocked_users_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("You haven't blocked anyone").assertIsDisplayed()
    }

    @Test
    fun blockedUsersScreen_searchAndConfirmUnblock_invokesCallback() {
        val bob = BlockedUser(
            id = "uid_bob",
            userId = "uid_alice",
            blockedUserId = "uid_bob",
            blockedUserName = "Bob Builder",
            reason = "Spam"
        )
        val charlie = BlockedUser(
            id = "uid_charlie",
            userId = "uid_alice",
            blockedUserId = "uid_charlie",
            blockedUserName = "Charlie Day",
            reason = "Harassment"
        )
        var unblockedId: String? = null

        composeRule.setContent {
            SnapVoteTheme {
                BlockedUsersScreen(
                    blockedUsersState = UiState.Success(listOf(bob, charlie)),
                    onBack = {},
                    onUnblockUser = { unblockedId = it.blockedUserId }
                )
            }
        }

        composeRule.onNodeWithTag("blocked_user_card_uid_bob").assertIsDisplayed()
        composeRule.onNodeWithTag("blocked_user_card_uid_charlie").assertIsDisplayed()

        // Filter search to Charlie only
        composeRule.onNodeWithTag("blocked_users_search_input").performTextInput("Charlie")
        composeRule.onNodeWithTag("blocked_user_card_uid_bob").assertDoesNotExist()
        composeRule.onNodeWithTag("blocked_user_card_uid_charlie").assertIsDisplayed()

        // Click Unblock and confirm dialog
        composeRule.onNodeWithTag("unblock_user_button_uid_charlie").performClick()
        composeRule.onNodeWithTag("confirm_unblock_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_unblock_button").performClick()

        assertEquals("uid_charlie", unblockedId)
    }

    @Test
    fun chatRoomsScreen_defaultsToMessagesTabShowingPrivateMessageLog() {
        val now = Timestamp(Date(System.currentTimeMillis() - 60_000L))
        val threads = listOf(
            DirectChatThread(
                threadId = "dm_alice_bob",
                participantIds = listOf("uid_alice", "uid_bob"),
                participantNames = mapOf("uid_alice" to "Alice", "uid_bob" to "Bob"),
                lastMessageText = "Hey Alice, check out my new shoot!",
                lastMessageSenderId = "uid_bob",
                lastMessageAt = now,
                updatedAt = now
            ),
            DirectChatThread(
                threadId = "dm_alice_carol",
                participantIds = listOf("uid_alice", "uid_carol"),
                participantNames = mapOf("uid_alice" to "Alice", "uid_carol" to "Carol"),
                lastMessageText = "Sent you a snap!",
                lastMessageSenderId = "uid_alice",
                lastMessageAt = now,
                updatedAt = now
            )
        )
        var openedThreadId: String? = null

        composeRule.setContent {
            SnapVoteTheme {
                ChatRoomsScreen(
                    currentUserId = "uid_alice",
                    staffTier = EffectiveStaffTier.NONE,
                    userProfile = UserProfile(userId = "uid_alice", displayName = "Alice"),
                    creators = listOf(
                        CreatorSummary(creatorId = "uid_bob", displayName = "Bob", isOnline = true),
                        CreatorSummary(creatorId = "uid_carol", displayName = "Carol", isOnline = false)
                    ),
                    myChatModeration = null,
                    activeRoomId = null,
                    activeDmThreadId = null,
                    activeGroupId = null,
                    activeMicSessionId = null,
                    isStaffInvisible = false,
                    roomState = null,
                    roomMessagesState = UiState.Success(emptyList()),
                    roomPresence = emptyList(),
                    myRoomBounce = null,
                    isRoomAudioMutedLocally = false,
                    friendRelationships = emptyList(),
                    directThreads = threads,
                    dmMessagesState = UiState.Success(emptyList()),
                    groupChats = emptyList(),
                    groupMessagesState = UiState.Success(emptyList()),
                    micSessions = emptyList(),
                    micLevel = 0f,
                    isMicCapturing = false,
                    micAudioChunks = emptyList(),
                    isActionBusy = false,
                    onToggleStaffInvisible = {},
                    onEnterRoom = {},
                    onLeaveRoom = {},
                    onClaimMonitorIfVacant = {},
                    onStartRoomVoiceTalk = {},
                    onStopRoomVoiceTalk = {},
                    onToggleRaiseHand = {},
                    onToggleLocalListenMute = {},
                    onSetMemberVoiceMuted = { _, _, _ -> },
                    onLowerMemberHand = { _, _ -> },
                    onSendMessage = { _, _ -> },
                    onDeleteMessage = {},
                    onBounceUser = { _, _, _ -> },
                    onMuteUser = { _, _, _ -> },
                    onUnmuteUser = {},
                    onBanUser = { _, _ -> },
                    onSuspendUser = { _, _, _, _ -> },
                    onBanAccountUser = { _, _, _ -> },
                    onReportUserClick = { _, _, _ -> },
                    onBuyChatPass = {},
                    onBuyVotesClick = {},
                    onBuyPrivatePass = {},
                    onBuyPhoneTokensClick = {},
                    onSendFriendRequest = { _, _ -> },
                    onRespondFriendRequest = { _, _ -> },
                    onRemoveFriend = { _, _ -> },
                    onOpenDirectChatWithUser = { _, _ -> },
                    onOpenDirectThread = { openedThreadId = it },
                    onCloseDirectChat = {},
                    onSendDirectMessage = { _, _ -> },
                    onCreateGroupChat = { _, _, _, _, _, _ -> },
                    onOpenGroupChat = {},
                    onCloseGroupChat = {},
                    onSendGroupMessage = { _, _ -> },
                    onUpdateGroupSettings = { _, _, _, _, _, _ -> },
                    onAddMembersToGroup = { _, _ -> },
                    onLeaveOrRemoveFromGroup = { _, _ -> },
                    onDeleteGroupChat = {},
                    onOpenScheduleMicDialog = {},
                    onRespondMicSession = { _, _ -> },
                    onCancelMicSession = {},
                    onJoinMicCall = {},
                    onStartMicCapture = { _, _ -> },
                    onStopMicCapture = {},
                    onLeaveMicCall = {},
                    onEndMicCall = {},
                    onOpenCreatorProfile = {},
                    initialSubTab = 0
                )
            }
        }

        composeRule.onNodeWithTag("private_message_log_list").assertIsDisplayed()
        composeRule.onNodeWithTag("private_message_log_header").assertIsDisplayed()
        composeRule.onNodeWithText("Private Message Log (2)").assertIsDisplayed()
        composeRule.onNodeWithTag("dm_thread_card_dm_alice_bob").assertIsDisplayed()
        composeRule.onNodeWithTag("dm_thread_card_dm_alice_carol").assertIsDisplayed()
        composeRule.onNodeWithText("You: Sent you a snap!").assertIsDisplayed()

        composeRule.onNodeWithTag("dm_thread_card_dm_alice_bob").performClick()
        assertEquals("dm_alice_bob", openedThreadId)
    }
}
