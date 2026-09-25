/**
 * EcoStep route proxy (Cloudflare Worker).
 *
 * App ──(Firebase ID token, no ORS key)──▶ this Worker ──(ORS key from secret)──▶ OpenRouteService
 *
 * Contract (agreed with Jianing):
 *   POST /v1/route
 *   Authorization: Bearer <Firebase ID token>
 *   { "profile": "foot-walking" | "cycling-regular" | "driving-car",
 *     "start": { "latitude": number, "longitude": number },
 *     "end":   { "latitude": number, "longitude": number } }
 *
 *   200 -> OpenRouteService /v2/directions/{profile} JSON, unchanged
 *   4xx/5xx -> { "error": { "code": string, "message": string } }
 *
 * Security: the ORS key lives only in the Worker secret ORS_API_KEY.
 * Logs contain profile + status only (no key, no coordinates, no token).
 */
import { createRemoteJWKSet, jwtVerify } from "jose";

export interface Env {
  ORS_API_KEY: string; // secret: `npx wrangler secret put ORS_API_KEY`
  FIREBASE_PROJECT_ID: string; // plain var in wrangler config
}

interface Point {
  latitude: number;
  longitude: number;
}

const ALLOWED_PROFILES = new Set(["foot-walking", "cycling-regular", "driving-car"]);
const ORS_DIRECTIONS_URL = "https://api.openrouteservice.org/v2/directions/";
const ORS_TIMEOUT_MS = 10_000;

// Google's public keys for Firebase ID tokens; jose caches them between requests.
const FIREBASE_JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);

function errorResponse(status: number, code: string, message: string): Response {
  return Response.json({ error: { code, message } }, { status });
}

function isPoint(value: unknown): value is Point {
  if (typeof value !== "object" || value === null) return false;
  const { latitude, longitude } = value as Record<string, unknown>;
  return (
    typeof latitude === "number" &&
    typeof longitude === "number" &&
    Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= -90 &&
    latitude <= 90 &&
    longitude >= -180 &&
    longitude <= 180
  );
}

async function isValidFirebaseToken(request: Request, projectId: string): Promise<boolean> {
  const header = request.headers.get("Authorization") ?? "";
  if (!header.startsWith("Bearer ")) return false;
  const token = header.slice("Bearer ".length).trim();
  if (!token) return false;
  try {
    const { payload } = await jwtVerify(token, FIREBASE_JWKS, {
      issuer: `https://securetoken.google.com/${projectId}`,
      audience: projectId,
      algorithms: ["RS256"],
    });
    return typeof payload.sub === "string" && payload.sub.length > 0;
  } catch {
    return false;
  }
}

async function readOrsErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { error?: { message?: string } | string };
    if (typeof body.error === "string") return body.error;
    if (body.error?.message) return body.error.message;
  } catch {
    // fall through
  }
  return `Route service returned HTTP ${response.status}.`;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname !== "/v1/route") {
      return errorResponse(404, "NOT_FOUND", "Unknown endpoint.");
    }
    if (request.method !== "POST") {
      return errorResponse(405, "METHOD_NOT_ALLOWED", "Use POST.");
    }
    if (!env.ORS_API_KEY) {
      return errorResponse(500, "SERVER_MISCONFIGURED", "Route service key is not configured.");
    }

    // 1. Only signed-in EcoStep users may use the key.
    if (!(await isValidFirebaseToken(request, env.FIREBASE_PROJECT_ID))) {
      return errorResponse(401, "UNAUTHENTICATED", "Missing, invalid or expired Firebase ID token.");
    }

    // 2. Validate the request body.
    let body: unknown;
    try {
      body = await request.json();
    } catch {
      return errorResponse(400, "INVALID_REQUEST", "Request body must be JSON.");
    }
    const { profile, start, end } = (body ?? {}) as Record<string, unknown>;
    if (typeof profile !== "string" || !ALLOWED_PROFILES.has(profile)) {
      return errorResponse(
        400,
        "INVALID_PROFILE",
        "profile must be foot-walking, cycling-regular or driving-car.",
      );
    }
    if (!isPoint(start) || !isPoint(end)) {
      return errorResponse(
        400,
        "INVALID_COORDINATES",
        "start and end must be { latitude, longitude } with valid ranges.",
      );
    }

    // 3. Call OpenRouteService. ORS expects [longitude, latitude] order.
    let orsResponse: Response;
    try {
      orsResponse = await fetch(ORS_DIRECTIONS_URL + profile, {
        method: "POST",
        headers: {
          Authorization: env.ORS_API_KEY,
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({
          coordinates: [
            [start.longitude, start.latitude],
            [end.longitude, end.latitude],
          ],
        }),
        signal: AbortSignal.timeout(ORS_TIMEOUT_MS),
      });
    } catch (error) {
      const timedOut =
        error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError");
      console.log(JSON.stringify({ profile, status: timedOut ? "timeout" : "network_error" }));
      return timedOut
        ? errorResponse(504, "UPSTREAM_TIMEOUT", "Route service did not respond in time.")
        : errorResponse(502, "UPSTREAM_UNAVAILABLE", "Route service is unreachable.");
    }

    console.log(JSON.stringify({ profile, status: orsResponse.status }));

    // 4. Map the result.
    if (orsResponse.ok) {
      return new Response(orsResponse.body, {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }
    switch (orsResponse.status) {
      case 400:
      case 404: // ORS uses 404 when no route can be found between the points
        return errorResponse(
          orsResponse.status,
          "ROUTE_REQUEST_REJECTED",
          await readOrsErrorMessage(orsResponse),
        );
      case 429:
        return errorResponse(429, "RATE_LIMITED", "Route service quota exceeded. Try again later.");
      case 401:
      case 403: // our server key is wrong/expired — not the user's fault
        return errorResponse(502, "UPSTREAM_AUTH", "Route service rejected the server credentials.");
      default:
        return errorResponse(502, "UPSTREAM_ERROR", `Route service returned HTTP ${orsResponse.status}.`);
    }
  },
} satisfies ExportedHandler<Env>;
