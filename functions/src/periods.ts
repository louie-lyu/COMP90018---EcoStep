/** Leaderboard period IDs, shared with Android's LeaderboardPeriods (UTC ISO weeks). */
export const ALL_TIME = "all_time";

export function weekId(epochMillis: number): string {
  const date = new Date(epochMillis);
  // ISO week: the week containing the year's first Thursday is week 1.
  const day = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const isoDay = day.getUTCDay() || 7;
  day.setUTCDate(day.getUTCDate() + 4 - isoDay);
  const isoYear = day.getUTCFullYear();
  const yearStart = Date.UTC(isoYear, 0, 1);
  const week = Math.ceil(((day.getTime() - yearStart) / 86_400_000 + 1) / 7);
  return `week-${String(isoYear).padStart(4, "0")}-W${String(week).padStart(2, "0")}`;
}
