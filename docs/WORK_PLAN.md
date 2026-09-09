# Work Plan

## Working Rules

- Every task must have one main owner and one reviewer
- Tasks progress through states: Backlog → Ready → In Progress → Review → Done
- A task is not Done until it has evidence (code, documentation, test results) that can be demonstrated
- Dependencies must be identified and flagged if blocking progress
- Weekly checkins on Mondays and Fridays
- Use GitHub issues linked to pull requests for all work

## Status Definitions

| Status | Meaning |
|--------|---------|
| Backlog | Task identified but not yet prioritised or scheduled |
| Ready | Task is prioritised, dependencies are clear, and work can begin |
| In Progress | Task is being actively worked on |
| Review | Task is complete and awaiting code review or approval |
| Blocked | Task cannot progress due to external dependencies or decisions |
| Done | Task is complete with evidence and has been merged or delivered |

## Must Have Features

| ID | Task | Owner | Reviewer | Due Date | Status | Evidence |
|----|------|-------|----------|----------|--------|----------|
| T01 | Set up Android project, architecture and code structure | Chi Hong Tam | Jianing Xia | 13 Sep 2026 | Ready | Project structure and build configuration |
| T02 | Implement user registration and login (Firebase Auth) | Jianing Xia | Chi Hong Tam | 20 Sep 2026 | Backlog | Sign-up and login screens, Firebase integration |
| T03 | Implement GPS journey tracking | Zongcheng Jiang | Chi Hong Tam | 20 Sep 2026 | Backlog | GPS data collected, journeys recorded, location permissions working |
| T04 | Implement accelerometer and gyroscope data collection | Zongcheng Jiang | Chi Hong Tam | 20 Sep 2026 | Backlog | Sensor data collection and storage, permissions working |
| T05 | Implement basic transport mode estimation | Rui Fang | Chi Hong Tam | 27 Sep 2026 | Backlog | Transport mode identified from journey data with accuracy metrics |
| T06 | Implement manual journey correction UI | Yu-Han Wang | Chi Hong Tam | 20 Sep 2026 | Backlog | UI to edit transport mode and distance, data persisted |
| T07 | Implement recurring journey detection | Rui Fang | Chi Hong Tam | 27 Sep 2026 | Backlog | Algorithm to identify and confirm recurring journeys |
| T08 | Integrate weather and route data | Jianing Xia | Rui Fang | 27 Sep 2026 | Backlog | Weather and route APIs integrated, data cached |
| T09 | Implement basic public transport information | Jianing Xia | Rui Fang | 27 Sep 2026 | Backlog | PTV data integrated, journey alternatives available |
| T10 | Implement carbon calculation module | Duo Lyu | Chi Hong Tam | 27 Sep 2026 | Backlog | Carbon emissions and savings calculated and validated |
| T11 | Implement AI API communication and validation | Jianing Xia | Rui Fang | 27 Sep 2026 | Backlog | AI API secure communication, response validation, fallback working |
| T12 | Implement EcoMission generation and ranking | Rui Fang | Duo Lyu | 27 Sep 2026 | Backlog | Missions generated, ranked by preference and savings |
| T13 | Implement mission acceptance and completion tracking | Yu-Han Wang | Duo Lyu | 27 Sep 2026 | Backlog | Mission UI, completion flow, tracking logic |
| T14 | Implement EcoPoints award system | Duo Lyu | Chi Hong Tam | 27 Sep 2026 | Backlog | Points calculated and awarded, user balance tracked |
| T15 | Implement Firebase data storage | Jianing Xia | Chi Hong Tam | 27 Sep 2026 | Backlog | User data, journeys and missions stored and retrieved |
| T16 | Implement journey history and weekly summary | Yu-Han Wang | Duo Lyu | 27 Sep 2026 | Backlog | History screen, weekly summary view with statistics |
| T17 | Implement privacy and notification settings | Yu-Han Wang | Duo Lyu | 27 Sep 2026 | Backlog | Settings UI, permission handling, notification dispatch |
| T18 | Integration and end-to-end testing | Chi Hong Tam | Duo Lyu | 4 Oct 2026 | Backlog | All modules integrated, end-to-end flow tested, bugs fixed |
| T19 | Feature freeze and final testing | Duo Lyu | Chi Hong Tam | 8 Oct 2026 | Backlog | No new features, critical bugs only, regression testing |

## Should Have Features

| ID | Task | Owner | Reviewer | Due Date | Status | Evidence |
|----|------|-------|----------|----------|--------|----------|
| T20 | Improve transport mode detection accuracy | Rui Fang | Chi Hong Tam | 4 Oct 2026 | Backlog | Accuracy metrics and test results |
| T21 | Implement offline data synchronisation | Jianing Xia | Chi Hong Tam | 4 Oct 2026 | Backlog | Data syncs when connection restored, no data loss |
| T22 | Improve AI prompt and mission quality | Duo Lyu | Rui Fang | 4 Oct 2026 | Backlog | Improved mission quality and user satisfaction |

## Could Have Features

| ID | Task | Owner | Reviewer | Due Date | Status | Evidence |
|----|------|-------|----------|----------|--------|----------|
| T23 | Regional leaderboard | Jianing Xia | Chi Hong Tam | Post-MVP | Backlog | Leaderboard UI and backend logic |
| T24 | Friend challenges | Yu-Han Wang | Chi Hong Tam | Post-MVP | Backlog | Challenge creation and tracking |
| T25 | Local reward marketplace | Duo Lyu | Chi Hong Tam | Post-MVP | Backlog | Marketplace UI and reward redemption |
| T26 | Advanced machine learning models | Rui Fang | Chi Hong Tam | Post-MVP | Backlog | Model training and deployment |
| T27 | Complex animations | Yu-Han Wang | Chi Hong Tam | Post-MVP | Backlog | Animation library integrated and applied |

## Team Integration Task

| ID | Task | Owner | Reviewer | Due Date | Status | Evidence |
|----|------|-------|----------|----------|--------|----------|
| T28 | First end-to-end prototype (all modules integrated) | Chi Hong Tam | Duo Lyu | 20 Sep 2026 | Ready | Prototype demonstrated, all team modules working together |

## Dependencies Reference

Refer to [DEPENDENCIES.md](DEPENDENCIES.md) for detailed information on module dependencies, shared interfaces and critical blockers.
