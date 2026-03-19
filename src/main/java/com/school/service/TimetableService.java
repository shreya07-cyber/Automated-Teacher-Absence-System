package com.school.service;

import com.school.algorithm.ConflictDetector;
import com.school.model.*;

import java.time.LocalTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages the Timetable: loading, real-time updates, and reporting.
 */
public class TimetableService {

    private static final Logger LOGGER = Logger.getLogger(TimetableService.class.getName());

    private final Timetable timetable;
    private final ConflictDetector conflictDetector;
    private int periodCounter = 1;

    public TimetableService(Timetable timetable) {
        this.timetable       = timetable;
        this.conflictDetector = new ConflictDetector(timetable);
    }

    // ── Period creation ───────────────────────────────────────────────────

    public Period createPeriod(int periodNumber, LocalTime start, LocalTime end,
                               String date, String subject, String grade,
                               String classroom, Teacher teacher) {
        String id = String.format("P-%04d", periodCounter++);
        Period period = new Period(id, periodNumber, start, end, date, subject, grade, classroom, teacher);
        timetable.addPeriod(period);
        teacher.incrementDailyLoad(date);
        return period;
    }

    /** Bulk-load a week's timetable from a structured list. */
    public List<Period> bulkLoad(List<PeriodSpec> specs, Map<String, Teacher> teacherMap) {
        List<Period> created = new ArrayList<>();
        for (PeriodSpec spec : specs) {
            Teacher teacher = teacherMap.get(spec.teacherId());
            if (teacher == null) {
                LOGGER.warning("Unknown teacher id in spec: " + spec.teacherId());
                continue;
            }
            Period p = createPeriod(spec.periodNumber(), spec.start(), spec.end(),
                    spec.date(), spec.subject(), spec.grade(), spec.classroom(), teacher);
            created.add(p);
        }
        LOGGER.info("Bulk loaded " + created.size() + " periods.");
        return created;
    }

    // ── Timetable queries ─────────────────────────────────────────────────

    public void printDailySchedule(String date) {
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("  DAILY TIMETABLE — " + date);
        System.out.println("═══════════════════════════════════════════════════════════");

        Collection<Period> all = timetable.getAllPeriods();
        Map<String, List<Period>> byGrade = all.stream()
                .filter(p -> p.getDate().equals(date))
                .sorted()
                .collect(Collectors.groupingBy(Period::getGrade,
                        TreeMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Period>> entry : byGrade.entrySet()) {
            System.out.printf("\n  [%s]%n", entry.getKey());
            System.out.printf("  %-6s %-10s %-20s %-25s %-10s%n",
                    "Period", "Time", "Subject", "Teacher", "Status");
            System.out.println("  " + "-".repeat(75));
            for (Period p : entry.getValue()) {
                String teacherName = p.getAssignedTeacher() != null
                        ? p.getAssignedTeacher().getName() : "⚠ VACANT";
                System.out.printf("  %-6d %-10s %-20s %-25s %-10s%n",
                        p.getPeriodNumber(),
                        p.getStartTime() + "–" + p.getEndTime(),
                        p.getSubject(),
                        teacherName,
                        p.getStatus());
            }
        }

        Map<String, Long> summary = timetable.generateDailySummary(date);
        System.out.println("\n  Summary: " + summary);
        System.out.println("═══════════════════════════════════════════════════════════\n");
    }

    public void printConflictReport() {
        List<ConflictDetector.Conflict> conflicts = conflictDetector.auditFullTimetable();
        System.out.println("\n──── CONFLICT AUDIT REPORT ────");
        if (conflicts.isEmpty()) {
            System.out.println("  ✓  No conflicts detected.");
        } else {
            conflicts.forEach(c ->
                    System.out.printf("  [%s] %-20s %s%n", c.severity(), c.type(), c.description()));
        }
        System.out.println("───────────────────────────────\n");
    }

    public Timetable getTimetable() { return timetable; }

    // ── Spec record for bulk loading ─────────────────────────────────────
    public record PeriodSpec(int periodNumber, LocalTime start, LocalTime end,
                             String date, String subject, String grade,
                             String classroom, String teacherId) {}
}
