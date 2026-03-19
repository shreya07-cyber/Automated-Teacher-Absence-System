package com.school.service;

import com.school.algorithm.*;
import com.school.exception.SchedulingConflictException;
import com.school.model.*;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Orchestrates the full proxy-assignment workflow:
 *  1. Receive an AbsenceRecord
 *  2. Fetch affected periods from the Timetable
 *  3. For each period → score all available teachers
 *  4. Validate conflict-free assignment
 *  5. Confirm assignment, update timetable and workload counters
 *  6. Return a result report
 */
public class ProxyAssignmentService {

    private static final Logger LOGGER = Logger.getLogger(ProxyAssignmentService.class.getName());

    private final Timetable timetable;
    private final Map<String, Teacher> teacherRegistry;
    private final ProxyScoringAlgorithm scoringAlgorithm;
    private final ConflictDetector conflictDetector;

    // Track proxy counts for fairness scoring
    private final Map<String, Integer> proxyCountTracker = new HashMap<>();

    private int assignmentCounter = 1;

    public ProxyAssignmentService(Timetable timetable,
                                  Map<String, Teacher> teacherRegistry,
                                  Map<String, Integer> proxyHistory,
                                  Map<String, Integer> seniorityMap) {
        this.timetable         = timetable;
        this.teacherRegistry   = teacherRegistry;
        this.scoringAlgorithm  = new ProxyScoringAlgorithm(proxyHistory, seniorityMap);
        this.conflictDetector  = new ConflictDetector(timetable);

        if (proxyHistory != null) proxyCountTracker.putAll(proxyHistory);
    }

    // ── Main entry point ─────────────────────────────────────────────────

    public AssignmentReport processAbsence(AbsenceRecord absenceRecord) {
        String teacherId = absenceRecord.getAbsentTeacher().getId();
        String date      = absenceRecord.getDate();

        LOGGER.info("Processing absence: " + absenceRecord);

        List<Period> affectedPeriods = timetable.getAbsentTeacherPeriods(teacherId, date);
        if (affectedPeriods.isEmpty()) {
            LOGGER.info("No periods to cover for " + teacherId + " on " + date);
            absenceRecord.setStatus(AbsenceRecord.AbsenceStatus.RESOLVED);
            return new AssignmentReport(absenceRecord, Collections.emptyList(), Collections.emptyList());
        }

        List<Teacher> candidates = getAvailableCandidates(date, teacherId);
        List<ProxyAssignment> successful = new ArrayList<>();
        List<PeriodFailure> failures     = new ArrayList<>();

        // Process periods in chronological order
        List<Period> sorted = new ArrayList<>(affectedPeriods);
        Collections.sort(sorted);

        for (Period period : sorted) {
            // Re-fetch candidates each iteration — workloads change as assignments are made
            List<Teacher> updatedCandidates = getAvailableCandidates(date, teacherId);
            Optional<ProxyAssignment> result = assignBestProxy(period, updatedCandidates, absenceRecord);
            if (result.isPresent()) {
                successful.add(result.get());
            } else {
                period.setStatus(Period.PeriodStatus.VACANT);
                failures.add(new PeriodFailure(period, "No eligible substitute found"));
                LOGGER.warning("No proxy found for period: " + period);
            }
        }

        absenceRecord.updateStatus();
        AssignmentReport report = new AssignmentReport(absenceRecord, successful, failures);
        LOGGER.info("Assignment complete: " + report.getSummary());
        return report;
    }

    private Optional<ProxyAssignment> assignBestProxy(Period period,
                                                       List<Teacher> candidates,
                                                       AbsenceRecord absenceRecord) {
        List<ProxyScoringAlgorithm.ScoredCandidate> ranked =
                scoringAlgorithm.rankCandidates(candidates, period, timetable);

        for (ProxyScoringAlgorithm.ScoredCandidate sc : ranked) {
            Teacher proxy = sc.getTeacher();
            try {
                // Validate — throws on CRITICAL conflicts
                List<ConflictDetector.Conflict> warnings =
                        conflictDetector.validateOrThrow(proxy, period);

                String id = "PA-" + String.format("%05d", assignmentCounter++);
                ProxyAssignment assignment = new ProxyAssignment(
                        id, period, proxy, period.getOriginalTeacher(), sc.getScore());

                if (!warnings.isEmpty()) {
                    assignment.setNotes("Warnings: " +
                            warnings.stream().map(c -> c.description()).collect(Collectors.joining("; ")));
                }

                assignment.confirm(); // applies side-effects to period + teacher
                proxyCountTracker.merge(proxy.getId(), 1, Integer::sum);
                absenceRecord.addAssignment(assignment);

                LOGGER.info(String.format("Assigned %s → period %s (score=%.3f)",
                        proxy.getName(), period.getId(), sc.getScore()));
                return Optional.of(assignment);

            } catch (SchedulingConflictException e) {
                LOGGER.fine("Skipping " + proxy.getName() + ": " + e.getMessage());
                // Try next candidate
            }
        }
        return Optional.empty();
    }

    /** Revoke a proxy assignment (e.g., the teacher becomes unavailable). */
    public void revokeAssignment(ProxyAssignment assignment) {
        assignment.revoke();
        proxyCountTracker.merge(assignment.getProxyTeacher().getId(), -1,
                (a, b) -> Math.max(0, a + b));
        LOGGER.info("Revoked assignment: " + assignment);
    }

    /** Manually override: force a specific teacher to cover a period. */
    public ProxyAssignment manualOverride(String teacherId, String periodId,
                                          AbsenceRecord absenceRecord) {
        Teacher proxy = teacherRegistry.get(teacherId);
        Period period = timetable.getPeriodById(periodId);
        if (proxy == null) throw new IllegalArgumentException("Unknown teacher: " + teacherId);
        if (period == null) throw new IllegalArgumentException("Unknown period: " + periodId);

        // Warn but don't block manual overrides
        List<ConflictDetector.Conflict> conflicts = conflictDetector.checkAssignment(proxy, period);
        String notes = conflicts.isEmpty() ? "Manual override" :
                "Manual override with conflicts: " +
                conflicts.stream().map(ConflictDetector.Conflict::description)
                         .collect(Collectors.joining("; "));

        String id = "PA-MAN-" + String.format("%05d", assignmentCounter++);
        ProxyAssignment assignment = new ProxyAssignment(id, period, proxy,
                period.getOriginalTeacher(), 0.0);
        assignment.setNotes(notes);
        assignment.confirm();
        absenceRecord.addAssignment(assignment);
        return assignment;
    }

    private List<Teacher> getAvailableCandidates(String date, String excludeTeacherId) {
        return teacherRegistry.values().stream()
                .filter(t -> !t.getId().equals(excludeTeacherId))
                .filter(t -> t.isAvailableOn(date))
                .collect(Collectors.toList());
    }

    public Map<String, Integer> getProxyCountSnapshot() {
        return Collections.unmodifiableMap(proxyCountTracker);
    }

    // ── Result types ─────────────────────────────────────────────────────

    public record PeriodFailure(Period period, String reason) {}

    public static class AssignmentReport {
        private final AbsenceRecord absenceRecord;
        private final List<ProxyAssignment> assignments;
        private final List<PeriodFailure> failures;

        public AssignmentReport(AbsenceRecord absenceRecord,
                                List<ProxyAssignment> assignments,
                                List<PeriodFailure> failures) {
            this.absenceRecord = absenceRecord;
            this.assignments   = assignments;
            this.failures      = failures;
        }

        public String getSummary() {
            return String.format("AbsenceReport[teacher=%s, date=%s, covered=%d, uncovered=%d, status=%s]",
                    absenceRecord.getAbsentTeacher().getName(),
                    absenceRecord.getDate(),
                    assignments.size(),
                    failures.size(),
                    absenceRecord.getStatus());
        }

        public AbsenceRecord getAbsenceRecord() { return absenceRecord; }
        public List<ProxyAssignment> getAssignments() { return assignments; }
        public List<PeriodFailure> getFailures() { return failures; }
        public boolean isFullyCovered() { return failures.isEmpty(); }
    }
}
