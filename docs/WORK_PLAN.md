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
| Secure AI API communication | 27 Sep | To Do | API keys handled securely, requests/responses working |
| Offline data caching | 27 Sep | To Do | Data available offline, syncs when online |
| Cloud architecture documentation for report | 8 Oct | To Do | API architecture and security explanation |

### 4. Transport Detection and AI Planning — Rui Fang

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Transport mode detection from sensor data | 27 Sep | To Do | Mode identified with confidence score |
| Recurring journey detection | 27 Sep | To Do | Recurring trips identified and confirmed |
| EcoMission validation from AI response | 27 Sep | To Do | Missions validated, ranking working |
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

### 6. EcoMission, Carbon Calculation, EcoPoints, and Testing — Duo Lyu

| Task | Due | Status | Evidence |
|------|-----|--------|----------|
| Carbon calculation engine (emissions, savings, alternatives) | 27 Sep | To Do | Carbon values calculated and verified |
| EcoPoints system (points awarded, user balance tracked) | 27 Sep | To Do | Points calculated and stored |
| End-to-end testing (complete user journey) | 27 Sep | To Do | All features working together, log evidence |
| Bug fixes and reliability improvements | 4 Oct | To Do | Bugs tracked and fixed |
| Coordinate report structure and story | 8 Oct | To Do | Report outline and sections assigned |
| Review and compile final report | 10 Oct | To Do | Final report with all sections |

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
- **20 Sep:** Sensors and journey tracking done → other modules can test with real data
- **27 Sep:** Feature freeze → all features must be in code, even if not perfect
- **8 Oct:** Report structure due
- **10 Oct:** Report and video complete
- **12 Oct:** Final submission
