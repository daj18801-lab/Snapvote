package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AdminAnnouncement
import com.example.data.model.CreatorPayout
import com.example.data.model.DirectMessage
import com.example.data.model.MediaPost
import com.example.data.model.MicSession
import com.example.data.model.ReferralCashout
import com.example.data.model.ReferralRecord
import com.example.data.model.UserProfile
import com.example.data.model.UserStory
import com.example.data.model.UserWarning
import com.example.data.model.VideoSession
import com.example.data.model.VotePackCatalog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SnapVoteRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun initUploadVoteBuyAndUnlock_authenticatedUsers_succeedsEndToEnd() = runBlocking {
        val aliceEmail = "alice_${UUID.randomUUID()}@test.com"
        val bobEmail = "bob_${UUID.randomUUID()}@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val repo = SnapVoteRepository(firestore, auth)

        withTimeout(TIMEOUT_MS) { repo.ensureUserAccountInitialized("Alice").getOrThrow() }
        val aliceProfile = withTimeout(TIMEOUT_MS) { repo.observeUserProfile(aliceUid).first { it != null } }
        assertNotNull(aliceProfile)
        assertEquals(100L, aliceProfile!!.voteBalance)

        // Alice updates her discovery age, sex, and online/offline presence
        withTimeout(TIMEOUT_MS) {
            repo.updateProfile("Alice", "Portrait photographer", age = 25, sex = UserProfile.SEX_FEMALE).getOrThrow()
            repo.setUserOnlineStatus(false).getOrThrow()
            repo.setUserOnlineStatus(true).getOrThrow()
        }
        val creators = withTimeout(TIMEOUT_MS) {
            repo.observePublicCreators().first { list -> list.any { it.creatorId == aliceUid && it.age == 25 && it.isOnline } }
        }
        val aliceSummary = creators.first { it.creatorId == aliceUid }
        assertEquals(UserProfile.SEX_FEMALE, aliceSummary.sex)
        assertTrue(aliceSummary.isOnline)

        // Alice uploads 3 posts (posts 0 & 1 free preview, post 2 gated behind unlock)
        val postIds = (1..3).map { idx ->
            withTimeout(TIMEOUT_MS) {
                repo.uploadPost(
                    creatorName = "Alice",
                    mediaType = MediaPost.MEDIA_TYPE_PHOTO,
                    caption = "Shot #$idx",
                    mediaBase64 = "ZmFrZV9qcGVnX2Jhc2U2NA=="
                ).getOrThrow()
            }
        }
        assertEquals(3, postIds.size)

        // Bob signs in, starts with 100 tokens, votes, buys votes, unlocks Alice, and buys public chat pass
        val bobUid = signInTestUser(bobEmail)
        withTimeout(TIMEOUT_MS) { repo.ensureUserAccountInitialized("Bob").getOrThrow() }

        val publicPosts = withTimeout(TIMEOUT_MS) {
            repo.observePublicPosts().first { list -> list.any { it.id == postIds.first() } }
        }
        val targetPost = publicPosts.first { it.id == postIds.first() }
        assertEquals(0L, targetPost.postIndex)

        withTimeout(TIMEOUT_MS) { repo.castVoteOnPost(targetPost).getOrThrow() }

        val bobAfterVote = withTimeout(TIMEOUT_MS) {
            repo.observeUserProfile(bobUid).first { it?.voteBalance == 99L }
        }
        assertEquals(1L, bobAfterVote!!.totalVotesCast)
        assertEquals(0L, bobAfterVote.paidVoteBalance)

        // Bob CANNOT unlock Alice's gallery (to pay her money) using the 100 free starter tokens
        val blockedUnlock = withTimeout(TIMEOUT_MS) { repo.unlockCreatorGallery(aliceUid) }
        assertTrue(blockedUnlock.isFailure)
        val insufficientPaid = blockedUnlock.exceptionOrNull() as? InsufficientVotesException
        assertNotNull(insufficientPaid)
        assertTrue(insufficientPaid!!.paidTokensOnly)

        // Bob buys Starter Spark (+10 paid tokens -> 109 total tokens, 10 paid tokens)
        withTimeout(TIMEOUT_MS) { repo.buyVotePack(VotePackCatalog.packs.first()).getOrThrow() }
        val bobAfterBuy = withTimeout(TIMEOUT_MS) {
            repo.observeUserProfile(bobUid).first { it?.voteBalance == 109L }
        }
        assertEquals(10L, bobAfterBuy!!.totalVotesPurchased)
        assertEquals(10L, bobAfterBuy.paidVoteBalance)

        // Now Bob unlocks Alice's full gallery (-3 paid tokens -> 106 total, 7 paid)
        withTimeout(TIMEOUT_MS) { repo.unlockCreatorGallery(aliceUid).getOrThrow() }
        val unlockedIds = withTimeout(TIMEOUT_MS) {
            repo.observeUnlockedCreatorIds(bobUid).first { it.contains(aliceUid) }
        }
        assertTrue(unlockedIds.contains(aliceUid))
        val bobAfterUnlock = withTimeout(TIMEOUT_MS) {
            repo.observeUserProfile(bobUid).first { it?.voteBalance == 106L }
        }
        assertEquals(7L, bobAfterUnlock?.paidVoteBalance)

        // Bob enters lounge chat room visibly and posts a message for free (no chat pass needed)
        withTimeout(TIMEOUT_MS) {
            repo.enterChatRoom("lounge", "Bob", UserProfile.ROLE_USER, invisible = false).getOrThrow()
        }
        val msgId = withTimeout(TIMEOUT_MS) {
            repo.sendChatMessage("lounge", "Bob", UserProfile.ROLE_USER, "Hey everyone!").getOrThrow()
        }
        val messages = withTimeout(TIMEOUT_MS) {
            repo.observeRoomMessages("lounge").first { list -> list.any { it.id == msgId } }
        }
        assertTrue(messages.any { it.text == "Hey everyone!" })

        // Bob sends a friend request to Alice, and starts a private DM & group chat for free (no pass needed)
        withTimeout(TIMEOUT_MS) {
            repo.sendFriendRequest(aliceUid, "Alice", "Bob").getOrThrow()
        }

        // Bob opens 1:1 DM with Alice and sends a private message
        val threadId = withTimeout(TIMEOUT_MS) {
            repo.openOrCreateDirectThread(aliceUid, "Alice", "Bob").getOrThrow()
        }
        val dmId = withTimeout(TIMEOUT_MS) {
            repo.sendDirectMessage(threadId, "Bob", "Hey Alice! Added you as a friend.").getOrThrow()
        }
        val dms = withTimeout(TIMEOUT_MS) {
            repo.observeDirectMessages(threadId).first { list -> list.any { it.id == dmId } }
        }
        assertTrue(dms.any { it.text == "Hey Alice! Added you as a friend." })

        // Bob creates a private group chat, updates settings, and posts a group message
        val groupId = withTimeout(TIMEOUT_MS) {
            repo.createPrivateGroupChat(
                name = "Night Shoot Crew",
                description = "Weekend photo meetup",
                privacyMode = "invite_only",
                allowMemberInvites = false,
                invitedMembers = mapOf(aliceUid to "Alice"),
                myName = "Bob"
            ).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            repo.updateGroupChatSettings(
                groupId = groupId,
                name = "Night Shoot VIP",
                description = "Private crew",
                privacyMode = "friends_can_invite",
                allowMemberInvites = true
            ).getOrThrow()
        }
        val grpMsgId = withTimeout(TIMEOUT_MS) {
            repo.sendGroupMessage(groupId, "Bob", "Welcome to the private group!").getOrThrow()
        }
        val grpMessages = withTimeout(TIMEOUT_MS) {
            repo.observeGroupMessages(groupId).first { list -> list.any { it.id == grpMsgId } }
        }
        assertTrue(grpMessages.any { it.text == "Welcome to the private group!" })

        // Alice accepts Bob's friend request
        signInTestUser(aliceEmail)
        val aliceReqs = withTimeout(TIMEOUT_MS) {
            repo.observeFriendRelationships(aliceUid).first { it.isNotEmpty() }
        }
        withTimeout(TIMEOUT_MS) {
            repo.respondToFriendRequest(aliceReqs.first().pairId, accept = true).getOrThrow()
        }
        val acceptedReqs = withTimeout(TIMEOUT_MS) {
            repo.observeFriendRelationships(aliceUid).first { list -> list.any { it.status == "accepted" } }
        }
        assertTrue(acceptedReqs.isNotEmpty())

        // Alice verifies she received the 40% creator content payout from Bob unlocking her gallery (3 tokens -> 12 cents = 40%)
        val aliceUnlockPayouts = withTimeout(TIMEOUT_MS) {
            repo.observeCreatorPayouts(aliceUid).first { list ->
                list.any { it.sourceType == CreatorPayout.SOURCE_GALLERY_UNLOCK && it.payerId == bobUid }
            }
        }
        val unlockPayout = aliceUnlockPayouts.first { it.sourceType == CreatorPayout.SOURCE_GALLERY_UNLOCK }
        assertEquals(40L, unlockPayout.creatorSharePercent)
        assertEquals(12L, unlockPayout.creatorShareCents)

        // Switch back to Bob: schedule free 3-min & 2-min mic-to-mic sessions without buying phone tokens
        signInTestUser(bobEmail)
        val micSessionId1 = withTimeout(TIMEOUT_MS) {
            repo.scheduleMicSession(
                creatorId = aliceUid,
                creatorName = "Alice",
                requesterName = "Bob",
                topic = "Portrait lighting 1-on-1",
                scheduledForMillis = System.currentTimeMillis() + 300_000L,
                durationMinutes = 3L
            ).getOrThrow()
        }
        val micSessionId2 = withTimeout(TIMEOUT_MS) {
            repo.scheduleMicSession(
                creatorId = aliceUid,
                creatorName = "Alice",
                requesterName = "Bob",
                topic = "Quick chat",
                scheduledForMillis = System.currentTimeMillis() + 900_000L,
                durationMinutes = 2L
            ).getOrThrow()
        }

        // Alice accepts micSessionId1 and declines micSessionId2 cleanly with free escrowStatus
        signInTestUser(aliceEmail)
        val aliceSessions = withTimeout(TIMEOUT_MS) {
            repo.observeMicSessions(aliceUid).first { list -> list.size >= 2 }
        }
        val s1 = aliceSessions.first { it.sessionId == micSessionId1 }
        val s2 = aliceSessions.first { it.sessionId == micSessionId2 }
        assertEquals(0L, s1.totalTokensCost)
        assertEquals(MicSession.ESCROW_FREE, s1.escrowStatus)

        withTimeout(TIMEOUT_MS) {
            repo.respondToMicSession(s1, accept = true).getOrThrow()
            repo.respondToMicSession(s2, accept = false).getOrThrow()
        }

        // Alice & Bob exchange live audio chunk on accepted session
        withTimeout(TIMEOUT_MS) {
            repo.sendMicAudioChunk(micSessionId1, "Alice", "AQIDBA==", 400L, 1L).getOrThrow()
        }
        val chunks = withTimeout(TIMEOUT_MS) {
            repo.observeMicAudioChunks(micSessionId1).first { it.isNotEmpty() }
        }
        assertEquals(1, chunks.size)

        signInTestUser(bobEmail)
        val declinedForBob = withTimeout(TIMEOUT_MS) {
            repo.observeMicSessions(bobUid).first { list ->
                list.any { it.sessionId == micSessionId2 && it.status == MicSession.STATUS_DECLINED }
            }
        }.first { it.sessionId == micSessionId2 }
        assertEquals(MicSession.ESCROW_FREE, declinedForBob.escrowStatus)
    }

    @Test
    fun observeUserProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_priv_${UUID.randomUUID()}@test.com")
        val repo = SnapVoteRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) { repo.ensureUserAccountInitialized("Alice").getOrThrow() }

        signInTestUser("bob_priv_${UUID.randomUUID()}@test.com")
        var caught: Throwable? = null
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val fsEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    @Test
    fun observePublicPosts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        var caught: Throwable? = null
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("posts").whereEqualTo("visibility", "public").get().await()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val fsEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    @Test
    fun adminAndModerationActions_regularUser_failsWithPermissionDenied() = runBlocking {
        val uid = signInTestUser("regular_${UUID.randomUUID()}@test.com")
        val repo = SnapVoteRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) { repo.ensureUserAccountInitialized("Regular").getOrThrow() }

        val roleResult = withTimeout(TIMEOUT_MS) {
            repo.upsertStaffRole(
                targetEmail = "helper@example.com",
                displayLabel = "Helper",
                role = "support"
            )
        }
        assertTrue(roleResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            roleResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Regular user cannot list /users
        var listUsersEx: Throwable? = null
        try {
            withTimeout(TIMEOUT_MS) { firestore.collection("users").get().await() }
        } catch (t: Throwable) {
            listUsersEx = t
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            listUsersEx?.unwrapFirestoreException()?.code
        )

        // Regular user cannot assign themselves admin role
        val selfAdminResult = withTimeout(TIMEOUT_MS) {
            repo.assignUserRole(UserProfile(userId = uid, displayName = "Regular"), UserProfile.ROLE_ADMIN)
        }
        assertTrue(selfAdminResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            selfAdminResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Regular user cannot mute or bounce others
        val muteResult = withTimeout(TIMEOUT_MS) {
            repo.muteUserInChat("someone_else", "Someone", 15L, "Test")
        }
        assertTrue(muteResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            muteResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun reportsSupportTicketsMediaDeleteAndRoleBranding_succeeds() = runBlocking {
        // 1. Role formatting & Red/Blue color assertions
        assertEquals("Alex", com.example.ui.theme.formatDisplayNameWithRole("Alex", UserProfile.ROLE_ADMIN))
        assertEquals("Sam", com.example.ui.theme.formatDisplayNameWithRole("Sam", UserProfile.ROLE_SUPPORT))
        assertEquals("Pat", com.example.ui.theme.formatDisplayNameWithRole("Pat", UserProfile.ROLE_USER))
        assertEquals(com.example.ui.theme.AdminRed, com.example.ui.theme.roleBadgeForegroundColor(UserProfile.ROLE_ADMIN))
        assertEquals(com.example.ui.theme.SupportBlue, com.example.ui.theme.roleBadgeForegroundColor(UserProfile.ROLE_SUPPORT))

        // 2. Support 6-month weekly stipend eligibility math
        val now = System.currentTimeMillis()
        val ninetyDaysAgo = com.google.firebase.Timestamp(java.util.Date(now - 90L * 24L * 3600_000L))
        val twoHundredDaysAgo = com.google.firebase.Timestamp(java.util.Date(now - 200L * 24L * 3600_000L))
        val juniorSupport = UserProfile(role = UserProfile.ROLE_SUPPORT, supportStartedAt = ninetyDaysAgo)
        val veteranSupport = UserProfile(role = UserProfile.ROLE_SUPPORT, supportStartedAt = twoHundredDaysAgo)
        assertTrue(!juniorSupport.isSupportSixMonthsEligible(nowMillis = now))
        assertTrue(veteranSupport.canClaimWeeklySupportPay(nowMillis = now))

        // 3. Authenticated user uploads & deletes both photo and video, submits user report & support ticket
        val reporterUid = signInTestUser("rep_${UUID.randomUUID()}@test.com")
        val targetUid = "target_user_${UUID.randomUUID().toString().take(8)}"
        val repo = SnapVoteRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) { repo.ensureUserAccountInitialized("Reporter").getOrThrow() }

        val photoId = withTimeout(TIMEOUT_MS) {
            repo.uploadPost(
                creatorName = "Reporter",
                mediaType = MediaPost.MEDIA_TYPE_PHOTO,
                caption = "Photo test",
                mediaBase64 = "ZmFrZV9waG90bw=="
            ).getOrThrow()
        }
        val videoId = withTimeout(TIMEOUT_MS) {
            repo.uploadPost(
                creatorName = "Reporter",
                mediaType = MediaPost.MEDIA_TYPE_VIDEO,
                caption = "Video test",
                mediaBase64 = "ZmFrZV92aWRlbw==",
                durationMs = 4500L
            ).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            repo.deletePost(MediaPost(id = photoId, creatorId = reporterUid, mediaType = MediaPost.MEDIA_TYPE_PHOTO)).getOrThrow()
            repo.deletePost(MediaPost(id = videoId, creatorId = reporterUid, mediaType = MediaPost.MEDIA_TYPE_VIDEO)).getOrThrow()
        }

        withTimeout(TIMEOUT_MS) {
            repo.submitUserReport(
                reportedUserId = targetUid,
                reportedUserName = "BadActor",
                reporterName = "Reporter",
                reason = "Harassment or Bullying",
                details = "Spamming chat",
                sourceContext = "chat_room"
            ).getOrThrow()
        }

        val ticketId = withTimeout(TIMEOUT_MS) {
            repo.createSupportTicket(
                userName = "Reporter",
                category = com.example.data.model.SupportTicket.CATEGORY_OTHER,
                subject = "Need help",
                message = "Testing support ticket flow"
            ).getOrThrow()
        }
        val tickets = withTimeout(TIMEOUT_MS) {
            repo.observeMySupportTickets(reporterUid).first { list -> list.any { it.ticketId == ticketId } }
        }
        assertTrue(tickets.any { it.subject == "Need help" })
        withTimeout(TIMEOUT_MS) { repo.closeOwnSupportTicket(ticketId).getOrThrow() }
    }

    @Test
    fun chatRoomVoiceMonitorAndHandover_succeedsEndToEnd() = runBlocking {
        val aliceEmail = "voice_alice_${UUID.randomUUID()}@test.com"
        val bobEmail = "voice_bob_${UUID.randomUUID()}@test.com"
        val charlieEmail = "voice_charlie_${UUID.randomUUID()}@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val repo = SnapVoteRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserAccountInitialized("Alice").getOrThrow()
            repo.enterChatRoom("creators_hub", "Alice", UserProfile.ROLE_USER, invisible = false).getOrThrow()
        }

        // First user to enter becomes Room Monitor
        val stateAfterAlice = withTimeout(TIMEOUT_MS) {
            repo.observeRoomState("creators_hub").first { it?.monitorUid == aliceUid }
        }
        assertEquals(aliceUid, stateAfterAlice?.monitorUid)
        assertEquals("Alice", stateAfterAlice?.monitorName)

        // Bob enters second: monitor stays Alice; Bob raises hand and sends a voice chunk
        val bobUid = signInTestUser(bobEmail)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserAccountInitialized("Bob").getOrThrow()
            repo.enterChatRoom("creators_hub", "Bob", UserProfile.ROLE_USER, invisible = false).getOrThrow()
            repo.setRoomHandRaised("creators_hub", true).getOrThrow()
            repo.sendRoomVoiceChunk("creators_hub", "Bob", "AQIDBA==", 400L, 1L).getOrThrow()
        }
        val stateAfterBob = withTimeout(TIMEOUT_MS) {
            repo.observeRoomState("creators_hub").first { it != null }
        }
        assertEquals(aliceUid, stateAfterBob?.monitorUid)
        val chunks = withTimeout(TIMEOUT_MS) {
            repo.observeRoomVoiceChunks("creators_hub", System.currentTimeMillis() - 10_000L).first { it.isNotEmpty() }
        }
        assertTrue(chunks.any { it.senderId == bobUid })

        // Charlie joins third
        val charlieUid = signInTestUser(charlieEmail)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserAccountInitialized("Charlie").getOrThrow()
            repo.enterChatRoom("creators_hub", "Charlie", UserProfile.ROLE_USER, invisible = false).getOrThrow()
        }

        // Alice (Monitor) mutes Bob, verifying Bob cannot send voice chunks OR chat messages while muted
        signInTestUser(aliceEmail)
        withTimeout(TIMEOUT_MS) {
            repo.setRoomMemberVoiceMuted("creators_hub", bobUid, true).getOrThrow()
        }
        val bobMutedPresence = withTimeout(TIMEOUT_MS) {
            repo.observeRoomPresence("creators_hub", isAdmin = false).first { list ->
                list.any { it.userId == bobUid && it.isVoiceMuted && !it.handRaised }
            }
        }
        assertTrue(bobMutedPresence.any { it.userId == bobUid && it.isVoiceMuted })

        signInTestUser(bobEmail)
        val blockedVoice = withTimeout(TIMEOUT_MS) {
            repo.sendRoomVoiceChunk("creators_hub", "Bob", "AQIDBA==", 400L, 2L)
        }
        assertTrue(blockedVoice.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            blockedVoice.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
        val blockedChat = withTimeout(TIMEOUT_MS) {
            repo.sendChatMessage("creators_hub", "Bob", UserProfile.ROLE_USER, "Trying to chat while muted")
        }
        assertTrue(blockedChat.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            blockedChat.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Alice unmutes Bob -> Bob can chat again; then Alice bounces Charlie and leaves -> monitor transfers to Bob
        signInTestUser(aliceEmail)
        withTimeout(TIMEOUT_MS) {
            repo.setRoomMemberVoiceMuted("creators_hub", bobUid, false).getOrThrow()
        }
        signInTestUser(bobEmail)
        withTimeout(TIMEOUT_MS) {
            repo.sendChatMessage("creators_hub", "Bob", UserProfile.ROLE_USER, "Unmuted in room!").getOrThrow()
        }

        signInTestUser(aliceEmail)
        withTimeout(TIMEOUT_MS) {
            repo.bounceUserFromRoom("creators_hub", charlieUid, "Monitor bounce", 5L).getOrThrow()
            repo.leaveChatRoom("creators_hub").getOrThrow()
        }

        // Bob should now be the new Monitor
        signInTestUser(bobEmail)
        val stateAfterAliceLeft = withTimeout(TIMEOUT_MS) {
            repo.observeRoomState("creators_hub").first { it?.monitorUid == bobUid }
        }
        assertEquals(bobUid, stateAfterAliceLeft?.monitorUid)
        assertEquals("Bob", stateAfterAliceLeft?.monitorName)
    }

    @Test
    fun freeVideoToVideo_privateChatVideoSession_succeedsEndToEnd() = runBlocking {
        val creatorEmail = "video_creator_${UUID.randomUUID()}@test.com"
        val fanEmail = "video_fan_${UUID.randomUUID()}@test.com"

        val creatorUid = signInTestUser(creatorEmail)
        val repo = SnapVoteRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserAccountInitialized("VickyCreator").getOrThrow()
        }

        // Fan requests a 2-minute live creator video in DM for free (0 tokens required)
        val fanUid = signInTestUser(fanEmail)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserAccountInitialized("FanVideo").getOrThrow()
        }

        val videoSessionId = withTimeout(TIMEOUT_MS) {
            repo.requestPrivateVideoSession(
                threadId = com.example.data.model.DirectChatThread.threadIdFor(fanUid, creatorUid),
                creatorId = creatorUid,
                creatorName = "VickyCreator",
                requesterName = "FanVideo",
                note = "Say hi on video!",
                durationMinutes = 2L
            ).getOrThrow()
        }

        // Creator accepts -> streams video frames & audio chunks
        signInTestUser(creatorEmail)
        val pendingSession = withTimeout(TIMEOUT_MS) {
            repo.observeVideoSessions(creatorUid).first { list -> list.any { it.sessionId == videoSessionId } }
                .first { it.sessionId == videoSessionId }
        }
        assertEquals(0L, pendingSession.totalTokensCost)
        assertEquals(0L, pendingSession.creatorShareCents)
        assertEquals(VideoSession.ESCROW_FREE, pendingSession.escrowStatus)

        withTimeout(TIMEOUT_MS) {
            repo.respondToVideoSession(pendingSession, accept = true).getOrThrow()
            repo.updateVideoSessionStatus(videoSessionId, VideoSession.STATUS_ACTIVE).getOrThrow()
            repo.sendVideoFrameChunk(
                sessionId = videoSessionId,
                senderName = "VickyCreator",
                frameBase64 = "ZmFrZV9qcGVnX2ZyYW1l",
                width = 240,
                height = 180,
                sequenceNumber = 1L
            ).getOrThrow()
            repo.sendVideoAudioChunk(
                sessionId = videoSessionId,
                senderName = "VickyCreator",
                audioBase64 = "AQIDBA==",
                durationMs = 350L,
                sequenceNumber = 1L
            ).getOrThrow()
        }

        val frames = withTimeout(TIMEOUT_MS) {
            repo.observeVideoFrameChunks(videoSessionId).first { it.isNotEmpty() }
        }
        assertTrue(frames.any { it.senderId == creatorUid && it.width == 240 })

        // Fan sends a second 1-minute request, Creator declines cleanly
        signInTestUser(fanEmail)
        val declinedSessionId = withTimeout(TIMEOUT_MS) {
            repo.requestPrivateVideoSession(
                threadId = com.example.data.model.DirectChatThread.threadIdFor(fanUid, creatorUid),
                creatorId = creatorUid,
                creatorName = "VickyCreator",
                requesterName = "FanVideo",
                note = "Quick 1m video",
                durationMinutes = 1L
            ).getOrThrow()
        }

        signInTestUser(creatorEmail)
        val secondPending = withTimeout(TIMEOUT_MS) {
            repo.observeVideoSessions(creatorUid).first { list -> list.any { it.sessionId == declinedSessionId } }
                .first { it.sessionId == declinedSessionId }
        }
        withTimeout(TIMEOUT_MS) {
            repo.respondToVideoSession(secondPending, accept = false).getOrThrow()
        }

        signInTestUser(fanEmail)
        val declinedSession = withTimeout(TIMEOUT_MS) {
            repo.observeVideoSessions(fanUid).first { list ->
                list.any { it.sessionId == declinedSessionId && it.status == VideoSession.STATUS_DECLINED }
            }.first { it.sessionId == declinedSessionId }
        }
        assertEquals(VideoSession.ESCROW_FREE, declinedSession.escrowStatus)
    }

    @Test
    fun referralSystem_tenPayingUsersEarn100CreatorOrCashableTokens_succeedsEndToEnd() = runBlocking {
        val referrerEmail = "referrer_${UUID.randomUUID()}@test.com"
        val creatorEmail = "refcreator_${UUID.randomUUID()}@test.com"

        val creatorUid = signInTestUser(creatorEmail)
        val repo = SnapVoteRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserAccountInitialized("RefCreator").getOrThrow()
            repeat(3) { idx ->
                repo.uploadPost(
                    creatorName = "RefCreator",
                    mediaType = MediaPost.MEDIA_TYPE_PHOTO,
                    caption = "Creator photo $idx",
                    mediaBase64 = "ZmFrZV9qcGVnX2Jhc2U2NA=="
                ).getOrThrow()
            }
        }

        val referrerUid = signInTestUser(referrerEmail)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserAccountInitialized("AliceReferrer").getOrThrow()
        }
        val referrerProfileInitial = withTimeout(TIMEOUT_MS) {
            repo.observeUserProfile(referrerUid).first { it != null && !it.referralCode.isNullOrBlank() }
        }
        val referralCode = referrerProfileInitial!!.referralCode!!

        // Self-referral fails
        val selfResult = withTimeout(TIMEOUT_MS) {
            repo.applyReferralCode(referralCode, "AliceReferrer")
        }
        assertTrue(selfResult.isFailure)

        // 10 friends sign up, apply Alice's referral code, and buy paid token packs
        for (i in 0 until 10) {
            val buyerEmail = "buyer_${i}_${UUID.randomUUID()}@test.com"
            val buyerUid = signInTestUser(buyerEmail)
            withTimeout(TIMEOUT_MS) {
                repo.ensureUserAccountInitialized("Buyer $i").getOrThrow()
                val applied = repo.applyReferralCode(referralCode, "Buyer $i").getOrThrow()
                assertEquals(ReferralRecord.STATUS_PENDING, applied.status)
                repo.buyVotePack(VotePackCatalog.packs[i % VotePackCatalog.packs.size]).getOrThrow()
            }
            val qualifiedRecord = withTimeout(TIMEOUT_MS) {
                repo.observeMyReferralRecord(buyerUid).first { it?.status == ReferralRecord.STATUS_QUALIFIED }
            }
            assertEquals(ReferralRecord.STATUS_QUALIFIED, qualifiedRecord?.status)
        }

        // Back to Alice: observe 10 unclaimed qualified referrals and claim +100 tokens
        signInTestUser(referrerEmail)
        val referralsMade = withTimeout(TIMEOUT_MS) {
            repo.observeReferralsMadeBy(referrerUid).first { list ->
                list.count { it.isQualified && !it.rewardClaimed } >= 10
            }
        }
        assertEquals(10, referralsMade.count { it.isQualified && !it.rewardClaimed })

        val claim = withTimeout(TIMEOUT_MS) {
            repo.claimReferralMilestoneReward("AliceReferrer").getOrThrow()
        }
        assertEquals(100L, claim.tokensAwarded)
        assertEquals(10, claim.qualifiedReferralIds.size)

        val profileAfterClaim = withTimeout(TIMEOUT_MS) {
            repo.observeUserProfile(referrerUid).first { (it?.referralCashableBalance ?: 0L) == 100L }
        }
        assertEquals(200L, profileAfterClaim?.voteBalance)
        assertEquals(100L, profileAfterClaim?.paidVoteBalance)
        assertEquals(100L, profileAfterClaim?.referralCashableBalance)
        assertEquals(100L, profileAfterClaim?.totalReferralTokensEarned)

        // Alice spends 3 of her referral tokens unlocking RefCreator (creator gets 40% content payout = 12 cents)
        withTimeout(TIMEOUT_MS) {
            repo.unlockCreatorGallery(creatorUid).getOrThrow()
        }
        signInTestUser(creatorEmail)
        val refCreatorPayouts = withTimeout(TIMEOUT_MS) {
            repo.observeCreatorPayouts(creatorUid).first { list ->
                list.any { it.sourceType == CreatorPayout.SOURCE_GALLERY_UNLOCK && it.payerId == referrerUid }
            }
        }
        val refUnlockPayout = refCreatorPayouts.first { it.sourceType == CreatorPayout.SOURCE_GALLERY_UNLOCK && it.payerId == referrerUid }
        assertEquals(40L, refUnlockPayout.creatorSharePercent)
        assertEquals(12L, refUnlockPayout.creatorShareCents)

        signInTestUser(referrerEmail)
        val profileAfterUnlock = withTimeout(TIMEOUT_MS) {
            repo.observeUserProfile(referrerUid).first { (it?.paidVoteBalance ?: -1L) == 97L }
        }
        assertEquals(197L, profileAfterUnlock?.voteBalance)
        assertEquals(97L, profileAfterUnlock?.paidVoteBalance)
        assertEquals(97L, profileAfterUnlock?.referralCashableBalance)

        // Alice cashes in the remaining 97 referral tokens for $9.70
        val cashout = withTimeout(TIMEOUT_MS) {
            repo.cashInReferralTokens(
                tokensToCashIn = 97L,
                payoutMethod = ReferralCashout.METHOD_PAYPAL,
                payoutDestination = "alice@example.com",
                userName = "AliceReferrer"
            ).getOrThrow()
        }
        assertEquals(97L, cashout.tokensCashedIn)
        assertEquals(970L, cashout.usdCents)

        val profileAfterCashout = withTimeout(TIMEOUT_MS) {
            repo.observeUserProfile(referrerUid).first { (it?.totalReferralTokensCashedIn ?: 0L) == 97L }
        }
        assertEquals(100L, profileAfterCashout?.voteBalance)
        assertEquals(0L, profileAfterCashout?.paidVoteBalance)
        assertEquals(0L, profileAfterCashout?.referralCashableBalance)
        assertEquals(97L, profileAfterCashout?.totalReferralTokensCashedIn)
    }

    @Test
    fun snapsStoriesCheckIn_andAdminAlertsWarnings_succeedEndToEnd() = runBlocking {
        // Owner/Admin signs in using permanent owner email (matches isAdmin() rule)
        val ownerUid = signInTestUser("daj18801@gmail.com", verifyEmail = true)
        val repo = SnapVoteRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserAccountInitialized("ChrisOwner").getOrThrow()
        }

        // Admin sends global news announcement + alert broadcast
        val newsId = withTimeout(TIMEOUT_MS) {
            repo.sendAdminAnnouncement(
                category = AdminAnnouncement.CATEGORY_NEWS,
                title = "Creator Split Update",
                message = "Creators earn 40% on content unlocks and 70% on live calls!",
                senderName = "ChrisOwner",
                senderRole = "owner"
            ).getOrThrow()
        }
        val announcements = withTimeout(TIMEOUT_MS) {
            repo.observeAdminAnnouncements().first { list -> list.any { it.announcementId == newsId } }
        }
        assertTrue(announcements.any { it.title == "Creator Split Update" })

        // Bob signs up, posts a message story & a snap photo story, checks in, and gets a Staff Warning
        val bobEmail = "snap_bob_${UUID.randomUUID()}@test.com"
        val bobUid = signInTestUser(bobEmail)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserAccountInitialized("BobSnap").getOrThrow()
            repo.buyPrivateChatPass().getOrThrow()
        }

        // Bob cannot send admin announcement (must fail with PERMISSION_DENIED)
        val blockedAnnouncement = withTimeout(TIMEOUT_MS) {
            repo.sendAdminAnnouncement(
                category = AdminAnnouncement.CATEGORY_ALERT,
                title = "Fake Alert",
                message = "Should be denied",
                senderName = "BobSnap",
                senderRole = "user"
            )
        }
        assertTrue(blockedAnnouncement.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            blockedAnnouncement.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Bob shares a Message Story and a Photo Story
        val msgStoryId = withTimeout(TIMEOUT_MS) {
            repo.postStory(
                userName = "BobSnap",
                storyType = UserStory.TYPE_MESSAGE,
                text = "Live from my home studio!",
                backgroundStyle = UserStory.STYLE_NEON
            ).getOrThrow()
        }
        val photoStoryId = withTimeout(TIMEOUT_MS) {
            repo.postStory(
                userName = "BobSnap",
                storyType = UserStory.TYPE_PHOTO,
                text = "Behind the scenes snap",
                backgroundStyle = UserStory.STYLE_SUNSET,
                mediaBase64 = "ZmFrZV9zdG9yeV9waG90bw=="
            ).getOrThrow()
        }
        val stories = withTimeout(TIMEOUT_MS) {
            repo.observeActiveStories().first { list ->
                list.any { it.storyId == msgStoryId } && list.any { it.storyId == photoStoryId }
            }
        }
        assertTrue(stories.any { it.storyType == UserStory.TYPE_MESSAGE && it.text == "Live from my home studio!" })
        assertTrue(stories.any { it.isPhoto && it.text == "Behind the scenes snap" })

        // Bob checks in voluntarily, then clears his check-in
        withTimeout(TIMEOUT_MS) {
            repo.setCheckIn(
                userName = "BobSnap",
                placeLabel = "Main Studio",
                statusNote = "Shooting portraits",
                vibeTag = "Creating"
            ).getOrThrow()
        }
        val checkIns = withTimeout(TIMEOUT_MS) {
            repo.observeActiveCheckIns().first { list -> list.any { it.userId == bobUid && it.placeLabel == "Main Studio" } }
        }
        assertTrue(checkIns.any { it.userId == bobUid })

        // Bob sends a DM snap to ChrisOwner
        val threadId = withTimeout(TIMEOUT_MS) {
            repo.openOrCreateDirectThread(ownerUid, "ChrisOwner", "BobSnap").getOrThrow()
        }
        val snapMsgId = withTimeout(TIMEOUT_MS) {
            repo.sendDirectSnap(
                threadId = threadId,
                senderName = "BobSnap",
                caption = "Quick preview snap!",
                snapBase64 = "ZmFrZV9zbWFw",
                durationSeconds = 5L
            ).getOrThrow()
        }

        // Owner sees and marks Bob's snap opened, increments Bob's story views, and sends Bob a targeted warning popup
        signInTestUser("daj18801@gmail.com", verifyEmail = true)
        val dmsForOwner = withTimeout(TIMEOUT_MS) {
            repo.observeDirectMessages(threadId).first { list -> list.any { it.id == snapMsgId && it.isSnap } }
        }
        val receivedSnap = dmsForOwner.first { it.id == snapMsgId }
        assertEquals(DirectMessage.TYPE_SNAP, receivedSnap.messageType)
        withTimeout(TIMEOUT_MS) {
            repo.markDirectSnapViewed(threadId, snapMsgId).getOrThrow()
            repo.incrementStoryView(photoStoryId).getOrThrow()
            repo.sendUserWarning(
                targetUserId = bobUid,
                targetUserName = "BobSnap",
                senderName = "ChrisOwner",
                senderRole = "owner",
                severity = UserWarning.SEVERITY_WARNING,
                title = "Keep chat respectful",
                message = "Please review the community guidelines."
            ).getOrThrow()
        }

        // Bob receives the warning popup and acknowledges it
        signInTestUser(bobEmail)
        val bobWarnings = withTimeout(TIMEOUT_MS) {
            repo.observeMyWarnings(bobUid).first { list -> list.any { !it.acknowledged && it.title == "Keep chat respectful" } }
        }
        val warning = bobWarnings.first()
        withTimeout(TIMEOUT_MS) {
            repo.acknowledgeUserWarning(warning.warningId).getOrThrow()
            repo.clearCheckIn().getOrThrow()
        }
        val remainingUnacked = withTimeout(TIMEOUT_MS) {
            repo.observeMyWarnings(bobUid).first { list -> list.none { !it.acknowledged } }
        }
        assertTrue(remainingUnacked.none { !it.acknowledged })
    }

    @Test
    fun blockedUsers_ownerBlockObserveAndUnblock_enforcesIsolationAndSucceeds() = runBlocking {
        val aliceEmail = "block_alice_${UUID.randomUUID()}@test.com"
        val bobEmail = "block_bob_${UUID.randomUUID()}@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val repo = SnapVoteRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) { repo.ensureUserAccountInitialized("AliceBlock").getOrThrow() }

        val bobUid = signInTestUser(bobEmail)
        withTimeout(TIMEOUT_MS) { repo.ensureUserAccountInitialized("BobBlock").getOrThrow() }

        // Alice blocks Bob with an optional reason and observes the updated list
        signInTestUser(aliceEmail)
        withTimeout(TIMEOUT_MS) {
            repo.blockUser(
                blockedUserId = bobUid,
                blockedUserName = "BobBlock",
                reason = "Unwanted messages"
            ).getOrThrow()
        }
        val blockedList = withTimeout(TIMEOUT_MS) {
            repo.observeBlockedUsers(aliceUid).first { list -> list.any { it.blockedUserId == bobUid } }
        }
        assertEquals(1, blockedList.size)
        assertEquals("BobBlock", blockedList.first().blockedUserName)
        assertEquals("Unwanted messages", blockedList.first().reason)

        // Bob cannot read or delete Alice's blocked_users subcollection
        signInTestUser(bobEmail)
        var crossReadEx: Throwable? = null
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("blocked_users").get().await()
            }
        } catch (t: Throwable) {
            crossReadEx = t
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            crossReadEx?.unwrapFirestoreException()?.code
        )

        var crossDeleteEx: Throwable? = null
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("blocked_users").document(bobUid).delete().await()
            }
        } catch (t: Throwable) {
            crossDeleteEx = t
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            crossDeleteEx?.unwrapFirestoreException()?.code
        )

        // Unauthenticated cannot read blocked_users
        auth.signOut()
        var unauthEx: Throwable? = null
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("blocked_users").get().await()
            }
        } catch (t: Throwable) {
            unauthEx = t
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unauthEx?.unwrapFirestoreException()?.code
        )

        // Alice unblocks Bob and observes empty blocked list
        signInTestUser(aliceEmail)
        withTimeout(TIMEOUT_MS) {
            repo.unblockUser(bobUid).getOrThrow()
        }
        val afterUnblock = withTimeout(TIMEOUT_MS) {
            repo.observeBlockedUsers(aliceUid).first { list -> list.none { it.blockedUserId == bobUid } }
        }
        assertTrue(afterUnblock.isEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 12000L
    }
}


