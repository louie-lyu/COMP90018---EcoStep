# androidTest

Owner: **Rui Fang** (Performance Evaluation module) for accuracy/latency instrumented tests;
**Chi Hong Tam** (Architecture module) for end-to-end integration tests later on.

On-device tests run here (Espresso / Compose UI test dependencies are already declared in
[`app/build.gradle.kts`](../../../../../../build.gradle.kts) under `androidTestImplementation`).
Rui: test against the real [`TransportClassifier`](../../../../../main/java/com/ecostep/app/algorithm/TransportClassifier.kt)
implementation once Duo has built it, to measure classification accuracy and latency.

Delete this file once real test files exist in this package.
