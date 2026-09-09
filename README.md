# EcoStep

EcoStep is an Android application designed to help users understand and reduce carbon emissions from everyday travel. The application uses GPS, accelerometer and gyroscope data to record journeys, [...]

## Overview

**Project:** COMP90018  
**Final Submission:** 12 October 2026  
**Feature Freeze:** 27 September 2026  
**Status:** Development starting 9 September

## Core User Flow

1. Record a journey using mobile sensors (GPS, accelerometer, gyroscope)
2. Estimate transport mode and distance
3. Confirm or correct the journey
4. Identify recurring journeys
5. Get weather and route information
6. Calculate carbon emissions and lower-carbon alternatives
7. AI API suggests EcoMission (personalised journey recommendation)
8. User accepts mission and completes journey
9. User receives EcoPoints and reviews progress

## MVP Features

- User registration and login (Firebase)
- GPS journey tracking
- Sensor data collection (accelerometer, gyroscope)
- Transport mode estimation (walking, cycling, public transport, car)
- Manual journey correction
- Recurring journey detection
- Weather and route information
- Public transport timetables
- Carbon emission calculations
- Lower-carbon alternatives with savings
- AI-generated EcoMission suggestions
- Mission acceptance and completion tracking
- EcoPoints system
- Journey history and weekly summary
- Privacy and notification settings

## Technical Approach

**Sensors:**
- GPS for location tracking
- Accelerometer for motion detection
- Gyroscope for orientation data

**External Services:**
- Maps (Google Maps or similar)
- Weather API
- Public transport API (e.g., PTV)
- Firebase (authentication, data storage)
- AI API for mission personalisation

**AI Role:**
The AI API personalises and ranks mission options based on carbon savings. It does not create routes, weather data, or carbon values — these come from other modules and external services.

## Team Responsibilities

| Member | Main Responsibility |
|--------|-----|
| Chi Hong Tam | Android architecture, Git workflow and integration |
| Zongcheng Jiang | Sensors, journey tracking, authentication and database |
| Jianing Xia | Weather, maps, routes and public transport APIs |
| Rui Fang | Real-time performance, algorithm evaluation and testing |
| Yu-Han Wang | Android UI, navigation and accessibility |
| Duo Lyu | Algorithms, carbon calculation, EcoMission, EcoPoints and AI personalisation |

## Timeline

| Phase | Dates | Work |
|-------|-------|------|
| Project setup | 9–13 Sep | Android project, architecture, shared interfaces |
| First prototype | 14–20 Sep | Sensors, journey tracking, basic UI, mock data |
| MVP development | 21–27 Sep | All features, integration, testing on device |
| Report and demo prep | 28 Sep–10 Oct | Bug fixes, testing, report writing, video recording |
| Final submission | 12 Oct | Submit code, report, and demonstration video |

After **27 September**, no new features will be added. Work focuses on:
- Bug fixing and reliability
- Testing on physical Android devices
- Integration between modules
- Report writing
- Demonstration preparation
- Video recording and editing

## How We Work

- One main owner for each module
- Create GitHub issues for all work
- Use feature branches (`git checkout -b feature/description`)
- Link pull requests to issues
- Code review before merging
- Clear commit messages
- Document dependencies when modules change

See [docs/WORK_PLAN.md](docs/WORK_PLAN.md) for tasks and [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) for module data flow.
