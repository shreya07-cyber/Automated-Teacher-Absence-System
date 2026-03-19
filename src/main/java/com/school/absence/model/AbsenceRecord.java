package com.school.absence.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class AbsenceRecord {
    public enum Status { PENDING, ASSIGNED, PARTIALLY_ASSIGNED, UNRESOLVED }
    public enum AbsenceType { SICK_LEAVE, EMERGENCY, PLANNED, TRAINING, OTHER }

    private final String recordId;
    private final Teacher absentTeacher;
    private final LocalDateTime reportedAt;
    private final AbsenceType absenceType;
    private final List<TimeSlot> affectedSlots;
    private final List<SubstituteAssignment> assignments;
    private Status status;
    private String notes;

    public AbsenceRecord(String recordId, Teacher absentTeacher,
                         AbsenceType absenceType, List<TimeSlot> affectedSlots) {
        this.recordId = recordId;
        this.absentTeacher = absentTeacher;
        this.reportedAt = LocalDateTime.now();
        this.absenceType = absenceType;
        this.affectedSlots = new ArrayList<>(affectedSlots);
        this.assignments = new ArrayList<>();
        this.status = Status.PENDING;
    }

    public void addAssignment(SubstituteAssignment assignment) {
        assignments.add(assignment);
        updateStatus();
    }

    private void updateStatus() {
        if (assignments.size() == affectedSlots.size()) {
            status = Status.ASSIGNED;
        } else if (!assignments.isEmpty()) {
            status = Status.PARTIALLY_ASSIGNED;
        }
    }

    public void markUnresolved() { this.status = Status.UNRESOLVED; }

    public List<TimeSlot> getUnassignedSlots() {
        List<TimeSlot> assigned = new ArrayList<>();
        for (SubstituteAssignment a : assignments) assigned.add(a.getSlot());
        List<TimeSlot> unassigned = new ArrayList<>(affectedSlots);
        unassigned.removeAll(assigned);
        return unassigned;
    }

    // Getters
    public String getRecordId() { return recordId; }
    public Teacher getAbsentTeacher() { return absentTeacher; }
    public LocalDateTime getReportedAt() { return reportedAt; }
    public AbsenceType getAbsenceType() { return absenceType; }
    public List<TimeSlot> getAffectedSlots() { return Collections.unmodifiableList(affectedSlots); }
    public List<SubstituteAssignment> getAssignments() { return Collections.unmodifiableList(assignments); }
    public Status getStatus() { return status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return String.format("AbsenceRecord[id=%s, teacher=%s, slots=%d, status=%s]",
                recordId, absentTeacher.getName(), affectedSlots.size(), status);
    }
}
