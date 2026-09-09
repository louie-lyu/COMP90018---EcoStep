# EcoStep

EcoStep is an Android application designed to help users understand and reduce carbon emissions from everyday travel. The application uses GPS, accelerometer and gyroscope data to record journeys and estimate transport modes. It learns a limited number of recurring journeys and generates personalised low-carbon missions before usual journeys.

## Overview

**Project:** COMP90018  
**Due Date:** 12 October 2026  
**Current Status:** Planning and initial project setup

## Core User Flow

1. Record a journey using mobile sensors
2. Estimate transport mode and travel distance
3. Confirm or correct the journey
4. Identify or confirm a recurring journey
5. Retrieve weather, route and public transport information
6. Calculate lower-carbon alternatives
7. Send information to AI API for mission personalisation
8. Accept and complete the mission
9. Receive EcoPoints and review progress

## Planned MVP

- User registration and login
- GPS journey tracking
- Accelerometer and gyroscope collection
- Basic transport mode estimation
- Manual journey correction
- Confirmation of a recurring journey
- Weather and route information
- Basic public transport information
- Carbon-emission and carbon-saving calculations
- AI-assisted EcoMission generation
- Mission acceptance and completion tracking
- EcoPoints
- Firebase data storage
- Basic journey history or weekly summary
- Privacy and notification settings

## Technical Approach

### Sensors
- GPS for location tracking
- Accelerometer for motion detection
- Gyroscope for orientation data

### External Services
The application integrates with external services for enhanced functionality. See [DEPENDENCIES.md](docs/DEPENDENCIES.md) for details.

### AI Role and Limitations
The AI API personalises, ranks and explains valid mission options only. It does not invent routes, weather, public transport information or carbon values. Carbon calculations and mission verification remain controlled by application logic. The application has a non-AI fallback when the API fails.

## Team Responsibilities

| Member | Role |
|--------|------|
| Chi Hong Tam | Project architecture, code quality, Git workflow and integration |
| Zongcheng Jiang | GPS, accelerometer, gyroscope, permissions and journey tracking |
| Jianing Xia | Firebase, external APIs, offline data and secure AI API communication |
| Rui Fang | Transport detection, route analysis, recurring journey detection and AI result validation |
| Yu-Han Wang | Android UI, navigation, accessibility and UI states |
| Duo Lyu | Project organisation, EcoMission rules, carbon calculation, EcoPoints, AI prompt requirements, privacy and testing |

## Milestones

| Milestone | Date |
|-----------|------|
| Project setup | 13 September 2026 |
| First end-to-end prototype | 20 September 2026 |
| MVP feature completion | 27 September 2026 |
| Integration and testing | 4 October 2026 |
| Feature freeze | 8 October 2026 |
| Final submission | 12 October 2026 |

## Development Workflow

- Use feature branches for all work
- Create GitHub issues for all tasks
- Link pull requests to related issues
- Require code review before merging
- Maintain clear commit messages
- Document dependencies and changes to shared interfaces

See [WORK_PLAN.md](docs/WORK_PLAN.md) for task planning and [DEPENDENCIES.md](docs/DEPENDENCIES.md) for module architecture and dependencies.
