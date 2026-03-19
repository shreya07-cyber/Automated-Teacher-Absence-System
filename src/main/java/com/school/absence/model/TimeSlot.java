package com.school.absence.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

public class TimeSlot {
    private final DayOfWeek day;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String subject;
    private final String classroomId;
    private final int period; // e.g., 1st period, 2nd period

    public TimeSlot(DayOfWeek day, LocalTime startTime, LocalTime endTime,
                    String subject, String classroomId, int period) {
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subject = subject;
        this.classroomId = classroomId;
        this.period = period;
    }

    /**
     * Checks if this slot overlaps with another.
     * Two slots conflict if they are on the same day and their time ranges overlap.
     */
    public boolean conflictsWith(TimeSlot other) {
        if (this.day != other.day) return false;
        // Overlap if: this.start < other.end AND this.end > other.start
        return this.startTime.isBefore(other.endTime) && this.endTime.isAfter(other.startTime);
    }

    public long getDurationMinutes() {
        return java.time.Duration.between(startTime, endTime).toMinutes();
    }

    public DayOfWeek getDay() { return day; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getSubject() { return subject; }
    public String getClassroomId() { return classroomId; }
    public int getPeriod() { return period; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeSlot)) return false;
        TimeSlot that = (TimeSlot) o;
        return period == that.period && day == that.day &&
               Objects.equals(startTime, that.startTime) &&
               Objects.equals(endTime, that.endTime) &&
               Objects.equals(subject, that.subject) &&
               Objects.equals(classroomId, that.classroomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(day, startTime, endTime, subject, classroomId, period);
    }

    @Override
    public String toString() {
        return String.format("[%s P%d %s-%s | %s | Room:%s]",
                day, period, startTime, endTime, subject, classroomId);
    }
}
