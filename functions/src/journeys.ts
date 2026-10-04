import { DocumentData, FieldValue, Firestore } from "firebase-admin/firestore";
import { recomputeUserAggregates } from "./aggregates";
import { paths } from "./db";
import { weekId } from "./periods";
import { verifyConfirmedJourney } from "./scoring";

/**
 * Handles a write to users/{uid}/journeys/{journeyId}:
 * 1. For a confirmed journey, writes the trusted carbonSavedGrams and verification result
 *    (and ecoPoints = 0 unless a mission award already set it).
 * 2. When the trusted carbon value or confirmation changes, recomputes stats and leaderboards.
 */
export async function handleJourneyWrite(
  db: Firestore,
  uid: string,
  journeyId: string,
  before: DocumentData | undefined,
  after: DocumentData | undefined,
  nowMillis: number,
): Promise<void> {
  if (after && after.confirmationStatus === "confirmed") {
    await applyTrustedJourneyFields(db, uid, journeyId);
  }

  const touchedWeeks = [before, after]
    .map((data) => (typeof data?.endTimeMillis === "number" ? weekId(data.endTimeMillis) : null))
    .filter((week): week is string => week !== null);

  // Aggregates only count confirmed journeys, so pending-only writes skip the recompute.
  const affectsAggregates =
    (before?.confirmationStatus === "confirmed" || after?.confirmationStatus === "confirmed") &&
    (!before || !after ||
      before.carbonSavedGrams !== after.carbonSavedGrams ||
      before.confirmationStatus !== after.confirmationStatus);
  if (affectsAggregates) {
    await recomputeUserAggregates(db, uid, touchedWeeks, nowMillis);
  }
}

async function applyTrustedJourneyFields(db: Firestore, uid: string, journeyId: string): Promise<void> {
  const ref = db.doc(paths.journey(uid, journeyId));
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const data = snapshot.data();
    if (!data || data.confirmationStatus !== "confirmed") return;

    const verification = verifyConfirmedJourney(data);
    const update: Record<string, unknown> = {};
    if (data.carbonSavedGrams !== verification.carbonSavedGrams) {
      update.carbonSavedGrams = verification.carbonSavedGrams;
    }
    if (data.verificationStatus !== verification.status) update.verificationStatus = verification.status;
    if ((data.verificationReason ?? null) !== verification.reason) update.verificationReason = verification.reason;
    // Points come from mission awards only; a plain journey shows 0 once processed.
    if (data.ecoPoints === undefined) update.ecoPoints = 0;
    if (Object.keys(update).length === 0) return;

    update.trustedComputedAt = FieldValue.serverTimestamp();
    transaction.update(ref, update);
  });
}
