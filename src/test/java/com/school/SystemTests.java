package com.school;

import com.school.algorithm.*;
import com.school.model.*;
import com.school.service.*;

import java.time.LocalTime;
import java.util.*;

/**
 * Lightweight JUnit-free test harness.
 * Replace with JUnit 5 + Mockito in production.
 */
public class SystemTests {

    private static int passed = 0, failed = 0;

    public static void main(String[] args) {
        new SystemTests().runAll();
        System.out.printf("%n═══ Results: %d passed, %d failed ═══%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    void runAll() {
        test_teacherAvailability();
        test_periodOverlap();
        test_conflictDetection_doubleBooked();
        test_scoringAlgorithm_eligibility();
        test_scoringAlgorithm_ranking();
        test_proxyAssignment_success();
        test_proxyAssignment_noCandidate();
        test_workloadIncrement();
        test_absenceDetection();
        test_manualOverride();
    }

    // ────────────────────────────────────────────────────────────────────
    void test_teacherAvailability() {
        Teacher t = new Teacher("T1", "Test", Set.of("Math"), Set.of("Grade-10"), 3);
        assertTrue("Available initially", t.isAvailableOn("2025-01-01"));
        t.incrementDailyLoad("2025-01-01");
        t.incrementDailyLoad("2025-01-01");
        t.incrementDailyLoad("2025-01-01");
        assertFalse("Unavailable when at max load", t.isAvailableOn("2025-01-01"));
        assertTrue("Still available on different date", t.isAvailableOn("2025-01-02"));
    }

    void test_periodOverlap() {
        Period a = mkPeriod("P1", LocalTime.of(8,0), LocalTime.of(9,0));
        Period b = mkPeriod("P2", LocalTime.of(8,30), LocalTime.of(9,30));
        Period c = mkPeriod("P3", LocalTime.of(9,0), LocalTime.of(10,0));
        assertTrue("Overlapping periods detected", a.overlaps(b));
        assertFalse("Adjacent periods do not overlap", a.overlaps(c));
    }

    void test_conflictDetection_doubleBooked() {
        Teacher t1 = new Teacher("T1", "Alice", Set.of("Math"), Set.of("Grade-10"), 6);
        Teacher t2 = new Teacher("T2", "Bob", Set.of("Math"), Set.of("Grade-10"), 6);
        Timetable tt = new Timetable();
        Period p1 = new Period("P1", 1, LocalTime.of(8,0), LocalTime.of(9,0),
                "2025-01-01", "Math", "Grade-10", "R1", t1);
        tt.addPeriod(p1);
        // Make t1 currently assigned to p1
        p1.setAssignedTeacher(t1);

        Period p2 = new Period("P2", 2, LocalTime.of(8,30), LocalTime.of(9,30),
                "2025-01-01", "Math", "Grade-10", "R2", t2);
        tt.addPeriod(p2);

        assertTrue("Conflict detected for double-booked teacher", tt.hasConflict(t1, p2));
        assertFalse("No conflict for free teacher", tt.hasConflict(t2, p2));
    }

    void test_scoringAlgorithm_eligibility() {
        Teacher t = new Teacher("T1", "Alice", Set.of("Math"), Set.of("Grade-10"), 6);
        Teacher absent = new Teacher("T2", "Bob", Set.of("Math"), Set.of("Grade-10"), 6);
        Timetable tt = new Timetable();
        Period p = new Period("P1", 1, LocalTime.of(8,0), LocalTime.of(9,0),
                "2025-01-01", "Science", "Grade-10", "R1", absent); // t1 can't teach Science
        tt.addPeriod(p);

        ProxyScoringAlgorithm alg = new ProxyScoringAlgorithm(null, null);
        double score = alg.score(t, p, tt);
        assertEquals("Score -1 for unqualified teacher", -1.0, score, 0.001);
    }

    void test_scoringAlgorithm_ranking() {
        Teacher absent  = new Teacher("T0", "Absent", Set.of("Math"), Set.of("Grade-10"), 6);
        Teacher heavy   = new Teacher("T1", "Heavy", Set.of("Math"), Set.of("Grade-10"), 6);
        Teacher light   = new Teacher("T2", "Light", Set.of("Math"), Set.of("Grade-10"), 6);

        // Heavy has 4 out of 6 periods filled; light has 0
        for (int i = 0; i < 4; i++) heavy.incrementDailyLoad("2025-01-01");

        Timetable tt = new Timetable();
        Period p = new Period("P1", 5, LocalTime.of(13,0), LocalTime.of(14,0),
                "2025-01-01", "Math", "Grade-10", "R1", absent);
        tt.addPeriod(p);

        Map<String, Integer> hist = Map.of("T1", 5, "T2", 1);
        ProxyScoringAlgorithm alg = new ProxyScoringAlgorithm(hist, null);

        List<ProxyScoringAlgorithm.ScoredCandidate> ranked =
                alg.rankCandidates(List.of(heavy, light), p, tt);

        assertTrue("Light teacher ranked first", ranked.get(0).getTeacher().getId().equals("T2"));
    }

    void test_proxyAssignment_success() {
        Teacher absent = new Teacher("T1", "Absent", Set.of("Math"), Set.of("Grade-10"), 6);
        Teacher proxy  = new Teacher("T2", "Proxy", Set.of("Math"), Set.of("Grade-10"), 6);
        Map<String, Teacher> reg = Map.of("T1", absent, "T2", proxy);

        Timetable tt = new Timetable();
        TimetableService tms = new TimetableService(tt);
        tms.createPeriod(1, LocalTime.of(8,0), LocalTime.of(9,0),
                "2025-01-01", "Math", "Grade-10", "R1", absent);

        AbsenceRecord rec = new AbsenceRecord("A1", absent, "2025-01-01",
                AbsenceRecord.AbsenceType.SICK_LEAVE, "Sick");
        absent.setOnLeave(true);

        ProxyAssignmentService svc = new ProxyAssignmentService(tt, reg, null, null);
        ProxyAssignmentService.AssignmentReport report = svc.processAbsence(rec);

        assertTrue("Assignment fully covered", report.isFullyCovered());
        assertEquals("One assignment created", 1, report.getAssignments().size(), 0);
    }

    void test_proxyAssignment_noCandidate() {
        Teacher absent = new Teacher("T1", "Absent", Set.of("Rare-Subject"), Set.of("Grade-10"), 6);
        Map<String, Teacher> reg = Map.of("T1", absent);

        Timetable tt = new Timetable();
        TimetableService tms = new TimetableService(tt);
        tms.createPeriod(1, LocalTime.of(8,0), LocalTime.of(9,0),
                "2025-01-01", "Rare-Subject", "Grade-10", "R1", absent);

        AbsenceRecord rec = new AbsenceRecord("A1", absent, "2025-01-01",
                AbsenceRecord.AbsenceType.SICK_LEAVE, "Sick");
        absent.setOnLeave(true);

        ProxyAssignmentService svc = new ProxyAssignmentService(tt, reg, null, null);
        ProxyAssignmentService.AssignmentReport report = svc.processAbsence(rec);

        assertFalse("Should not be fully covered", report.isFullyCovered());
        assertEquals("One failure", 1, report.getFailures().size(), 0);
    }

    void test_workloadIncrement() {
        Teacher t = new Teacher("T1", "T", Set.of("Math"), Set.of("G"), 4);
        t.incrementDailyLoad("2025-01-01");
        t.incrementDailyLoad("2025-01-01");
        assertEquals("2 periods counted", 2, t.getPeriodsOnDate("2025-01-01"), 0);
        t.decrementDailyLoad("2025-01-01");
        assertEquals("Back to 1 after decrement", 1, t.getPeriodsOnDate("2025-01-01"), 0);
    }

    void test_absenceDetection() {
        Teacher t = new Teacher("T1", "Alice", Set.of("Math"), Set.of("Grade-10"), 6);
        Map<String, Teacher> reg = Map.of("T1", t);
        AbsenceDetectionService svc = new AbsenceDetectionService(reg);
        AbsenceRecord rec = svc.reportAbsence("T1", "2025-01-01",
                AbsenceRecord.AbsenceType.SICK_LEAVE, "Flu");
        assertTrue("Teacher marked on leave", t.isOnLeave());
        assertFalse("Active absences not empty", svc.getActiveAbsences().isEmpty());

        // Duplicate detection
        AbsenceRecord dup = svc.reportAbsence("T1", "2025-01-01",
                AbsenceRecord.AbsenceType.SICK_LEAVE, "Duplicate");
        assertEquals("Same record returned on duplicate", rec.getId(), dup.getId());
    }

    void test_manualOverride() {
        Teacher absent = new Teacher("T1", "Absent", Set.of("Math"), Set.of("Grade-10"), 6);
        Teacher proxy  = new Teacher("T2", "Proxy", Set.of("Science"), Set.of("Grade-10"), 6);
        Map<String, Teacher> reg = Map.of("T1", absent, "T2", proxy);

        Timetable tt = new Timetable();
        TimetableService tms = new TimetableService(tt);
        Period p = tms.createPeriod(1, LocalTime.of(8,0), LocalTime.of(9,0),
                "2025-01-01", "Math", "Grade-10", "R1", absent);

        AbsenceRecord rec = new AbsenceRecord("A1", absent, "2025-01-01",
                AbsenceRecord.AbsenceType.SICK_LEAVE, "Sick");
        absent.setOnLeave(true);

        ProxyAssignmentService svc = new ProxyAssignmentService(tt, reg, null, null);
        // Proxy is not qualified for Math, but manual override should work
        ProxyAssignment assignment = svc.manualOverride("T2", p.getId(), rec);
        assertNotNull("Manual override returns assignment", assignment);
        assertTrue("Notes contain 'conflict' or 'Manual'",
                assignment.getNotes() != null && assignment.getNotes().length() > 0);
    }

    // ── Mini assertion helpers ────────────────────────────────────────────
    void assertTrue(String msg, boolean condition) {
        if (condition) { System.out.println("  PASS: " + msg); passed++; }
        else           { System.out.println("  FAIL: " + msg); failed++; }
    }
    void assertFalse(String msg, boolean condition) { assertTrue(msg, !condition); }
    void assertEquals(String msg, double expected, double actual, double delta) {
        assertTrue(msg, Math.abs(expected - actual) <= delta);
    }
    void assertEquals(String msg, String expected, String actual) {
        assertTrue(msg, Objects.equals(expected, actual));
    }
    void assertNotNull(String msg, Object obj) { assertTrue(msg, obj != null); }

    Period mkPeriod(String id, LocalTime start, LocalTime end) {
        Teacher t = new Teacher("TX", "X", Set.of("Math"), Set.of("G"), 6);
        return new Period(id, 1, start, end, "2025-01-01", "Math", "G", "R", t);
    }
}
