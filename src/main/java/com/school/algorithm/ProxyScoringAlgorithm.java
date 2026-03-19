package com.school.algorithm;

import com.school.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-criteria weighted scoring algorithm for proxy selection.
 *
 * Score = Σ (weight_i × normalised_criterion_i)
 *
 * Criteria:
 *  1. Subject match          – hard filter + 0/1 bonus for exact match
 *  2. Grade qualification    – hard filter + 0/1 bonus
 *  3. Workload balance       – inverse of current daily load ratio
 *  4. Proximity (rooms)      – same building → bonus
 *  5. Historical proxy count – teachers who have done fewer proxies recently are preferred
 *  6. Seniority bias         – configurable; senior teachers can be deprioritised
 */
public class ProxyScoringAlgorithm {

    // Weights (must sum to 1.0)
    private static final double W_SUBJECT_EXACT   = 0.30;
    private static final double W_GRADE_EXACT     = 0.20;
    private static final double W_WORKLOAD        = 0.25;
    private static final double W_PROXIMITY       = 0.10;
    private static final double W_PROXY_HISTORY   = 0.10;
    private static final double W_SENIORITY       = 0.05;

    private final Map<String, Integer> proxyCountHistory;   // teacherId → recent proxy count
    private final Map<String, Integer> seniorityMap;        // teacherId → years (higher = senior)

    public ProxyScoringAlgorithm(Map<String, Integer> proxyCountHistory,
                                 Map<String, Integer> seniorityMap) {
        this.proxyCountHistory = proxyCountHistory != null ? proxyCountHistory : new HashMap<>();
        this.seniorityMap      = seniorityMap      != null ? seniorityMap      : new HashMap<>();
    }

    /**
     * Score a candidate teacher for a given vacant period.
     * Returns -1.0 if the teacher is ineligible (hard constraint violated).
     */
    public double score(Teacher candidate, Period period, Timetable timetable) {
        // ── Hard constraints ──────────────────────────────────────────────
        if (!candidate.isAvailableOn(period.getDate()))            return -1.0;
        if (!candidate.canTeach(period.getSubject(), period.getGrade())) return -1.0;
        if (timetable.hasConflict(candidate, period))              return -1.0;
        if (candidate.equals(period.getOriginalTeacher()))         return -1.0;

        // ── Soft scores ───────────────────────────────────────────────────
        double subjectScore  = scoreSubjectMatch(candidate, period);
        double gradeScore    = scoreGradeMatch(candidate, period);
        double workloadScore = scoreWorkload(candidate, period);
        double proximityScore= scoreProximity(candidate, period);
        double historyScore  = scoreHistory(candidate);
        double seniorityScore= scoreSeniority(candidate);

        double total =
                W_SUBJECT_EXACT   * subjectScore  +
                W_GRADE_EXACT     * gradeScore     +
                W_WORKLOAD        * workloadScore  +
                W_PROXIMITY       * proximityScore +
                W_PROXY_HISTORY   * historyScore   +
                W_SENIORITY       * seniorityScore;

        return Math.min(1.0, Math.max(0.0, total));
    }

    // 1 = exact match; 0.5 = related subject (same department prefix); 0 = no match
    private double scoreSubjectMatch(Teacher t, Period p) {
        if (t.getSubjects().contains(p.getSubject())) return 1.0;
        // Partial: same subject family (e.g., "Math-Advanced" and "Math-Basic")
        boolean related = t.getSubjects().stream()
                .anyMatch(s -> s.split("-")[0].equalsIgnoreCase(p.getSubject().split("-")[0]));
        return related ? 0.5 : 0.0;
    }

    // 1 = qualified for that exact grade; 0.6 = adjacent grade (±1); 0 = not qualified
    private double scoreGradeMatch(Teacher t, Period p) {
        String targetGrade = p.getGrade();
        if (t.getQualifiedGrades().contains(targetGrade)) return 1.0;
        int targetNum = extractGradeNumber(targetGrade);
        boolean adjacent = t.getQualifiedGrades().stream()
                .anyMatch(g -> Math.abs(extractGradeNumber(g) - targetNum) == 1);
        return adjacent ? 0.6 : 0.0;
    }

    // Teachers with lower current load get higher scores
    private double scoreWorkload(Teacher t, Period p) {
        double load = t.getWorkloadScore();
        // Non-linear: reward lightly-loaded teachers more strongly
        return Math.pow(1.0 - load, 1.5);
    }

    // Same building prefix gets a bonus (e.g., "B1-101" and "B1-205" → same building B1)
    private double scoreProximity(Teacher t, Period p) {
        // We'd normally know the teacher's current room from their schedule;
        // for simplicity, use the first char(s) of the classroom code.
        String targetBuilding = extractBuilding(p.getClassroom());
        // In a real system, query the teacher's other periods to find their likely location.
        // Here we give a flat 0.5 as a neutral mid-score if we can't determine proximity.
        return 0.5;
    }

    // Fewer recent proxies → higher score (fairness)
    private double scoreHistory(Teacher t) {
        int count = proxyCountHistory.getOrDefault(t.getId(), 0);
        int max   = proxyCountHistory.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        if (max == 0) return 1.0;
        return 1.0 - ((double) count / (max + 1));  // +1 to avoid division issues
    }

    // More senior → slight penalty (protect from over-burdening senior staff)
    private double scoreSeniority(Teacher t) {
        int years = seniorityMap.getOrDefault(t.getId(), 0);
        int max   = seniorityMap.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        if (max == 0) return 1.0;
        return 1.0 - (0.3 * (double) years / (max + 1));  // mild penalty, not exclusionary
    }

    private int extractGradeNumber(String grade) {
        try {
            // e.g., "Grade-10A" → 10
            String numeric = grade.replaceAll("[^0-9]", "");
            return numeric.isEmpty() ? 0 : Integer.parseInt(numeric);
        } catch (NumberFormatException e) { return 0; }
    }

    private String extractBuilding(String classroom) {
        if (classroom == null || classroom.isEmpty()) return "";
        // e.g., "B1-101" → "B1"
        return classroom.contains("-") ? classroom.split("-")[0] : classroom.substring(0, 1);
    }

    /**
     * Rank a list of candidates for a given period, highest score first.
     * Ineligible candidates (score < 0) are excluded.
     */
    public List<ScoredCandidate> rankCandidates(List<Teacher> candidates,
                                                Period period,
                                                Timetable timetable) {
        return candidates.stream()
                .map(t -> new ScoredCandidate(t, score(t, period, timetable)))
                .filter(sc -> sc.getScore() >= 0)
                .sorted(Comparator.comparingDouble(ScoredCandidate::getScore).reversed())
                .collect(Collectors.toList());
    }

    // ── Value object ─────────────────────────────────────────────────────
    public static class ScoredCandidate {
        private final Teacher teacher;
        private final double score;

        public ScoredCandidate(Teacher teacher, double score) {
            this.teacher = teacher;
            this.score = score;
        }

        public Teacher getTeacher() { return teacher; }
        public double getScore()    { return score;   }

        @Override
        public String toString() {
            return String.format("ScoredCandidate{teacher='%s', score=%.4f}",
                    teacher.getName(), score);
        }
    }
}
