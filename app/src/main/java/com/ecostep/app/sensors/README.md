# sensors

Owner: **Zongcheng Jiang** (Sensors, Journey Tracking, Auth & Database module).

GPS, accelerometer and gyroscope data collection, and journey tracking logic live here.
Implement [`JourneyRepository`](../data/repository/JourneyRepository.kt) against this package's
output, then wire it into `journeyRepository` in
[`core/di/AppContainer.kt`](../core/di/AppContainer.kt) (search for `TODO(Zongcheng)`).

Due dates and evidence: see [docs/WORK_PLAN.md](../../../../../../../../docs/WORK_PLAN.md).

Delete this file once real source files exist in this package.
