# 🏫 Automated Teacher Absence System

> **Intelligent scheduling system that detects teacher absences and automatically assigns the most suitable substitute — eliminating timetable conflicts and manual coordination delays.**

---

## Overview

Manual proxy allocation in schools is slow, error-prone, and often causes cascading scheduling conflicts. This system automates the entire pipeline — from absence detection to timetable update — using a multi-criteria weighted scoring algorithm to always assign the *best available* substitute teacher.

---

## Problem Statement

When a teacher is absent, school administrators must:

1. Manually identify which periods are affected
2. Find an available substitute who knows the subject
3. Verify the substitute has no conflicting classes
4. Update the timetable across all affected classes
5. Notify the substitute and students

This process typically takes 20–40 minutes and still results in conflicts. This system does it in milliseconds.

---

## Features

| Feature | Description |
|---|---|
| **Automatic Absence Detection** | Integrates with attendance systems; flags unmarked teachers after a configurable grace period |
| **Smart Substitute Scoring** | Multi-criteria weighted algorithm ranks every available teacher for each vacant period |
| **Conflict Prevention** | Hard-constraint checks block double-booking, classroom conflicts, and workload breaches before assignment |
| **Fairness Distribution** | Proxy history tracking ensures the same teachers aren't always burdened |
| **Real-time Timetable Update** | Timetable state is updated atomically when a proxy is confirmed |
| **Manual Override** | Administrators can force-assign a specific teacher with full conflict disclosure |
| **Full Audit Trail** | Every absence, assignment, and conflict is logged with timestamps |
| **Notification Ready** | Pluggable notifier interface for SMS, email, or push alerts |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Main / Controller                     │
└───────────────┬─────────────────────────┬───────────────────┘
                │                         │
    ┌───────────▼──────────┐   ┌──────────▼──────────────┐
    │  AbsenceDetection    │   │  TimetableService        │
    │  Service             │   │  (load, query, display)  │
    └───────────┬──────────┘   └──────────┬───────────────┘
                │                         │
    ┌───────────▼─────────────────────────▼───────────────┐
    │              ProxyAssignmentService                  │
    │   (orchestrates scoring + conflict validation)       │
    └──────────┬──────────────────────┬────────────────────┘
               │                      │
   ┌───────────▼──────────┐  ┌────────▼──────────────┐
   │ ProxyScoringAlgorithm│  │  ConflictDetector      │
   │ (multi-criteria rank)│  │  (hard constraint check│
   └──────────────────────┘  └────────────────────────┘
                │
   ┌────────────▼──────────────────────────────────────┐
   │                     Models                         │
   │  Teacher · Period · Timetable · AbsenceRecord      │
   │  ProxyAssignment                                   │
   └────────────────────────────────────────────────────┘
```

---

## Project Structure

```
TeacherAbsenceSystem/
├── src/
│   ├── main/java/com/school/
│   │   ├── Main.java                          # Entry point & demo
│   │   ├── model/
│   │   │   ├── Teacher.java                   # Teacher entity + workload tracking
│   │   │   ├── Period.java                    # Timetable slot with overlap detection
│   │   │   ├── Timetable.java                 # Central schedule registry (two indexes)
│   │   │   ├── AbsenceRecord.java             # Absence event with status lifecycle
│   │   │   └── ProxyAssignment.java           # Assignment with confirm/revoke logic
│   │   ├── algorithm/
│   │   │   ├── ProxyScoringAlgorithm.java     # ★ Weighted multi-criteria scorer
│   │   │   └── ConflictDetector.java          # ★ Full conflict detection engine
│   │   ├── service/
│   │   │   ├── AbsenceDetectionService.java   # Detection, recording, event dispatch
│   │   │   ├── ProxyAssignmentService.java    # Orchestration + manual override
│   │   │   └── TimetableService.java          # Period CRUD + schedule printing
│   │   ├── notification/
│   │   │   └── NotificationService.java       # Console stub (swap for SMS/email)
│   │   └── exception/
│   │       └── SchedulingConflictException.java
│   └── test/java/com/school/
│       └── SystemTests.java                   # 10 self-contained test cases
└── README.md
```

---

## Core Algorithm

### `ProxyScoringAlgorithm.java`

Each candidate teacher is scored against six weighted criteria:

```
Score = (0.30 × subject_match)
      + (0.20 × grade_qualification)
      + (0.25 × workload_balance)
      + (0.10 × room_proximity)
      + (0.10 × proxy_history_fairness)
      + (0.05 × seniority_adjustment)
```

| Criterion | Weight | Notes |
|---|---|---|
| Subject match | 30% | Hard filter; exact = 1.0, related family = 0.5 |
| Grade qualification | 20% | Exact = 1.0, adjacent grade = 0.6 |
| Workload balance | 25% | Non-linear: `(1 − load)^1.5` — rewards lightly loaded teachers more strongly |
| Room proximity | 10% | Same building prefix → bonus (extensible to real room distances) |
| Proxy history | 10% | Teachers with fewer recent proxies are preferred (fairness) |
| Seniority | 5% | Mild penalty for very senior teachers — avoids over-burdening them |

**Hard constraints** (return score = −1, candidate excluded):
- Teacher is on leave or at max daily periods
- Teacher is not qualified for the subject AND grade
- Teacher has a time-overlapping period on that day
- Teacher is the original assigned teacher for the period

Candidates are ranked highest-score-first. The system iterates the ranked list and attempts assignment, skipping any that fail conflict validation.

---

## Conflict Detection

### `ConflictDetector.java`

Five conflict types are detected:

| Type | Severity | Description |
|---|---|---|
| `TEACHER_DOUBLE_BOOKED` | CRITICAL | Same teacher in two overlapping periods |
| `CLASSROOM_CONFLICT` | CRITICAL | Same room assigned to two different classes |
| `SUBJECT_MISMATCH` | CRITICAL | Proxy not qualified for the period's subject/grade |
| `WORKLOAD_BREACH` | WARNING | Assignment would exceed teacher's max daily periods |
| `GRADE_OVERLOAD` | WARNING | Grade has overlapping periods (data integrity issue) |

`CRITICAL` conflicts throw `SchedulingConflictException` and prevent assignment.
`WARNING` conflicts are logged and attached as notes to the assignment — they do not block it.

The full-timetable audit (`auditFullTimetable()`) runs O(n²) pair checks per day; acceptable for typical school sizes (< 500 periods/day). Can be optimised with interval trees for larger deployments.

---

## Getting Started

### Prerequisites

- Java 17+ (JDK)
- No external dependencies — pure standard library

### Compile

```bash
# From project root
find src -name "*.java" > sources.txt
javac --release 17 -d out @sources.txt
```

### Run Demo

```bash
java -cp out com.school.Main
```

### Run Tests

```bash
java -cp out com.school.SystemTests
```

---

## Running the Demo

The demo (`Main.java`) simulates a full school day:

1. **Builds** a registry of 5 teachers with different subject/grade qualifications
2. **Loads** a sample Monday timetable (8 periods across multiple grades)
3. **Prints** the clean schedule
4. **Reports** Alice Sharma absent (sick leave)
5. **Runs** the proxy assignment engine across her 3 periods
6. **Prints** notifications for each assignment
7. **Prints** the updated timetable showing proxy teachers
8. **Displays** the fairness snapshot (proxy count per teacher)
9. **Resolves** the absence record

Sample output excerpt:

```
[NOTIFICATION] 🔔 Absence Detected
  Teacher : Alice Sharma
  Date    : 2025-09-01
  Type    : SICK_LEAVE

[NOTIFICATION] ✅ Proxy Assigned
  Proxy   : Bob Patel
  Period  : 1 (08:00–08:45) on 2025-09-01
  Subject : Mathematics | Grade: Grade-10A | Room: B1-101
  Score   : 0.87

╔══════════════════════════════════════╗
║      ASSIGNMENT REPORT               ║
╠══════════════════════════════════════╣
║  Absent : Alice Sharma               ║
║  Date   : 2025-09-01                 ║
║  Status : RESOLVED                   ║
║  Covered: 3  / Uncovered: 0          ║
╚══════════════════════════════════════╝
```

---

## Running Tests

`SystemTests.java` contains 10 self-contained test cases covering:

- Teacher availability and workload capping
- Period overlap detection
- Conflict detector — double-booking
- Scoring algorithm — ineligibility (wrong subject)
- Scoring algorithm — ranking (lighter workload wins)
- Proxy assignment — successful end-to-end
- Proxy assignment — no candidate available
- Workload increment/decrement correctness
- Absence detection + duplicate guard
- Manual override with conflict notes

```
  PASS: Available initially
  PASS: Unavailable when at max load
  PASS: Overlapping periods detected
  PASS: Adjacent periods do not overlap
  ...
═══ Results: 10 passed, 0 failed ═══
```

---

## Workflow Diagram

```
ABSENCE REPORTED / AUTO-DETECTED
           │
           ▼
  Mark teacher ON_LEAVE
  Create AbsenceRecord
  Fire AbsenceListener events
           │
           ▼
  Fetch affected periods
  from Timetable (sorted)
           │
     ┌─────▼──────┐
     │ For each   │
     │  period    │◄──────────────────────┐
     └─────┬──────┘                       │
           │                              │
           ▼                              │
  Get available candidates                │
  (exclude absent teacher,                │
   on-leave, overloaded)                  │
           │                              │
           ▼                              │
  Score & rank via                        │
  ProxyScoringAlgorithm                   │
           │                              │
           ▼                              │
  Take top candidate                      │
           │                              │
           ▼                              │
  ConflictDetector.validateOrThrow()      │
       /        \                         │
  CRITICAL    WARNINGS only              │
  conflicts   ─────────────────────►  Confirm assignment
      │                                  │
      │                           Update Period status
      ▼                           Increment proxy workload
  Try next                        Add to AbsenceRecord
  candidate                             │
      │                                 └──────────────────┐
  No more candidates                                       │
      │                                              Next period ──┘
      ▼
  Mark period VACANT
  Fire notification
           │
           ▼
  Update AbsenceRecord status
  (RESOLVED / PARTIALLY / UNRESOLVED)
           │
           ▼
  Send AssignmentReport
```

---

## Configuration

Key constants to adjust per school:

| Location | Constant | Default | Purpose |
|---|---|---|---|
| `Teacher` constructor | `maxPeriodsPerDay` | 6 | Max periods before teacher is considered overloaded |
| `ProxyScoringAlgorithm` | `W_*` weights | See above | Adjust importance of each scoring criterion |
| `ConflictDetector` | Severity levels | CRITICAL/WARNING | Which conflicts block vs. warn |

To integrate a real attendance feed, replace the manual call to `reportAbsence()` with a scheduled task polling your HR/attendance API, feeding into `AbsenceDetectionService.detectUnmarkedAbsences()`.

---

## Future Plans

- **Web Interface** — REST API (Spring Boot) + React dashboard for live schedule view
- **Attendance Integration** — Direct feed from biometric/RFID attendance systems
- **SMS / Email Notifications** — Swap `NotificationService` stub with Twilio / JavaMail
- **Recurring Absence Patterns** — ML model to predict likely absences and pre-warm substitutes
- **Multi-School Support** — Tenant-aware scheduling across a school district
- **Database Persistence** — JPA/Hibernate layer replacing in-memory maps

---

*Built with plain Java 17 — no frameworks, no external libraries.*
