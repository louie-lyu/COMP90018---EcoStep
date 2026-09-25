# EcoStep route proxy (Cloudflare Worker)

Keeps the OpenRouteService (ORS) API key off the Android app.
Owner: Zongcheng Jiang (proxy + secret). App-side client/mapping: Jianing Xia.

```
App --(Firebase ID token)--> Worker POST /v1/route --(ORS key from secret)--> ORS /v2/directions/{profile}
```

## Contract
- `POST /v1/route`, header `Authorization: Bearer <Firebase ID token>`
- Body: `{"profile":"foot-walking|cycling-regular|driving-car","start":{"latitude":..,"longitude":..},"end":{...}}`
- 200: ORS directions JSON unchanged (`routes[0].summary.distance/duration`)
- Errors: `{"error":{"code":"...","message":"..."}}`
  | Case | Status | code |
  |---|---|---|
  | bad JSON / profile / coordinates | 400 | INVALID_REQUEST / INVALID_PROFILE / INVALID_COORDINATES |
  | missing/invalid token | 401 | UNAUTHENTICATED |
  | ORS 400/404 (e.g. no route) | 400/404 | ROUTE_REQUEST_REJECTED |
  | ORS 429 | 429 | RATE_LIMITED |
  | ORS 401/403 (server key problem) | 502 | UPSTREAM_AUTH |
  | ORS timeout (10 s) | 504 | UPSTREAM_TIMEOUT |
  | other upstream failure | 502 | UPSTREAM_ERROR / UPSTREAM_UNAVAILABLE |

## Deploy
```powershell
cd cloud\route-proxy
npm install
npx wrangler login                    # opens browser, sign in to Cloudflare
npx wrangler secret put ORS_API_KEY   # paste the ORS key when prompted (key owner does this)
npx wrangler deploy                   # prints https://ecostep-route-proxy.<subdomain>.workers.dev
```

## Test
```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-route-proxy.ps1 -ProxyUrl https://ecostep-route-proxy.<subdomain>.workers.dev
npx wrangler tail   # live logs: profile + status only
```

## Security notes
- ORS key only in Cloudflare secret; never in Git, chat or the APK.
- Only signed-in users of Firebase project `comp90018-cb523` can call it (token signature, issuer, audience, expiry verified).
- Logs never contain the key, token or coordinates.
