# Work Plan

## Overview

Each team member owns one main module. Work is tracked through GitHub issues linked to pull requests. Everyone participates in the final report and demonstration video.

## Task Status

- **To Do:** Not yet started
- **In Progress:** Currently being worked on
- **Review:** Finished and waiting for code review
- **Done:** Merged and working

## Module Tasks

### 1. Android Architecture and Integration — Chi Hong Tam

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Set up Android project, build configuration, shared interfaces | 13 Sep | To Do | Project structure, Gradle config, interface files |
| First end-to-end integration test (connect all modules) | 20 Sep | To Do | Prototype running on device, log output |
| Integration testing and bug fixes | 27 Sep | To Do | Test results, bugs fixed |
| Provide architecture and integration diagrams for report | 8 Oct | To Do | Diagram files in docs/ |

### 2. Sensors, Journey Tracking, Authentication and Database — Zongcheng Jiang

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| GPS tracking and location permissions | 20 Sep | To Do | GPS data logged, permissions working |
| Accelerometer and gyroscope data collection | 20 Sep | To Do | Sensor data logged locally |
| Journey summary creation | 20 Sep | To Do | Journey object with location, time, distance |
| Set up Firebase authentication and Firestore database | 13 Sep | To Do | Firebase config, auth and database working |
| Firestore schema and security rules | 13 Sep | To Do | Database structure defined, security rules implemented |
| Local-to-cloud journey summary storage | 27 Sep | To Do | Journeys synced to Firestore when online |
| Basic offline data handling | 27 Sep | To Do | App works without internet, syncs when online |
| Testing on physical devices | 27 Sep | To Do | Test results on multiple devices |
| Documentation and sensor testing evidence for report | 8 Oct | To Do | Test data and implementation details |

### 3. Weather, Maps and Public Transport APIs — Jianing Xia

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Weather API integration and caching | 27 Sep | To Do | Weather data retrieved and cached |
| Route/maps API integration and caching | 27 Sep | To Do | Route data retrieved and cached |
| Public transport API integration | 27 Sep | To Do | PT timetables retrieved and cached |
| External API response handling and error management | 27 Sep | To Do | APIs handle errors gracefully |
| Offline data caching | 27 Sep | To Do | Data available offline, syncs when online |
| Cloud architecture documentation for report | 8 Oct | To Do | API architecture and security explanation |

### 4. Algorithm Evaluation and Real-time Performance — Rui Fang

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Real-time performance evaluation framework | 27 Sep | To Do | Performance metrics collected |
| Testing and accuracy metrics for transport detection | 27 Sep | To Do | Test results showing accuracy |
| Recurring journey detection validation | 27 Sep | To Do | Recurring detection accuracy measured |
| Testing on physical devices | 27 Sep | To Do | All features tested on Android devices |
| Prepare test data for transport-mode evaluation | 8 Oct | To Do | Curated test datasets |
| Comparing predicted and corrected transport modes | 8 Oct | To Do | Analysis of algorithm accuracy |
| Measuring response time and latency | 8 Oct | To Do | Performance benchmarks |
| Supporting sensor-to-algorithm integration | 27 Sep | To Do | Sensor data flows to algorithm correctly |
| Evaluation results for the report | 8 Oct | To Do | Test results, metrics, and analysis |

### 5. Android UI and User Flow — Yu-Han Wang

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Journey recording and review screen | 20 Sep | To Do | UI shows journey, allows correction |
| Mission display and acceptance flow | 27 Sep | To Do | Missions shown, user can accept/reject |
| Journey history and summary screens | 27 Sep | To Do | History and weekly stats visible |
| Settings and permissions UI | 27 Sep | To Do | Privacy settings and notification control working |
| Physical device testing and fixes | 27 Sep | To Do | All screens working on real devices |
| UI screenshots and user-flow material for report | 8 Oct | To Do | Screenshots and flow diagrams |

### 6. Algorithms, Carbon Calculation, EcoPoints and AI Personalisation — Duo Lyu

**Owner responsibilities:**
- Sensor-data processing requirements and feature extraction
- Transport mode classification logic
- Recurring-route detection logic
- Carbon emissions and savings calculations
- Candidate low-carbon route scoring
- EcoPoints reward system and bonuses
- AI prompt construction from verified data
- AI request and response models
- AI response parsing and validation
- Mission validation and fallback logic
- Non-AI fallback mission generation
- AI personalisation logic
- Weekly coach and journey insights
- Algorithm and AI documentation for the report

**Ten tasks with separate GitHub issues:**

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Carbon Calculator: transport factors, emissions, alternatives, savings | 14 Sep | To Do | Unit tests passing, calculation validated |
| EcoPoints Calculator: points from savings, bonuses, incomplete handling, max limits | 17 Sep | To Do | Unit tests passing, points calculated correctly |
| Transport Classification: sensor-data processing, feature extraction, classification logic | 20 Sep | To Do | Transport mode identified with confidence score |
| Recurring-Route Detection: logic and validation | 20 Sep | To Do | Recurring trips identified and confirmed |
| AI EcoMission Builder: MissionContext, prompt construction, structured response | 20 Sep | To Do | Prompt-building tests passing, example prompts in docs |
| AI Mission Validator: validate responses, reject modifications, generate fallback template | 23 Sep | To Do | Validator tests passing, fallback working |
| AI Privacy and Data-Minimisation: secure AI integration requirements | 27 Sep | To Do | Data minimisation implemented, privacy rules enforced |
| Weekly Coach: weekly summary model, personalised insights, non-AI fallback | 27 Sep | To Do | Weekly summary generated, tests passing |
| Unit Tests: tests for carbon, points, mission validation and algorithm logic | 27 Sep | To Do | All modules tested, high coverage |
| Documentation: carbon model, mission rules, AI prompt strategy, algorithm explanation | 8 Oct | To Do | Explanation of algorithms and design decisions |

**Boundaries with other team members:**
- Zongcheng Jiang owns Firebase storage and sensor data collection. Duo uses authenticated sensor data.
- Jianing Xia owns secure external API communication. Duo constructs mission context and receives verified data (weather, routes, public transport).
- Rui Fang owns performance testing and evaluation. Duo provides algorithm implementation for testing.
- Yu-Han Wang owns the general UI system. Duo may implement the Weekly Insight screen after agreeing on the UI structure with Yu-Han.
- Chi Hong Tam owns architecture decisions and final integration testing.

### 7. Shared Integration Task — All Members

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| End-to-end prototype: all modules connected and working | 20 Sep | To Do | Demo running on device, all modules providing data |
| Physical device testing and fixes | 27 Sep | To Do | All features tested on Android device(s) |
| Report writing (each member writes their section) | 4–8 Oct | To Do | Sections submitted to Duo Lyu |
| Demonstration video: record, edit, and verify | 9–10 Oct | To Do | Video recorded and edited |
| Final review and submission | 11–12 Oct | To Do | Code, report, and video submitted |

## Report and Demonstration Responsibilities

**Duo Lyu (Coordinator):**
- Coordinate report structure and sections
- Compile final report
- Verify all sections are included
- Ensure video quality and completeness

**Each Member (Their Module Section):**
- Write technical explanation of your module
- Include implementation decisions and challenges
- Provide evidence (code snippets, test results)
- Explain how your module connects to others

**Specific Additional Contributions:**
- Chi Hong Tam: Architecture diagrams, integration approach
- Zongcheng Jiang: Sensor and database implementation, testing on devices
- Jianing Xia: External API architecture and security
- Rui Fang: Performance evaluation methodology and results
- Yu-Han Wang: UI screenshots, user-flow diagrams
- Duo Lyu: Algorithm design, carbon model, EcoPoints system, AI prompt strategy, mission validation

**Video:**
- All members: Help record demonstration
- Participate as team discusses features (TBD roles)
- Showcase one journey from start to finish
- Show working features on physical device
- Explain how AI suggests lower-carbon options

## Feature Freeze

**27 September 2026:** No new features after this date.

Work after feature freeze (28 Sep–12 Oct) focuses on:
- Bug fixes (critical only)
- Testing on physical devices
- Integration between modules
- Code cleanup and documentation
- Report writing
- Demonstration video preparation
- Final submission checks

Any bugs or issues discovered in testing should be logged but may not require fixes if they don't affect core functionality. Priority goes to report and video quality.

## Dependencies

See [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) for how modules depend on each other and how to use mock data when blocked.

**Key dates:**
- **13 Sep:** Project setup and Firebase configuration complete → all modules can start work
- **14 Sep:** Carbon Calculator ready (Duo's first deliverable)
- **17 Sep:** EcoPoints Calculator ready
- **20 Sep:** Sensors, journey tracking, transport classification, AI prompt builder, first prototype → other modules can test with real data
- **23 Sep:** Mission Validator ready → AI responses validated
- **27 Sep:** Feature freeze → all features in code, even if not perfect
- **8 Oct:** Report structure due
- **10 Oct:** Report and video complete
- **12 Oct:** Final submission
