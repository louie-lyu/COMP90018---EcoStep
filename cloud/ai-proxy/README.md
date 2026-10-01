# EcoStep AI Proxy

This Cloudflare Worker provides a secure proxy between the EcoStep Android app
and the Gemini API.

## Architecture

EcoStep Android app → Firebase ID token → Cloudflare Worker → Gemini API

The Gemini API key is stored as the Cloudflare secret `GEMINI_API_KEY`.
The key must never be committed to GitHub or included in the Android app.

## Endpoints

### Health check

`GET /health`

Returns the service status and whether the Gemini API key is configured.

### AI request

`POST /v1/ai`

Requires a Firebase ID token:

`Authorization: Bearer <Firebase ID token>`

Supported tasks:

- `mission`: generates a personalised EcoMission suggestion
- `weekly`: generates a personalised weekly coaching message

## Security

The Worker verifies the Firebase ID token before forwarding a request to Gemini.
Requests without a valid token are rejected.

## Deployment

The Worker is currently deployed through the Cloudflare dashboard as:

`ecostep-ai-proxy`

The production URL is:

`https://ecostep-ai-proxy.louie415520.workers.dev`

## Verification

The following flow has been tested successfully:

1. Sign in with Firebase Authentication.
2. Obtain a Firebase ID token.
3. Send an authenticated request to the Worker.
4. Receive a structured weekly coaching response from Gemini.