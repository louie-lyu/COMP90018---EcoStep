import assert from "node:assert/strict";
import { test } from "node:test";
import { calculateMissionPoints, carbonSavedVersusCarGrams, verifyConfirmedJourney } from "../../src/scoring";

test("carbon saving uses the same-distance car baseline", () => {
  assert.equal(carbonSavedVersusCarGrams(2000, "WALKING"), 384);
  assert.equal(carbonSavedVersusCarGrams(2000, "PUBLIC_TRANSPORT"), 206);
  assert.equal(carbonSavedVersusCarGrams(2000, "CAR"), 0);
  assert.equal(carbonSavedVersusCarGrams(2000, "UNKNOWN"), 0);
});

test("carbon saving rejects NaN, Infinity and negative distances", () => {
  for (const bad of [Number.NaN, Number.POSITIVE_INFINITY, -1]) {
    assert.throws(() => carbonSavedVersusCarGrams(bad, "WALKING"), RangeError);
  }
});

test("mission points mirror DefaultEcoPointsCalculator", () => {
  const completed = { accepted: true, completed: true };
  assert.equal(calculateMissionPoints({ ...completed, actualTransportMode: "WALKING", actualCarbonSavingGrams: 384 }), 58);
  assert.equal(calculateMissionPoints({ ...completed, actualTransportMode: "PUBLIC_TRANSPORT", actualCarbonSavingGrams: 205 }), 26);
  assert.equal(calculateMissionPoints({ ...completed, actualTransportMode: "CYCLING", actualCarbonSavingGrams: 100_000 }), 500);
  assert.equal(calculateMissionPoints({ ...completed, actualTransportMode: "WALKING", actualCarbonSavingGrams: 0 }), 0);
  assert.equal(calculateMissionPoints({ ...completed, actualTransportMode: null, actualCarbonSavingGrams: 300 }), 0);
  assert.equal(calculateMissionPoints({ ...completed, actualTransportMode: "WALKING", actualCarbonSavingGrams: null }), 0);
});

test("the mode bonus grows with distance up to 1 km", () => {
  const walk = { accepted: true, completed: true, actualTransportMode: "WALKING" as const };
  // 26 m on foot: base round(0.4992) = 0, bonus round(20 × 0.026) = 1.
  assert.equal(calculateMissionPoints({ ...walk, actualCarbonSavingGrams: 4.992, actualDistanceMeters: 26 }), 1);
  // 500 m: base 10, half the 20-point bonus.
  assert.equal(calculateMissionPoints({ ...walk, actualCarbonSavingGrams: 96, actualDistanceMeters: 500 }), 20);
  // From 1 km on the bonus is full.
  assert.equal(calculateMissionPoints({ ...walk, actualCarbonSavingGrams: 384, actualDistanceMeters: 2000 }), 58);
  // Without a distance it is derived from the saving versus driving (500 m here).
  assert.equal(calculateMissionPoints({ ...walk, actualCarbonSavingGrams: 96 }), 20);
  assert.throws(() => calculateMissionPoints({ ...walk, actualCarbonSavingGrams: 96, actualDistanceMeters: -1 }), RangeError);
});

test("only accepted and completed missions earn points", () => {
  const result = { actualTransportMode: "WALKING" as const, actualCarbonSavingGrams: 384 };
  assert.equal(calculateMissionPoints({ ...result, accepted: false, completed: true }), 0);
  assert.equal(calculateMissionPoints({ ...result, accepted: true, completed: false }), 0);
  assert.throws(() => calculateMissionPoints({ ...result, accepted: true, completed: true, actualCarbonSavingGrams: -1 }));
});

test("confirmed journey verification gives credit only to plausible journeys", () => {
  const base = { distanceMeters: 2000, startTimeMillis: 0, endTimeMillis: 1_200_000, confirmationStatus: "confirmed" };
  const ok = verifyConfirmedJourney({ ...base, confirmedTransportMode: "WALKING", transportMode: "WALKING" });
  assert.deepEqual(ok, { status: "verified", reason: null, carbonSavedGrams: 384, mode: "WALKING" });

  // 2 km in 60 s is not walking.
  const tooFast = verifyConfirmedJourney({ ...base, endTimeMillis: 60_000, confirmedTransportMode: "WALKING" });
  assert.equal(tooFast.status, "rejected");
  assert.equal(tooFast.reason, "implausible_speed");
  assert.equal(tooFast.carbonSavedGrams, 0);

  assert.equal(verifyConfirmedJourney({ ...base, distanceMeters: Number.NaN, transportMode: "CYCLING" }).reason, "invalid_distance");
  assert.equal(verifyConfirmedJourney({ ...base, distanceMeters: 5_000_000, transportMode: "CAR" }).reason, "invalid_distance");
  assert.equal(verifyConfirmedJourney({ ...base, endTimeMillis: -5, transportMode: "CAR" }).reason, "invalid_times");
});

test("legacy transportMode is used when no confirmed mode exists", () => {
  const legacy = verifyConfirmedJourney({ distanceMeters: 1000, startTimeMillis: 0, endTimeMillis: 600_000, transportMode: "CYCLING" });
  assert.equal(legacy.mode, "CYCLING");
  const junk = verifyConfirmedJourney({ distanceMeters: 1000, startTimeMillis: 0, endTimeMillis: 600_000, transportMode: "TELEPORT" });
  assert.equal(junk.mode, "UNKNOWN");
  assert.equal(junk.carbonSavedGrams, 0);
});
