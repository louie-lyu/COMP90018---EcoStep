/**
 * Every emulator test imports this first. It refuses to run unless the Firestore emulator is
 * configured, so a misconfigured run can never touch a real Firebase project.
 */
export const DEMO_PROJECT_ID = "demo-ecostep";
export const RULES_PROJECT_ID = "demo-ecostep-rules";

export function requireEmulator(): { host: string; port: number } {
  const hostAndPort = process.env.FIRESTORE_EMULATOR_HOST;
  if (!hostAndPort) {
    throw new Error("FIRESTORE_EMULATOR_HOST is not set: run these tests via `npm test` (firebase emulators:exec).");
  }
  const [host, port] = hostAndPort.split(":");
  return { host: host!, port: Number(port) };
}
