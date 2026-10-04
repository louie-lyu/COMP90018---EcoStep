import assert from "node:assert/strict";
import { test } from "node:test";
import { friendRequestId, InvalidTransitionError, maskEmail, nextFriendRequestStatus } from "../../src/friendState";
import { weekId } from "../../src/periods";
import { generateRedemptionCode } from "../../src/rewards";

test("ISO week IDs match Android's LeaderboardPeriods", () => {
  assert.equal(weekId(Date.UTC(2026, 9, 3, 12)), "week-2026-W40");
  assert.equal(weekId(Date.UTC(2027, 0, 1, 12)), "week-2026-W53");
  assert.equal(weekId(Date.UTC(2024, 11, 30, 12)), "week-2025-W01");
  assert.equal(weekId(Date.UTC(2026, 9, 5, 0, 0, 0)), "week-2026-W41");
});

test("receiver accepts or declines a pending request", () => {
  assert.equal(nextFriendRequestStatus("pending", "accept", "receiver"), "accepted");
  assert.equal(nextFriendRequestStatus("pending", "decline", "receiver"), "declined");
  assert.equal(nextFriendRequestStatus("pending", "cancel", "sender"), "cancelled");
});

test("illegal friend request transitions are rejected", () => {
  assert.throws(() => nextFriendRequestStatus("pending", "accept", "sender"), InvalidTransitionError);
  assert.throws(() => nextFriendRequestStatus("pending", "cancel", "receiver"), InvalidTransitionError);
  for (const done of ["accepted", "declined", "cancelled"] as const) {
    assert.throws(() => nextFriendRequestStatus(done, "accept", "receiver"), InvalidTransitionError);
    assert.throws(() => nextFriendRequestStatus(done, "cancel", "sender"), InvalidTransitionError);
  }
});

test("request IDs are deterministic per direction", () => {
  assert.equal(friendRequestId("a", "b"), "a_b");
  assert.notEqual(friendRequestId("a", "b"), friendRequestId("b", "a"));
});

test("emails are masked in search results", () => {
  assert.equal(maskEmail("emily@example.com"), "e***@example.com");
  assert.equal(maskEmail("bad"), "***");
});

test("redemption codes are well-formed and come from the random source", () => {
  const code = generateRedemptionCode();
  assert.match(code, /^ECO-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  const codes = new Set(Array.from({ length: 200 }, () => generateRedemptionCode()));
  assert.equal(codes.size, 200);
  assert.equal(generateRedemptionCode(() => Buffer.alloc(12, 0)), "ECO-AAAA-AAAA-AAAA");
});
