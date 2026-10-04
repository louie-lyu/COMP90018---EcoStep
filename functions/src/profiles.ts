import { DocumentData, FieldValue, Firestore } from "firebase-admin/firestore";
import { readVisibility, recomputeUserAggregates, removeFromCommunityLeaderboards } from "./aggregates";
import { paths } from "./db";

function visibleFields(data: DocumentData | undefined) {
  const preferences = (data?.preferences ?? {}) as Record<string, unknown>;
  return {
    displayName: data?.displayName ?? null,
    communityRankingEnabled: preferences.communityRankingEnabled !== false,
  };
}

/**
 * Keeps publicProfiles/{uid} (whitelisted fields only: no email, preferences or journeys)
 * and the user's leaderboard rows in sync with their private profile.
 */
export async function handleUserWrite(
  db: Firestore,
  uid: string,
  before: DocumentData | undefined,
  after: DocumentData | undefined,
  nowMillis: number,
): Promise<void> {
  if (!after) {
    await db.doc(paths.publicProfile(uid)).delete();
    await removeFromCommunityLeaderboards(db, uid);
    return;
  }
  const previous = visibleFields(before);
  const current = visibleFields(after);
  if (before && previous.displayName === current.displayName &&
    previous.communityRankingEnabled === current.communityRankingEnabled) {
    return;
  }

  const visibility = await readVisibility(db, uid);
  await db.doc(paths.publicProfile(uid)).set({
    displayName: visibility.displayName,
    displayNameLower: visibility.displayName.toLowerCase(),
    communityRankingEnabled: visibility.communityRankingEnabled,
    updatedAt: FieldValue.serverTimestamp(),
  });

  if (!visibility.communityRankingEnabled) {
    await removeFromCommunityLeaderboards(db, uid);
  }
  await recomputeUserAggregates(db, uid, [], nowMillis);
}
