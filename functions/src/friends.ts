import { getAuth } from "firebase-admin/auth";
import { FieldValue, Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { DEFAULT_DISPLAY_NAME } from "./aggregates";
import { paths } from "./db";
import {
  FriendRequestAction,
  FriendRequestStatus,
  friendRequestId,
  InvalidTransitionError,
  maskEmail,
  nextFriendRequestStatus,
} from "./friendState";

async function displayNameOf(db: Firestore, uid: string): Promise<string | null> {
  const profile = await db.doc(paths.publicProfile(uid)).get();
  if (profile.exists) return String(profile.data()!.displayName ?? DEFAULT_DISPLAY_NAME);
  const user = await db.doc(paths.user(uid)).get();
  return user.exists ? String(user.data()!.displayName ?? DEFAULT_DISPLAY_NAME) : null;
}

/** Creates (or re-opens) a pending request, mirrored into both users' friendRequests. */
export async function sendFriendRequest(db: Firestore, senderUid: string, receiverUid: string) {
  if (senderUid === receiverUid) {
    throw new HttpsError("invalid-argument", "You cannot add yourself.");
  }
  const [senderName, receiverName] = await Promise.all([
    displayNameOf(db, senderUid),
    displayNameOf(db, receiverUid),
  ]);
  if (receiverName === null) throw new HttpsError("not-found", "That user does not exist.");

  const requestId = friendRequestId(senderUid, receiverUid);
  const senderCopy = db.doc(paths.friendRequest(senderUid, requestId));
  const receiverCopy = db.doc(paths.friendRequest(receiverUid, requestId));
  const reverse = db.doc(paths.friendRequest(senderUid, friendRequestId(receiverUid, senderUid)));
  const friendship = db.doc(paths.friend(senderUid, receiverUid));

  return db.runTransaction(async (transaction) => {
    const [existing, reverseRequest, alreadyFriends] = await Promise.all([
      transaction.get(senderCopy),
      transaction.get(reverse),
      transaction.get(friendship),
    ]);
    if (alreadyFriends.exists) throw new HttpsError("already-exists", "You are already friends.");
    if (reverseRequest.data()?.status === "pending") {
      throw new HttpsError("failed-precondition", "This user already sent you a request.");
    }
    if (existing.data()?.status === "pending") return { requestId, status: "pending" };

    const request = {
      senderUid,
      receiverUid,
      senderDisplayName: senderName ?? DEFAULT_DISPLAY_NAME,
      receiverDisplayName: receiverName,
      status: "pending",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    };
    transaction.set(senderCopy, request);
    transaction.set(receiverCopy, request);
    return { requestId, status: "pending" };
  });
}

/**
 * Applies accept/decline (receiver) or cancel (sender). Accepting creates both friend
 * documents in the same transaction as the status change.
 */
export async function transitionFriendRequest(
  db: Firestore,
  actorUid: string,
  requestId: string,
  action: FriendRequestAction,
) {
  const actorCopy = db.doc(paths.friendRequest(actorUid, requestId));
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(actorCopy);
    const request = snapshot.data();
    if (!request) throw new HttpsError("not-found", "Friend request not found.");

    const role = request.receiverUid === actorUid ? "receiver" : request.senderUid === actorUid ? "sender" : null;
    if (role === null) throw new HttpsError("permission-denied", "This is not your request.");

    let next: FriendRequestStatus;
    try {
      next = nextFriendRequestStatus(request.status as FriendRequestStatus, action, role);
    } catch (error) {
      if (error instanceof InvalidTransitionError) {
        throw new HttpsError("failed-precondition", error.message);
      }
      throw error;
    }

    const update = { status: next, updatedAt: FieldValue.serverTimestamp() };
    transaction.update(db.doc(paths.friendRequest(request.senderUid, requestId)), update);
    transaction.update(db.doc(paths.friendRequest(request.receiverUid, requestId)), update);
    if (next === "accepted") {
      transaction.set(db.doc(paths.friend(request.senderUid, request.receiverUid)), {
        friendUid: request.receiverUid,
        requestId,
        since: FieldValue.serverTimestamp(),
      });
      transaction.set(db.doc(paths.friend(request.receiverUid, request.senderUid)), {
        friendUid: request.senderUid,
        requestId,
        since: FieldValue.serverTimestamp(),
      });
    }
    return { requestId, status: next };
  });
}

type Relationship = "self" | "friend" | "request_sent" | "request_received" | "none";

async function relationship(db: Firestore, uid: string, other: string): Promise<Relationship> {
  if (uid === other) return "self";
  const [friend, sent, received] = await Promise.all([
    db.doc(paths.friend(uid, other)).get(),
    db.doc(paths.friendRequest(uid, friendRequestId(uid, other))).get(),
    db.doc(paths.friendRequest(uid, friendRequestId(other, uid))).get(),
  ]);
  if (friend.exists) return "friend";
  if (sent.data()?.status === "pending") return "request_sent";
  if (received.data()?.status === "pending") return "request_received";
  return "none";
}

/**
 * Finds users by exact email (through Admin Auth, never exposing the address beyond a mask)
 * or by display-name prefix in publicProfiles.
 */
export async function searchUsers(db: Firestore, uid: string, query: string) {
  const matches = new Map<string, { displayName: string; maskedEmail: string | null }>();

  if (query.includes("@")) {
    try {
      const user = await getAuth().getUserByEmail(query.toLowerCase());
      const name = await displayNameOf(db, user.uid);
      if (name !== null) matches.set(user.uid, { displayName: name, maskedEmail: maskEmail(user.email ?? query) });
    } catch (error) {
      if ((error as { code?: string }).code !== "auth/user-not-found") throw error;
    }
  } else {
    const prefix = query.toLowerCase();
    const profiles = await db.collection("publicProfiles")
      .where("displayNameLower", ">=", prefix)
      .where("displayNameLower", "<", `${prefix}`)
      .limit(10)
      .get();
    profiles.docs.forEach((doc) => {
      matches.set(doc.id, { displayName: String(doc.data().displayName ?? DEFAULT_DISPLAY_NAME), maskedEmail: null });
    });
  }

  const results = await Promise.all(
    [...matches].map(async ([otherUid, match]) => ({
      uid: otherUid,
      displayName: match.displayName,
      maskedEmail: match.maskedEmail,
      relationship: await relationship(db, uid, otherUid),
    })),
  );
  return { results };
}
