import { DocumentData, FieldValue, Firestore, Transaction } from "firebase-admin/firestore";
import { paths } from "./db";
import { ALL_TIME, weekId } from "./periods";
import { isFiniteNonNegative } from "./scoring";

export const DEFAULT_DISPLAY_NAME = "EcoStep User";

interface Totals {
  carbonSavedGrams: number;
  completedJourneys: number;
}

export interface UserVisibility {
  displayName: string;
  communityRankingEnabled: boolean;
}

export async function readVisibility(db: Firestore, uid: string): Promise<UserVisibility> {
  return visibilityFrom((await db.doc(paths.user(uid)).get()).data());
}

function visibilityFrom(data: DocumentData | undefined): UserVisibility {
  const profile = data ?? {};
  const preferences = (profile.preferences ?? {}) as Record<string, unknown>;
  return {
    displayName: typeof profile.displayName === "string" && profile.displayName.trim() !== ""
      ? profile.displayName.trim()
      : DEFAULT_DISPLAY_NAME,
    communityRankingEnabled: preferences.communityRankingEnabled !== false,
  };
}

/**
 * Recomputes a user's journey statistics and leaderboard rows from their confirmed journeys
 * and the backend-written carbon values. Recomputing (rather than incrementing) keeps the
 * result correct when a journey is re-confirmed with another mode or a trigger retries.
 *
 * It runs in a transaction: the journeys it read are locked until it commits, so a trigger
 * that read older data can never overwrite the result of one that read newer data.
 */
export async function recomputeUserAggregates(
  db: Firestore,
  uid: string,
  touchedWeekIds: string[],
  nowMillis: number,
): Promise<void> {
  await db.runTransaction(async (transaction) => {
    const confirmed = await transaction.get(
      db.collection(paths.journeys(uid)).where("confirmationStatus", "==", "confirmed"),
    );
    const visibility = visibilityFrom((await transaction.get(db.doc(paths.user(uid)))).data());
    writeAggregates(db, transaction, uid, confirmed.docs.map((doc) => doc.data()), visibility, touchedWeekIds, nowMillis);
  });
}

function writeAggregates(
  db: Firestore,
  writer: Transaction,
  uid: string,
  journeys: DocumentData[],
  visibility: UserVisibility,
  touchedWeekIds: string[],
  nowMillis: number,
): void {
  const allTime: Totals = { carbonSavedGrams: 0, completedJourneys: 0 };
  const byWeek = new Map<string, Totals>();
  for (const data of journeys) {
    const carbon = isFiniteNonNegative(data.carbonSavedGrams) ? data.carbonSavedGrams : 0;
    const end = typeof data.endTimeMillis === "number" ? data.endTimeMillis : 0;
    allTime.carbonSavedGrams += carbon;
    allTime.completedJourneys += 1;
    const week = weekId(end);
    const totals = byWeek.get(week) ?? { carbonSavedGrams: 0, completedJourneys: 0 };
    totals.carbonSavedGrams += carbon;
    totals.completedJourneys += 1;
    byWeek.set(week, totals);
  }

  const currentWeek = weekId(nowMillis);
  const currentTotals = byWeek.get(currentWeek) ?? { carbonSavedGrams: 0, completedJourneys: 0 };

  writer.set(db.doc(paths.userStats(uid)), {
    totalJourneys: allTime.completedJourneys,
    carbonSavedGrams: allTime.carbonSavedGrams,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  writer.set(db.doc(paths.leaderboardStats(uid)), {
    displayName: visibility.displayName,
    communityRankingEnabled: visibility.communityRankingEnabled,
    allTime,
    currentWeek: { weekId: currentWeek, ...currentTotals },
    updatedAt: FieldValue.serverTimestamp(),
  });

  const periods = new Map<string, Totals>([[ALL_TIME, allTime]]);
  for (const week of new Set([...touchedWeekIds, currentWeek])) {
    periods.set(week, byWeek.get(week) ?? { carbonSavedGrams: 0, completedJourneys: 0 });
  }
  for (const [periodId, totals] of periods) {
    const entry = db.doc(paths.leaderboardEntry(periodId, uid));
    if (visibility.communityRankingEnabled && totals.completedJourneys > 0) {
      writer.set(entry, {
        uid,
        displayName: visibility.displayName,
        carbonSavedGrams: totals.carbonSavedGrams,
        completedJourneys: totals.completedJourneys,
        updatedAt: FieldValue.serverTimestamp(),
      });
    } else {
      writer.delete(entry);
    }
  }
}

/** Removes a user from every community leaderboard period (ranking opt-out). */
export async function removeFromCommunityLeaderboards(db: Firestore, uid: string): Promise<void> {
  const entries = await db.collectionGroup("entries").where("uid", "==", uid).get();
  const batch = db.batch();
  entries.docs.forEach((doc) => batch.delete(doc.ref));
  if (!entries.empty) await batch.commit();
}
