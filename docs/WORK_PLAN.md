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

### 2. Sensors and Journey Tracking — Zongcheng Jiang

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| GPS tracking and location permissions | 20 Sep | To Do | GPS data logged, permissions working |
| Accelerometer and gyroscope data collection | 20 Sep | To Do | Sensor data logged locally |
| Journey summary creation | 20 Sep | To Do | Journey object with location, time, distance |
| Testing on physical devices | 27 Sep | To Do | Test results on multiple devices |
| Documentation and sensor testing evidence for report | 8 Oct | To Do | Test data and implementation details |

### 3. Firebase and External APIs — Jianing Xia

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Set up Firebase (authentication and database) | 13 Sep | To Do | Firebase config, auth working |
| Weather API integration and caching | 27 Sep | To Do | Weather data retrieved and cached |
| Route/maps API integration and caching | 27 Sep | To Do | Route data retrieved and cached |
| Public transport API integration | 27 Sep | To Do | PT timetables retrieved and cached |
| Secure AI API communication (no prompt construction) | 27 Sep | To Do | API keys handled securely, requests/responses working |
| Offline data caching | 27 Sep | To Do | Data available offline, syncs when online |
| Cloud architecture documentation for report | 8 Oct | To Do | API architecture and security explanation |

### 4. Transport Detection and Valid Routes — Rui Fang

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Transport mode detection from sensor data | 27 Sep | To Do | Mode identified with confidence score |
| Generate valid candidate route/transport combinations | 27 Sep | To Do | List of feasible alternatives from journey data |
| Recurring journey detection | 27 Sep | To Do | Recurring trips identified and confirmed |
| Testing and accuracy metrics | 27 Sep | To Do | Test results showing accuracy |
| Algorithm and AI explanation for report | 8 Oct | To Do | How detection and AI work explained |

### 5. Android UI and User Flow — Yu-Han Wang

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Journey recording and review screen | 20 Sep | To Do | UI shows journey, allows correction |
| Mission display and acceptance flow | 27 Sep | To Do | Missions shown, user can accept/reject |
| Journey history and summary screens | 27 Sep | To Do | History and weekly stats visible |
| Settings and permissions UI | 27 Sep | To Do | Privacy settings and notification control working |
| Physical device testing and fixes | 27 Sep | To Do | All screens working on real devices |
| UI screenshots and user-flow material for report | 8 Oct | To Do | Screenshots and flow diagrams |

### 6. EcoMission, Carbon Calculation and AI Personalisation — Duo Lyu

**Owner responsibilities:**
- Carbon emissions and savings calculations
- EcoPoints reward system and bonuses
- AI prompt construction from verified data
- Mission validation and fallback logic
- Weekly coach and journey insights

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Carbon Calculator module with tests | 14 Sep | To Do | Unit tests passing, calculation validated |
| EcoPoints Calculator with bonus logic and tests | 17 Sep | To Do | Unit tests passing, points calculated correctly |
| AI EcoMission Builder (prompt construction, no network) | 20 Sep | To Do | Prompt-building tests passing, example prompts in docs |
| AI Mission Validator and Fallback (validates responses, generates template missions) | 23 Sep | To Do | Validator tests passing, fallback working |
| Weekly Coach module (optional: personalised weekly insights) | 27 Sep | To Do | Weekly summary generated, tests passing |
| Testing and reliability improvements | 27 Sep | To Do | All modules tested on device |
| Documentation: carbon model, mission rules, AI prompt strategy | 8 Oct | To Do | Explanation of algorithms and design decisions |

**Boundaries with other team members:**
- **Jianing Xia** owns secure API communication and Firebase data storage. Duo constructs the request, Jianing sends it.
- **Rui Fang** owns transport detection and generates valid candidate routes. Duo uses these as input to missions.
- **Yu-Han Wang** owns the general UI system. Duo may implement the Weekly Insight screen after agreeing on the UI structure with Yu-Han.
- **Chi Hong Tam** owns architecture decisions and final integration testing.

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
- **Chi Hong Tam:** Architecture diagrams, integration approach
- **Zongcheng Jiang:** Sensor implementation, testing on devices
- **Jianing Xia:** Cloud services and API architecture
- **Rui Fang:** Transport detection algorithm and AI integration
- **Yu-Han Wang:** UI screenshots, user-flow diagrams
- **Duo Lyu:** Carbon model, EcoPoints system, AI prompt strategy, mission validation

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
- **13 Sep:** Project setup complete → all modules can start work
- **14 Sep:** Carbon Calculator ready → Duo's first deliverable
- **17 Sep:** EcoPoints Calculator ready → points system working
- **20 Sep:** Sensors, AI prompt builder, first prototype → other modules can test with real data
- **23 Sep:** Mission Validator ready → AI responses validated
- **27 Sep:** Feature freeze → all features in code, even if not perfect
- **8 Oct:** Report structure due
- **10 Oct:** Report and video complete
- **12 Oct:** Final submission
