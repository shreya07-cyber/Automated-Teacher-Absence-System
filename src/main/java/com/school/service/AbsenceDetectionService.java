package com.school.service;

import com.school.model.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Detects, records, and tracks teacher absences.
 * In production this would integrate with an HR/attendance system via an adapter.
 */
public class AbsenceDetectionService {

    private static final Logger LOGGER = Logger.getLogger(AbsenceDetectionService.class.getName());

    private final Map<String, Teacher> teacherRegistry;
    private final Map<String, AbsenceRecord> absenceRecords = new ConcurrentHashMap<>();
    private final List<AbsenceListener> listeners = new ArrayList<>();

    private int recordCounter = 1;

    public interface AbsenceListener {
        void onAbsenceDetected(AbsenceRecord record);
        void onAbsenceResolved(AbsenceRecord record);
    }

    public AbsenceDetectionService(Map<String, Teacher> teacherRegistry) {
        this.teacherRegistry = teacherRegistry;
    }

    public void addListener(AbsenceListener listener) {
        listeners.add(listener);
    }

    /**
     * Report an absence for a teacher on a specific date.
     */
    public AbsenceRecord reportAbsence(String teacherId, String date,
                                       AbsenceRecord.AbsenceType type, String reason) {
        Teacher teacher = teacherRegistry.get(teacherId);
        if (teacher == null) {
            throw new IllegalArgumentException("Unknown teacher id: " + teacherId);
        }

        // Check for duplicate
        Optional<AbsenceRecord> existing = absenceRecords.values().stream()
                .filter(r -> r.getAbsentTeacher().getId().equals(teacherId) &&
                             r.getDate().equals(date))
                .findFirst();
        if (existing.isPresent()) {
            LOGGER.warning(String.format("Absence already recorded for teacher %s on %s", teacherId, date));
            return existing.get();
        }

        String id = "ABS-" + String.format("%04d", recordCounter++);
        AbsenceRecord record = new AbsenceRecord(id, teacher, date, type, reason);
        teacher.setOnLeave(true);
        absenceRecords.put(id, record);

        LOGGER.info(String.format("Absence recorded: %s", record));
        notifyAbsenceDetected(record);
        return record;
    }

    /**
     * Simulate automatic detection — e.g. teacher not marked present 15 min after school starts.
     * In production, poll attendance API every N minutes.
     */
    public List<AbsenceRecord> detectUnmarkedAbsences(String date, List<Teacher> allTeachers,
                                                       Set<String> presentTeacherIds) {
        List<AbsenceRecord> detected = new ArrayList<>();
        for (Teacher teacher : allTeachers) {
            boolean alreadyRecorded = absenceRecords.values().stream()
                    .anyMatch(r -> r.getAbsentTeacher().getId().equals(teacher.getId()) &&
                                  r.getDate().equals(date));
            if (!presentTeacherIds.contains(teacher.getId()) && !alreadyRecorded) {
                AbsenceRecord rec = reportAbsence(teacher.getId(), date,
                        AbsenceRecord.AbsenceType.UNKNOWN, "Auto-detected: not marked present");
                detected.add(rec);
                LOGGER.info("Auto-detected absence: " + rec);
            }
        }
        return detected;
    }

    /** Mark an absence as resolved (teacher returned or all periods covered). */
    public void resolveAbsence(String absenceId) {
        AbsenceRecord record = absenceRecords.get(absenceId);
        if (record == null) throw new IllegalArgumentException("Unknown absence id: " + absenceId);
        record.updateStatus();
        if (record.getStatus() == AbsenceRecord.AbsenceStatus.RESOLVED) {
            record.getAbsentTeacher().setOnLeave(false);
            LOGGER.info("Absence resolved: " + record);
            notifyAbsenceResolved(record);
        }
    }

    public AbsenceRecord getAbsenceRecord(String id) { return absenceRecords.get(id); }

    public List<AbsenceRecord> getAbsencesOnDate(String date) {
        return absenceRecords.values().stream()
                .filter(r -> r.getDate().equals(date))
                .sorted(Comparator.comparing(AbsenceRecord::getReportedAt))
                .collect(Collectors.toList());
    }

    public List<AbsenceRecord> getActiveAbsences() {
        return absenceRecords.values().stream()
                .filter(r -> r.getStatus() != AbsenceRecord.AbsenceStatus.RESOLVED)
                .collect(Collectors.toList());
    }

    public Map<String, Long> generateAbsenceStats(String fromDate, String toDate) {
        Map<String, Long> stats = new LinkedHashMap<>();
        List<AbsenceRecord> filtered = absenceRecords.values().stream()
                .filter(r -> r.getDate().compareTo(fromDate) >= 0 &&
                             r.getDate().compareTo(toDate) <= 0)
                .collect(Collectors.toList());

        stats.put("total_absences", (long) filtered.size());
        for (AbsenceRecord.AbsenceType t : AbsenceRecord.AbsenceType.values()) {
            stats.put("type_" + t.name().toLowerCase(),
                    filtered.stream().filter(r -> r.getType() == t).count());
        }
        stats.put("resolved", filtered.stream()
                .filter(r -> r.getStatus() == AbsenceRecord.AbsenceStatus.RESOLVED).count());
        stats.put("unresolved", filtered.stream()
                .filter(r -> r.getStatus() == AbsenceRecord.AbsenceStatus.UNRESOLVED).count());
        return stats;
    }

    private void notifyAbsenceDetected(AbsenceRecord r) {
        listeners.forEach(l -> l.onAbsenceDetected(r));
    }
    private void notifyAbsenceResolved(AbsenceRecord r) {
        listeners.forEach(l -> l.onAbsenceResolved(r));
    }
}
