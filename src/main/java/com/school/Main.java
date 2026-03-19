package com.school;

import com.school.model.*;
import com.school.notification.NotificationService;
import com.school.service.*;

import java.time.LocalTime;
import java.util.*;

/**
 * End-to-end demonstration of the Automated Teacher Absence System.
 *
 * Run: javac -d out src/**\/*.java && java -cp out com.school.Main
 */
public class Main {

    public static void main(String[] args) {

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("   AUTOMATED TEACHER ABSENCE SYSTEM — Demo");
        System.out.println("═══════════════════════════════════════════════════════════\n");

        // ── 1. Build teacher registry ─────────────────────────────────────
        Map<String, Teacher> teachers = buildTeacherRegistry();

        // ── 2. Set up timetable ───────────────────────────────────────────
        Timetable timetable   = new Timetable();
        TimetableService tms  = new TimetableService(timetable);
        String today          = "2025-09-01";

        loadSampleTimetable(tms, teachers, today);

        // Print before-absence schedule
        tms.printDailySchedule(today);
        tms.printConflictReport();

        // ── 3. Report an absence ──────────────────────────────────────────
        NotificationService notifier = new NotificationService();
        AbsenceDetectionService absenceService =
                new AbsenceDetectionService(teachers);

        absenceService.addListener(new AbsenceDetectionService.AbsenceListener() {
            @Override
            public void onAbsenceDetected(AbsenceRecord r) { notifier.notifyAbsenceDetected(r); }
            @Override
            public void onAbsenceResolved(AbsenceRecord r) {
                System.out.println("[NOTIFICATION] ✔ Absence resolved for: " + r.getAbsentTeacher().getName());
            }
        });

        AbsenceRecord absence = absenceService.reportAbsence(
                "T001", today,
                AbsenceRecord.AbsenceType.SICK_LEAVE,
                "Reported fever — will not attend today");

        // ── 4. Run proxy assignment ───────────────────────────────────────
        Map<String, Integer> proxyHistory = new HashMap<>();
        proxyHistory.put("T002", 3);
        proxyHistory.put("T003", 1);
        proxyHistory.put("T004", 2);
        proxyHistory.put("T005", 0);

        Map<String, Integer> seniority = new HashMap<>();
        seniority.put("T001", 12); seniority.put("T002", 8);
        seniority.put("T003", 5);  seniority.put("T004", 3);
        seniority.put("T005", 1);

        ProxyAssignmentService assignService = new ProxyAssignmentService(
                timetable, teachers, proxyHistory, seniority);

        ProxyAssignmentService.AssignmentReport report =
                assignService.processAbsence(absence);

        // ── 5. Notify outcomes ────────────────────────────────────────────
        report.getAssignments().forEach(notifier::notifyProxyAssigned);
        report.getFailures().forEach(f -> notifier.notifyVacantPeriod(f.period()));
        notifier.sendAssignmentReport(report);

        // ── 6. Print updated schedule ─────────────────────────────────────
        tms.printDailySchedule(today);
        tms.printConflictReport();

        // ── 7. Print proxy workload fairness snapshot ─────────────────────
        System.out.println("── Proxy Count Fairness Snapshot ──");
        assignService.getProxyCountSnapshot()
                .forEach((id, count) -> {
                    Teacher t = teachers.get(id);
                    String name = t != null ? t.getName() : id;
                    System.out.printf("  %-20s → %d proxies%n", name, count);
                });

        // ── 8. Resolve absence ────────────────────────────────────────────
        absenceService.resolveAbsence(absence.getId());

        System.out.println("\nDemo complete.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static Map<String, Teacher> buildTeacherRegistry() {
        Map<String, Teacher> map = new LinkedHashMap<>();

        map.put("T001", new Teacher("T001", "Alice Sharma",
                Set.of("Mathematics", "Statistics"),
                Set.of("Grade-10A", "Grade-10B", "Grade-11A"), 6));

        map.put("T002", new Teacher("T002", "Bob Patel",
                Set.of("Mathematics", "Physics"),
                Set.of("Grade-10A", "Grade-10B", "Grade-11A", "Grade-11B"), 6));

        map.put("T003", new Teacher("T003", "Clara Nair",
                Set.of("Physics", "Chemistry"),
                Set.of("Grade-10A", "Grade-10B", "Grade-11A"), 6));

        map.put("T004", new Teacher("T004", "Dev Mehta",
                Set.of("Mathematics", "Computer Science"),
                Set.of("Grade-10B", "Grade-11A", "Grade-11B"), 6));

        map.put("T005", new Teacher("T005", "Eva Joshi",
                Set.of("Mathematics", "Economics"),
                Set.of("Grade-10A", "Grade-10B", "Grade-11A", "Grade-11B"), 6));

        return map;
    }

    private static void loadSampleTimetable(TimetableService tms,
                                            Map<String, Teacher> teachers,
                                            String date) {
        Teacher alice = teachers.get("T001");
        Teacher bob   = teachers.get("T002");
        Teacher clara = teachers.get("T003");
        Teacher dev   = teachers.get("T004");
        Teacher eva   = teachers.get("T005");

        // Alice's periods (she will be absent)
        tms.createPeriod(1, LocalTime.of(8,0), LocalTime.of(8,45), date,
                "Mathematics", "Grade-10A", "B1-101", alice);
        tms.createPeriod(3, LocalTime.of(9,45), LocalTime.of(10,30), date,
                "Mathematics", "Grade-10B", "B1-102", alice);
        tms.createPeriod(5, LocalTime.of(11,30), LocalTime.of(12,15), date,
                "Statistics", "Grade-11A", "B2-201", alice);

        // Other teachers' normal periods
        tms.createPeriod(1, LocalTime.of(8,0), LocalTime.of(8,45), date,
                "Physics", "Grade-10B", "B1-103", clara);
        tms.createPeriod(2, LocalTime.of(8,45), LocalTime.of(9,30), date,
                "Mathematics", "Grade-11B", "B2-202", bob);
        tms.createPeriod(3, LocalTime.of(9,45), LocalTime.of(10,30), date,
                "Computer Science", "Grade-11A", "B3-301", dev);
        tms.createPeriod(4, LocalTime.of(10,30), LocalTime.of(11,15), date,
                "Economics", "Grade-11B", "B2-203", eva);
        tms.createPeriod(6, LocalTime.of(12,15), LocalTime.of(13,0), date,
                "Physics", "Grade-11A", "B1-103", clara);
    }
}
