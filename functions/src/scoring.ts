import scoring from "./config/scoring.json";

/*
 * Server-side copy of the client's scoring rules. Values live in config/scoring.json, which
 * the Android ScoringConfigContractTest compares with EmissionFactors and
 * DefaultEcoPointsCalculator, so the two sides cannot drift silently.
 */

export const TRANSPORT_MODES = ["WALKING", "CYCLING", "PUBLIC_TRANSPORT", "CAR", "UNKNOWN"] as const;
export type TransportMode = (typeof TRANSPORT_MODES)[number];

export function isTransportMode(value: unknown): value is TransportMode {
  return typeof value === "string" && (TRANSPORT_MODES as readonly string[]).includes(value);
}

const factors = scoring.emissionFactorsGramsPerKm as Record<string, number>;
const bonuses = scoring.ecoPoints.modeBonusPoints as Record<string, number>;
const speedLimits = scoring.journeyLimits.maxAverageSpeedMps as Record<string, number>;

export const MAX_JOURNEY_DISTANCE_METERS = scoring.journeyLimits.maxDistanceMeters;
export const REDEMPTION_VALIDITY_DAYS = scoring.redemptionValidityDays;

export function isFiniteNonNegative(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

/** max(0, carEmissions - modeEmissions) for the same distance; UNKNOWN saves nothing. */
export function carbonSavedVersusCarGrams(distanceMeters: number, mode: TransportMode): number {
  if (!isFiniteNonNegative(distanceMeters)) {
    throw new RangeError("Journey distance must be finite and non-negative.");
  }
  const car = factors.CAR;
  const modeFactor = factors[mode];
  if (car === undefined || modeFactor === undefined) return 0;
  return Math.max(0, (distanceMeters / 1000) * (car - modeFactor));
}

export interface MissionOutcome {
  accepted: boolean;
  completed: boolean;
  actualTransportMode: TransportMode | null;
  actualCarbonSavingGrams: number | null;
  /** Distance travelled; scales the mode bonus. Derived from the saving when absent. */
  actualDistanceMeters?: number | null;
}

/** saving = km × (car factor − mode factor), so the distance follows from the saving. */
function distanceFromSaving(carbon: number, mode: TransportMode): number {
  const car = factors.CAR;
  const modeFactor = factors[mode];
  if (car === undefined || modeFactor === undefined) return 0;
  const savingPerKm = car - modeFactor;
  return savingPerKm > 0 ? carbon / savingPerKm * 1000 : 0;
}

/**
 * Mirrors DefaultEcoPointsCalculator.calculatePoints:
 * min(round(saving / 10 g) + round(modeBonus × min(1, distance / 1000 m)), 500).
 */
export function calculateMissionPoints(outcome: MissionOutcome): number {
  if (!outcome.accepted || !outcome.completed) return 0;
  const carbon = outcome.actualCarbonSavingGrams;
  if (carbon === null) return 0;
  if (!isFiniteNonNegative(carbon)) {
    throw new RangeError("Actual carbon saving must be finite and non-negative.");
  }
  const distance = outcome.actualDistanceMeters ?? null;
  if (distance !== null && !isFiniteNonNegative(distance)) {
    throw new RangeError("Actual distance must be finite and non-negative.");
  }
  if (carbon === 0) return 0;
  const mode = outcome.actualTransportMode;
  if (mode === null) return 0;
  // Kotlin's roundToInt and Math.round agree for non-negative values (half rounds up).
  const base = Math.round(carbon / scoring.ecoPoints.gramsPerPoint);
  const share = Math.min((distance ?? distanceFromSaving(carbon, mode)) / scoring.ecoPoints.bonusFullDistanceMeters, 1);
  const bonus = Math.round((bonuses[mode] ?? 0) * share);
  return Math.min(base + bonus, scoring.ecoPoints.maxPoints);
}

export type VerificationStatus = "verified" | "rejected";

export interface JourneyVerification {
  status: VerificationStatus;
  reason: string | null;
  carbonSavedGrams: number;
  mode: TransportMode;
}

/**
 * Trusted carbon value for a confirmed journey. Implausible data (bad numbers or an average
 * speed impossible for the confirmed mode) is kept but earns no carbon credit.
 */
export function verifyConfirmedJourney(journey: Record<string, unknown>): JourneyVerification {
  const rawMode = journey.confirmedTransportMode ?? journey.transportMode;
  const mode: TransportMode = isTransportMode(rawMode) ? rawMode : "UNKNOWN";
  const distance = journey.distanceMeters;
  const start = journey.startTimeMillis;
  const end = journey.endTimeMillis;
  const reject = (reason: string): JourneyVerification =>
    ({ status: "rejected", reason, carbonSavedGrams: 0, mode });

  if (!isFiniteNonNegative(distance) || distance > MAX_JOURNEY_DISTANCE_METERS) {
    return reject("invalid_distance");
  }
  if (typeof start !== "number" || typeof end !== "number" || end < start) {
    return reject("invalid_times");
  }
  const durationSeconds = (end - start) / 1000;
  const limit = speedLimits[mode];
  if (limit !== undefined && distance > 0) {
    if (durationSeconds <= 0 || distance / durationSeconds > limit) {
      return reject("implausible_speed");
    }
  }
  return { status: "verified", reason: null, carbonSavedGrams: carbonSavedVersusCarGrams(distance, mode), mode };
}
