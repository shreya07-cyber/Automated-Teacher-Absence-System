package com.school.exception;

import com.school.algorithm.ConflictDetector;
import java.util.List;

public class SchedulingConflictException extends RuntimeException {
    private final List<ConflictDetector.Conflict> conflicts;

    public SchedulingConflictException(String message, List<ConflictDetector.Conflict> conflicts) {
        super(message + " → " + conflicts.stream()
                .map(ConflictDetector.Conflict::description)
                .reduce("", (a, b) -> a + "; " + b));
        this.conflicts = conflicts;
    }

    public List<ConflictDetector.Conflict> getConflicts() { return conflicts; }
}
