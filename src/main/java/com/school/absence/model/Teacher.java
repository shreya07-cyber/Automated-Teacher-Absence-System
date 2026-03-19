package com.school.absence.model;

import java.util.*;

public class Teacher {
    private String id;
    private String name;
    private String email;
    private String phone;
    private Set<String> qualifiedSubjects;
    private List<TimeSlot> schedule;
    private boolean isPresent;
    private int workloadScore; // lower = less loaded, preferred for substitution

    public Teacher(String id, String name, String email, String phone) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.qualifiedSubjects = new HashSet<>();
        this.schedule = new ArrayList<>();
        this.isPresent = true;
        this.workloadScore = 0;
    }

    public void addQualifiedSubject(String subject) {
        qualifiedSubjects.add(subject.toLowerCase());
    }

    public boolean isQualifiedFor(String subject) {
        return qualifiedSubjects.contains(subject.toLowerCase());
    }

    public boolean isFreeAt(TimeSlot slot) {
        return schedule.stream().noneMatch(s -> s.conflictsWith(slot));
    }

    public void assignSlot(TimeSlot slot) {
        schedule.add(slot);
        workloadScore += slot.getDurationMinutes();
    }

    public void removeSlot(TimeSlot slot) {
        schedule.removeIf(s -> s.equals(slot));
        workloadScore = Math.max(0, workloadScore - slot.getDurationMinutes());
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Set<String> getQualifiedSubjects() { return Collections.unmodifiableSet(qualifiedSubjects); }
    public List<TimeSlot> getSchedule() { return Collections.unmodifiableList(schedule); }
    public boolean isPresent() { return isPresent; }
    public void setPresent(boolean present) { isPresent = present; }
    public int getWorkloadScore() { return workloadScore; }

    @Override
    public String toString() {
        return String.format("Teacher[id=%s, name=%s, subjects=%s, workload=%d]",
                id, name, qualifiedSubjects, workloadScore);
    }
}
