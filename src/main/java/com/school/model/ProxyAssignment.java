package com.school.model;

import java.time.LocalDateTime;

public class ProxyAssignment {
    private final String id;
    private final Period period;
    private final Teacher proxyTeacher;
    private final Teacher originalTeacher;
    private final LocalDateTime assignedAt;
    private AssignmentStatus status;
    private double confidenceScore;     // 0.0 – 1.0 from the scoring algorithm
    private String notes;

    public enum AssignmentStatus {
        PENDING, CONFIRMED, DECLINED, REVOKED, COMPLETED
    }

    public ProxyAssignment(String id, Period period, Teacher proxyTeacher,
                           Teacher originalTeacher, double confidenceScore) {
        this.id = id;
        this.period = period;
        this.proxyTeacher = proxyTeacher;
        this.originalTeacher = originalTeacher;
        this.assignedAt = LocalDateTime.now();
        this.status = AssignmentStatus.PENDING;
        this.confidenceScore = confidenceScore;
    }

    public void confirm() {
        this.status = AssignmentStatus.CONFIRMED;
        period.setAssignedTeacher(proxyTeacher);
        period.setStatus(Period.PeriodStatus.PROXY_ASSIGNED);
        proxyTeacher.incrementDailyLoad(period.getDate());
        proxyTeacher.setStatus(Teacher.TeacherStatus.ON_PROXY);
    }

    public void revoke() {
        this.status = AssignmentStatus.REVOKED;
        period.setAssignedTeacher(null);
        period.setStatus(Period.PeriodStatus.VACANT);
        proxyTeacher.decrementDailyLoad(period.getDate());
        if (proxyTeacher.getStatus() == Teacher.TeacherStatus.ON_PROXY) {
            proxyTeacher.setStatus(Teacher.TeacherStatus.AVAILABLE);
        }
    }

    // Getters & Setters
    public String getId() { return id; }
    public Period getPeriod() { return period; }
    public Teacher getProxyTeacher() { return proxyTeacher; }
    public Teacher getOriginalTeacher() { return originalTeacher; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus s) { this.status = s; }
    public double getConfidenceScore() { return confidenceScore; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return String.format("ProxyAssignment{id='%s', period=%s, proxy='%s', score=%.2f, status=%s}",
                id, period.getId(), proxyTeacher.getName(), confidenceScore, status);
    }
}
