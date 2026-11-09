package wil_connect.controller;

import java.io.IOException;
import java.security.Principal;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import wil_connect.service.ComplianceReportService;

@RestController
public class ComplianceReportController {

    private final JdbcTemplate jdbc;
    private final ComplianceReportService reportService;

    public ComplianceReportController(JdbcTemplate jdbc, ComplianceReportService reportService) {
        this.jdbc = jdbc;
        this.reportService = reportService;
    }

    @GetMapping("/api/placements/{placementId}/compliance-report")
    public ResponseEntity<?> downloadReport(
            @PathVariable("placementId") int placementId, Principal principal) throws IOException {
        Boolean allowed = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM PLACEMENT p
                    JOIN STUDENT s ON s.student_id = p.student_id
                    JOIN USER_ACCOUNT a ON a.email = ?
                    WHERE p.placement_id = ?
                      AND (UPPER(a.role) = 'COORDINATOR'
                           OR (UPPER(a.role) = 'STUDENT' AND s.user_id = a.user_id))
                )
                """, Boolean.class, principal.getName(), placementId);

        if (!Boolean.TRUE.equals(allowed)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var report = reportService.generateComplianceReport(placementId);
        if (report.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("There are no signed logs to include in the report.");
        }

        var pdf = report.get();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }
}
