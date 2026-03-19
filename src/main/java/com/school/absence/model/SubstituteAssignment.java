package com.school.absence.model;

import java.time.LocalDateTime;

public class SubstituteAssignment {
    public enum AssignmentStatus { PENDING_CONFIRMATION, CONFIRMED, DECLINED, COMPLETED }

    private final String assignmentId;
    private final Teacher substitute;
    private final Teacher absentTeacher;
    private final TimeSlot slot;
    private final LocalDateTime assignedAt;
    private AssignmentStatus status;
    private int priorityScore; // higher = better fit

    public SubstituteAssignment(String assignmentId, Teacher substitute,
                                 Teacher absentTeacher, TimeSlot slot, int priorityScore) {
        this.assignmentId = assignmentId;
        this.substitute = substitute;
        this.absentTeacher = absentTeacher;
        this.slot = slot;
        this.assignedAt = LocalDateTime.now();
        this.status = AssignmentStatus.PENDING_CONFIRMATION;
        this.priorityScore = priorityScore;
    }

    public void confirm() { this.status = AssignmentStatus.CONFIRMED; }
    public void decline() { this.status = AssignmentStatus.DECLINED; }
    public void complete() { this.status = AssignmentStatus.COMPLETED; }

    public String getAssignmentId() { return assignmentId; }
    public Teacher getSubstitute() { return substitute; }
    public Teacher getAbsentTeacher() { return absentTeacher; }
    public TimeSlot getSlot() { return slot; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public AssignmentStatus getStatus() { return status; }
    public int getPriorityScore() { return priorityScore; }

    @Override
    public String toString() {
        return String.format("Assignment[%s covers %s's %s | score=%d | status=%s]",
                substitute.getName(), absentTeacher.getName(), slot, priorityScore, status);
    }
}
