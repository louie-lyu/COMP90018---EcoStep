import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";

/** Admin Firestore for the project the function runs in (the emulator's demo project in tests). */
export function db(): Firestore {
  // Check for the default app by name: the emulator runtime may register other apps.
  if (!getApps().some((app) => app.name === "[DEFAULT]")) initializeApp();
  return getFirestore();
}

export const paths = {
  user: (uid: string) => `users/${uid}`,
  journey: (uid: string, journeyId: string) => `users/${uid}/journeys/${journeyId}`,
  journeys: (uid: string) => `users/${uid}/journeys`,
  missionResult: (uid: string, resultId: string) => `users/${uid}/missionResults/${resultId}`,
  pointTransaction: (uid: string, id: string) => `users/${uid}/pointTransactions/${id}`,
  redemption: (uid: string, id: string) => `users/${uid}/rewardRedemptions/${id}`,
  friendRequest: (uid: string, id: string) => `users/${uid}/friendRequests/${id}`,
  friend: (uid: string, friendUid: string) => `users/${uid}/friends/${friendUid}`,
  userStats: (uid: string) => `userStats/${uid}`,
  publicProfile: (uid: string) => `publicProfiles/${uid}`,
  reward: (rewardId: string) => `rewards/${rewardId}`,
  leaderboardStats: (uid: string) => `leaderboardStats/${uid}`,
  leaderboardEntry: (periodId: string, uid: string) => `leaderboards/${periodId}/entries/${uid}`,
};
