package com.school.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central timetable registry.
 * Maps: date → teacher-id → sorted list of periods
 * Also maintains a grade-date index for quick conflict checks.
 */
public class Timetable {

    // date -> teacherId -> periods
    private final Map<String, Map<String, List<Period>>> teacherSchedule = new HashMap<>();
    // date -> grade -> periods
    private final Map<String, Map<String, List<Period>>> gradeSchedule = new HashMap<>();
    // All periods by id
    private final Map<String, Period> allPeriods = new HashMap<>();

    public void addPeriod(Period period) {
        allPeriods.put(period.getId(), period);

        // Teacher index
        teacherSchedule
                .computeIfAbsent(period.getDate(), d -> new HashMap<>())
                .computeIfAbsent(period.getOriginalTeacher().getId(), t -> new ArrayList<>())
                .add(period);

        // Grade index
        gradeSchedule
                .computeIfAbsent(period.getDate(), d -> new HashMap<>())
                .computeIfAbsent(period.getGrade(), g -> new ArrayList<>())
                .add(period);

        // Keep sorted
        teacherSchedule.get(period.getDate()).get(period.getOriginalTeacher().getId())
                .sort(Comparator.naturalOrder());
        gradeSchedule.get(period.getDate()).get(period.getGrade())
                .sort(Comparator.naturalOrder());
    }

    /** All periods assigned to a teacher on a date (original assignments). */
    public List<Period> getTeacherPeriods(String teacherId, String date) {
        return teacherSchedule
                .getOrDefault(date, Collections.emptyMap())
                .getOrDefault(teacherId, Collections.emptyList());
    }

    /** All periods a teacher is CURRENTLY teaching on a date (including proxies). */
    public List<Period> getCurrentTeacherPeriods(String teacherId, String date) {
        return allPeriods.values().stream()
                .filter(p -> p.getDate().equals(date))
                .filter(p -> p.getAssignedTeacher() != null &&
                             p.getAssignedTeacher().getId().equals(teacherId))
                .sorted()
                .collect(Collectors.toList());
    }

    /** All periods for a grade on a date. */
    public List<Period> getGradePeriods(String grade, String date) {
        return gradeSchedule
                .getOrDefault(date, Collections.emptyMap())
                .getOrDefault(grade, Collections.emptyList());
    }

    /** Check if a teacher is free at the exact time of the given period. */
    public boolean hasConflict(Teacher teacher, Period candidatePeriod) {
        String date = candidatePeriod.getDate();
        List<Period> current = getCurrentTeacherPeriods(teacher.getId(), date);
        return current.stream()
                .filter(p -> !p.getId().equals(candidatePeriod.getId()))
                .anyMatch(p -> p.overlaps(candidatePeriod));
    }

    /** Vacant periods on a date (no assigned teacher). */
    public List<Period> getVacantPeriods(String date) {
        return allPeriods.values().stream()
                .filter(p -> p.getDate().equals(date) && p.isVacant())
                .sorted()
                .collect(Collectors.toList());
    }

    /** Periods of an absent teacher on a date. */
    public List<Period> getAbsentTeacherPeriods(String teacherId, String date) {
        return getTeacherPeriods(teacherId, date).stream()
                .filter(p -> p.getStatus() != Period.PeriodStatus.CANCELLED)
                .collect(Collectors.toList());
    }

    public Period getPeriodById(String id) { return allPeriods.get(id); }
    public Collection<Period> getAllPeriods() { return Collections.unmodifiableCollection(allPeriods.values()); }

    public Map<String, Long> generateDailySummary(String date) {
        Map<String, Long> summary = new LinkedHashMap<>();
        long total = allPeriods.values().stream().filter(p -> p.getDate().equals(date)).count();
        long vacant = getVacantPeriods(date).size();
        long proxied = allPeriods.values().stream()
                .filter(p -> p.getDate().equals(date) &&
                             p.getStatus() == Period.PeriodStatus.PROXY_ASSIGNED).count();
        summary.put("total_periods", total);
        summary.put("vacant", vacant);
        summary.put("proxied", proxied);
        summary.put("normal", total - vacant - proxied);
        return summary;
    }
}
