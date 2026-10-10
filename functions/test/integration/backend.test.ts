import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { after, describe, test } from "node:test";
import {
  disableNetwork,
  doc,
  enableNetwork,
  getDoc,
  getDocFromCache,
  onSnapshot,
  serverTimestamp,
  setDoc,
  updateDoc,
} from "firebase/firestore";
import { Timestamp } from "firebase-admin/firestore";
import { awardMissionResult } from "../../src/missions";
import { ALL_TIME, weekId } from "../../src/periods";
import {
  adminDb,
  clientApp,
  expectCode,
  getAuth,
  journeyData,
  signInWithEmailAndPassword,
  signOut,
  signUp,
  TestUser,
  waitFor,
} from "./helpers";

const users: TestUser[] = [];
async function newUser(label: string, displayName?: string) {
  const user = await signUp(label, displayName);
  users.push(user);
  return user;
}

after(async () => {
  await Promise.all(users.map((user) => user.close().catch(() => undefined)));
});

const confirmWalking = {
  confirmedTransportMode: "WALKING",
  transportMode: "WALKING",
  confirmationStatus: "confirmed",
  updatedAt: serverTimestamp(),
};

describe("auth emulator", () => {
  test("register, sign out and sign in again keep the same UID", async () => {
    const user = await newUser("auth");
    const auth = getAuth(user.app);
    assert.equal(auth.currentUser?.uid, user.uid);

    await signOut(auth);
    assert.equal(auth.currentUser, null);

    const again = await signInWithEmailAndPassword(auth, user.email, user.password);
    assert.equal(again.user.uid, user.uid);
  });
});

describe("journeys", () => {
  test("create, listen, confirm; backend writes trusted carbon and aggregates", async () => {
    const user = await newUser("journey", "Jay");
    const ref = doc(user.db, `users/${user.uid}/journeys/j1`);
    const seen: string[] = [];
    const unsubscribe = onSnapshot(ref, (snapshot) => {
      if (snapshot.exists()) seen.push(String(snapshot.data().confirmationStatus));
    });

    await setDoc(ref, { ...journeyData(user.uid, "j1"), createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
    await updateDoc(ref, confirmWalking);

    const trusted = await waitFor(async () => {
      const data = (await adminDb().doc(`users/${user.uid}/journeys/j1`).get()).data();
      return data?.verificationStatus ? data : null;
    }, "trusted journey fields");
    unsubscribe();

    assert.deepEqual([...new Set(seen)], ["pending", "confirmed"]);
    assert.equal(trusted.carbonSavedGrams, 384);
    assert.equal(trusted.ecoPoints, 0);
    assert.equal(trusted.verificationStatus, "verified");
    assert.equal(trusted.detectedTransportMode, "CYCLING");

    await waitFor(async () => {
      const data = (await adminDb().doc(`userStats/${user.uid}`).get()).data();
      return data?.totalJourneys === 1 && data.carbonSavedGrams === 384;
    }, "user stats");
    // Concurrent trigger runs must not overwrite the converged aggregate with stale data.
    await new Promise((resolve) => setTimeout(resolve, 1_500));
    const stats = (await adminDb().doc(`userStats/${user.uid}`).get()).data()!;
    assert.equal(stats.carbonSavedGrams, 384);
    assert.equal(stats.totalJourneys, 1);

    const week = weekId(trusted.endTimeMillis);
    const entry = await waitFor(async () => (await adminDb().doc(`leaderboards/${week}/entries/${user.uid}`).get()).data(), "weekly entry");
    assert.equal(entry.carbonSavedGrams, 384);
    assert.equal(entry.displayName, "Jay");
    const allTime = (await adminDb().doc(`leaderboards/${ALL_TIME}/entries/${user.uid}`).get()).data();
    assert.equal(allTime?.completedJourneys, 1);
  });

  test("user B cannot read user A's journey", async () => {
    const a = await newUser("owner");
    const b = await newUser("intruder");
    await setDoc(doc(a.db, `users/${a.uid}/journeys/j1`), journeyData(a.uid, "j1"));

    await expectCode(getDoc(doc(b.db, `users/${a.uid}/journeys/j1`)), "permission-denied");
  });

  test("offline write is readable from cache and syncs after reconnecting", async () => {
    const user = await newUser("offline");
    await disableNetwork(user.db);
    const ref = doc(user.db, `users/${user.uid}/journeys/offline-1`);
    // The promise resolves only on server acknowledgement.
    const write = setDoc(ref, { ...journeyData(user.uid, "offline-1"), createdAt: serverTimestamp(), updatedAt: serverTimestamp() });

    const cached = await getDocFromCache(ref);
    assert.equal(cached.exists(), true);
    assert.equal(cached.metadata.hasPendingWrites, true);
    assert.equal(cached.data({ serverTimestamps: "none" })?.createdAt, null);
    assert.equal((await adminDb().doc(`users/${user.uid}/journeys/offline-1`).get()).exists, false);

    await enableNetwork(user.db);
    await write;
    const synced = await adminDb().doc(`users/${user.uid}/journeys/offline-1`).get();
    assert.equal(synced.exists, true);
    assert.ok(synced.data()?.createdAt instanceof Timestamp);
  });
});

describe("missions and EcoPoints", () => {
  test("completed mission occurrence is awarded exactly once", async () => {
    const user = await newUser("mission", "Mia");
    const db = user.db;
    const today = new Date().toISOString().slice(0, 10);
    const resultId = `m1_${today}`;

    await setDoc(doc(db, `users/${user.uid}/missions/m1`), {
      schemaVersion: 1,
      missionId: "m1",
      title: "Home → Uni",
      status: "active",
      recurrence: { type: "daily", interval: 1, daysOfWeek: [], timezone: "UTC" },
      scheduledMinuteOfDay: 510,
      activeOccurrenceDate: today,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    await setDoc(doc(db, `users/${user.uid}/missionResults/${resultId}`), {
      schemaVersion: 1, missionId: "m1", occurrenceDate: today, accepted: true, skipped: false,
      lastActionAtMillis: Date.now(), updatedAt: serverTimestamp(),
    });
    // Starting twice writes the same document: occurrences are idempotent per (mission, date).
    await setDoc(doc(db, `users/${user.uid}/missionResults/${resultId}`), {
      missionId: "m1", occurrenceDate: today, accepted: true, lastActionAtMillis: Date.now(), updatedAt: serverTimestamp(),
    }, { merge: true });

    await setDoc(doc(db, `users/${user.uid}/journeys/mj1`), journeyData(user.uid, "mj1", { linkedMissionId: "m1" }));
    await updateDoc(doc(db, `users/${user.uid}/journeys/mj1`), confirmWalking);
    await setDoc(doc(db, `users/${user.uid}/missionResults/${resultId}`), {
      missionId: "m1", occurrenceDate: today, accepted: true, completed: true, skipped: false,
      linkedJourneyId: "mj1", completedAtMillis: Date.now(), lastActionAtMillis: Date.now(), updatedAt: serverTimestamp(),
    }, { merge: true });

    const result = await waitFor(async () => {
      const data = (await adminDb().doc(`users/${user.uid}/missionResults/${resultId}`).get()).data();
      return data?.awardStatus ? data : null;
    }, "mission award");
    assert.equal(result.awardStatus, "awarded");
    assert.equal(result.ecoPointsAwarded, 58);
    assert.equal(result.actualCarbonSavingGrams, 384);
    assert.equal(result.actualTransportMode, "WALKING");
    assert.ok(result.createdAt instanceof Timestamp);

    // Re-running the award (a retried trigger) and touching the result change nothing.
    assert.deepEqual(await awardMissionResult(adminDb(), user.uid, resultId), { status: "already_awarded" });
    await setDoc(doc(db, `users/${user.uid}/missionResults/${resultId}`), {
      lastActionAtMillis: Date.now(), updatedAt: serverTimestamp(),
    }, { merge: true });
    await new Promise((resolve) => setTimeout(resolve, 1_500));

    const ledger = await adminDb().collection(`users/${user.uid}/pointTransactions`).get();
    assert.equal(ledger.size, 1);
    assert.equal(ledger.docs[0]!.id, `mission_${resultId}`);
    assert.equal(ledger.docs[0]!.data().amount, 58);
    const stats = (await adminDb().doc(`userStats/${user.uid}`).get()).data();
    assert.equal(stats?.pointsBalance, 58);
    assert.equal(stats?.completedMissions, 1);
    const journey = (await adminDb().doc(`users/${user.uid}/journeys/mj1`).get()).data();
    assert.equal(journey?.ecoPoints, 58);

    // The same journey cannot be claimed by another occurrence.
    await setDoc(doc(db, `users/${user.uid}/missionResults/m1_2000-01-01`), {
      missionId: "m1", occurrenceDate: "2000-01-01", accepted: true, completed: true,
      linkedJourneyId: "mj1", completedAtMillis: Date.now(), lastActionAtMillis: Date.now(), updatedAt: serverTimestamp(),
    });
    const reused = await waitFor(async () => {
      const data = (await adminDb().doc(`users/${user.uid}/missionResults/m1_2000-01-01`).get()).data();
      return data?.awardStatus ? data : null;
    }, "rejected reuse");
    assert.equal(reused.awardStatus, "rejected");
    assert.equal(reused.awardReason, "journey_already_used");
    assert.equal((await adminDb().doc(`userStats/${user.uid}`).get()).data()?.pointsBalance, 58);
  });

  test("mission completion with an unconfirmed or unlinked journey earns nothing", async () => {
    const user = await newUser("cheat");
    await setDoc(doc(user.db, `users/${user.uid}/journeys/free`), journeyData(user.uid, "free"));
    await setDoc(doc(user.db, `users/${user.uid}/missionResults/m9_2026-10-05`), {
      missionId: "m9", occurrenceDate: "2026-10-05", accepted: true, completed: true,
      linkedJourneyId: "free", completedAtMillis: 1, lastActionAtMillis: 1, updatedAt: serverTimestamp(),
    });
    const result = await waitFor(async () => {
      const data = (await adminDb().doc(`users/${user.uid}/missionResults/m9_2026-10-05`).get()).data();
      return data?.awardStatus ? data : null;
    }, "rejected award");
    assert.equal(result.awardStatus, "rejected");
    assert.equal(result.awardReason, "journey_not_confirmed");
    assert.equal((await adminDb().collection(`users/${user.uid}/pointTransactions`).get()).size, 0);
  });
});

describe("reward redemption callable", () => {
  test("demo catalog is readable and retries deduct points exactly once", async () => {
    const user = await newUser("demo-catalog");
    const admin = adminDb();
    const rewards = JSON.parse(readFileSync("config/demo-rewards.json", "utf8")) as
      Array<{ rewardId: string; pointsRequired: number; title: string }>;
    for (const { rewardId, ...reward } of rewards) {
      await admin.doc(`rewards/${rewardId}`).set(reward);
      const offer = await getDoc(doc(user.db, `rewards/${rewardId}`));
      assert.equal(offer.data()?.active, true);
      assert.match(offer.data()?.description, /Demo only/);
    }
    await admin.doc(`userStats/${user.uid}`).set({ pointsBalance: 21 });
    const request = { rewardId: rewards[0]!.rewardId, requestId: "demo-request-0001" };
    const first = await user.call("redeemReward", request);
    const retry = await user.call("redeemReward", request);
    assert.equal(first.redemption.pointsSpent, 10);
    assert.equal(first.redemption.rewardTitle, rewards[0]!.title);
    assert.equal(retry.redemption.redemptionCode, first.redemption.redemptionCode);
    assert.equal((await admin.doc(`userStats/${user.uid}`).get()).data()?.pointsBalance, 11);
    assert.equal((await admin.collection(`users/${user.uid}/rewardRedemptions`).get()).size, 1);
    const saved = await getDoc(doc(user.db, `users/${user.uid}/rewardRedemptions/${request.requestId}`));
    assert.equal(saved.data()?.pointsSpent, 10);
    await expectCode(user.call("redeemReward", {
      rewardId: rewards[1]!.rewardId, requestId: "demo-request-0002",
    }), "functions/failed-precondition");
  });

  test("redeems in one transaction, is idempotent per request and checks balance, stock and state", async () => {
    const user = await newUser("rewards");
    const admin = adminDb();
    await admin.doc("rewards/coffee").set({
      merchantName: "Green Bean", title: "Coffee", description: "One coffee", pointsRequired: 300,
      category: "food_and_drink", active: true, inventory: 2,
    });
    await admin.doc("rewards/retired").set({ title: "Old", pointsRequired: 10, active: false });
    await admin.doc("rewards/expired").set({
      title: "Late", pointsRequired: 10, active: true, validUntil: Timestamp.fromMillis(Date.now() - 1_000),
    });
    await admin.doc("rewards/bike").set({ title: "Bike pass", pointsRequired: 5_000, active: true });
    await admin.doc(`userStats/${user.uid}`).set({ pointsBalance: 700 });
    await admin.doc(`users/${user.uid}/pointTransactions/adjust_seed`).set({ type: "adjustment", amount: 700, sourceId: "seed" });

    const first = await user.call("redeemReward", { rewardId: "coffee", requestId: "request-0001" });
    assert.equal(first.redemption.pointsSpent, 300);
    assert.match(first.redemption.redemptionCode, /^ECO-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);

    // Retry of the same request (e.g. after a timeout or a double tap): no second charge.
    const [retryA, retryB] = await Promise.all([
      user.call("redeemReward", { rewardId: "coffee", requestId: "request-0001" }),
      user.call("redeemReward", { rewardId: "coffee", requestId: "request-0001" }),
    ]);
    assert.equal(retryA.redemption.redemptionCode, first.redemption.redemptionCode);
    assert.equal(retryB.redemption.redemptionCode, first.redemption.redemptionCode);
    assert.equal((await admin.doc(`userStats/${user.uid}`).get()).data()?.pointsBalance, 400);

    await user.call("redeemReward", { rewardId: "coffee", requestId: "request-0002" });
    await expectCode(user.call("redeemReward", { rewardId: "coffee", requestId: "request-0003" }), "functions/failed-precondition");
    await expectCode(user.call("redeemReward", { rewardId: "bike", requestId: "request-0004" }), "functions/failed-precondition");
    await expectCode(user.call("redeemReward", { rewardId: "retired", requestId: "request-0005" }), "functions/failed-precondition");
    await expectCode(user.call("redeemReward", { rewardId: "expired", requestId: "request-0006" }), "functions/failed-precondition");
    await expectCode(user.call("redeemReward", { rewardId: "missing", requestId: "request-0007" }), "functions/failed-precondition");
    await expectCode(user.call("redeemReward", { rewardId: "coffee", requestId: "x" }), "functions/invalid-argument");
    await expectCode(user.call("redeemReward", { rewardId: "bike", requestId: "request-0001" }), "functions/already-exists");

    const stats = (await admin.doc(`userStats/${user.uid}`).get()).data();
    assert.equal(stats?.pointsBalance, 100);
    assert.equal((await admin.doc("rewards/coffee").get()).data()?.inventory, 0);
    const ledger = await admin.collection(`users/${user.uid}/pointTransactions`).get();
    const total = ledger.docs.reduce((sum, entry) => sum + (entry.data().amount as number), 0);
    assert.equal(total, stats?.pointsBalance, "balance equals the ledger sum");
    assert.equal((await admin.collection(`users/${user.uid}/rewardRedemptions`).get()).size, 2);
  });

  test("callables reject unauthenticated callers", async () => {
    const app = clientApp(`anonymous-${Date.now()}`);
    const { getFunctions, httpsCallable } = await import("firebase/functions");
    await expectCode(
      httpsCallable(getFunctions(app, "us-central1"), "redeemReward")({ rewardId: "coffee", requestId: "request-0009" }),
      "functions/unauthenticated",
    );
  });
});

describe("friends and leaderboards", () => {
  test("friend request state machine is enforced by the backend", async () => {
    const alice = await newUser("alice", "Alice");
    const bob = await newUser("bob", "Bob");
    const carol = await newUser("carol", "Carol");
    const admin = adminDb();
    const requestId = `${alice.uid}_${bob.uid}`;

    await alice.call("sendFriendRequest", { receiverUid: bob.uid });
    await alice.call("sendFriendRequest", { receiverUid: bob.uid }); // idempotent
    assert.equal((await admin.doc(`users/${bob.uid}/friendRequests/${requestId}`).get()).data()?.status, "pending");
    assert.equal((await admin.doc(`users/${alice.uid}/friendRequests/${requestId}`).get()).data()?.status, "pending");

    await expectCode(alice.call("sendFriendRequest", { receiverUid: alice.uid }), "functions/invalid-argument");
    await expectCode(bob.call("sendFriendRequest", { receiverUid: alice.uid }), "functions/failed-precondition");
    await expectCode(alice.call("respondToFriendRequest", { requestId, accept: true }), "functions/failed-precondition");
    await expectCode(carol.call("respondToFriendRequest", { requestId, accept: true }), "functions/not-found");
    await expectCode(bob.call("cancelFriendRequest", { requestId }), "functions/failed-precondition");

    const search = await alice.call("searchUsers", { query: bob.email });
    assert.equal(search.results[0].uid, bob.uid);
    assert.equal(search.results[0].relationship, "request_sent");
    assert.match(search.results[0].maskedEmail, /^b\*\*\*@example\.com$/);

    await bob.call("respondToFriendRequest", { requestId, accept: true });
    assert.equal((await admin.doc(`users/${alice.uid}/friends/${bob.uid}`).get()).exists, true);
    assert.equal((await admin.doc(`users/${bob.uid}/friends/${alice.uid}`).get()).exists, true);
    assert.equal((await admin.doc(`users/${alice.uid}/friendRequests/${requestId}`).get()).data()?.status, "accepted");
    await expectCode(bob.call("respondToFriendRequest", { requestId, accept: false }), "functions/failed-precondition");
    await expectCode(alice.call("sendFriendRequest", { receiverUid: bob.uid }), "functions/already-exists");

    // Cancel path: only the sender, only while pending.
    const toCarol = `${alice.uid}_${carol.uid}`;
    await alice.call("sendFriendRequest", { receiverUid: carol.uid });
    await alice.call("cancelFriendRequest", { requestId: toCarol });
    await expectCode(carol.call("respondToFriendRequest", { requestId: toCarol, accept: true }), "functions/failed-precondition");
    assert.equal((await admin.doc(`users/${carol.uid}/friends/${alice.uid}`).get()).exists, false);

    // Friend stats are readable by friends only.
    await admin.doc(`leaderboardStats/${bob.uid}`).set({ displayName: "Bob", allTime: { carbonSavedGrams: 1, completedJourneys: 1 } });
    await getDoc(doc(alice.db, `leaderboardStats/${bob.uid}`));
    await expectCode(getDoc(doc(carol.db, `leaderboardStats/${bob.uid}`)), "permission-denied");
  });

  test("leaderboards reject client writes and drop users who opt out", async () => {
    const user = await newUser("ranker", "Ranker");
    await expectCode(
      setDoc(doc(user.db, `leaderboards/${ALL_TIME}/entries/${user.uid}`), { uid: user.uid, carbonSavedGrams: 1e9 }),
      "permission-denied",
    );

    await setDoc(doc(user.db, `users/${user.uid}/journeys/r1`), journeyData(user.uid, "r1"));
    await updateDoc(doc(user.db, `users/${user.uid}/journeys/r1`), confirmWalking);
    const entryRef = adminDb().doc(`leaderboards/${ALL_TIME}/entries/${user.uid}`);
    await waitFor(async () => (await entryRef.get()).exists, "leaderboard entry");

    await setDoc(doc(user.db, `users/${user.uid}`), {
      preferences: { communityRankingEnabled: false },
      updatedAt: serverTimestamp(),
    }, { merge: true });
    await waitFor(async () => !(await entryRef.get()).exists, "opt-out removal");

    const publicProfile = (await adminDb().doc(`publicProfiles/${user.uid}`).get()).data()!;
    assert.deepEqual(
      Object.keys(publicProfile).sort(),
      ["communityRankingEnabled", "displayName", "displayNameLower", "updatedAt"],
    );
    assert.equal(publicProfile.communityRankingEnabled, false);
  });
});
