import { HttpsError } from "firebase-functions/v2/https";

const ID_PATTERN = /^[A-Za-z0-9_-]+$/;

/** Reads a document-ID-safe string field from callable input or throws invalid-argument. */
export function requireId(data: unknown, field: string, minLength = 1, maxLength = 128): string {
  const value = (data as Record<string, unknown> | null | undefined)?.[field];
  if (typeof value !== "string" || value.length < minLength || value.length > maxLength || !ID_PATTERN.test(value)) {
    throw new HttpsError("invalid-argument", `Field '${field}' is missing or invalid.`);
  }
  return value;
}

export function requireBoolean(data: unknown, field: string): boolean {
  const value = (data as Record<string, unknown> | null | undefined)?.[field];
  if (typeof value !== "boolean") {
    throw new HttpsError("invalid-argument", `Field '${field}' is missing or invalid.`);
  }
  return value;
}

export function requireString(data: unknown, field: string, minLength: number, maxLength: number): string {
  const value = (data as Record<string, unknown> | null | undefined)?.[field];
  if (typeof value !== "string") {
    throw new HttpsError("invalid-argument", `Field '${field}' is missing or invalid.`);
  }
  const trimmed = value.trim();
  if (trimmed.length < minLength || trimmed.length > maxLength) {
    throw new HttpsError("invalid-argument", `Field '${field}' must be ${minLength}-${maxLength} characters.`);
  }
  return trimmed;
}

/** The verified Firebase Auth UID of the caller. */
export function requireAuth(auth: { uid: string } | undefined): string {
  if (!auth?.uid) throw new HttpsError("unauthenticated", "Please sign in again.");
  return auth.uid;
}
