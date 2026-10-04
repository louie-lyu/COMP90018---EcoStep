import { setGlobalOptions } from "firebase-functions/v2";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { onCall } from "firebase-functions/v2/https";
import { db } from "./db";
import { searchUsers as searchUsersCore, sendFriendRequest as sendFriendRequestCore, transitionFriendRequest } from "./friends";
import { handleJourneyWrite } from "./journeys";
import { handleMissionResultWrite } from "./missions";
import { handleUserWrite } from "./profiles";
import { redeemReward as redeemRewardCore } from "./rewards";
import { requireAuth, requireBoolean, requireId, requireString } from "./validation";

setGlobalOptions({ region: "us-central1", maxInstances: 10 });

// Firestore triggers: trusted values, awards and aggregates are only ever written here.

export const onJourneyWritten = onDocumentWritten("users/{uid}/journeys/{journeyId}", async (event) =>
  handleJourneyWrite(
    db(),
    event.params.uid,
    event.params.journeyId,
    event.data?.before.data(),
    event.data?.after.data(),
    Date.now(),
  ));

export const onMissionResultWritten = onDocumentWritten("users/{uid}/missionResults/{resultId}", async (event) =>
  handleMissionResultWrite(db(), event.params.uid, event.params.resultId, event.data?.after.data()));

export const onUserWritten = onDocumentWritten("users/{uid}", async (event) =>
  handleUserWrite(db(), event.params.uid, event.data?.before.data(), event.data?.after.data(), Date.now()));

// Callable functions: the Firebase SDK verifies the caller's ID token before these run.

const callableOptions = { invoker: "public" as const };

export const redeemReward = onCall(callableOptions, async (request) => {
  const uid = requireAuth(request.auth);
  const rewardId = requireId(request.data, "rewardId");
  const requestId = requireId(request.data, "requestId", 8, 64);
  return { redemption: await redeemRewardCore(db(), uid, rewardId, requestId, Date.now()) };
});

export const sendFriendRequest = onCall(callableOptions, async (request) => {
  const uid = requireAuth(request.auth);
  return sendFriendRequestCore(db(), uid, requireId(request.data, "receiverUid"));
});

export const respondToFriendRequest = onCall(callableOptions, async (request) => {
  const uid = requireAuth(request.auth);
  const accept = requireBoolean(request.data, "accept");
  return transitionFriendRequest(db(), uid, requireId(request.data, "requestId", 3, 260), accept ? "accept" : "decline");
});

export const cancelFriendRequest = onCall(callableOptions, async (request) => {
  const uid = requireAuth(request.auth);
  return transitionFriendRequest(db(), uid, requireId(request.data, "requestId", 3, 260), "cancel");
});

export const searchUsers = onCall(callableOptions, async (request) => {
  const uid = requireAuth(request.auth);
  return searchUsersCore(db(), uid, requireString(request.data, "query", 2, 100));
});
