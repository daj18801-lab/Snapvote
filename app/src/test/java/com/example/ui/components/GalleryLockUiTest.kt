package com.example.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.CreatorSummary
import com.example.data.model.MediaPost
import com.example.ui.theme.SnapVoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GalleryLockUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val creator = CreatorSummary(
        creatorId = "creator_1",
        displayName = "Aria Vance",
        postCount = 4,
        freePreviewLimit = 2,
        unlockCostVotes = 3
    )

    private val restrictedPost = MediaPost(
        id = "post_restricted_1",
        creatorId = "creator_1",
        creatorName = "Aria Vance",
        mediaType = "photo",
        caption = "Secret VIP backstage photo",
        postIndex = 2,
        voteCount = 12
    )

    @Test
    fun mediaPostCard_hidesRestrictedMediaAndTriggersUnlockDialogForNonPayingUser() {
        var unlockClicked = false
        var openedViewer = false
        var voteClicked = false

        composeRule.setContent {
            SnapVoteTheme {
                MediaPostCard(
                    post = restrictedPost,
                    creatorSummary = creator,
                    isOwner = false,
                    isCreatorUnlocked = false,
                    isStaff = false,
                    onCreatorClick = {},
                    onVoteClick = { voteClicked = true },
                    onUnlockCreatorClick = { unlockClicked = true },
                    onOpenMediaClick = { openedViewer = true },
                    onReportClick = {},
                    onDeleteClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Locked Creator Media").assertIsDisplayed()
        composeRule.onNodeWithText("Unlock for 3 tokens").assertIsDisplayed()
        composeRule.onNodeWithText("Secret VIP backstage photo").assertDoesNotExist()
        composeRule.onNodeWithTag("vote_button_${restrictedPost.id}").assertIsNotEnabled()

        composeRule.onNodeWithTag("unlock_post_cta_${restrictedPost.id}").performClick()

        assertTrue(unlockClicked)
        assertFalse(openedViewer)
        assertFalse(voteClicked)
    }

    @Test
    fun mediaViewerDialog_blocksRestrictedMediaWhenLocked() {
        composeRule.setContent {
            SnapVoteTheme {
                MediaViewerDialog(
                    post = restrictedPost,
                    isLockedForViewer = true,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Locked Creator Media").assertIsDisplayed()
        composeRule.onNodeWithText("Secret VIP backstage photo").assertDoesNotExist()
    }

    @Test
    fun lockCardAndBadge_triggerUnlockForXTokensDialog() {
        var badgeClicks = 0
        var cardClicks = 0

        composeRule.setContent {
            SnapVoteTheme {
                androidx.compose.foundation.layout.Column {
                    GalleryLockBadge(
                        isUnlocked = false,
                        unlockCostTokens = 3,
                        onClick = { badgeClicks++ },
                        compact = false,
                        modifier = Modifier.testTag("test_lock_badge")
                    )
                    CreatorGalleryLockCard(
                        creator = creator,
                        lockedCount = 2,
                        freeLimit = 2,
                        unlockCostTokens = 3,
                        currentPaidBalance = 0,
                        onOpenUnlockDialog = { cardClicks++ }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("test_lock_badge").performClick()
        composeRule.onNodeWithTag("unlock_gallery_button").performClick()

        assertEquals(1, badgeClicks)
        assertEquals(1, cardClicks)
    }

    @Test
    fun unlockGalleryDialog_requiresPaidTokensAndConfirmsWhenFunded() {
        var buyClicked = false
        var confirmClicked = false

        composeRule.setContent {
            SnapVoteTheme {
                UnlockGalleryDialog(
                    creator = creator,
                    lockedPostCount = 2,
                    currentTotalBalance = 100,
                    currentPaidBalance = 0,
                    isBusy = false,
                    onConfirmUnlock = { confirmClicked = true },
                    onBuyPaidTokens = { buyClicked = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("unlock_dialog_title").assertIsDisplayed()
        composeRule.onNodeWithText("Unlock for 3 tokens").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_buy_paid_tokens_button").performClick()

        assertTrue(buyClicked)
        assertFalse(confirmClicked)
    }

    @Test
    fun unlockGalleryDialog_confirmsUnlockWhenUserHasPaidTokens() {
        var confirmedCreatorId: String? = null

        composeRule.setContent {
            SnapVoteTheme {
                UnlockGalleryDialog(
                    creator = creator,
                    lockedPostCount = 2,
                    currentTotalBalance = 110,
                    currentPaidBalance = 10,
                    isBusy = false,
                    onConfirmUnlock = { confirmedCreatorId = it.creatorId },
                    onBuyPaidTokens = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("unlock_dialog_title").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_unlock_gallery_button").performClick()

        assertEquals("creator_1", confirmedCreatorId)
    }
}
