package com.school.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AbsenceRecord {
    private final String id;
    private final Teacher absentTeacher;
    private final String date;
    private final AbsenceType type;
    private final String reason;
    private final LocalDateTime reportedAt;
    private AbsenceStatus status;
    private final List<ProxyAssignment> assignments;

    public enum AbsenceType {
        SICK_LEAVE, PERSONAL_LEAVE, EMERGENCY, TRAINING, OFFICIAL_DUTY, UNKNOWN
    }

    public enum AbsenceStatus {
        REPORTED, PROCESSING, RESOLVED, PARTIALLY_RESOLVED, UNRESOLVED
    }

    public AbsenceRecord(String id, Teacher absentTeacher, String date,
                         AbsenceType type, String reason) {
        this.id = id;
        this.absentTeacher = absentTeacher;
        this.date = date;
        this.type = type;
        this.reason = reason;
        this.reportedAt = LocalDateTime.now();
        this.status = AbsenceStatus.REPORTED;
        this.assignments = new ArrayList<>();
    }

    public void addAssignment(ProxyAssignment assignment) {
        assignments.add(assignment);
    }

    public void updateStatus() {
        if (assignments.isEmpty()) {
            status = AbsenceStatus.UNRESOLVED;
        } else {
            long resolved = assignments.stream()
                    .filter(a -> a.getStatus() == ProxyAssignment.AssignmentStatus.CONFIRMED)
                    .count();
            if (resolved == assignments.size()) status = AbsenceStatus.RESOLVED;
            else if (resolved > 0) status = AbsenceStatus.PARTIALLY_RESOLVED;
            else status = AbsenceStatus.UNRESOLVED;
        }
    }

    // Getters
    public String getId() { return id; }
    public Teacher getAbsentTeacher() { return absentTeacher; }
    public String getDate() { return date; }
    public AbsenceType getType() { return type; }
    public String getReason() { return reason; }
    public LocalDateTime getReportedAt() { return reportedAt; }
    public AbsenceStatus getStatus() { return status; }
    public void setStatus(AbsenceStatus s) { this.status = s; }
    public List<ProxyAssignment> getAssignments() { return assignments; }

    @Override
    public String toString() {
        return String.format("AbsenceRecord{id='%s', teacher='%s', date='%s', type=%s, status=%s, assignments=%d}",
                id, absentTeacher.getName(), date, type, status, assignments.size());
    }
}
