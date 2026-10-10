# Carbon statistics and Firebase demo rewards

Validated on 2026-10-10. Architecture and Firestore field names are unchanged.

## Carbon units and scope

- All carbon amounts shown in journey review, route alternatives, mission estimates, profile,
  rankings and weekly reports use **g CO₂**, with one decimal and the current Android locale.
  Small positive values below 0.05 g display `<0.1 g` rather than zero.
- Firestore and algorithms already store/calculate grams. Existing UI models with `Kg` in
  their names remain compatible; conversion happens at display boundaries. Aggregate raw
  values first and round only for display.
- Journey savings: `max(0, distanceMeters / 1000 × (carFactor − selectedModeFactor))`.
  Current prototype factors are car 192, public transport 89, walk/cycle 0 g/km. A car
  journey saves 0 g against itself; unknown mode earns no estimated savings.
- For the screenshot's 368 m distance, walking/cycling saves an estimated **70.7 g** and
  public transport **37.9 g** against a same-distance car journey. These are estimates,
  not measured emissions or guaranteed awards.
- Alternative rows show **extra** estimated savings against the currently selected mode.
  They are excluded from totals. Selecting an alternative recalculates the journey estimate.
- Profile totals and rankings use backend aggregates. Profile shows locally confirmed
  journeys awaiting a server carbon result as a separate pending estimate; this estimate
  never replaces server totals or adds to the leaderboard. Confirmed journey counts can
  update before carbon calculation finishes, because those are separate backend steps.
- Weekly reports include accepted, completed mission occurrences from local Monday 00:00
  through now, using actual recorded savings. Ordinary journeys and route estimates are
  excluded. This differs from all-time profile totals. Community ranking weeks follow the
  existing backend UTC week convention.
- EcoPoints are awarded for eligible completed missions; converting display units does
  not change their calculation or reward prices.

## Rewards findings and setup

`origin/main` at `a27f305` already contains the reward catalogue repository, reward screens,
redemption/history UI, and the transactional `redeemReward` callable. The callable is
deployed and active in **comp90018-cb523**, the project in `app/google-services.json`.
The production `rewards` collection was empty; three offers have now been created:

| Document ID | Demo token | EcoPoints |
| --- | --- | ---: |
| ecostep-demo-starter | Green Starter | 10 |
| ecostep-demo-explorer | Green Explorer | 20 |
| ecostep-demo-champion | Green Champion | 50 |

Every title and description explicitly marks the offer as demo-only, with no monetary
value or merchant discount. Redemption spends actual app EcoPoints and creates a token
with a code in My Rewards using the existing backend. Codes expire after the existing
30-day validity period. No real merchant fulfillment or profile badge is promised.

The reusable catalogue is `functions/config/demo-rewards.json`. From `functions`:

```powershell
npm.cmd run rewards:inspect -- comp90018-cb523
npm.cmd run rewards:seed:demo -- --project comp90018-cb523
# Apply the reviewed dry run; existing offers, even inactive ones, are never overwritten.
npm.cmd run rewards:seed:demo -- --project comp90018-cb523 --apply
```

Scripts reuse Firebase CLI login locally; no credentials are stored in the repository.
Only missing demo catalogue documents are created. User balances, redemptions, rules,
schema and deployed function code are untouched by catalogue initialization.

## Validation

- Android debug APK build and Lint passed (0 errors, 11 existing warnings).
- After integrating current `origin/main` (`a27f305`), debug build and Lint passed again;
  the full compiled Android JVM suite on Linux/WSL passed **375 tests**. Upstream locale
  and evaluation changes were preserved; locale observation now lives in the shared
  gram formatter.
- Carbon precision/locale, pending vs counted totals, and Rewards ViewModel: **10 tests passed**.
- Catalogue script tests cover schema-compatible fields, affordable prices, rerun safety
  (including preserving disabled offers) and permission-error reporting. Backend unit and
  catalogue script suite: **16 passed**.
- Firebase emulator rules/integration suite: **36 passed**, including reading the same
  demo catalogue, redeeming with 21 points, ending with 11, retrying without a second debit,
  owner history reads, and refusing a subsequent 20-point offer.
- Production catalogue readback confirmed all three active documents; a second import
  reported all three existing and unchanged. No production user's points were spent.
- Default localhost 8080 was occupied, so validation used a temporary Firebase config
  with Firestore on 8180; committed emulator ports are unchanged.

Install the new debug APK for the g UI. Reopen the Rewards page (or restart the app if its
existing screen remains cached) to reload the newly created catalogue. Traffic detection
evaluation remains deferred as requested. No real-device end-to-end validation is claimed.
