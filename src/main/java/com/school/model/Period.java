package com.school.model;

import java.time.LocalTime;
import java.util.Objects;

public class Period implements Comparable<Period> {
    private final String id;
    private final int periodNumber;     // 1-8
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String date;          // "YYYY-MM-DD"
    private final String subject;
    private final String grade;         // "Grade-10A"
    private final String classroom;
    private Teacher assignedTeacher;
    private Teacher originalTeacher;
    private PeriodStatus status;

    public enum PeriodStatus {
        SCHEDULED, PROXY_ASSIGNED, VACANT, CANCELLED, MERGED
    }

    public Period(String id, int periodNumber, LocalTime startTime, LocalTime endTime,
                  String date, String subject, String grade, String classroom, Teacher originalTeacher) {
        this.id = id;
        this.periodNumber = periodNumber;
        this.startTime = startTime;
        this.endTime = endTime;
        this.date = date;
        this.subject = subject;
        this.grade = grade;
        this.classroom = classroom;
        this.originalTeacher = originalTeacher;
        this.assignedTeacher = originalTeacher;
        this.status = PeriodStatus.SCHEDULED;
    }

    public boolean overlaps(Period other) {
        if (!this.date.equals(other.date)) return false;
        return startTime.isBefore(other.endTime) && endTime.isAfter(other.startTime);
    }

    public boolean isVacant() {
        return status == PeriodStatus.VACANT || assignedTeacher == null;
    }

    public long getDurationMinutes() {
        return java.time.Duration.between(startTime, endTime).toMinutes();
    }

    // Getters & Setters
    public String getId() { return id; }
    public int getPeriodNumber() { return periodNumber; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getDate() { return date; }
    public String getSubject() { return subject; }
    public String getGrade() { return grade; }
    public String getClassroom() { return classroom; }
    public Teacher getAssignedTeacher() { return assignedTeacher; }
    public void setAssignedTeacher(Teacher t) { this.assignedTeacher = t; }
    public Teacher getOriginalTeacher() { return originalTeacher; }
    public PeriodStatus getStatus() { return status; }
    public void setStatus(PeriodStatus s) { this.status = s; }

    @Override
    public int compareTo(Period other) {
        int dateCmp = this.date.compareTo(other.date);
        if (dateCmp != 0) return dateCmp;
        return this.startTime.compareTo(other.startTime);
    }

    @Override
    public String toString() {
        return String.format("Period{id='%s', date='%s', period=%d [%s-%s], subject='%s', " +
                "grade='%s', room='%s', teacher=%s, status=%s}",
                id, date, periodNumber, startTime, endTime, subject, grade, classroom,
                assignedTeacher != null ? assignedTeacher.getName() : "NONE", status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Period)) return false;
        return Objects.equals(id, ((Period) o).id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
