package com.school.model;

import java.util.*;

public class Teacher {
    private final String id;
    private final String name;
    private final Set<String> subjects;
    private final Set<String> qualifiedGrades; // e.g., "Grade-10", "Grade-11"
    private double workloadScore;              // 0.0 = free, 1.0 = fully booked
    private boolean onLeave;
    private final int maxPeriodsPerDay;
    private final Map<String, Integer> dailyPeriodCount; // date -> count
    private TeacherStatus status;

    public enum TeacherStatus { AVAILABLE, ABSENT, ON_PROXY, OVERLOADED }

    public Teacher(String id, String name, Set<String> subjects,
                   Set<String> qualifiedGrades, int maxPeriodsPerDay) {
        this.id = id;
        this.name = name;
        this.subjects = new HashSet<>(subjects);
        this.qualifiedGrades = new HashSet<>(qualifiedGrades);
        this.maxPeriodsPerDay = maxPeriodsPerDay;
        this.workloadScore = 0.0;
        this.onLeave = false;
        this.dailyPeriodCount = new HashMap<>();
        this.status = TeacherStatus.AVAILABLE;
    }

    public boolean canTeach(String subject, String grade) {
        return subjects.contains(subject) && qualifiedGrades.contains(grade);
    }

    public boolean isAvailableOn(String date) {
        int count = dailyPeriodCount.getOrDefault(date, 0);
        return !onLeave && count < maxPeriodsPerDay && status != TeacherStatus.ABSENT;
    }

    public void incrementDailyLoad(String date) {
        dailyPeriodCount.merge(date, 1, Integer::sum);
        recalculateWorkload(date);
    }

    public void decrementDailyLoad(String date) {
        dailyPeriodCount.computeIfPresent(date, (k, v) -> Math.max(0, v - 1));
        recalculateWorkload(date);
    }

    private void recalculateWorkload(String date) {
        int count = dailyPeriodCount.getOrDefault(date, 0);
        this.workloadScore = (double) count / maxPeriodsPerDay;
        if (workloadScore >= 1.0) status = TeacherStatus.OVERLOADED;
        else if (status == TeacherStatus.OVERLOADED) status = TeacherStatus.AVAILABLE;
    }

    public int getPeriodsOnDate(String date) {
        return dailyPeriodCount.getOrDefault(date, 0);
    }

    // Getters & Setters
    public String getId() { return id; }
    public String getName() { return name; }
    public Set<String> getSubjects() { return Collections.unmodifiableSet(subjects); }
    public Set<String> getQualifiedGrades() { return Collections.unmodifiableSet(qualifiedGrades); }
    public double getWorkloadScore() { return workloadScore; }
    public boolean isOnLeave() { return onLeave; }
    public void setOnLeave(boolean onLeave) {
        this.onLeave = onLeave;
        this.status = onLeave ? TeacherStatus.ABSENT : TeacherStatus.AVAILABLE;
    }
    public TeacherStatus getStatus() { return status; }
    public void setStatus(TeacherStatus status) { this.status = status; }
    public int getMaxPeriodsPerDay() { return maxPeriodsPerDay; }

    @Override
    public String toString() {
        return String.format("Teacher{id='%s', name='%s', subjects=%s, workload=%.2f, status=%s}",
                id, name, subjects, workloadScore, status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Teacher)) return false;
        return id.equals(((Teacher) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
