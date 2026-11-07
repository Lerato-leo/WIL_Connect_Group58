package wil_connect.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
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
        Boolean coordinator = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM USER_ACCOUNT
                    WHERE email = ? AND UPPER(role) = 'COORDINATOR'
                )
                """, Boolean.class, principal.getName());

        if (!Boolean.TRUE.equals(coordinator)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<PlacementDetails> placements = jdbc.query("""
                SELECT p.placement_id,
                       s.student_number,
                       CONCAT(s.first_name, ' ', s.last_name) AS student_name,
                       e.employer_name,
                       CONCAT(v.first_name, ' ', v.last_name) AS supervisor_name
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
                row.getString("supervisor_name")));

        List<StudentProgress> progress = placements.stream()
                .map(placement -> new StudentProgress(
                        placement.placementId(),
                        placement.studentNumber(),
                        placement.studentName(),
                        placement.employerName(),
                        placement.supervisorName(),
                        attendanceService.calculateAttendance(placement.placementId())))
                .toList();

        return ResponseEntity.ok(progress);
    }

    private record PlacementDetails(
            int placementId,
            String studentNumber,
            String studentName,
            String employerName,
            String supervisorName) {
    }

    public record StudentProgress(
            int placementId,
            String studentNumber,
            String studentName,
            String employerName,
            String supervisorName,
            AttendanceProgress attendance) {
    }
}
