package com.school.algorithm;

import com.school.model.*;
import com.school.exception.SchedulingConflictException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive conflict detection engine.
 *
 * Detects and categorises all types of timetable conflicts:
 *  - TEACHER_DOUBLE_BOOKED : same teacher in two periods at overlapping times
 *  - CLASSROOM_CONFLICT    : same room booked for two different classes simultaneously
 *  - GRADE_OVERLOAD        : a grade has more periods in a slot than physically possible
 *  - WORKLOAD_BREACH       : teacher exceeds max daily period count
 *  - SUBJECT_MISMATCH      : assigned proxy is not qualified for the subject/grade
 */
public class ConflictDetector {

    public enum ConflictType {
        TEACHER_DOUBLE_BOOKED,
        CLASSROOM_CONFLICT,
        GRADE_OVERLOAD,
        WORKLOAD_BREACH,
        SUBJECT_MISMATCH
    }

    public record Conflict(ConflictType type, String description,
                           List<Period> involvedPeriods, Severity severity) {
        public enum Severity { CRITICAL, WARNING, INFO }
    }

    private final Timetable timetable;

    public ConflictDetector(Timetable timetable) {
        this.timetable = timetable;
    }

    // ── Primary check used before confirming a proxy ──────────────────────
    public List<Conflict> checkAssignment(Teacher proxy, Period target) {
        List<Conflict> conflicts = new ArrayList<>();

        // 1. Teacher double-booked?
        if (timetable.hasConflict(proxy, target)) {
            List<Period> clashing = timetable
                    .getCurrentTeacherPeriods(proxy.getId(), target.getDate())
                    .stream()
                    .filter(p -> p.overlaps(target))
                    .collect(Collectors.toList());

            conflicts.add(new Conflict(
                    ConflictType.TEACHER_DOUBLE_BOOKED,
                    String.format("Teacher '%s' is already scheduled during %s–%s on %s",
                            proxy.getName(), target.getStartTime(), target.getEndTime(), target.getDate()),
                    clashing,
                    Conflict.Severity.CRITICAL));
        }

        // 2. Workload breach?
        int current = proxy.getPeriodsOnDate(target.getDate());
        if (current >= proxy.getMaxPeriodsPerDay()) {
            conflicts.add(new Conflict(
                    ConflictType.WORKLOAD_BREACH,
                    String.format("Teacher '%s' already has %d/%d periods on %s",
                            proxy.getName(), current, proxy.getMaxPeriodsPerDay(), target.getDate()),
                    List.of(target),
                    Conflict.Severity.WARNING));
        }

        // 3. Subject / grade qualification?
        if (!proxy.canTeach(target.getSubject(), target.getGrade())) {
            conflicts.add(new Conflict(
                    ConflictType.SUBJECT_MISMATCH,
                    String.format("Teacher '%s' is not qualified for %s / %s",
                            proxy.getName(), target.getSubject(), target.getGrade()),
                    List.of(target),
                    Conflict.Severity.CRITICAL));
        }

        return conflicts;
    }

    // ── Full timetable audit ──────────────────────────────────────────────
    public List<Conflict> auditFullTimetable() {
        List<Conflict> allConflicts = new ArrayList<>();
        Collection<Period> periods = timetable.getAllPeriods();

        // Group by date for efficiency
        Map<String, List<Period>> byDate = periods.stream()
                .collect(Collectors.groupingBy(Period::getDate));

        for (Map.Entry<String, List<Period>> entry : byDate.entrySet()) {
            List<Period> dayPeriods = entry.getValue();

            allConflicts.addAll(detectTeacherDoubleBookings(dayPeriods));
            allConflicts.addAll(detectClassroomConflicts(dayPeriods));
            allConflicts.addAll(detectGradeOverloads(dayPeriods));
        }

        return allConflicts;
    }

    private List<Conflict> detectTeacherDoubleBookings(List<Period> dayPeriods) {
        List<Conflict> conflicts = new ArrayList<>();
        Map<String, List<Period>> byTeacher = dayPeriods.stream()
                .filter(p -> p.getAssignedTeacher() != null)
                .collect(Collectors.groupingBy(p -> p.getAssignedTeacher().getId()));

        for (Map.Entry<String, List<Period>> entry : byTeacher.entrySet()) {
            List<Period> tp = entry.getValue();
            for (int i = 0; i < tp.size(); i++) {
                for (int j = i + 1; j < tp.size(); j++) {
                    if (tp.get(i).overlaps(tp.get(j))) {
                        conflicts.add(new Conflict(
                                ConflictType.TEACHER_DOUBLE_BOOKED,
                                String.format("Teacher '%s' double-booked: period %s and %s overlap",
                                        tp.get(i).getAssignedTeacher().getName(),
                                        tp.get(i).getId(), tp.get(j).getId()),
                                List.of(tp.get(i), tp.get(j)),
                                Conflict.Severity.CRITICAL));
                    }
                }
            }
        }
        return conflicts;
    }

    private List<Conflict> detectClassroomConflicts(List<Period> dayPeriods) {
        List<Conflict> conflicts = new ArrayList<>();
        Map<String, List<Period>> byRoom = dayPeriods.stream()
                .collect(Collectors.groupingBy(Period::getClassroom));

        for (Map.Entry<String, List<Period>> entry : byRoom.entrySet()) {
            List<Period> rp = entry.getValue();
            for (int i = 0; i < rp.size(); i++) {
                for (int j = i + 1; j < rp.size(); j++) {
                    if (rp.get(i).overlaps(rp.get(j)) &&
                        !rp.get(i).getGrade().equals(rp.get(j).getGrade())) {
                        conflicts.add(new Conflict(
                                ConflictType.CLASSROOM_CONFLICT,
                                String.format("Room '%s' double-booked: %s and %s at the same time",
                                        entry.getKey(), rp.get(i).getId(), rp.get(j).getId()),
                                List.of(rp.get(i), rp.get(j)),
                                Conflict.Severity.CRITICAL));
                    }
                }
            }
        }
        return conflicts;
    }

    private List<Conflict> detectGradeOverloads(List<Period> dayPeriods) {
        List<Conflict> conflicts = new ArrayList<>();
        Map<String, List<Period>> byGrade = dayPeriods.stream()
                .collect(Collectors.groupingBy(Period::getGrade));

        for (Map.Entry<String, List<Period>> entry : byGrade.entrySet()) {
            List<Period> gp = entry.getValue();
            for (int i = 0; i < gp.size(); i++) {
                for (int j = i + 1; j < gp.size(); j++) {
                    Period a = gp.get(i), b = gp.get(j);
                    if (a.overlaps(b)) {
                        conflicts.add(new Conflict(
                                ConflictType.GRADE_OVERLOAD,
                                String.format("Grade '%s' has overlapping periods: %s and %s",
                                        entry.getKey(), a.getId(), b.getId()),
                                List.of(a, b),
                                Conflict.Severity.WARNING));
                    }
                }
            }
        }
        return conflicts;
    }

    /**
     * Throws if any CRITICAL conflicts exist; otherwise returns warnings list.
     */
    public List<Conflict> validateOrThrow(Teacher proxy, Period target) {
        List<Conflict> conflicts = checkAssignment(proxy, target);
        List<Conflict> critical = conflicts.stream()
                .filter(c -> c.severity() == Conflict.Severity.CRITICAL)
                .collect(Collectors.toList());
        if (!critical.isEmpty()) {
            throw new SchedulingConflictException(
                    "Cannot assign proxy due to critical conflicts", critical);
        }
        return conflicts.stream()
                .filter(c -> c.severity() != Conflict.Severity.CRITICAL)
                .collect(Collectors.toList());
    }
}
