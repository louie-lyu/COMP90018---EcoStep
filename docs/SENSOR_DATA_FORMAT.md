# Sensor feature data format

Owner: Zongcheng (collection/storage); consumer: Duo (classification). This is the original v1 interface proposal, pending Duo and Chi Hong Tam's agreement at the time of writing. It extends JourneySummary and its Firestore mapper without requiring UI or external API changes.

## Purpose and compatibility

JourneySummary originally had no accelerometer or gyroscope fields, although the sensor rubric requires GPS, accelerometer and gyroscope use. Speed alone cannot reliably distinguish travel modes. Add a nullable feature object with a default value so existing constructors, mock data and stored journeys still work. Upload aggregates only; never raw sensor streams. Magnitudes avoid dependence on device orientation. Use SI units and a feature version for future calculation changes.

## Kotlin contract

```kotlin
package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/**
 * Per-journey sensor aggregates, computed by Sensors (Zongcheng) when recording ends,
 * for Algorithm (Duo) and TransportClassifier.
 * Contains statistics only, without raw GPS traces or sensor samples.
 */
@Serializable
data class SensorFeatures(
    /** Feature calculation version; currently 1. */
    val featureVersion: Int = 1,

    // ---- GPS ----
    /** distanceMeters / duration in seconds, in m/s. */
    val averageSpeedMps: Double,
    /** GPS speed 95th percentile in m/s, less sensitive to spikes than the maximum. */
    val p95SpeedMps: Double,
    /** Maximum filtered speed in m/s. */
    val maxSpeedMps: Double,
    /** Fraction of GPS speeds below 0.5 m/s, in 0.0-1.0. */
    val stopRatio: Double,
    /** Mean GPS accuracy in meters; smaller is better. */
    val averageGpsAccuracyMeters: Double,
    /** Number of valid GPS samples. */
    val gpsSampleCount: Int,

    // ---- Accelerometer ----
    /** Mean absolute dynamic acceleration: abs(magnitude(a) - g), in m/s squared. */
    val accelMagnitudeMean: Double,
    /** Population standard deviation of acceleration magnitude, in m/s squared. */
    val accelMagnitudeStd: Double,
    /** Accelerometer sample count. */
    val accelSampleCount: Int,

    // ---- Gyroscope ----
    /** Mean angular velocity magnitude, in rad/s. */
    val gyroMagnitudeMean: Double,
    /** Population standard deviation of angular velocity magnitude, in rad/s. */
    val gyroMagnitudeStd: Double,
    /** Gyroscope sample count; 0 when the device has no gyroscope. */
    val gyroSampleCount: Int,
)
```

```kotlin
@Serializable
data class JourneySummary(
    val journeyId: String,
    val userId: String,
    val startLocation: GeoPoint,
    val endLocation: GeoPoint,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val distanceMeters: Double,
    val transportMode: TransportMode,
    /** Sensor features; null for manually created or legacy journeys. */
    val sensorFeatures: SensorFeatures? = null,
)
```

The proposal replaces acceleration/gyroscope variance with mean and standard deviation, records a count for each sensor separately, and adds stop ratio, GPS accuracy and featureVersion. Version 1 contains 12 statistics plus the version field.

## Calculation rules

- averageSpeedMps = filtered distance / journey duration in seconds; use zero for zero duration.
- p95SpeedMps comes from sorted valid GPS speeds; maxSpeedMps is their maximum.
- stopRatio = count(speed < 0.5 m/s) / valid GPS count.
- averageGpsAccuracyMeters is the average reported accuracy of accepted samples.
- Reject GPS fixes worse than 30 m accuracy and movements implying more than 60 m/s. Prefer GPS-provided speed, otherwise distance / elapsed time.
- accelMagnitudeMean averages `abs(sqrt(x*x + y*y + z*z) - 9.80665)`. accelMagnitudeStd is the population standard deviation of the raw acceleration magnitude.
- Gyroscope mean and population standard deviation use `sqrt(x*x + y*y + z*z)` in rad/s.
- Use Welford's online algorithm for sensor means/standard deviations. A GPS speed list is small enough to retain: roughly 1,800 samples/hour at two-second intervals.
- With no samples, mean and standard deviation are zero; consumers must check the corresponding sample count before using those values.

## JSON and Firestore

```json
{
  "journeyId": "5f1c2b1e-8a0e-4a5c-9b8e-2f7d1f0c1234",
  "userId": "PglpHhCR04ZcnkMA3LlrWoj8sEs2",
  "startLocation": { "latitude": -37.7963, "longitude": 144.9614 },
  "endLocation":   { "latitude": -37.8183, "longitude": 144.9671 },
  "startTimeMillis": 1790300000000,
  "endTimeMillis":   1790301200000,
  "distanceMeters": 2600.0,
  "transportMode": "UNKNOWN",
  "sensorFeatures": {
    "featureVersion": 1,
    "averageSpeedMps": 2.17,
    "p95SpeedMps": 5.9,
    "maxSpeedMps": 7.2,
    "stopRatio": 0.18,
    "averageGpsAccuracyMeters": 8.4,
    "gpsSampleCount": 598,
    "accelMagnitudeMean": 0.62,
    "accelMagnitudeStd": 0.81,
    "accelSampleCount": 59210,
    "gyroMagnitudeMean": 0.35,
    "gyroMagnitudeStd": 0.29,
    "gyroSampleCount": 59188
  }
}
```

Store sensorFeatures as a nested map at `users/{uid}/journeys/{journeyId}`, with keys matching Kotlin property names. Convert Firestore integer values using `(value as Number).toInt()`. Missing sensorFeatures, including legacy or manually created `test_1` documents, reads as null.

## Initial classification reference ranges

These rough ranges are starting points only. Rui must calibrate actual thresholds with measured physical-device data; do not present this table as measured evaluation evidence.

| Mode | Average speed (m/s) | p95 speed (m/s) | Acceleration std | Stop ratio | Typical pattern |
|---|---|---|---|---|---|
| Walking | 0.8-1.8 | < 2.5 | High | Low | Rhythmic footstep impacts |
| Cycling | 2.5-7 | < 10 | Medium | Low-medium | Continuous movement; angular motion on turns |
| Public transport | 3-12 | Up to 20 | Low | High | Frequent stops |
| Car | 5-20 | Can exceed 30 | Low | Medium | Faster, relatively smooth motion |

When sensorFeatures is null, fall back to distance and duration. Ignore gyroscope values when gyroSampleCount is zero: some devices and emulators lack that sensor. Consider lowering confidence when gpsSampleCount < 5 or averageGpsAccuracyMeters > 30. Keep TransportResult.confidence on its existing 0-100 scale.

## Ownership and confirmation

Zongcheng adds SensorFeatures, the nullable JourneySummary field and Firestore mapping; the shared model change needs Chi Hong Tam's approval. Duo consumes the features in TransportClassifier. Rui can use them in accuracy evaluation. Existing UI and external API code need no changes because defaults preserve compatibility. Updating mock_journeys.json is optional; one feature-bearing example may be useful.

Open an issue titled `[interface-change] Add SensorFeatures to JourneySummary`, label it `interface-change`, and link this document. Ask Duo to confirm field coverage, Chi Hong Tam to confirm the shared model change, and notify Rui. The original plan allowed proceeding with v1 if no reply arrived by noon on 26 September; defaulted fields keep later changes inexpensive.

### Copyable issue template

```markdown
## Background
TransportClassifier needs sensor features, but JourneySummary has no accelerometer or gyroscope fields. The Sensors rubric requires GPS, accelerometer and gyroscope use.

## Proposal
- Add data/model/SensorFeatures.kt: v1, 12 statistics described in docs/SENSOR_DATA_FORMAT.md.
- Add val sensorFeatures: SensorFeatures? = null to JourneySummary.

## Compatibility
Existing callers and Firestore records remain valid. Upload only aggregate features, without raw traces or samples.

## Confirmation
- [ ] @Duo: Are the fields sufficient for classification? What else is needed?
- [ ] @Chi Hong Tam: Approve the shared model change.
- [ ] @Rui: Evaluation can use these features.

Original deadline: 26 September at noon; proceed with v1 if there is no reply.
```
