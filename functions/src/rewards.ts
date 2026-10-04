import { randomBytes } from "node:crypto";
import { FieldValue, Firestore, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { paths } from "./db";
import { REDEMPTION_VALIDITY_DAYS } from "./scoring";

export interface RedemptionResponse {
  redemptionId: string;
  rewardId: string;
  rewardTitle: string;
  merchantName: string;
  rewardDescription: string;
  category: string;
  pointsSpent: number;
  redemptionCode: string;
  status: string;
  redeemedAtMillis: number;
  expiresAtMillis: number;
  usedAtMillis: number | null;
}

const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";

/** Unpredictable code from a CSPRNG, e.g. "ECO-7KQ2-M9XA-T3PV". */
export function generateRedemptionCode(random: (size: number) => Buffer = randomBytes): string {
  const bytes = random(12);
  let code = "";
  for (let i = 0; i < 12; i++) {
    code += CODE_ALPHABET[bytes[i]! % CODE_ALPHABET.length];
    if (i % 4 === 3 && i < 11) code += "-";
  }
  return `ECO-${code}`;
}

function toMillis(value: unknown): number | null {
  return value instanceof Timestamp ? value.toMillis() : null;
}

function toResponse(redemptionId: string, data: FirebaseFirestore.DocumentData): RedemptionResponse {
  return {
    redemptionId,
    rewardId: data.rewardId,
    rewardTitle: data.rewardTitle,
    merchantName: data.merchantName,
    rewardDescription: data.rewardDescription,
    category: data.category,
    pointsSpent: data.pointsSpent,
    redemptionCode: data.redemptionCode,
    status: data.status,
    redeemedAtMillis: toMillis(data.redeemedAt) ?? 0,
    expiresAtMillis: toMillis(data.expiresAt) ?? 0,
    usedAtMillis: toMillis(data.usedAt),
  };
}

/**
 * Redeems a reward in one transaction: checks the reward (active, valid now, in stock) and
 * the user's balance, writes the negative ledger entry, the redemption and the new balance,
 * and decrements inventory. The client-chosen requestId is the redemption ID, so a retry of
 * the same request returns the original redemption without charging again.
 */
export async function redeemReward(
  db: Firestore,
  uid: string,
  rewardId: string,
  requestId: string,
  nowMillis: number,
): Promise<RedemptionResponse> {
  const redemptionRef = db.doc(paths.redemption(uid, requestId));
  const rewardRef = db.doc(paths.reward(rewardId));
  const statsRef = db.doc(paths.userStats(uid));
  const ledgerRef = db.doc(paths.pointTransaction(uid, `redeem_${requestId}`));

  return db.runTransaction(async (transaction) => {
    const existing = await transaction.get(redemptionRef);
    if (existing.exists) {
      const data = existing.data()!;
      if (data.rewardId !== rewardId) {
        throw new HttpsError("already-exists", "This request was already used for another reward.");
      }
      return toResponse(requestId, data);
    }

    const reward = (await transaction.get(rewardRef)).data();
    const now = Timestamp.fromMillis(nowMillis);
    const points = reward?.pointsRequired;
    const available = !!reward &&
      reward.active === true &&
      Number.isInteger(points) && points > 0 &&
      (!(reward.validFrom instanceof Timestamp) || reward.validFrom.toMillis() <= nowMillis) &&
      (!(reward.validUntil instanceof Timestamp) || nowMillis < reward.validUntil.toMillis()) &&
      (reward.inventory === undefined || reward.inventory === null ||
        (Number.isInteger(reward.inventory) && reward.inventory > 0));
    if (!available) {
      throw new HttpsError("failed-precondition", "This reward is no longer available.");
    }

    const stats = (await transaction.get(statsRef)).data();
    const balance = Number.isInteger(stats?.pointsBalance) ? (stats!.pointsBalance as number) : 0;
    if (balance < points) {
      throw new HttpsError("failed-precondition", "You do not have enough EcoPoints for this reward.");
    }

    const redemption = {
      rewardId,
      rewardTitle: String(reward.title ?? ""),
      merchantName: String(reward.merchantName ?? ""),
      rewardDescription: String(reward.description ?? ""),
      category: String(reward.category ?? "other"),
      pointsSpent: points,
      redemptionCode: generateRedemptionCode(),
      status: "available",
      redeemedAt: now,
      expiresAt: Timestamp.fromMillis(nowMillis + REDEMPTION_VALIDITY_DAYS * 86_400_000),
      usedAt: null,
    };

    transaction.create(ledgerRef, {
      type: "reward_redemption",
      amount: -points,
      sourceId: requestId,
      idempotencyKey: `redeem_${requestId}`,
      createdAt: FieldValue.serverTimestamp(),
      metadata: { rewardId },
    });
    transaction.set(statsRef, {
      pointsBalance: balance - points,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    if (Number.isInteger(reward.inventory)) {
      transaction.update(rewardRef, { inventory: reward.inventory - 1 });
    }
    transaction.create(redemptionRef, redemption);
    return toResponse(requestId, redemption);
  });
}
