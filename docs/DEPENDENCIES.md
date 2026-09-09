# Module Dependencies

This document explains how data flows between the six modules and what mock data to use if a module is delayed.

## Data Flow

```
Sensors & Journeys (Zongcheng)
    ↓ JourneySummary
Transport Detection (Rui)
    ↓ Transport mode + confidence
Carbon Calculation (Duo)
    ↓ Carbon emissions & alternatives
    
Journeys (Zongcheng) + Weather/Route (Jianing) + Carbon (Duo)
    ↓ MissionContext
AI Planning (Rui + Jianing)
    ↓ EcoMission (ranked options)
    
UI (Yu-Han) receives EcoMission and shows to user
    ↓ MissionResult (user's choice + actual savings)
EcoPoints (Duo)
    ↓ Points awarded
```

## Shared Data Objects

These are the main objects that modules exchange:

**JourneySummary** (from Sensors)
- Journey ID
- Start location (lat/lon)
- End location (lat/lon)
- Start and end time
- Distance (estimated or user-corrected)
- Transport mode (estimated, pending confirmation)

**TransportResult** (from Transport Detection)
- Transport mode (walking, cycling, public transport, car, unknown)
- Confidence score (0–100%)
- Alternatives considered

**CarbonResult** (from Carbon Calculation)
- Carbon emissions (grams CO2)
- Lower-carbon alternatives with savings (grams CO2)

**MissionContext** (assembled from Journey + Transport + Carbon + Weather + Route)
- Journey summary
- Transport result
- Carbon result
- Route data (distance, duration)
- Weather data (temperature, conditions)
- Public transport timetables
- User's journey history

**EcoMission** (from AI API)
- Mission ID
- Recommended transport mode
- Estimated carbon saving (grams CO2)
- Explanation
- Confidence/reliability

**MissionResult** (from UI)
- Mission ID
- Whether user accepted/completed it
- Actual transport mode used
- Actual carbon saving (grams CO2)
- Timestamp

## Who Provides, Who Needs

| Data | From | Needed By | Due |
|------|------|-----------|-----|
| JourneySummary | Zongcheng | Rui, Jianing | 20 Sep |
| TransportResult | Rui | Duo, Jianing | 27 Sep |
| CarbonResult | Duo | Jianing, Rui | 27 Sep |
| Route & Weather | Jianing | Rui, UI | 27 Sep |
| AI Response | Jianing | Rui, UI | 27 Sep |
| EcoMission | Rui | Yu-Han | 27 Sep |
| MissionResult | Yu-Han | Duo | 4 Oct |

## Using Mock Data

If a module isn't ready, use mock data so other modules can continue:

**If Zongcheng isn't ready by 20 Sep:**
Create mock JourneySummary objects:
```
JourneyID: "mock_1"
StartLoc: -37.8, 144.9 (Melbourne city)
EndLoc: -37.81, 145.0
Distance: 1.5 km
TransportMode: "walking" (or cycling, car, etc.)
```
Save in: `app/src/test/assets/mock_journeys.json`

**If Rui isn't ready by 27 Sep:**
Mock TransportResult and recurring detection:
```
TransportMode: "car"
Confidence: 0.85
IsRecurring: true
```

**If Duo isn't ready by 27 Sep:**
Mock carbon calculations:
```
Emissions: 250 grams CO2
Alternatives: [
  {mode: "public_transport", savings: 180},
  {mode: "bike", savings: 250}
]
```

**If Jianing isn't ready by 27 Sep:**
Mock weather, routes, and AI responses:
```
Temperature: 22°C
Weather: "sunny"
RouteOptions: [{distance: 2km, duration: 15min}]
AIResponse: EcoMission with savings
```

Mock data files should be clearly named with "mock_" prefix and documented in the code.

## Critical Dependencies

These milestones block other work:

1. **Project setup by 13 Sep** → Everyone can start coding
2. **JourneySummary by 20 Sep** → Transport and route work can use real data
3. **Transport + Carbon + Weather/Route by 27 Sep** → AI can generate missions
4. **All features by 27 Sep** → Testing phase begins (no new features after this)

If a dependency is delayed, use mock data and continue. Document in the GitHub issue what's being mocked and when it will be replaced.

## Interface Changes

If you need to change a shared object (like adding a field to JourneySummary):

1. Create a GitHub issue with `interface-change` label
2. Mention affected team members
3. Discuss and get approval
4. Update the interface definition here
5. Notify consumers to update their code

## Offline Support

These items should be cached locally so the app works without internet:

- User settings and preferences
- Recent route data
- Weather forecasts (from last sync)
- Public transport timetables (updated periodically)
- Journey history and EcoPoints

Raw GPS and sensor data stays on device only.

## Security Notes

- API keys are not stored in source code
- Keys fetched at runtime (e.g., Firebase Remote Config)
- User location data minimised (endpoints only, not full GPS traces)
- Sensitive data in Firebase has security rules
- AI API communication is encrypted
