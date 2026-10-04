/** Friend request state machine; every transition is checked here before any write. */

export type FriendRequestStatus = "pending" | "accepted" | "declined" | "cancelled";
export type FriendRequestAction = "accept" | "decline" | "cancel";
export type Role = "sender" | "receiver";

export class InvalidTransitionError extends Error {}

const TRANSITIONS: Record<FriendRequestAction, { role: Role; to: FriendRequestStatus }> = {
  accept: { role: "receiver", to: "accepted" },
  decline: { role: "receiver", to: "declined" },
  cancel: { role: "sender", to: "cancelled" },
};

/**
 * Only a pending request can change. The receiver accepts or declines; only the sender can
 * cancel. Anything else throws.
 */
export function nextFriendRequestStatus(
  current: FriendRequestStatus,
  action: FriendRequestAction,
  actor: Role,
): FriendRequestStatus {
  const transition = TRANSITIONS[action];
  if (current !== "pending") {
    throw new InvalidTransitionError(`This request is already ${current}.`);
  }
  if (transition.role !== actor) {
    throw new InvalidTransitionError(
      action === "cancel" ? "Only the sender can cancel this request." : "Only the receiver can answer this request.",
    );
  }
  return transition.to;
}

export function friendRequestId(senderUid: string, receiverUid: string): string {
  return `${senderUid}_${receiverUid}`;
}

/** "emily@example.com" -> "e***@example.com"; shown only to someone who typed the email. */
export function maskEmail(email: string): string {
  const at = email.indexOf("@");
  if (at <= 0) return "***";
  return `${email[0]}***${email.slice(at)}`;
}
