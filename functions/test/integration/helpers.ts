import { deleteApp, FirebaseApp, initializeApp } from "firebase/app";
import {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import { connectFirestoreEmulator, Firestore, getFirestore } from "firebase/firestore";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { App, getApps, initializeApp as initializeAdminApp } from "firebase-admin/app";
import { Firestore as AdminFirestore, getFirestore as getAdminFirestore } from "firebase-admin/firestore";
import { DEMO_PROJECT_ID, requireEmulator } from "../emulatorGuard";

const { host, port } = requireEmulator();
if (!process.env.FIREBASE_AUTH_EMULATOR_HOST) {
  throw new Error("FIREBASE_AUTH_EMULATOR_HOST is not set: run via `npm test`.");
}

let adminApp: App | undefined;

/** Admin access to the emulator only (env vars from emulators:exec point the SDK there). */
export function adminDb(): AdminFirestore {
  adminApp ??= getApps().find((app) => app.name === "integration-admin") ??
    initializeAdminApp({ projectId: DEMO_PROJECT_ID }, "integration-admin");
  return getAdminFirestore(adminApp);
}

export interface TestUser {
  uid: string;
  email: string;
  password: string;
  app: FirebaseApp;
  db: Firestore;
  call: <T = any>(name: string, data: Record<string, unknown>) => Promise<T>;
  close: () => Promise<void>;
}

let counter = 0;

/** A separate client app per user, like separate devices. */
export function clientApp(name: string): FirebaseApp {
  const app = initializeApp({ projectId: DEMO_PROJECT_ID, apiKey: "demo-api-key", appId: "demo-app" }, name);
  connectAuthEmulator(getAuth(app), `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
  connectFirestoreEmulator(getFirestore(app), host, port);
  connectFunctionsEmulator(getFunctions(app, "us-central1"), "127.0.0.1", 5001);
  return app;
}

export async function signUp(label: string, displayName?: string): Promise<TestUser> {
  counter += 1;
  const email = `${label}-${Date.now()}-${counter}@example.com`;
  const password = "test-password-123";
  const app = clientApp(`${label}-${counter}-${Date.now()}`);
  const credential = await createUserWithEmailAndPassword(getAuth(app), email, password);
  const user: TestUser = {
    uid: credential.user.uid,
    email,
    password,
    app,
    db: getFirestore(app),
    call: async (name, data) => (await httpsCallable(getFunctions(app, "us-central1"), name)(data)).data as any,
    close: async () => {
      await signOut(getAuth(app));
      await deleteApp(app);
    },
  };
  if (displayName) {
    await adminDb().doc(`users/${user.uid}`).set({
      schemaVersion: 1,
      displayName,
      preferences: { communityRankingEnabled: true },
    });
  }
  return user;
}

export { getAuth, signInWithEmailAndPassword, signOut };

/** Polls until [read] returns a truthy value; Firestore triggers run asynchronously. */
export async function waitFor<T>(read: () => Promise<T | null | undefined | false>, label: string, timeoutMs = 20_000): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const value = await read();
    if (value) return value;
    if (Date.now() > deadline) throw new Error(`Timed out waiting for ${label}`);
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
}

export function journeyData(uid: string, journeyId: string, overrides: Record<string, unknown> = {}) {
  const end = Date.now() - 60_000;
  return {
    schemaVersion: 2,
    journeyId,
    userId: uid,
    startLocation: { latitude: -37.8, longitude: 144.96 },
    endLocation: { latitude: -37.81, longitude: 144.97 },
    startTimeMillis: end - 1_200_000,
    endTimeMillis: end,
    distanceMeters: 2000,
    transportMode: "CYCLING",
    detectedTransportMode: "CYCLING",
    confirmationStatus: "pending",
    linkedMissionId: null,
    routePolyline: null,
    ...overrides,
  };
}

/** Asserts that a promise rejects with a Firebase error code such as "functions/failed-precondition". */
export async function expectCode(promise: Promise<unknown>, code: string): Promise<void> {
  try {
    await promise;
  } catch (error) {
    const actual = (error as { code?: string }).code;
    if (actual !== code) throw new Error(`Expected ${code} but got ${actual}: ${(error as Error).message}`);
    return;
  }
  throw new Error(`Expected ${code} but the call succeeded.`);
}
