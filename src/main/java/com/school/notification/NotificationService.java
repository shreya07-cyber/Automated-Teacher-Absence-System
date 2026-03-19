package com.school.notification;

import com.school.model.*;
import com.school.service.ProxyAssignmentService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

/**
 * Notification dispatcher (console/log stub — replace with SMTP / SMS gateway in production).
 */
public class NotificationService {

    private static final Logger LOGGER = Logger.getLogger(NotificationService.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    public void notifyAbsenceDetected(AbsenceRecord record) {
        String msg = String.format(
                "[NOTIFICATION] 🔔 Absence Detected\n" +
                "  Teacher : %s\n" +
                "  Date    : %s\n" +
                "  Type    : %s\n" +
                "  Reason  : %s\n" +
                "  Reported: %s",
                record.getAbsentTeacher().getName(),
                record.getDate(),
                record.getType(),
                record.getReason(),
                record.getReportedAt().format(FMT));
        LOGGER.info(msg);
        System.out.println(msg);
    }

    public void notifyProxyAssigned(ProxyAssignment assignment) {
        Period p = assignment.getPeriod();
        String msg = String.format(
                "[NOTIFICATION] ✅ Proxy Assigned\n" +
                "  Proxy   : %s\n" +
                "  Period  : %d (%s – %s) on %s\n" +
                "  Subject : %s | Grade: %s | Room: %s\n" +
                "  Score   : %.2f\n" +
                "  Notes   : %s",
                assignment.getProxyTeacher().getName(),
                p.getPeriodNumber(), p.getStartTime(), p.getEndTime(), p.getDate(),
                p.getSubject(), p.getGrade(), p.getClassroom(),
                assignment.getConfidenceScore(),
                assignment.getNotes() != null ? assignment.getNotes() : "—");
        LOGGER.info(msg);
        System.out.println(msg);
    }

    public void notifyVacantPeriod(Period period) {
        String msg = String.format(
                "[NOTIFICATION] ⚠️  Unresolved Vacant Period\n" +
                "  Period  : %d (%s – %s) on %s\n" +
                "  Subject : %s | Grade: %s | Room: %s\n" +
                "  Action  : Manual intervention required",
                period.getPeriodNumber(), period.getStartTime(), period.getEndTime(), period.getDate(),
                period.getSubject(), period.getGrade(), period.getClassroom());
        LOGGER.warning(msg);
        System.out.println(msg);
    }

    public void sendAssignmentReport(ProxyAssignmentService.AssignmentReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════╗\n");
        sb.append("║      ASSIGNMENT REPORT               ║\n");
        sb.append("╠══════════════════════════════════════╣\n");
        sb.append(String.format("║  Absent : %-27s║\n", report.getAbsenceRecord().getAbsentTeacher().getName()));
        sb.append(String.format("║  Date   : %-27s║\n", report.getAbsenceRecord().getDate()));
        sb.append(String.format("║  Status : %-27s║\n", report.getAbsenceRecord().getStatus()));
        sb.append(String.format("║  Covered: %-3d / Uncovered: %-14s║\n",
                report.getAssignments().size(), report.getFailures().size() + "         "));
        sb.append("╠══════════════════════════════════════╣\n");

        if (!report.getAssignments().isEmpty()) {
            sb.append("║  COVERED PERIODS                     ║\n");
            for (ProxyAssignment a : report.getAssignments()) {
                sb.append(String.format("║  P%-2d → %-30s║\n",
                        a.getPeriod().getPeriodNumber(),
                        a.getProxyTeacher().getName()));
            }
        }

        if (!report.getFailures().isEmpty()) {
            sb.append("║  UNCOVERED PERIODS                   ║\n");
            for (ProxyAssignmentService.PeriodFailure f : report.getFailures()) {
                sb.append(String.format("║  P%-2d → %-30s║\n",
                        f.period().getPeriodNumber(), "⚠ " + f.reason()));
            }
        }

        sb.append("╚══════════════════════════════════════╝\n");
        System.out.println(sb);
    }
}
