import { DocumentData, FieldValue, Firestore } from "firebase-admin/firestore";
import { paths } from "./db";
import { calculateMissionPoints, verifyConfirmedJourney } from "./scoring";

export type AwardOutcome =
  | { status: "awarded"; points: number }
  | { status: "already_awarded" }
  | { status: "rejected"; reason: string }
  | { status: "skipped" };

function isCompletionRequest(data: DocumentData | undefined): data is DocumentData {
  return !!data &&
    data.accepted === true &&
    data.completed === true &&
    data.skipped !== true &&
    typeof data.linkedJourneyId === "string" &&
    data.linkedJourneyId !== "";
}

/**
 * Awards EcoPoints for a completed mission occurrence exactly once. The ledger entry's ID is
 * deterministic (mission_{resultId}), and the linked journey must be the user's own,
 * confirmed, recorded for this mission and not already used by another occurrence.
 */
export async function awardMissionResult(db: Firestore, uid: string, resultId: string): Promise<AwardOutcome> {
  const resultRef = db.doc(paths.missionResult(uid, resultId));
  const transactionRef = db.doc(paths.pointTransaction(uid, `mission_${resultId}`));
  const statsRef = db.doc(paths.userStats(uid));

  const outcome = await db.runTransaction<AwardOutcome>(async (transaction) => {
    const result = (await transaction.get(resultRef)).data();
    if (!isCompletionRequest(result)) return { status: "skipped" };
    if ((await transaction.get(transactionRef)).exists) return { status: "already_awarded" };

    const journeyRef = db.doc(paths.journey(uid, result.linkedJourneyId));
    const journey = (await transaction.get(journeyRef)).data();

    const reject = (reason: string): AwardOutcome => {
      transaction.update(resultRef, {
        awardStatus: "rejected",
        awardReason: reason,
        ecoPointsAwarded: 0,
        awardedAt: FieldValue.serverTimestamp(),
      });
      return { status: "rejected", reason };
    };

    if (!journey) return reject("journey_not_found");
    if (journey.confirmationStatus !== "confirmed") return reject("journey_not_confirmed");
    if (journey.linkedMissionId !== result.missionId) return reject("journey_not_linked_to_mission");
    if (journey.awardedMissionResultId && journey.awardedMissionResultId !== resultId) {
      return reject("journey_already_used");
    }

    const verification = verifyConfirmedJourney(journey);
    const points = verification.status === "verified"
      ? calculateMissionPoints({
        accepted: true,
        completed: true,
        actualTransportMode: verification.mode,
        actualCarbonSavingGrams: verification.carbonSavedGrams,
        // Verified as finite and within limits by verifyConfirmedJourney.
        actualDistanceMeters: journey.distanceMeters as number,
      })
      : 0;

    transaction.create(transactionRef, {
      type: "mission_award",
      amount: points,
      sourceId: resultId,
      idempotencyKey: `mission_${resultId}`,
      createdAt: FieldValue.serverTimestamp(),
      metadata: { missionId: result.missionId, journeyId: result.linkedJourneyId },
    });
    transaction.set(statsRef, {
      pointsBalance: FieldValue.increment(points),
      completedMissions: FieldValue.increment(1),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    transaction.update(resultRef, {
      actualTransportMode: verification.mode,
      actualCarbonSavingGrams: verification.carbonSavedGrams,
      ecoPointsAwarded: points,
      awardStatus: "awarded",
      awardReason: verification.reason,
      awardedAt: FieldValue.serverTimestamp(),
    });
    transaction.update(journeyRef, { ecoPoints: points, awardedMissionResultId: resultId });
    return { status: "awarded", points };
  });
  return outcome;
}

/** Trigger entry point for users/{uid}/missionResults/{resultId}. */
export async function handleMissionResultWrite(
  db: Firestore,
  uid: string,
  resultId: string,
  after: DocumentData | undefined,
): Promise<void> {
  if (!after) return;
  if (after.createdAt === undefined) {
    // createdAt is backend-only so client merge writes can stay idempotent.
    await db.doc(paths.missionResult(uid, resultId)).update({ createdAt: FieldValue.serverTimestamp() });
  }
  if (after.awardStatus === "awarded" || after.awardStatus === "rejected") return;
  if (!isCompletionRequest(after)) return;
  await awardMissionResult(db, uid, resultId);
}
