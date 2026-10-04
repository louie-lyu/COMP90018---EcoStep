import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { after, before, beforeEach, describe, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  Firestore,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
} from "firebase/firestore";
import { requireEmulator, RULES_PROJECT_ID } from "../emulatorGuard";

let env: RulesTestEnvironment;

const alice = () => env.authenticatedContext("alice").firestore() as unknown as Firestore;
const bob = () => env.authenticatedContext("bob").firestore() as unknown as Firestore;
const anonymous = () => env.unauthenticatedContext().firestore() as unknown as Firestore;

function journey(uid: string, journeyId = "j1"): Record<string, unknown> {
  return {
    schemaVersion: 2,
    journeyId,
    userId: uid,
    startLocation: { latitude: -37.8, longitude: 144.96 },
    endLocation: { latitude: -37.81, longitude: 144.97 },
    startTimeMillis: 1_757_800_000_000,
    endTimeMillis: 1_757_800_900_000,
    distanceMeters: 2000,
    transportMode: "CYCLING",
    detectedTransportMode: "CYCLING",
    confirmationStatus: "pending",
    linkedMissionId: null,
    routePolyline: null,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function mission(missionId = "m1"): Record<string, unknown> {
  return {
    schemaVersion: 1,
    missionId,
    title: "Home → Uni",
    description: "",
    missionType: "route",
    startLabel: "Home",
    destinationLabel: "Uni",
    targetTransportMode: "CYCLING",
    targetDistanceMeters: null,
    status: "accepted",
    recurrence: { type: "weekly", interval: 1, daysOfWeek: [1, 3], timezone: "Australia/Melbourne" },
    scheduledMinuteOfDay: 510,
    nextOccurrenceDate: "2026-10-05",
    activeOccurrenceDate: null,
    estimates: [],
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

async function seed(path: string, data: Record<string, unknown>) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore() as unknown as Firestore, path), data);
  });
}

before(async () => {
  const { host, port } = requireEmulator();
  env = await initializeTestEnvironment({
    projectId: RULES_PROJECT_ID,
    firestore: { host, port, rules: readFileSync(resolve(__dirname, "../../../../firestore.rules"), "utf8") },
  });
});

beforeEach(async () => env.clearFirestore());

after(async () => env.cleanup());

describe("authentication", () => {
  test("unauthenticated users cannot read or write user data", async () => {
    await seed("users/alice", { displayName: "Alice" });
    await assertFails(getDoc(doc(anonymous(), "users/alice")));
    await assertFails(setDoc(doc(anonymous(), "users/alice/journeys/j1"), journey("alice")));
    await assertFails(getDoc(doc(anonymous(), "rewards/r1")));
    await assertFails(getDocs(collection(anonymous(), "leaderboards/all_time/entries")));
  });
});

describe("profile", () => {
  test("owner can create and update an allowed profile", async () => {
    const db = alice();
    await assertSucceeds(setDoc(doc(db, "users/alice"), {
      schemaVersion: 1,
      displayName: "Alice",
      preferences: { communityRankingEnabled: true, defaultReminderMinutes: 60 },
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(setDoc(doc(db, "users/alice"), {
      preferences: { missionNotificationsEnabled: false },
      updatedAt: serverTimestamp(),
    }, { merge: true }));
  });

  test("cross-user profile access is denied", async () => {
    await seed("users/alice", { displayName: "Alice" });
    await assertFails(getDoc(doc(bob(), "users/alice")));
    await assertFails(setDoc(doc(bob(), "users/alice"), { displayName: "Mallory", updatedAt: serverTimestamp() }, { merge: true }));
  });

  test("unknown fields, email, long names and bad preference values are denied", async () => {
    const db = alice();
    const base = { updatedAt: serverTimestamp() };
    await assertFails(setDoc(doc(db, "users/alice"), { ...base, email: "a@example.com" }));
    await assertFails(setDoc(doc(db, "users/alice"), { ...base, displayName: "x".repeat(51) }));
    await assertFails(setDoc(doc(db, "users/alice"), { ...base, preferences: { defaultReminderMinutes: -5 } }));
    await assertFails(setDoc(doc(db, "users/alice"), { ...base, preferences: { hacker: true } }));
    await assertFails(setDoc(doc(db, "users/alice"), { ...base, pointsBalance: 1_000_000 }));
  });

  test("createdAt is immutable once set", async () => {
    await seed("users/alice", { displayName: "Alice", createdAt: new Date(0) });
    await assertFails(setDoc(doc(alice(), "users/alice"), {
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }, { merge: true }));
  });
});

describe("journeys", () => {
  test("owner can create, read and confirm a journey", async () => {
    const db = alice();
    await assertSucceeds(setDoc(doc(db, "users/alice/journeys/j1"), journey("alice")));
    await assertSucceeds(getDoc(doc(db, "users/alice/journeys/j1")));
    await assertSucceeds(updateDoc(doc(db, "users/alice/journeys/j1"), {
      confirmedTransportMode: "WALKING",
      transportMode: "WALKING",
      confirmationStatus: "confirmed",
      updatedAt: serverTimestamp(),
    }));
  });

  test("schema v1 journeys from older app versions can still be created", async () => {
    const legacy = journey("alice");
    for (const key of ["schemaVersion", "detectedTransportMode", "confirmationStatus", "linkedMissionId",
      "routePolyline", "createdAt", "updatedAt"]) {
      delete legacy[key];
    }
    await assertSucceeds(setDoc(doc(alice(), "users/alice/journeys/j1"), legacy));
  });

  test("cross-user read and write are denied", async () => {
    await seed("users/alice/journeys/j1", journey("alice"));
    await assertFails(getDoc(doc(bob(), "users/alice/journeys/j1")));
    await assertFails(getDocs(collection(bob(), "users/alice/journeys")));
    await assertFails(setDoc(doc(bob(), "users/alice/journeys/j2"), journey("alice", "j2")));
  });

  test("a forged userId or mismatched journeyId is denied", async () => {
    await assertFails(setDoc(doc(alice(), "users/alice/journeys/j1"), journey("bob")));
    await assertFails(setDoc(doc(alice(), "users/alice/journeys/j1"), journey("alice", "other")));
  });

  test("clients cannot write trusted carbon, EcoPoints or award fields", async () => {
    const db = alice();
    await assertFails(setDoc(doc(db, "users/alice/journeys/j1"), { ...journey("alice"), carbonSavedGrams: 999 }));
    await assertFails(setDoc(doc(db, "users/alice/journeys/j1"), { ...journey("alice"), ecoPoints: 500 }));
    await seed("users/alice/journeys/j2", { ...journey("alice", "j2"), createdAt: new Date(0), updatedAt: new Date(0) });
    await assertFails(updateDoc(doc(db, "users/alice/journeys/j2"), { ecoPoints: 500 }));
    await assertFails(updateDoc(doc(db, "users/alice/journeys/j2"), {
      confirmedTransportMode: "WALKING",
      transportMode: "WALKING",
      confirmationStatus: "confirmed",
      carbonSavedGrams: 10_000,
      updatedAt: serverTimestamp(),
    }));
  });

  test("an existing journey cannot be overwritten or deleted", async () => {
    await seed("users/alice/journeys/j1", { ...journey("alice"), createdAt: new Date(0), updatedAt: new Date(0) });
    await assertFails(setDoc(doc(alice(), "users/alice/journeys/j1"), journey("alice")));
    await assertFails(deleteDoc(doc(alice(), "users/alice/journeys/j1")));
  });

  test("invalid values are denied", async () => {
    const db = alice();
    const bad: Record<string, unknown>[] = [
      { distanceMeters: -1 },
      { distanceMeters: 5_000_000 },
      { distanceMeters: "far" },
      { transportMode: "TELEPORT" },
      { confirmationStatus: "maybe" },
      { endTimeMillis: 1 },
      { startLocation: { latitude: 200, longitude: 0 } },
      { linkedMissionId: "m".repeat(129) },
      { sensorFeatures: { rawAccelerometer: [1, 2, 3] } },
    ];
    for (const change of bad) {
      await assertFails(setDoc(doc(db, "users/alice/journeys/j1"), { ...journey("alice"), ...change }));
    }
  });

  test("confirmation must use a real mode and only touch confirmation fields", async () => {
    await seed("users/alice/journeys/j1", { ...journey("alice"), createdAt: new Date(0), updatedAt: new Date(0) });
    const ref = doc(alice(), "users/alice/journeys/j1");
    const confirm = { transportMode: "UNKNOWN", confirmedTransportMode: "UNKNOWN", confirmationStatus: "confirmed", updatedAt: serverTimestamp() };
    await assertFails(updateDoc(ref, confirm));
    await assertFails(updateDoc(ref, { ...confirm, transportMode: "WALKING", confirmedTransportMode: "WALKING", distanceMeters: 99_999 }));
    await assertFails(updateDoc(ref, { ...confirm, transportMode: "CAR", confirmedTransportMode: "WALKING" }));
  });
});

describe("missions and occurrences", () => {
  test("owner can create and update missions; createdAt is immutable", async () => {
    const db = alice();
    await assertSucceeds(setDoc(doc(db, "users/alice/missions/m1"), mission()));
    await assertSucceeds(setDoc(doc(db, "users/alice/missions/m1"), { status: "active", activeOccurrenceDate: "2026-10-05", updatedAt: serverTimestamp() }, { merge: true }));
    await assertFails(setDoc(doc(db, "users/alice/missions/m1"), { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }, { merge: true }));
    await assertFails(setDoc(doc(bob(), "users/alice/missions/m2"), mission("m2")));
  });

  test("invalid mission fields are denied", async () => {
    const db = alice();
    for (const change of [{ status: "paused" }, { title: "" }, { scheduledMinuteOfDay: 1440 }, { nextOccurrenceDate: "tomorrow" }]) {
      await assertFails(setDoc(doc(db, "users/alice/missions/m1"), { ...mission(), ...change }));
    }
  });

  test("occurrence IDs must be missionId_date and server fields are protected", async () => {
    const db = alice();
    const occurrence = { schemaVersion: 1, missionId: "m1", occurrenceDate: "2026-10-05", accepted: true, lastActionAtMillis: 1, updatedAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(db, "users/alice/missionResults/m1_2026-10-05"), occurrence));
    await assertFails(setDoc(doc(db, "users/alice/missionResults/other"), occurrence));
    await assertFails(setDoc(doc(db, "users/alice/missionResults/m1_2026-10-06"), { ...occurrence, occurrenceDate: "2026-10-06", ecoPointsAwarded: 500 }));
    await assertFails(setDoc(doc(db, "users/alice/missionResults/m1_2026-10-05"), { ecoPointsAwarded: 500, updatedAt: serverTimestamp() }, { merge: true }));
    await assertFails(setDoc(doc(db, "users/alice/missionResults/m1_2026-10-05"), { completed: true, updatedAt: serverTimestamp() }, { merge: true }));
  });

  test("a completed occurrence is final", async () => {
    await seed("users/alice/missionResults/m1_2026-10-05", {
      missionId: "m1", occurrenceDate: "2026-10-05", accepted: true, completed: true, linkedJourneyId: "j1", updatedAt: new Date(0),
    });
    const ref = doc(alice(), "users/alice/missionResults/m1_2026-10-05");
    await assertFails(setDoc(ref, { linkedJourneyId: "j2", updatedAt: serverTimestamp() }, { merge: true }));
    await assertFails(setDoc(ref, { completed: false, skipped: true, updatedAt: serverTimestamp() }, { merge: true }));
    await assertSucceeds(setDoc(ref, { lastActionAtMillis: 5, updatedAt: serverTimestamp() }, { merge: true }));
  });
});

describe("backend-only data", () => {
  test("ledger, stats and redemptions are owner-read-only", async () => {
    await seed("users/alice/pointTransactions/t1", { type: "mission_award", amount: 10 });
    await seed("userStats/alice", { pointsBalance: 10 });
    await seed("users/alice/rewardRedemptions/r1", { rewardId: "x" });
    const db = alice();
    await assertSucceeds(getDoc(doc(db, "users/alice/pointTransactions/t1")));
    await assertSucceeds(getDoc(doc(db, "userStats/alice")));
    await assertSucceeds(getDoc(doc(db, "users/alice/rewardRedemptions/r1")));
    await assertFails(setDoc(doc(db, "users/alice/pointTransactions/t2"), { type: "adjustment", amount: 1_000_000 }));
    await assertFails(setDoc(doc(db, "userStats/alice"), { pointsBalance: 1_000_000 }));
    await assertFails(updateDoc(doc(db, "userStats/alice"), { pointsBalance: 1_000_000 }));
    await assertFails(setDoc(doc(db, "users/alice/rewardRedemptions/r2"), { rewardId: "x", status: "available" }));
    await assertFails(updateDoc(doc(db, "users/alice/rewardRedemptions/r1"), { status: "available" }));
    await assertFails(getDoc(doc(bob(), "userStats/alice")));
    await assertFails(getDoc(doc(bob(), "users/alice/pointTransactions/t1")));
  });

  test("rewards are readable by signed-in users and never writable", async () => {
    await seed("rewards/r1", { title: "Coffee", pointsRequired: 300, active: true });
    await assertSucceeds(getDoc(doc(alice(), "rewards/r1")));
    await assertFails(setDoc(doc(alice(), "rewards/r1"), { title: "Coffee", pointsRequired: 1, active: true }));
    await assertFails(setDoc(doc(alice(), "rewards/r2"), { title: "Free", pointsRequired: 1 }));
  });

  test("friend requests are visible only to their sender and receiver", async () => {
    const request = { senderUid: "alice", receiverUid: "bob", status: "pending" };
    await seed("users/alice/friendRequests/alice_bob", request);
    await seed("users/bob/friendRequests/alice_bob", request);
    await assertSucceeds(getDoc(doc(alice(), "users/alice/friendRequests/alice_bob")));
    await assertSucceeds(getDoc(doc(bob(), "users/bob/friendRequests/alice_bob")));
    const carol = env.authenticatedContext("carol").firestore() as unknown as Firestore;
    await assertFails(getDoc(doc(carol, "users/alice/friendRequests/alice_bob")));
    await assertFails(getDoc(doc(carol, "users/bob/friendRequests/alice_bob")));
    // Clients cannot change state directly; transitions go through the backend.
    await assertFails(updateDoc(doc(bob(), "users/bob/friendRequests/alice_bob"), { status: "accepted" }));
    await assertFails(setDoc(doc(alice(), "users/alice/friendRequests/alice_carol"), { senderUid: "alice", receiverUid: "carol", status: "pending" }));
  });

  test("clients cannot add themselves to someone's friends", async () => {
    await assertFails(setDoc(doc(alice(), "users/bob/friends/alice"), { friendUid: "alice" }));
    await assertFails(setDoc(doc(alice(), "users/alice/friends/bob"), { friendUid: "bob" }));
  });

  test("leaderboards are readable and never client-writable", async () => {
    await seed("leaderboards/all_time/entries/alice", { uid: "alice", carbonSavedGrams: 10 });
    await assertSucceeds(getDocs(collection(bob(), "leaderboards/all_time/entries")));
    await assertFails(setDoc(doc(alice(), "leaderboards/all_time/entries/alice"), { uid: "alice", carbonSavedGrams: 1e9 }));
    await assertFails(updateDoc(doc(alice(), "leaderboards/all_time/entries/alice"), { carbonSavedGrams: 1e9 }));
    await assertFails(setDoc(doc(alice(), "leaderboardStats/alice"), { allTime: { carbonSavedGrams: 1e9 } }));
  });

  test("leaderboard stats are visible to the user and their friends only", async () => {
    await seed("leaderboardStats/alice", { displayName: "Alice" });
    await seed("users/alice/friends/bob", { friendUid: "bob" });
    await assertSucceeds(getDoc(doc(alice(), "leaderboardStats/alice")));
    await assertSucceeds(getDoc(doc(bob(), "leaderboardStats/alice")));
    const carol = env.authenticatedContext("carol").firestore() as unknown as Firestore;
    await assertFails(getDoc(doc(carol, "leaderboardStats/alice")));
  });

  test("public profiles are readable but only the backend writes them", async () => {
    await seed("publicProfiles/alice", { displayName: "Alice" });
    await assertSucceeds(getDoc(doc(bob(), "publicProfiles/alice")));
    await assertFails(setDoc(doc(alice(), "publicProfiles/alice"), { displayName: "Alice", email: "a@example.com" }));
  });

  test("unknown collections are denied", async () => {
    await assertFails(setDoc(doc(alice(), "admin/config"), { open: true }));
    await assertFails(getDoc(doc(alice(), "admin/config")));
  });
});
