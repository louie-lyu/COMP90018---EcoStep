# Dependencies and Architecture

## Module Flow

The EcoStep application follows this high-level data flow:

1. **Sensor Collection** (Zongcheng Jiang) → Raw GPS, accelerometer and gyroscope data
2. **Journey Tracking** (Zongcheng Jiang) → JourneySummary
3. **Transport Detection** (Rui Fang) → TransportResult
4. **Route and Weather** (Jianing Xia) → MissionContext (with route and weather data)
5. **Recurring Detection** (Rui Fang) → Confirmed recurring journey identifier
6. **Carbon Calculation** (Duo Lyu) → CarbonResult
7. **AI Personalisation** (Jianing Xia) → Ranked options from AI API
8. **Mission Validation** (Rui Fang) → Validated EcoMission
9. **Mission Display** (Yu-Han Wang) → User accepts mission
10. **Completion Tracking** (Yu-Han Wang) → MissionResult
11. **Points Award** (Duo Lyu) → Final EcoPoints

## Shared Interfaces

All modules must use these shared interfaces to communicate:

### JourneySummary
- Journey ID
- Start and end location (lat/lon only)
- Start and end time
- Raw sensor data reference (stored locally)
- Distance (estimated or confirmed)
- Transport mode (estimated, pending confirmation)

### TransportResult
- Transport mode (walking, cycling, public transport, car, unknown)
- Confidence score
- Alternative modes considered
- Data quality indicators

### CarbonResult
- Estimated carbon emissions (grams CO2)
- Valid lower-carbon alternatives with estimated emissions
- Carbon savings for each alternative (grams CO2)
- Calculation method and confidence

### MissionContext
- Journey summary
- Transport result
- Carbon result
- Route information (distance, duration, stops)
- Weather data (temperature, conditions, wind)
- Public transport options and timetables
- User preferences (journey acceptance history)

### EcoMission
- Mission ID
- Recommended transport mode
- Estimated carbon saving (grams CO2)
- Explanation for recommendation
- Acceptance deadline
- Confidence or reliability indicator

### MissionResult
- Mission ID
- Completion status (completed, abandoned, other)
- Actual transport mode used
- Actual carbon saving (grams CO2)
- EcoPoints awarded
- Timestamp of completion

## Module Dependencies

| Provider | Output | Consumer | Required Date | Status |
|----------|--------|----------|----------------|--------|
| Zongcheng Jiang (Sensor/Journey) | JourneySummary | Rui Fang (Transport Detection) | 20 Sep 2026 | Ready |
| Rui Fang (Transport Detection) | TransportResult | Duo Lyu (Carbon Calculation) | 27 Sep 2026 | Blocked |
| Duo Lyu (Carbon Calculation) | CarbonResult | Jianing Xia (AI Communication) | 27 Sep 2026 | Blocked |
| Jianing Xia (Weather/Route) | Route and weather data | Rui Fang (Mission Validation) | 27 Sep 2026 | Blocked |
| Rui Fang (Recurring Detection) | Confirmed journey ID | Jianing Xia (Context Assembly) | 27 Sep 2026 | Blocked |
| Jianing Xia (AI Communication) | AI response | Rui Fang (Mission Validation) | 27 Sep 2026 | Blocked |
| Rui Fang (Mission Validation) | EcoMission | Yu-Han Wang (UI Display) | 27 Sep 2026 | Blocked |
| Yu-Han Wang (Mission Completion) | MissionResult | Duo Lyu (Points Award) | 4 Oct 2026 | Blocked |

## Mock Data

While real modules are incomplete, teams should:

- Create mock data matching shared interface contracts
- Use mock data in tests and UI development
- Document mock data location and format
- Replace mock data with real data when modules are ready

This allows parallel development and avoids blocking on incomplete dependencies.

## Interface Changes

Any changes to shared interfaces must be:
1. Proposed in an issue with tags `interface-change` and affected team members
2. Discussed and approved by all affected consumers
3. Implemented with a deprecation period if backward compatibility is needed
4. Documented in the interface definition with version and date

Affected members are notified automatically and must review before changes are merged.

## Critical Dependencies

The following dependencies are critical and cannot be delayed without blocking the MVP:

1. **JourneySummary ready by 20 Sep** → All transport detection and routing work depends on this
2. **TransportResult stable by 20 Sep** → Carbon calculation cannot begin without transport mode
3. **CarbonResult and weather/route data ready by 27 Sep** → AI personalisation requires complete context
4. **AI API communication stable by 27 Sep** → Mission validation and UI display depend on reliable API
5. **Mission validation complete by 27 Sep** → Completion tracking and points award cannot proceed without validated missions

## Offline Data

The application should cache the following locally to support offline journeys:

- User preferences and settings
- Recent route data
- Weather forecasts
- Public transport timetables (periodic updates)
- Journey history and EcoPoints

Raw GPS and sensor data should remain local unless explicitly uploaded as part of a journey summary.

## Security and Privacy

- AI API keys must not be stored directly in Android source code
- API keys must be fetched securely at runtime (e.g., from Firebase Remote Config or backend service)
- User location data should be minimised and not retained longer than necessary
- Journey summaries should contain only location endpoints and timestamps, not raw GPS traces
- Sensitive data in Firebase must use appropriate security rules
