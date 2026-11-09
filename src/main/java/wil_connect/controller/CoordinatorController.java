package wil_connect.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import wil_connect.service.AttendanceService;
import wil_connect.service.AttendanceService.AttendanceProgress;

@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private final JdbcTemplate jdbc;
    private final AttendanceService attendanceService;

    public CoordinatorController(JdbcTemplate jdbc, AttendanceService attendanceService) {
        this.jdbc = jdbc;
        this.attendanceService = attendanceService;
    }

    @GetMapping("/student-progress")
    public ResponseEntity<List<StudentProgress>> getStudentProgress(Principal principal) {
        if (!isCoordinator(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<PlacementDetails> placements = jdbc.query("""
                SELECT p.placement_id,
                       s.student_number,
                       CONCAT(s.first_name, ' ', s.last_name) AS student_name,
                       e.employer_name,
                       CONCAT(v.first_name, ' ', v.last_name) AS supervisor_name,
                       p.start_date,
                       p.end_date,
                       (SELECT COUNT(*) FROM DUTY_LOG d
                        WHERE d.placement_id = p.placement_id AND d.status = 'Pending') AS unverified_logs,
                       (SELECT COUNT(*) FROM DUTY_LOG d
                        WHERE d.placement_id = p.placement_id AND d.status = 'Pending'
                          AND d.log_date < CURRENT_DATE - INTERVAL 3 DAY) AS waiting_logs
                FROM PLACEMENT p
                JOIN STUDENT s ON s.student_id = p.student_id
                JOIN EMPLOYER e ON e.employer_id = p.employer_id
                JOIN SUPERVISOR v ON v.supervisor_id = p.supervisor_id
                ORDER BY s.last_name, s.first_name, p.placement_id
                """, (row, index) -> new PlacementDetails(
                row.getInt("placement_id"),
                row.getString("student_number"),
                row.getString("student_name"),
                row.getString("employer_name"),
                row.getString("supervisor_name"),
                row.getObject("start_date", LocalDate.class),
                row.getObject("end_date", LocalDate.class),
                row.getInt("unverified_logs"),
                row.getInt("waiting_logs")));

        List<StudentProgress> progress = placements.stream()
                .map(placement -> new StudentProgress(
                        placement.placementId(),
                        placement.studentNumber(),
                        placement.studentName(),
                        placement.employerName(),
                        placement.supervisorName(),
                        attendanceService.calculateAttendance(placement.placementId()),
                        placement.unverifiedLogs(),
                        placement.logsWaitingOver3Days(),
                        placement.startDate(),
                        placement.endDate()))
                .toList();

        return ResponseEntity.ok(progress);
    }

    /**
     * Sign Off Audit Log: every SIGN_OFF row, newest first, read only.
     * Days are counted from the log date, because DUTY_LOG keeps no submission time.
     */
    @GetMapping("/sign-offs")
    public ResponseEntity<List<SignOffAudit>> getSignOffAudit(Principal principal) {
        if (!isCoordinator(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(jdbc.query("""
                SELECT o.signoff_id, o.log_id, d.log_date, o.verification_method, o.signoff_date,
                       CONCAT(s.first_name, ' ', s.last_name) AS student_name, s.student_number,
                       CONCAT(v.first_name, ' ', v.last_name) AS supervisor_name
                FROM SIGN_OFF o
                JOIN DUTY_LOG d ON d.log_id = o.log_id
                JOIN PLACEMENT p ON p.placement_id = d.placement_id
                JOIN STUDENT s ON s.student_id = p.student_id
                JOIN SUPERVISOR v ON v.supervisor_id = o.supervisor_id
                ORDER BY o.signoff_date DESC, o.signoff_id DESC
                """, (row, index) -> new SignOffAudit(
                row.getInt("signoff_id"),
                row.getInt("log_id"),
                row.getObject("log_date", LocalDate.class),
                row.getString("student_name"),
                row.getString("student_number"),
                row.getString("supervisor_name"),
                row.getString("verification_method"),
                row.getObject("signoff_date", LocalDateTime.class))));
    }

    @GetMapping("/employers")
    public ResponseEntity<List<EmployerSummary>> getEmployers(Principal principal) {
        if (!isCoordinator(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<EmployerSummary> employers = jdbc.query("""
                SELECT employer_id, employer_name, status
                FROM EMPLOYER
                ORDER BY employer_name, employer_id
                """, (row, index) -> new EmployerSummary(
                row.getInt("employer_id"),
                row.getString("employer_name"),
                row.getString("status")));

        return ResponseEntity.ok(employers);
    }

    @PatchMapping("/employers/{employerId}/approve")
    public ResponseEntity<Void> approveEmployer(
            @PathVariable("employerId") int employerId, Principal principal) {
        return changeEmployerStatus(principal, employerId, "Pending", "Approved");
    }

    @PatchMapping("/employers/{employerId}/deactivate")
    public ResponseEntity<Void> deactivateEmployer(
            @PathVariable("employerId") int employerId, Principal principal) {
        return changeEmployerStatus(principal, employerId, "Approved", "Inactive");
    }

    private ResponseEntity<Void> changeEmployerStatus(
            Principal principal, int employerId, String expectedStatus, String newStatus) {
        if (!isCoordinator(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        int updated = jdbc.update("""
                UPDATE EMPLOYER
                SET status = ?
                WHERE employer_id = ? AND UPPER(status) = UPPER(?)
                """, newStatus, employerId, expectedStatus);

        if (updated == 1) {
            return ResponseEntity.noContent().build();
        }

        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM EMPLOYER WHERE employer_id = ?
                )
                """, Boolean.class, employerId);

        return ResponseEntity.status(
                        Boolean.TRUE.equals(exists) ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND)
                .build();
    }

    private boolean isCoordinator(Principal principal) {
        Boolean coordinator = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM USER_ACCOUNT
                    WHERE email = ? AND UPPER(role) = 'COORDINATOR'
                )
                """, Boolean.class, principal.getName());

        return Boolean.TRUE.equals(coordinator);
    }

    private record PlacementDetails(
            int placementId,
            String studentNumber,
            String studentName,
            String employerName,
            String supervisorName,
            LocalDate startDate,
            LocalDate endDate,
            int unverifiedLogs,
            int logsWaitingOver3Days) {
    }

    public record StudentProgress(
            int placementId,
            String studentNumber,
            String studentName,
            String employerName,
            String supervisorName,
            AttendanceProgress attendance,
            int unverifiedLogs,
            int logsWaitingOver3Days,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record SignOffAudit(
            int signoffId,
            int logId,
            LocalDate logDate,
            String studentName,
            String studentNumber,
            String supervisorName,
            String verificationMethod,
            LocalDateTime signoffDate) {
    }

    public record EmployerSummary(int employerId, String employerName, String status) {
    }
}