const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_uid_1";
const OWNER_EMAIL = "daj18801@gmail.com";
const ALICE_UID = "alice_123";
const ALICE_EMAIL = "alice@example.com";
const BOB_UID = "bob_456";
const BOB_EMAIL = "bob@example.com";
const SUPPORT_UID = "support_789";
const SUPPORT_EMAIL = "support@example.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or list posts, creators, users, staff_roles, reports, or chat rooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("creators").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("staff_roles").get());
  await assertFails(unauthDb.collection("reports").get());
  await assertFails(unauthDb.collection("chat_rooms").doc("lounge").collection("messages").get());
  await assertFails(unauthDb.collection("chat_moderation").get());
});

test("Email spoofing test: unverified owner email is denied admin privileges", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofDb.collection("staff_roles").doc(ALICE_EMAIL).set({
      email: ALICE_EMAIL,
      role: "admin",
      active: true,
      assignedByUid: "spoof_uid",
      assignedByEmail: OWNER_EMAIL,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertFails(spoofDb.collection("staff_roles").get());
  await assertFails(spoofDb.collection("users").get());
});

test("Admin Dashboard user listing & role assignment work for Owner/Admin; regular users cannot list users or self-escalate", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      email: OWNER_EMAIL,
      displayName: "Owner",
      role: "admin",
      voteBalance: 5,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      voteBalance: 5,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      role: "user",
      voteBalance: 5,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: BOB_EMAIL,
    email_verified: true,
  }).firestore();

  // Regular user cannot list /users or self-assign role
  await assertFails(aliceDb.collection("users").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: new Date(),
    })
  );

  // Owner lists all users and assigns Alice -> admin and Bob -> support
  await assertSucceeds(ownerDb.collection("users").get());
  await assertSucceeds(
    ownerDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(BOB_UID).update({
      role: "support",
      updatedAt: new Date(),
    })
  );

  // Appointed admin cannot demote owner account
  await assertFails(
    aliceDb.collection("users").doc(OWNER_UID).update({
      role: "user",
      updatedAt: new Date(),
    })
  );

  // Support user can list /users to moderate community members, but CANNOT assign roles
  await assertSucceeds(bobDb.collection("users").get());
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      role: "admin",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      role: "user",
      updatedAt: new Date(),
    })
  );
});

test("Chat Rooms: free access for all signed-in users, unknown room & ban checks preserved", async () => {
  const now = new Date();
  const eightDaysAgo = new Date(now.getTime() - 8 * 24 * 60 * 60 * 1000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      voteBalance: 5,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    // Bob signed up 8 days ago with no paid chat pass -> should still have free chat access
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      role: "user",
      voteBalance: 0,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: eightDaysAgo,
      updatedAt: eightDaysAgo,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: BOB_EMAIL,
    email_verified: true,
  }).firestore();

  // Alice can read & send in a valid room for free
  await assertSucceeds(aliceDb.collection("chat_rooms").doc("lounge").collection("messages").get());
  await assertSucceeds(
    aliceDb.collection("chat_rooms").doc("lounge").collection("messages").doc("msg_1").set({
      roomId: "lounge",
      senderId: ALICE_UID,
      senderName: "Alice",
      senderRole: "user",
      text: "Hello SnapVote lounge!",
      createdAt: new Date(),
    })
  );

  // Unknown chat room is still rejected
  await assertFails(aliceDb.collection("chat_rooms").doc("secret_room").collection("messages").get());

  // Bob (even >7 days old with 0 coins & no pass) can read & chat for free
  await assertSucceeds(bobDb.collection("chat_rooms").doc("lounge").collection("messages").get());
  await assertSucceeds(
    bobDb.collection("chat_rooms").doc("lounge").collection("messages").doc("msg_2").set({
      roomId: "lounge",
      senderId: BOB_UID,
      senderName: "Bob",
      senderRole: "user",
      text: "Free chat rooms rock!",
      createdAt: new Date(),
    })
  );
});

test("Chat moderation: invisible staff, bounce, mute, and ban enforcement", async () => {
  const now = new Date();
  const tenMinsLater = new Date(now.getTime() + 10 * 60 * 1000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("staff_roles").doc(SUPPORT_EMAIL).set({
      email: SUPPORT_EMAIL,
      role: "support",
      active: true,
      assignedByUid: OWNER_UID,
      assignedByEmail: OWNER_EMAIL,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      voteBalance: 5,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      role: "user",
      voteBalance: 5,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();
  const supportDb = testEnv.authenticatedContext(SUPPORT_UID, {
    email: SUPPORT_EMAIL,
    email_verified: true,
  }).firestore();

  // Regular user cannot set invisible presence
  await assertFails(
    aliceDb.collection("chat_rooms").doc("lounge").collection("presence").doc(ALICE_UID).set({
      roomId: "lounge",
      userId: ALICE_UID,
      displayName: "Alice",
      role: "user",
      isInvisible: true,
      joinedAt: now,
      lastActiveAt: now,
    })
  );

  // Regular user enters visibly
  await assertSucceeds(
    aliceDb.collection("chat_rooms").doc("lounge").collection("presence").doc(ALICE_UID).set({
      roomId: "lounge",
      userId: ALICE_UID,
      displayName: "Alice",
      role: "user",
      isInvisible: false,
      joinedAt: now,
      lastActiveAt: now,
    })
  );

  // Support can read messages and visible presence (even without creating a presence doc)
  await assertSucceeds(supportDb.collection("chat_rooms").doc("lounge").collection("messages").get());
  await assertSucceeds(
    supportDb.collection("chat_rooms").doc("lounge").collection("presence")
      .where("isInvisible", "==", false).get()
  );

  // Support bounces Alice from lounge and deletes her presence doc
  await assertSucceeds(
    supportDb.collection("chat_rooms").doc("lounge").collection("bounces").doc(ALICE_UID).set({
      roomId: "lounge",
      userId: ALICE_UID,
      reason: "Cool off",
      bouncedByUid: SUPPORT_UID,
      bouncedUntil: tenMinsLater,
      bouncedAt: now,
    })
  );
  await assertSucceeds(
    supportDb.collection("chat_rooms").doc("lounge").collection("presence").doc(ALICE_UID).delete()
  );

  // Bounced Alice cannot read or post in lounge, though she can still access creators_hub
  await assertFails(aliceDb.collection("chat_rooms").doc("lounge").collection("messages").get());
  await assertSucceeds(aliceDb.collection("chat_rooms").doc("creators_hub").collection("messages").get());

  // Support mutes Alice globally in chat
  await assertSucceeds(
    supportDb.collection("chat_moderation").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      mutedUntil: tenMinsLater,
      muteReason: "Spamming",
      banned: false,
      moderatedByUid: SUPPORT_UID,
      moderatedByEmail: SUPPORT_EMAIL,
      updatedAt: now,
    })
  );

  // Muted Alice can read creators_hub messages, but cannot send a message
  await assertSucceeds(aliceDb.collection("chat_rooms").doc("creators_hub").collection("messages").get());
  await assertFails(
    aliceDb.collection("chat_rooms").doc("creators_hub").collection("messages").doc("m_muted").set({
      roomId: "creators_hub",
      senderId: ALICE_UID,
      senderName: "Alice",
      senderRole: "user",
      text: "Trying to chat while muted",
      createdAt: new Date(),
    })
  );

  // Support cannot directly set banned=true; Admin must apply chat bans
  await assertFails(
    supportDb.collection("chat_moderation").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      banned: true,
      banReason: "Direct ban attempt",
      moderatedByUid: SUPPORT_UID,
      moderatedByEmail: SUPPORT_EMAIL,
      updatedAt: new Date(),
    })
  );

  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    ownerDb.collection("chat_moderation").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      banned: true,
      banReason: "Repeated abuse",
      moderatedByUid: OWNER_UID,
      moderatedByEmail: OWNER_EMAIL,
      updatedAt: new Date(),
    })
  );

  // Banned Alice cannot even read creators_hub
  await assertFails(aliceDb.collection("chat_rooms").doc("creators_hub").collection("messages").get());
});

test("User wallet, public posts query alignment, voting, and unlocking work securely", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: BOB_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("creators").doc(ALICE_UID).set({
      creatorId: ALICE_UID,
      displayName: "Alice",
      visibility: "public",
      postCount: 1,
      totalVotesReceived: 0,
      freePreviewLimit: 2,
      unlockCostVotes: 3,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      creatorId: ALICE_UID,
      creatorName: "Alice",
      mediaType: "photo",
      caption: "Sunset",
      mediaBase64: "abc123",
      durationMs: 0,
      postIndex: 0,
      voteCount: 0,
      visibility: "public",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("posts").get());
  await assertSucceeds(bobDb.collection("posts").where("visibility", "==", "public").get());

  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({
      voteCount: 1,
      updatedAt: new Date(),
    })
  );

  // Alice updates her age and sex for discovery filtering
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      age: 26,
      sex: "female",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("creators").doc(ALICE_UID).update({
      age: 26,
      sex: "female",
      updatedAt: new Date(),
    })
  );
  // Age under 18 is rejected
  await assertFails(
    aliceDb.collection("creators").doc(ALICE_UID).update({
      age: 16,
      updatedAt: new Date(),
    })
  );
});

test("Friends list, online/offline status, free private DMs, and free private group chats", async () => {
  const now = new Date();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    for (const [uid, email, name] of [
      [ALICE_UID, ALICE_EMAIL, "Alice"],
      [BOB_UID, BOB_EMAIL, "Bob"],
      [SUPPORT_UID, SUPPORT_EMAIL, "Charlie"],
    ]) {
      await ctx.firestore().collection("users").doc(uid).set({
        userId: uid,
        email,
        displayName: name,
        role: "user",
        voteBalance: 0,
        totalVotesCast: 0,
        totalVotesPurchased: 0,
        createdAt: now,
        updatedAt: now,
      });
      await ctx.firestore().collection("creators").doc(uid).set({
        creatorId: uid,
        displayName: name,
        visibility: "public",
        postCount: 0,
        totalVotesReceived: 0,
        freePreviewLimit: 2,
        unlockCostVotes: 3,
        isOnline: false,
        createdAt: now,
        updatedAt: now,
      });
    }
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const charlieDb = testEnv.authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true }).firestore();

  // Online/offline status updates on creators
  await assertSucceeds(
    aliceDb.collection("creators").doc(ALICE_UID).update({
      isOnline: true,
      lastSeenAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("creators").doc(ALICE_UID).update({
      isOnline: false,
      updatedAt: now,
    })
  );

  // Friend request flow: Alice -> Bob, Bob accepts
  const pairId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("friend_requests").doc(pairId).set({
      pairId,
      senderId: ALICE_UID,
      senderName: "Alice",
      receiverId: BOB_UID,
      receiverName: "Bob",
      participantIds: [ALICE_UID, BOB_UID],
      status: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );
  // Charlie cannot read Alice<->Bob friend request
  await assertFails(charlieDb.collection("friend_requests").doc(pairId).get());
  // Bob accepts
  await assertSucceeds(
    bobDb.collection("friend_requests").doc(pairId).update({
      status: "accepted",
      updatedAt: new Date(),
    })
  );

  // Alice starts a private DM and sends message freely without any pass
  const threadId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("direct_chats").doc(threadId).set({
      threadId,
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      lastMessageText: "Hey Bob!",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("direct_chats").doc(threadId).collection("messages").doc("dm_1").set({
      threadId,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Hey Bob!",
      createdAt: now,
    })
  );
  // Charlie cannot read Alice<->Bob DM
  await assertFails(charlieDb.collection("direct_chats").doc(threadId).get());
  // Bob can read and reply immediately for free
  await assertSucceeds(bobDb.collection("direct_chats").doc(threadId).collection("messages").get());
  await assertSucceeds(
    bobDb.collection("direct_chats").doc(threadId).collection("messages").doc("dm_2").set({
      threadId,
      senderId: BOB_UID,
      senderName: "Bob",
      text: "Free DM reply!",
      createdAt: now,
    })
  );

  // Alice creates a private group chat & updates settings for free
  await assertSucceeds(
    aliceDb.collection("group_chats").doc("grp_1").set({
      groupId: "grp_1",
      name: "Photo Squad",
      description: "Friends chat",
      ownerId: ALICE_UID,
      memberIds: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      privacyMode: "invite_only",
      allowMemberInvites: false,
      lastMessageText: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_chats").doc("grp_1").update({
      privacyMode: "friends_can_invite",
      allowMemberInvites: true,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_chats").doc("grp_1").collection("messages").doc("gm_1").set({
      groupId: "grp_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Welcome to Photo Squad!",
      createdAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("group_chats").doc("grp_1").collection("messages").doc("gm_2").set({
      groupId: "grp_1",
      senderId: BOB_UID,
      senderName: "Bob",
      text: "Free group chat works!",
      createdAt: now,
    })
  );
  // Charlie is not a member and cannot read the group
  await assertFails(charlieDb.collection("group_chats").doc("grp_1").get());
});

test("40% creator gallery unlock payouts and free mic-to-mic calls", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      phoneTokenBalance: 0,
      totalPhoneTokensPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      role: "user",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      phoneTokenBalance: 0,
      totalPhoneTokensPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("creators").doc(ALICE_UID).set({
      creatorId: ALICE_UID,
      displayName: "Alice",
      visibility: "public",
      postCount: 3,
      totalVotesReceived: 10,
      freePreviewLimit: 2,
      unlockCostVotes: 5,
      isOnline: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const charlieDb = testEnv.authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true }).firestore();

  // 1a. Bob ONLY has 100 free starter tokens (paidVoteBalance=0) -> CANNOT unlock or pay Alice!
  const freeUnlockAttempt = bobDb.batch();
  freeUnlockAttempt.update(bobDb.collection("users").doc(BOB_UID), {
    voteBalance: 95,
    updatedAt: now,
  });
  freeUnlockAttempt.set(bobDb.collection("users").doc(BOB_UID).collection("unlocks").doc(ALICE_UID), {
    userId: BOB_UID,
    creatorId: ALICE_UID,
    votesSpent: 5,
    unlockedAt: now,
  });
  freeUnlockAttempt.set(bobDb.collection("creator_payouts").doc("payout_free_rejected"), {
    payoutId: "payout_free_rejected",
    creatorId: ALICE_UID,
    payerId: BOB_UID,
    payerName: "Bob",
    sourceType: "gallery_unlock",
    sourceId: ALICE_UID,
    grossTokens: 5,
    creatorSharePercent: 40,
    creatorShareCents: 20,
    createdAt: now,
  });
  await assertFails(freeUnlockAttempt.commit());

  // 1b. Bob buys a 10-token PAID pack (+10 voteBalance, +10 paidVoteBalance, +10 totalVotesPurchased)
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).update({
      voteBalance: 110,
      paidVoteBalance: 10,
      totalVotesPurchased: 10,
      updatedAt: now,
    })
  );

  // 1c. Now Bob spends 5 PAID tokens to unlock Alice's gallery and create a 40% creator payout (20 cents)
  const unlockBatch = bobDb.batch();
  unlockBatch.update(bobDb.collection("users").doc(BOB_UID), {
    voteBalance: 105,
    paidVoteBalance: 5,
    updatedAt: now,
  });
  unlockBatch.set(bobDb.collection("users").doc(BOB_UID).collection("unlocks").doc(ALICE_UID), {
    userId: BOB_UID,
    creatorId: ALICE_UID,
    votesSpent: 5,
    unlockedAt: now,
  });
  unlockBatch.set(bobDb.collection("creator_payouts").doc("payout_unlock_1"), {
    payoutId: "payout_unlock_1",
    creatorId: ALICE_UID,
    payerId: BOB_UID,
    payerName: "Bob",
    sourceType: "gallery_unlock",
    sourceId: ALICE_UID,
    grossTokens: 5,
    creatorSharePercent: 40,
    creatorShareCents: 20,
    createdAt: now,
  });
  await assertSucceeds(unlockBatch.commit());

  // Forging the old 70% payout on gallery_unlock fails (only 40% allowed on content unlocks)
  await assertFails(
    bobDb.collection("creator_payouts").doc("fake_payout_70").set({
      payoutId: "fake_payout_70",
      creatorId: ALICE_UID,
      payerId: BOB_UID,
      payerName: "Bob",
      sourceType: "gallery_unlock",
      sourceId: ALICE_UID,
      grossTokens: 5,
      creatorSharePercent: 70,
      creatorShareCents: 35,
      createdAt: now,
    })
  );

  // 2. Forging a non-free mic session (e.g. tokensPerMinute: 5 or escrowStatus: held) is rejected
  await assertFails(
    bobDb.collection("mic_sessions").doc("forged_mic").set({
      sessionId: "forged_mic",
      requesterId: BOB_UID,
      requesterName: "Bob",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      participantIds: [BOB_UID, ALICE_UID],
      topic: "Forged rate",
      scheduledForMillis: now.getTime() + 600000,
      durationMinutes: 4,
      tokensPerMinute: 5,
      totalTokensCost: 20,
      creatorShareTokens: 14,
      creatorShareCents: 140,
      status: "pending",
      escrowStatus: "held",
      createdAt: now,
      updatedAt: now,
    })
  );

  // 3. Bob schedules a free 4-minute mic-to-mic session with Alice (0 tokens, no phone tokens required)
  await assertSucceeds(
    bobDb.collection("mic_sessions").doc("mic_1").set({
      sessionId: "mic_1",
      requesterId: BOB_UID,
      requesterName: "Bob",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      participantIds: [BOB_UID, ALICE_UID],
      topic: "Camera & lighting Q&A",
      scheduledForMillis: now.getTime() + 600000,
      durationMinutes: 4,
      tokensPerMinute: 0,
      totalTokensCost: 0,
      creatorShareTokens: 0,
      creatorShareCents: 0,
      status: "pending",
      escrowStatus: "free",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cannot send audio chunks while session is still pending
  await assertFails(
    bobDb.collection("mic_sessions").doc("mic_1").collection("audio_chunks").doc("c0").set({
      chunkId: "c0",
      sessionId: "mic_1",
      senderId: BOB_UID,
      senderName: "Bob",
      sampleRate: 8000,
      audioBase64: "AAAA",
      durationMs: 400,
      sequenceNumber: 0,
      createdAt: now,
    })
  );

  // 4. Alice accepts mic_1 freely
  await assertSucceeds(
    aliceDb.collection("mic_sessions").doc("mic_1").update({
      status: "accepted",
      escrowStatus: "free",
      updatedAt: new Date(),
    })
  );

  // 5. Alice & Bob can exchange live audio chunks; Charlie cannot read or write
  await assertSucceeds(
    bobDb.collection("mic_sessions").doc("mic_1").collection("audio_chunks").doc("c1").set({
      chunkId: "c1",
      sessionId: "mic_1",
      senderId: BOB_UID,
      senderName: "Bob",
      sampleRate: 8000,
      audioBase64: "AQIDBA==",
      durationMs: 400,
      sequenceNumber: 1,
      createdAt: now,
    })
  );
  await assertFails(charlieDb.collection("mic_sessions").doc("mic_1").get());
  await assertFails(charlieDb.collection("mic_sessions").doc("mic_1").collection("audio_chunks").get());

  // 6. Declined & cancelled free mic sessions transition cleanly
  await assertSucceeds(
    bobDb.collection("mic_sessions").doc("mic_2").set({
      sessionId: "mic_2",
      requesterId: BOB_UID,
      requesterName: "Bob",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      participantIds: [BOB_UID, ALICE_UID],
      topic: "Quick check-in",
      scheduledForMillis: now.getTime() + 900000,
      durationMinutes: 2,
      tokensPerMinute: 0,
      totalTokensCost: 0,
      creatorShareTokens: 0,
      creatorShareCents: 0,
      status: "pending",
      escrowStatus: "free",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("mic_sessions").doc("mic_2").update({
      status: "declined",
      escrowStatus: "free",
      updatedAt: new Date(),
    })
  );
});

test("Support & Admin moderation: Suspend, Account Ban, User Reports, and Support Tickets", async () => {
  const now = new Date();
  const oneDayLater = new Date(now.getTime() + 24 * 60 * 60 * 1000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      email: OWNER_EMAIL,
      displayName: "Owner",
      role: "admin",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "SupportMod",
      role: "support",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      role: "user",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const supportDb = testEnv.authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();

  // 1. Alice submits a User Report against Bob
  await assertSucceeds(
    aliceDb.collection("user_reports").doc("ur_1").set({
      reportId: "ur_1",
      reportedUserId: BOB_UID,
      reportedUserName: "Bob",
      reporterId: ALICE_UID,
      reporterName: "Alice",
      reason: "Harassment",
      details: "Spamming abusive messages in chat",
      sourceContext: "chat_room",
      status: "open",
      createdAt: now,
      updatedAt: now,
    })
  );
  // Bob cannot read Alice's report against him
  await assertFails(bobDb.collection("user_reports").doc("ur_1").get());

  // 2. Support reads user report, suspends Bob for 24h, and resolves the report
  await assertSucceeds(supportDb.collection("user_reports").get());
  await assertSucceeds(
    supportDb.collection("chat_moderation").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      banned: false,
      suspendedUntil: oneDayLater,
      suspendReason: "Harassment in lounge",
      accountBanned: false,
      moderatedByUid: SUPPORT_UID,
      moderatedByEmail: SUPPORT_EMAIL,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    supportDb.collection("user_reports").doc("ur_1").update({
      status: "action_taken",
      reviewedByUid: SUPPORT_UID,
      resolutionNote: "Suspended 24 hours",
      updatedAt: new Date(),
    })
  );

  // Support CANNOT suspend or ban Owner/Admin
  await assertFails(
    supportDb.collection("chat_moderation").doc(OWNER_UID).set({
      userId: OWNER_UID,
      displayName: "Owner",
      banned: false,
      accountBanned: true,
      accountBanReason: "Unauthorized",
      moderatedByUid: SUPPORT_UID,
      moderatedByEmail: SUPPORT_EMAIL,
      updatedAt: now,
    })
  );

  // 3. Suspended Bob is blocked from creating posts, friend requests, or entering chat rooms
  await assertFails(
    bobDb.collection("posts").doc("p_blocked").set({
      creatorId: BOB_UID,
      creatorName: "Bob",
      mediaType: "photo",
      caption: "Should be blocked",
      mediaBase64: "abc",
      durationMs: 0,
      postIndex: 0,
      voteCount: 0,
      visibility: "public",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("chat_rooms").doc("lounge").collection("messages").get());

  // 4. Suspended Bob CAN still submit a Support Ticket to appeal!
  await assertSucceeds(
    bobDb.collection("support_tickets").doc("tk_1").set({
      ticketId: "tk_1",
      userId: BOB_UID,
      userName: "Bob",
      userEmail: BOB_EMAIL,
      category: "account_appeal",
      subject: "Suspension Appeal",
      message: "Please review my 24h suspension, I understand the rules now.",
      status: "open",
      createdAt: now,
      updatedAt: now,
    })
  );
  // Alice cannot read Bob's support ticket
  await assertFails(aliceDb.collection("support_tickets").doc("tk_1").get());

  // 5. Support replies & resolves the support ticket
  await assertSucceeds(
    supportDb.collection("support_tickets").doc("tk_1").update({
      status: "resolved",
      staffReply: "Thanks for apologizing — suspension expires in 24h.",
      respondedByUid: SUPPORT_UID,
      respondedByName: "SupportMod",
      updatedAt: new Date(),
    })
  );

  // Support CANNOT directly set accountBanned=true; it must suspend + request ban approval from Admin
  await assertFails(
    supportDb.collection("chat_moderation").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      banned: true,
      banReason: "Repeated abuse",
      accountBanned: true,
      accountBanReason: "Severe ToS violation",
      moderatedByUid: SUPPORT_UID,
      moderatedByEmail: SUPPORT_EMAIL,
      updatedAt: new Date(),
    })
  );
});

test("Support 6-month (180-day) 100 token/week payroll eligibility and cooldown", async () => {
  const now = new Date();
  const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
  const twoHundredDaysAgo = new Date(now.getTime() - 200 * 24 * 60 * 60 * 1000);
  const twoDaysAgo = new Date(now.getTime() - 2 * 24 * 60 * 60 * 1000);
  const eightDaysAgo = new Date(now.getTime() - 8 * 24 * 60 * 60 * 1000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    // Alice: regular user (never eligible)
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: twoHundredDaysAgo,
      updatedAt: now,
    });
    // Bob: support user with only 30 days tenure (not yet 6 months)
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      role: "support",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      supportStartedAt: thirtyDaysAgo,
      totalSupportPayTokensEarned: 0,
      createdAt: twoHundredDaysAgo,
      updatedAt: now,
    });
    // SupportMod: support user with 200 days tenure (>= 6 months)
    await ctx.firestore().collection("users").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "SupportMod",
      role: "support",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      supportStartedAt: twoHundredDaysAgo,
      totalSupportPayTokensEarned: 0,
      createdAt: twoHundredDaysAgo,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const supportDb = testEnv.authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true }).firestore();

  // Regular user cannot claim support payroll
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      voteBalance: 200,
      totalSupportPayTokensEarned: 100,
      lastSupportPayClaimAt: now,
      updatedAt: now,
    })
  );

  // 30-day support user cannot claim before 6 months (180 days)
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      voteBalance: 200,
      totalSupportPayTokensEarned: 100,
      lastSupportPayClaimAt: now,
      updatedAt: now,
    })
  );

  // 200-day support user atomically claims 100 tokens + creates support_payroll ledger record
  const payBatch = supportDb.batch();
  payBatch.update(supportDb.collection("users").doc(SUPPORT_UID), {
    voteBalance: 200,
    totalSupportPayTokensEarned: 100,
    lastSupportPayClaimAt: now,
    updatedAt: now,
  });
  payBatch.set(supportDb.collection("support_payroll").doc("pay_1"), {
    payoutId: "pay_1",
    staffUserId: SUPPORT_UID,
    staffName: "SupportMod",
    staffEmail: SUPPORT_EMAIL,
    tokensAwarded: 100,
    usdValueCents: 1000,
    weekLabel: "2026-W46",
    createdAt: now,
  });
  await assertSucceeds(payBatch.commit());

  // Claiming a second time immediately within the 7-day cooldown fails
  const doubleBatch = supportDb.batch();
  doubleBatch.update(supportDb.collection("users").doc(SUPPORT_UID), {
    voteBalance: 300,
    totalSupportPayTokensEarned: 200,
    lastSupportPayClaimAt: new Date(),
    updatedAt: new Date(),
  });
  doubleBatch.set(supportDb.collection("support_payroll").doc("pay_2"), {
    payoutId: "pay_2",
    staffUserId: SUPPORT_UID,
    staffName: "SupportMod",
    staffEmail: SUPPORT_EMAIL,
    tokensAwarded: 100,
    usdValueCents: 1000,
    weekLabel: "2026-W46b",
    createdAt: new Date(),
  });
  await assertFails(doubleBatch.commit());

  // Once 8 days have passed since lastSupportPayClaimAt, SupportMod can claim next weekly 100 tokens
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(SUPPORT_UID).update({
      lastSupportPayClaimAt: eightDaysAgo,
    });
  });
  const nextWeekBatch = supportDb.batch();
  nextWeekBatch.update(supportDb.collection("users").doc(SUPPORT_UID), {
    voteBalance: 300,
    totalSupportPayTokensEarned: 200,
    lastSupportPayClaimAt: now,
    updatedAt: now,
  });
  nextWeekBatch.set(supportDb.collection("support_payroll").doc("pay_3"), {
    payoutId: "pay_3",
    staffUserId: SUPPORT_UID,
    staffName: "SupportMod",
    staffEmail: SUPPORT_EMAIL,
    tokensAwarded: 100,
    usdValueCents: 1000,
    weekLabel: "2026-W47",
    createdAt: now,
  });
  await assertSucceeds(nextWeekBatch.commit());
});

test("Admin & Support can delete photos/videos; Support ban suspends & creates ban_requests for Admin approval", async () => {
  const now = new Date();
  const threeDaysLater = new Date(now.getTime() + 72 * 60 * 60 * 1000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      email: OWNER_EMAIL,
      displayName: "Owner",
      role: "admin",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "SupportMod",
      role: "support",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      role: "user",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("posts").doc("photo_1").set({
      creatorId: ALICE_UID,
      creatorName: "Alice",
      mediaType: "photo",
      caption: "Alice Photo",
      mediaBase64: "abc",
      durationMs: 0,
      postIndex: 0,
      voteCount: 0,
      visibility: "public",
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("posts").doc("video_1").set({
      creatorId: ALICE_UID,
      creatorName: "Alice",
      mediaType: "video",
      caption: "Alice Video",
      mediaBase64: "xyz",
      durationMs: 5000,
      postIndex: 1,
      voteCount: 0,
      visibility: "public",
      createdAt: now,
      updatedAt: now,
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL, email_verified: true }).firestore();
  const supportDb = testEnv.authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();

  // Bob (regular user) cannot delete Alice's photo or video
  await assertFails(bobDb.collection("posts").doc("photo_1").delete());
  await assertFails(bobDb.collection("posts").doc("video_1").delete());

  // Support deletes Alice's photo and Admin deletes Alice's video
  await assertSucceeds(supportDb.collection("posts").doc("photo_1").delete());
  await assertSucceeds(ownerDb.collection("posts").doc("video_1").delete());

  // Support requests account ban for Bob -> suspends Bob + creates pending ban_request
  await assertSucceeds(
    supportDb.collection("chat_moderation").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      banned: false,
      suspendedUntil: threeDaysLater,
      suspendReason: "Pending Admin ban review: Harassment",
      accountBanned: false,
      moderatedByUid: SUPPORT_UID,
      moderatedByEmail: SUPPORT_EMAIL,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    supportDb.collection("ban_requests").doc("br_1").set({
      requestId: "br_1",
      targetUserId: BOB_UID,
      targetUserName: "Bob",
      requestedByUid: SUPPORT_UID,
      requestedByName: "SupportMod",
      requestedByEmail: SUPPORT_EMAIL,
      banScope: "account",
      reason: "Harassment in chat rooms",
      status: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular user cannot read ban_requests; Support cannot approve their own ban_request
  await assertFails(aliceDb.collection("ban_requests").get());
  await assertFails(
    supportDb.collection("ban_requests").doc("br_1").update({
      status: "approved",
      reviewedByUid: SUPPORT_UID,
      reviewedByName: "SupportMod",
      updatedAt: new Date(),
    })
  );

  // Admin reads and approves br_1, then applies accountBanned=true
  await assertSucceeds(ownerDb.collection("ban_requests").get());
  await assertSucceeds(
    ownerDb.collection("ban_requests").doc("br_1").update({
      status: "approved",
      reviewedByUid: OWNER_UID,
      reviewedByName: "Owner",
      reviewNote: "Approved permanent account ban",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    ownerDb.collection("chat_moderation").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      banned: true,
      banReason: "Harassment in chat rooms",
      accountBanned: true,
      accountBanReason: "Harassment in chat rooms",
      moderatedByUid: OWNER_UID,
      moderatedByEmail: OWNER_EMAIL,
      updatedAt: new Date(),
    })
  );
});

test("Invisible Support presence in chat rooms is hidden from users/Support but visible to Admins", async () => {
  const now = new Date();
  const SUPPORT2_UID = "support_second";
  const SUPPORT2_EMAIL = "support2@example.com";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      email: OWNER_EMAIL,
      displayName: "Owner",
      role: "admin",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(SUPPORT_UID).set({
      userId: SUPPORT_UID,
      email: SUPPORT_EMAIL,
      displayName: "SupportOne",
      role: "support",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(SUPPORT2_UID).set({
      userId: SUPPORT2_UID,
      email: SUPPORT2_EMAIL,
      displayName: "SupportTwo",
      role: "support",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      voteBalance: 100,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL, email_verified: true }).firestore();
  const supportDb = testEnv.authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true }).firestore();
  const support2Db = testEnv.authenticatedContext(SUPPORT2_UID, { email: SUPPORT2_EMAIL, email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();

  // SupportOne enters lounge invisibly, Alice enters visibly
  await assertSucceeds(
    supportDb.collection("chat_rooms").doc("lounge").collection("presence").doc(SUPPORT_UID).set({
      roomId: "lounge",
      userId: SUPPORT_UID,
      displayName: "SupportOne",
      role: "support",
      isInvisible: true,
      joinedAt: now,
      lastActiveAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("chat_rooms").doc("lounge").collection("presence").doc(ALICE_UID).set({
      roomId: "lounge",
      userId: ALICE_UID,
      displayName: "Alice",
      role: "user",
      isInvisible: false,
      joinedAt: now,
      lastActiveAt: now,
    })
  );

  // Alice and SupportTwo cannot directly get SupportOne's invisible presence or run an unfiltered list
  await assertFails(aliceDb.collection("chat_rooms").doc("lounge").collection("presence").doc(SUPPORT_UID).get());
  await assertFails(aliceDb.collection("chat_rooms").doc("lounge").collection("presence").get());
  await assertFails(support2Db.collection("chat_rooms").doc("lounge").collection("presence").doc(SUPPORT_UID).get());
  await assertFails(support2Db.collection("chat_rooms").doc("lounge").collection("presence").get());

  // Visible-only query works for Alice and hides SupportOne
  const aliceVisibleSnap = await assertSucceeds(
    aliceDb.collection("chat_rooms").doc("lounge").collection("presence").where("isInvisible", "==", false).get()
  );
  if (aliceVisibleSnap.docs.some((d) => d.id === SUPPORT_UID)) {
    throw new Error("Invisible Support user leaked into regular user query");
  }

  // Admin CAN get SupportOne's invisible presence and list ALL members including invisible SupportOne
  await assertSucceeds(ownerDb.collection("chat_rooms").doc("lounge").collection("presence").doc(SUPPORT_UID).get());
  const adminAllSnap = await assertSucceeds(
    ownerDb.collection("chat_rooms").doc("lounge").collection("presence").get()
  );
  const supportEntry = adminAllSnap.docs.find((d) => d.id === SUPPORT_UID);
  if (!supportEntry || supportEntry.data().isInvisible !== true) {
    throw new Error("Admin should see invisible Support presence entry");
  }
});

test("Chat Room Voice Chat: Push-to-Talk, Raise Hand, First-In Monitor Mute/Bounce, and Monitor Handover", async () => {
  const now = new Date();
  const fiveMinsLater = new Date(now.getTime() + 5 * 60 * 1000);
  const CHARLIE_UID = "charlie_999";
  const CHARLIE_EMAIL = "charlie@example.com";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    for (const [uid, email, name, role] of [
      [ALICE_UID, ALICE_EMAIL, "Alice", "user"],
      [BOB_UID, BOB_EMAIL, "Bob", "user"],
      [CHARLIE_UID, CHARLIE_EMAIL, "Charlie", "user"],
      [SUPPORT_UID, SUPPORT_EMAIL, "SupportMod", "support"],
    ]) {
      await ctx.firestore().collection("users").doc(uid).set({
        userId: uid,
        email,
        displayName: name,
        role,
        voteBalance: 100,
        totalVotesCast: 0,
        totalVotesPurchased: 0,
        createdAt: now,
        updatedAt: now,
      });
    }
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID, { email: CHARLIE_EMAIL, email_verified: true }).firestore();
  const supportDb = testEnv.authenticatedContext(SUPPORT_UID, { email: SUPPORT_EMAIL, email_verified: true }).firestore();

  const roomRefAlice = aliceDb.collection("chat_rooms").doc("lounge");
  const roomRefBob = bobDb.collection("chat_rooms").doc("lounge");
  const roomRefCharlie = charlieDb.collection("chat_rooms").doc("lounge");

  // 1. Alice enters lounge first -> becomes Room Monitor
  const aliceEnter = aliceDb.batch();
  aliceEnter.set(roomRefAlice.collection("presence").doc(ALICE_UID), {
    roomId: "lounge",
    userId: ALICE_UID,
    displayName: "Alice",
    role: "user",
    isInvisible: false,
    handRaised: false,
    handRaisedAt: 0,
    isVoiceMuted: false,
    voiceMutedByUid: null,
    isSpeaking: false,
    joinedAtMillis: 1000,
    joinedAt: now,
    lastActiveAt: now,
  });
  aliceEnter.set(roomRefAlice, {
    roomId: "lounge",
    monitorUid: ALICE_UID,
    monitorName: "Alice",
    monitorAssignedAt: now,
    updatedAt: now,
  });
  await assertSucceeds(aliceEnter.commit());

  // 2. Bob enters second -> cannot steal monitor from Alice, joins visibly
  await assertFails(
    roomRefBob.set({
      roomId: "lounge",
      monitorUid: BOB_UID,
      monitorName: "Bob",
      monitorAssignedAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    roomRefBob.collection("presence").doc(BOB_UID).set({
      roomId: "lounge",
      userId: BOB_UID,
      displayName: "Bob",
      role: "user",
      isInvisible: false,
      handRaised: false,
      handRaisedAt: 0,
      isVoiceMuted: false,
      voiceMutedByUid: null,
      isSpeaking: false,
      joinedAtMillis: 2000,
      joinedAt: now,
      lastActiveAt: now,
    })
  );

  // 3. Support enters visibly AFTER Bob
  await assertSucceeds(
    supportDb.collection("chat_rooms").doc("lounge").collection("presence").doc(SUPPORT_UID).set({
      roomId: "lounge",
      userId: SUPPORT_UID,
      displayName: "SupportMod",
      role: "support",
      isInvisible: false,
      handRaised: false,
      handRaisedAt: 0,
      isVoiceMuted: false,
      voiceMutedByUid: null,
      isSpeaking: false,
      joinedAtMillis: 3000,
      joinedAt: now,
      lastActiveAt: now,
    })
  );

  // 4. Bob raises hand and talks (PTT voice chunk + isSpeaking=true)
  await assertSucceeds(
    roomRefBob.collection("presence").doc(BOB_UID).update({
      handRaised: true,
      handRaisedAt: now.getTime(),
      isSpeaking: true,
      lastActiveAt: new Date(),
    })
  );
  await assertSucceeds(
    roomRefBob.collection("voice_chunks").doc("vc_bob_1").set({
      chunkId: "vc_bob_1",
      roomId: "lounge",
      senderId: BOB_UID,
      senderName: "Bob",
      sampleRate: 8000,
      audioBase64: "AQIDBA==",
      durationMs: 400,
      sequenceNumber: 1,
      createdAt: now,
    })
  );

  // 5. Alice (Room Monitor) mutes Bob's mic -> Bob cannot self-unmute, send new voice chunks, or send chat messages
  await assertSucceeds(
    roomRefAlice.collection("presence").doc(BOB_UID).update({
      isVoiceMuted: true,
      voiceMutedByUid: ALICE_UID,
      isSpeaking: false,
      handRaised: false,
      handRaisedAt: 0,
      lastActiveAt: new Date(),
    })
  );
  await assertFails(
    roomRefBob.collection("presence").doc(BOB_UID).update({
      isVoiceMuted: false,
      voiceMutedByUid: null,
      lastActiveAt: new Date(),
    })
  );
  await assertFails(
    roomRefBob.collection("voice_chunks").doc("vc_bob_blocked").set({
      chunkId: "vc_bob_blocked",
      roomId: "lounge",
      senderId: BOB_UID,
      senderName: "Bob",
      sampleRate: 8000,
      audioBase64: "AQIDBA==",
      durationMs: 400,
      sequenceNumber: 2,
      createdAt: new Date(),
    })
  );
  await assertFails(
    roomRefBob.collection("messages").doc("msg_bob_blocked").set({
      roomId: "lounge",
      senderId: BOB_UID,
      senderName: "Bob",
      senderRole: "user",
      text: "Blocked chat message while muted",
      createdAt: new Date(),
    })
  );
  // Once Alice unmutes Bob, he can talk and send chat messages again
  await assertSucceeds(
    roomRefAlice.collection("presence").doc(BOB_UID).update({
      isVoiceMuted: false,
      voiceMutedByUid: null,
      lastActiveAt: new Date(),
    })
  );
  await assertSucceeds(
    roomRefBob.collection("messages").doc("msg_bob_unmuted").set({
      roomId: "lounge",
      senderId: BOB_UID,
      senderName: "Bob",
      senderRole: "user",
      text: "Back in chat after unmute!",
      createdAt: new Date(),
    })
  );

  // 6. Regular monitor Alice CANNOT mute or bounce Support staff, and Bob (non-monitor) cannot mute anyone
  await assertFails(
    roomRefAlice.collection("presence").doc(SUPPORT_UID).update({
      isVoiceMuted: true,
      voiceMutedByUid: ALICE_UID,
      isSpeaking: false,
      lastActiveAt: new Date(),
    })
  );
  await assertFails(
    roomRefAlice.collection("bounces").doc(SUPPORT_UID).set({
      roomId: "lounge",
      userId: SUPPORT_UID,
      reason: "Unauthorized bounce",
      bouncedByUid: ALICE_UID,
      bouncedUntil: fiveMinsLater,
      bouncedAt: new Date(),
    })
  );
  await assertFails(
    roomRefBob.collection("presence").doc(ALICE_UID).update({
      isVoiceMuted: true,
      voiceMutedByUid: BOB_UID,
      isSpeaking: false,
      lastActiveAt: new Date(),
    })
  );

  // 7. Charlie joins visibly, and Alice (Monitor) bounces Charlie from the room
  await assertSucceeds(
    roomRefCharlie.collection("presence").doc(CHARLIE_UID).set({
      roomId: "lounge",
      userId: CHARLIE_UID,
      displayName: "Charlie",
      role: "user",
      isInvisible: false,
      handRaised: false,
      handRaisedAt: 0,
      isVoiceMuted: false,
      voiceMutedByUid: null,
      isSpeaking: false,
      joinedAtMillis: 4000,
      joinedAt: now,
      lastActiveAt: now,
    })
  );
  const bounceBatch = aliceDb.batch();
  bounceBatch.set(roomRefAlice.collection("bounces").doc(CHARLIE_UID), {
    roomId: "lounge",
    userId: CHARLIE_UID,
    reason: "Disrupting voice chat",
    bouncedByUid: ALICE_UID,
    bouncedUntil: fiveMinsLater,
    bouncedAt: new Date(),
  });
  bounceBatch.delete(roomRefAlice.collection("presence").doc(CHARLIE_UID));
  await assertSucceeds(bounceBatch.commit());
  await assertFails(roomRefCharlie.collection("messages").get());

  // 8. Alice leaves room and atomically hands monitor role to next user in room (Bob)
  const leaveBatch = aliceDb.batch();
  leaveBatch.delete(roomRefAlice.collection("presence").doc(ALICE_UID));
  leaveBatch.set(roomRefAlice, {
    roomId: "lounge",
    monitorUid: BOB_UID,
    monitorName: "Bob",
    monitorAssignedAt: new Date(),
    updatedAt: new Date(),
  });
  await assertSucceeds(leaveBatch.commit());

  // 9. Bob is now the Room Monitor! Alice re-enters with hand raised and Bob mutes her mic
  await assertSucceeds(
    roomRefAlice.collection("presence").doc(ALICE_UID).set({
      roomId: "lounge",
      userId: ALICE_UID,
      displayName: "Alice",
      role: "user",
      isInvisible: false,
      handRaised: true,
      handRaisedAt: now.getTime(),
      isVoiceMuted: false,
      voiceMutedByUid: null,
      isSpeaking: false,
      joinedAtMillis: 5000,
      joinedAt: new Date(),
      lastActiveAt: new Date(),
    })
  );
  await assertSucceeds(
    roomRefBob.collection("presence").doc(ALICE_UID).update({
      isVoiceMuted: true,
      voiceMutedByUid: BOB_UID,
      isSpeaking: false,
      handRaised: false,
      handRaisedAt: 0,
      lastActiveAt: new Date(),
    })
  );
});

test("free video-to-video calls: request, accept, stream frames/audio, and decline without tokens", async () => {
  const CHARLIE_UID = "charlie_999";
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const baseUser = (uid, name) => ({
      userId: uid,
      displayName: name,
      role: "user",
      voteBalance: 0,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      videoTokenBalance: 0,
      totalVideoTokensPurchased: 0,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(ALICE_UID).set(baseUser(ALICE_UID, "Alice"));
    await ctx.firestore().collection("users").doc(BOB_UID).set(baseUser(BOB_UID, "Bob"));
    await ctx.firestore().collection("users").doc(CHARLIE_UID).set(baseUser(CHARLIE_UID, "Charlie"));
    await ctx.firestore().collection("creators").doc(ALICE_UID).set({
      creatorId: ALICE_UID,
      displayName: "Alice",
      visibility: "public",
      postCount: 0,
      totalVotesReceived: 0,
      freePreviewLimit: 2,
      unlockCostVotes: 3,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID, { email: "charlie@example.com", email_verified: true }).firestore();

  // 1. Reject non-free video session pricing (e.g., 100 tokens/min or escrowStatus 'held')
  await assertFails(
    bobDb.collection("video_sessions").doc("forged_vid").set({
      sessionId: "forged_vid",
      threadId: `${ALICE_UID}_${BOB_UID}`,
      requesterId: BOB_UID,
      requesterName: "Bob",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      participantIds: [BOB_UID, ALICE_UID],
      note: "Forged rate",
      durationMinutes: 2,
      tokensPerMinute: 100,
      totalTokensCost: 200,
      creatorShareTokens: 140,
      creatorShareCents: 1400,
      status: "pending",
      escrowStatus: "held",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // 2. Bob requests a free 2-minute video session with 0 tokens
  const vidRefBob = bobDb.collection("video_sessions").doc("vid_sess_1");
  await assertSucceeds(
    vidRefBob.set({
      sessionId: "vid_sess_1",
      threadId: `${ALICE_UID}_${BOB_UID}`,
      requesterId: BOB_UID,
      requesterName: "Bob",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      participantIds: [BOB_UID, ALICE_UID],
      note: "Private DM live video",
      durationMinutes: 2,
      tokensPerMinute: 0,
      totalTokensCost: 0,
      creatorShareTokens: 0,
      creatorShareCents: 0,
      status: "pending",
      escrowStatus: "free",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Frames/audio blocked while pending
  await assertFails(
    bobDb.collection("video_sessions").doc("vid_sess_1").collection("video_frames").doc("f0").set({
      frameId: "f0",
      sessionId: "vid_sess_1",
      senderId: BOB_UID,
      senderName: "Bob",
      frameBase64: "AAAA",
      width: 160,
      height: 120,
      sequenceNumber: 1,
      createdAt: new Date(),
    })
  );

  // 3. Alice accepts free video session
  await assertSucceeds(
    aliceDb.collection("video_sessions").doc("vid_sess_1").update({
      status: "accepted",
      escrowStatus: "free",
      updatedAt: new Date(),
    })
  );

  // 4. Participants can stream video frames & audio chunks; outsiders (Charlie) cannot
  await assertSucceeds(
    aliceDb.collection("video_sessions").doc("vid_sess_1").collection("video_frames").doc("f1").set({
      frameId: "f1",
      sessionId: "vid_sess_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      frameBase64: "ZmFrZV9qcGVn",
      width: 240,
      height: 180,
      sequenceNumber: 1,
      createdAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("video_sessions").doc("vid_sess_1").collection("audio_chunks").doc("a1").set({
      chunkId: "a1",
      sessionId: "vid_sess_1",
      senderId: BOB_UID,
      senderName: "Bob",
      sampleRate: 8000,
      audioBase64: "AQIDBA==",
      durationMs: 400,
      sequenceNumber: 1,
      createdAt: new Date(),
    })
  );
  await assertFails(
    charlieDb.collection("video_sessions").doc("vid_sess_1").collection("video_frames").get()
  );
  await assertFails(
    charlieDb.collection("video_sessions").doc("vid_sess_1").collection("audio_chunks").doc("a2").set({
      chunkId: "a2",
      sessionId: "vid_sess_1",
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      sampleRate: 8000,
      audioBase64: "AQIDBA==",
      durationMs: 400,
      sequenceNumber: 2,
      createdAt: new Date(),
    })
  );

  // 5. Declining a free session updates status to declined cleanly
  const vidRef2Bob = bobDb.collection("video_sessions").doc("vid_sess_2");
  await assertSucceeds(
    vidRef2Bob.set({
      sessionId: "vid_sess_2",
      threadId: `${ALICE_UID}_${BOB_UID}`,
      requesterId: BOB_UID,
      requesterName: "Bob",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      participantIds: [BOB_UID, ALICE_UID],
      note: "1 min video",
      durationMinutes: 1,
      tokensPerMinute: 0,
      totalTokensCost: 0,
      creatorShareTokens: 0,
      creatorShareCents: 0,
      status: "pending",
      escrowStatus: "free",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("video_sessions").doc("vid_sess_2").update({
      status: "declined",
      escrowStatus: "free",
      updatedAt: new Date(),
    })
  );
});

test("Referral system: 10 paying referrals unlock 100 creator-usable OR cashable tokens and prevent double-dipping", async () => {
  const now = new Date(Date.now() - 1000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const aliceUserRef = aliceDb.collection("users").doc(ALICE_UID);
  const aliceCode = "SVALICE123";
  const CHARLIE_UID = "creator_charlie_ref";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      voteBalance: 100,
      paidVoteBalance: 0,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      referralCode: aliceCode,
      referralCashableBalance: 0,
      totalReferralTokensEarned: 0,
      totalReferralTokensCashedIn: 0,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("creators").doc(CHARLIE_UID).set({
      creatorId: CHARLIE_UID,
      displayName: "CharlieCreator",
      bio: "",
      visibility: "public",
      postCount: 3,
      totalVotesReceived: 0,
      freePreviewLimit: 2,
      unlockCostVotes: 3,
      isOnline: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertSucceeds(
    aliceDb.collection("referral_codes").doc(aliceCode).set({
      code: aliceCode,
      ownerId: ALICE_UID,
      ownerName: "Alice",
      createdAt: now,
    })
  );

  // Self-referral must fail
  await assertFails(
    aliceDb.collection("referrals").doc(ALICE_UID).set({
      referredUserId: ALICE_UID,
      referredUserName: "Alice",
      referrerId: ALICE_UID,
      referrerName: "Alice",
      referralCode: aliceCode,
      status: "pending",
      rewardClaimed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  const referralIds = [];
  for (let i = 0; i < 10; i++) {
    const refUid = `ref_buyer_${i}`;
    referralIds.push(refUid);
    const refDb = testEnv.authenticatedContext(refUid, {
      email: `${refUid}@example.com`,
      email_verified: true,
    }).firestore();

    await assertSucceeds(
      refDb.collection("users").doc(refUid).set({
        userId: refUid,
        displayName: `Buyer ${i}`,
        role: "user",
        voteBalance: 100,
        paidVoteBalance: 0,
        totalVotesCast: 0,
        totalVotesPurchased: 0,
        phoneTokenBalance: 0,
        totalPhoneTokensPurchased: 0,
        videoTokenBalance: 0,
        totalVideoTokensPurchased: 0,
        referralCode: `SVBUYER${i}00`,
        referralCashableBalance: 0,
        totalReferralTokensEarned: 0,
        totalReferralTokensCashedIn: 0,
        createdAt: now,
        updatedAt: now,
      })
    );

    await assertSucceeds(
      refDb.collection("referrals").doc(refUid).set({
        referredUserId: refUid,
        referredUserName: `Buyer ${i}`,
        referrerId: ALICE_UID,
        referrerName: "Alice",
        referralCode: aliceCode,
        status: "pending",
        rewardClaimed: false,
        createdAt: now,
        updatedAt: now,
      })
    );

    if (i === 0) {
      await assertFails(
        refDb.collection("referrals").doc(refUid).update({
          status: "qualified",
          qualifyingPurchaseType: "vote_pack",
          qualifyingPurchaseId: "fake_missing_purchase",
          qualifiedAt: now,
          updatedAt: now,
        })
      );
    }

    const purchaseId = `pur_${i}`;
    const buyBatch = refDb.batch();
    const uRef = refDb.collection("users").doc(refUid);
    const rRef = refDb.collection("referrals").doc(refUid);
    if (i % 3 === 0) {
      buyBatch.update(uRef, {
        voteBalance: 110,
        paidVoteBalance: 10,
        totalVotesPurchased: 10,
        updatedAt: now,
      });
      buyBatch.set(uRef.collection("purchases").doc(purchaseId), {
        userId: refUid,
        packId: "pack_10",
        packTitle: "10 Tokens",
        votesAdded: 10,
        priceLabel: "$1.99",
        purchasedAt: now,
      });
      buyBatch.update(rRef, {
        status: "qualified",
        qualifyingPurchaseType: "vote_pack",
        qualifyingPurchaseId: purchaseId,
        qualifiedAt: now,
        updatedAt: now,
      });
    } else if (i % 3 === 1) {
      buyBatch.update(uRef, {
        phoneTokenBalance: 50,
        totalPhoneTokensPurchased: 50,
        updatedAt: now,
      });
      buyBatch.set(uRef.collection("phone_purchases").doc(purchaseId), {
        userId: refUid,
        packId: "phone_50",
        packTitle: "50 Phone Tokens",
        tokensAdded: 50,
        priceLabel: "$4.99",
        purchasedAt: now,
      });
      buyBatch.update(rRef, {
        status: "qualified",
        qualifyingPurchaseType: "phone_pack",
        qualifyingPurchaseId: purchaseId,
        qualifiedAt: now,
        updatedAt: now,
      });
    } else {
      buyBatch.update(uRef, {
        videoTokenBalance: 100,
        totalVideoTokensPurchased: 100,
        updatedAt: now,
      });
      buyBatch.set(uRef.collection("video_purchases").doc(purchaseId), {
        userId: refUid,
        packId: "video_100",
        packTitle: "1-Min Video",
        tokensAdded: 100,
        priceLabel: "$9.99",
        purchasedAt: now,
      });
      buyBatch.update(rRef, {
        status: "qualified",
        qualifyingPurchaseType: "video_pack",
        qualifyingPurchaseId: purchaseId,
        qualifiedAt: now,
        updatedAt: now,
      });
    }
    await assertSucceeds(buyBatch.commit());
  }

  // Claim 10 qualified referrals -> +100 creator-usable OR cashable tokens
  const claimId = "claim_milestone_1";
  const claimBatch = aliceDb.batch();
  claimBatch.update(aliceUserRef, {
    voteBalance: 200,
    paidVoteBalance: 100,
    referralCashableBalance: 100,
    totalReferralTokensEarned: 100,
    lastReferralClaimId: claimId,
    updatedAt: now,
  });
  for (const refUid of referralIds) {
    claimBatch.update(aliceDb.collection("referrals").doc(refUid), {
      rewardClaimed: true,
      rewardClaimId: claimId,
      claimedAt: now,
      updatedAt: now,
    });
  }
  claimBatch.set(aliceDb.collection("referral_claims").doc(claimId), {
    claimId,
    referrerId: ALICE_UID,
    referrerName: "Alice",
    qualifiedReferralIds: referralIds,
    referralCountRequired: 10,
    tokensAwarded: 100,
    cashValueCents: 1000,
    createdAt: now,
  });
  await assertSucceeds(claimBatch.commit());

  // Spend 3 referral tokens to unlock Charlie -> cashable clamps from 100 to 97
  const unlockBatch = aliceDb.batch();
  unlockBatch.update(aliceUserRef, {
    voteBalance: 197,
    paidVoteBalance: 97,
    referralCashableBalance: 97,
    updatedAt: now,
  });
  unlockBatch.set(aliceUserRef.collection("unlocks").doc(CHARLIE_UID), {
    userId: ALICE_UID,
    creatorId: CHARLIE_UID,
    votesSpent: 3,
    unlockedAt: now,
  });
  unlockBatch.set(aliceDb.collection("creator_payouts").doc("payout_ref_unlock_1"), {
    payoutId: "payout_ref_unlock_1",
    creatorId: CHARLIE_UID,
    payerId: ALICE_UID,
    payerName: "Alice",
    sourceType: "gallery_unlock",
    sourceId: CHARLIE_UID,
    grossTokens: 3,
    creatorSharePercent: 40,
    creatorShareCents: 12,
    createdAt: now,
  });
  await assertSucceeds(unlockBatch.commit());

  // Over-cashing 98 fails
  const overCashBatch = aliceDb.batch();
  overCashBatch.update(aliceUserRef, {
    voteBalance: 99,
    paidVoteBalance: -1,
    referralCashableBalance: -1,
    totalReferralTokensCashedIn: 98,
    lastReferralCashoutId: "cashout_bad",
    updatedAt: now,
  });
  overCashBatch.set(aliceDb.collection("referral_cashouts").doc("cashout_bad"), {
    cashoutId: "cashout_bad",
    userId: ALICE_UID,
    userName: "Alice",
    tokensCashedIn: 98,
    usdCents: 980,
    payoutMethod: "paypal",
    payoutDestination: "alice@example.com",
    status: "submitted",
    createdAt: now,
  });
  await assertFails(overCashBatch.commit());

  // Cash in remaining 97 referral tokens for $9.70
  const cashoutId = "cashout_good_1";
  const cashBatch = aliceDb.batch();
  cashBatch.update(aliceUserRef, {
    voteBalance: 100,
    paidVoteBalance: 0,
    referralCashableBalance: 0,
    totalReferralTokensCashedIn: 97,
    lastReferralCashoutId: cashoutId,
    updatedAt: now,
  });
  cashBatch.set(aliceDb.collection("referral_cashouts").doc(cashoutId), {
    cashoutId,
    userId: ALICE_UID,
    userName: "Alice",
    tokensCashedIn: 97,
    usdCents: 970,
    payoutMethod: "paypal",
    payoutDestination: "alice@example.com",
    status: "submitted",
    createdAt: now,
  });
  await assertSucceeds(cashBatch.commit());
});

test("Storage-backed media posts, DM snaps, photo/message stories, and optional check-ins follow security rules", async () => {
  const now = new Date();
  const in12Hours = new Date(now.getTime() + 12 * 60 * 60 * 1000);
  const in48Hours = new Date(now.getTime() + 48 * 60 * 60 * 1000);
  const threadId = `${ALICE_UID}_${BOB_UID}`;

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      voteBalance: 100,
      paidVoteBalance: 0,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      privateChatAccessExpiresAt: in12Hours,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      voteBalance: 100,
      paidVoteBalance: 0,
      totalVotesCast: 0,
      totalVotesPurchased: 0,
      privateChatAccessExpiresAt: in12Hours,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("direct_chats").doc(threadId).set({
      threadId,
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      lastMessageText: "",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();

  // 1. Storage-backed MediaPost
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_storage_1").set({
      creatorId: ALICE_UID,
      creatorName: "Alice",
      mediaType: "video",
      caption: "Shot with CameraX",
      mediaBase64: "AAAA",
      mediaDownloadUrl: "https://firebasestorage.googleapis.com/v0/b/demo/o/posts%2Falice%2Fpost_1.mp4",
      storagePath: `posts/${ALICE_UID}/post_storage_1.mp4`,
      durationMs: 4200,
      postIndex: 0,
      voteCount: 0,
      visibility: "public",
      createdAt: now,
      updatedAt: now,
    })
  );

  // 2. DM Snap create & recipient mark viewed
  const snapRef = aliceDb.collection("direct_chats").doc(threadId).collection("messages").doc("dm_snap_1");
  await assertSucceeds(
    snapRef.set({
      threadId,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Check out this snap",
      messageType: "snap",
      snapBase64: "AAAA",
      snapDownloadUrl: "https://firebasestorage.googleapis.com/v0/b/demo/o/dm_snaps%2Fsnap.jpg",
      snapStoragePath: `dm_snaps/${threadId}/${ALICE_UID}/snap_1.jpg`,
      snapDurationSeconds: 5,
      viewedByIds: [ALICE_UID],
      createdAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("direct_chats").doc(threadId).collection("messages").doc("dm_snap_1").update({
      viewedByIds: [ALICE_UID, BOB_UID],
    })
  );

  // Cannot mutate snap text after sending
  await assertFails(
    bobDb.collection("direct_chats").doc(threadId).collection("messages").doc("dm_snap_1").update({
      text: "Tampered",
    })
  );

  // 3. Stories (photo & message)
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_photo_1").set({
      storyId: "story_photo_1",
      userId: ALICE_UID,
      userName: "Alice",
      storyType: "photo",
      text: "Studio session!",
      mediaBase64: "AAAA",
      mediaDownloadUrl: "https://firebasestorage.googleapis.com/v0/b/demo/o/stories%2Fstory_1.jpg",
      storagePath: `stories/${ALICE_UID}/story_photo_1.jpg`,
      backgroundStyle: "sunset",
      viewCount: 0,
      expiresAt: in12Hours,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("stories").doc("story_msg_1").set({
      storyId: "story_msg_1",
      userId: BOB_UID,
      userName: "Bob",
      storyType: "message",
      text: "Drop questions for tonight's live room!",
      backgroundStyle: "midnight",
      viewCount: 0,
      expiresAt: in12Hours,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Story expiry > 24h fails
  await assertFails(
    bobDb.collection("stories").doc("story_too_long").set({
      storyId: "story_too_long",
      userId: BOB_UID,
      userName: "Bob",
      storyType: "message",
      text: "Too long",
      backgroundStyle: "midnight",
      viewCount: 0,
      expiresAt: in48Hours,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can increment viewCount by 1 on Alice's story
  await assertSucceeds(
    bobDb.collection("stories").doc("story_photo_1").update({
      viewCount: 1,
      updatedAt: now,
    })
  );

  // 4. Optional Check-In (owner create/update/delete; non-owner rejected)
  await assertSucceeds(
    aliceDb.collection("check_ins").doc(ALICE_UID).set({
      userId: ALICE_UID,
      userName: "Alice",
      placeLabel: "Downtown Coffee Loft",
      statusNote: "Editing tonight's snaps",
      vibeTag: "Creating",
      expiresAt: in12Hours,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    bobDb.collection("check_ins").doc(ALICE_UID).update({
      placeLabel: "Spoofed Place",
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("check_ins").doc(ALICE_UID).delete()
  );
});

test("Admin broadcast alerts/news and Admin/Support targeted user warning popups enforce roles", async () => {
  const now = new Date(Date.now() - 1000);
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL, email_verified: true }).firestore();
  const supportDb = testEnv.authenticatedContext("support_staff_1", { email: "support1@example.com", email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staff_roles").doc("support1@example.com").set({
      email: "support1@example.com",
      role: "support",
      active: true,
      addedByEmail: OWNER_EMAIL,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Regular user & Support cannot send global admin news/alerts
  await assertFails(
    aliceDb.collection("admin_announcements").doc("ann_fail_user").set({
      announcementId: "ann_fail_user",
      category: "alert",
      title: "Fake Alert",
      message: "Should fail",
      senderId: ALICE_UID,
      senderName: "Alice",
      senderRole: "admin",
      createdAt: now,
    })
  );
  await assertFails(
    supportDb.collection("admin_announcements").doc("ann_fail_support").set({
      announcementId: "ann_fail_support",
      category: "news",
      title: "Support News",
      message: "Should fail because only Admin can broadcast globally",
      senderId: "support_staff_1",
      senderName: "Support",
      senderRole: "admin",
      createdAt: now,
    })
  );

  // Admin/Owner can broadcast alerts & news to everyone
  await assertSucceeds(
    ownerDb.collection("admin_announcements").doc("ann_good_1").set({
      announcementId: "ann_good_1",
      category: "news",
      title: "Weekend Creator Spotlight!",
      message: "New CameraX Studio & Stories are live for everyone.",
      senderId: OWNER_UID,
      senderName: "RootOwner",
      senderRole: "owner",
      createdAt: now,
    })
  );

  // Any signed-in user can read global announcements
  await assertSucceeds(bobDb.collection("admin_announcements").doc("ann_good_1").get());

  // Regular user cannot send staff warnings
  await assertFails(
    bobDb.collection("user_warnings").doc("warn_fail_user").set({
      warningId: "warn_fail_user",
      targetUserId: ALICE_UID,
      targetUserName: "Alice",
      senderId: BOB_UID,
      senderName: "Bob",
      senderRole: "support",
      severity: "warning",
      title: "Fake warning",
      message: "Stop",
      acknowledged: false,
      createdAt: now,
    })
  );

  // Support CAN send popup warning to a regular user
  await assertSucceeds(
    supportDb.collection("user_warnings").doc("warn_good_1").set({
      warningId: "warn_good_1",
      targetUserId: ALICE_UID,
      targetUserName: "Alice",
      senderId: "support_staff_1",
      senderName: "SupportMod",
      senderRole: "support",
      severity: "warning",
      title: "Keep chat respectful",
      message: "Please follow room guidelines before continuing.",
      acknowledged: false,
      createdAt: now,
    })
  );

  // Target user can read and acknowledge their own warning, other non-staff users cannot
  await assertFails(bobDb.collection("user_warnings").doc("warn_good_1").get());
  await assertSucceeds(aliceDb.collection("user_warnings").doc("warn_good_1").get());
  await assertFails(
    bobDb.collection("user_warnings").doc("warn_good_1").update({
      acknowledged: true,
      acknowledgedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("user_warnings").doc("warn_good_1").update({
      acknowledged: true,
      acknowledgedAt: now,
    })
  );
});

test("Blocked users subcollection enforces owner-only read, create, list, delete, and strict field validation", async () => {
  const now = new Date(Date.now() - 1000);
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();

  const aliceBlockedRef = aliceDb.collection("users").doc(ALICE_UID).collection("blocked_users");

  // Unauthenticated cannot create, read, or list
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("blocked_users").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).set({
      userId: ALICE_UID,
      blockedUserId: BOB_UID,
      blockedUserName: "Bob",
      createdAt: now,
    })
  );

  // Alice cannot self-block
  await assertFails(
    aliceBlockedRef.doc(ALICE_UID).set({
      userId: ALICE_UID,
      blockedUserId: ALICE_UID,
      blockedUserName: "Alice",
      createdAt: now,
    })
  );

  // Reject userId / blockedUserId mismatch, ghost fields, and oversized reason
  await assertFails(
    aliceBlockedRef.doc(BOB_UID).set({
      userId: BOB_UID,
      blockedUserId: BOB_UID,
      blockedUserName: "Bob",
      createdAt: now,
    })
  );
  await assertFails(
    aliceBlockedRef.doc(BOB_UID).set({
      userId: ALICE_UID,
      blockedUserId: "someone_else",
      blockedUserName: "Bob",
      createdAt: now,
    })
  );
  await assertFails(
    aliceBlockedRef.doc(BOB_UID).set({
      userId: ALICE_UID,
      blockedUserId: BOB_UID,
      blockedUserName: "Bob",
      extraGhost: true,
      createdAt: now,
    })
  );
  await assertFails(
    aliceBlockedRef.doc(BOB_UID).set({
      userId: ALICE_UID,
      blockedUserId: BOB_UID,
      blockedUserName: "Bob",
      reason: "x".repeat(161),
      createdAt: now,
    })
  );

  // Alice CAN block Bob with a valid reason, and another user without reason
  await assertSucceeds(
    aliceBlockedRef.doc(BOB_UID).set({
      userId: ALICE_UID,
      blockedUserId: BOB_UID,
      blockedUserName: "Bob",
      reason: "Unwanted messages",
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceBlockedRef.doc("charlie_999").set({
      userId: ALICE_UID,
      blockedUserId: "charlie_999",
      blockedUserName: "Charlie",
      createdAt: now,
    })
  );

  // Owner can get and list
  await assertSucceeds(aliceBlockedRef.doc(BOB_UID).get());
  await assertSucceeds(aliceBlockedRef.get());

  // Cross-user (Bob) cannot get, list, create in Alice's blocked_users, or delete
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("blocked_users").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("blocked_users").doc("david_1").set({
      userId: ALICE_UID,
      blockedUserId: "david_1",
      blockedUserName: "David",
      createdAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).delete());

  // Updates are forbidden (create/delete only)
  await assertFails(
    aliceBlockedRef.doc(BOB_UID).update({
      reason: "Updated reason",
    })
  );

  // Owner CAN unblock (delete)
  await assertSucceeds(aliceBlockedRef.doc(BOB_UID).delete());
});







