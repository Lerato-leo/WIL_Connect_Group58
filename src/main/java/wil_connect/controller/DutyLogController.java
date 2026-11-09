package wil_connect.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import wil_connect.dto.DutyLogRequest;
import wil_connect.service.DutyLogService;
import wil_connect.service.DutyLogService.ProofFile;
import wil_connect.service.DutyLogViews.LogView;

import java.util.List;

/**
 * C4: daily duty logs and proof of work (submitDutyLog, 4.6.5).
 *
 * <p>Logs are sent as multipart form data: logDate, hoursWorked, taskDescription,
 * skillsLearnt and up to 3 files in "proof".</p>
 */
@RestController
public class DutyLogController {

    private final DutyLogService dutyLogService;

    public DutyLogController(DutyLogService dutyLogService) {
        this.dutyLogService = dutyLogService;
    }

    /** My Logs, newest first. */
    @GetMapping("/api/students/duty-logs")
    public ResponseEntity<List<LogView>> myLogs() {
        return ResponseEntity.ok(dutyLogService.myLogs());
    }

    @PostMapping(value = "/api/students/duty-logs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LogView> submit(
            @Valid @ModelAttribute DutyLogRequest form,
            @RequestParam(value = "proof", required = false) List<MultipartFile> proof) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dutyLogService.submit(form, proof));
    }

    /** Corrects a pending or rejected log, or a signed log within 24 hours of sign off. */
    @PutMapping(value = "/api/students/duty-logs/{logId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LogView> correct(
            @PathVariable("logId") int logId,
            @Valid @ModelAttribute DutyLogRequest form,
            @RequestParam(value = "proof", required = false) List<MultipartFile> proof) {
        return ResponseEntity.ok(dutyLogService.correct(logId, form, proof));
    }

    /** Every log of a placement, oldest first (coordinator, or the student who owns it). */
    @GetMapping("/api/placements/{placementId}/duty-logs")
    public ResponseEntity<List<LogView>> placementLogs(@PathVariable("placementId") int placementId) {
        return ResponseEntity.ok(dutyLogService.placementLogs(placementId));
    }

    /** A proof file, shown in the browser. */
    @GetMapping("/api/proofs/{proofId}/file")
    public ResponseEntity<Resource> proofFile(@PathVariable("proofId") int proofId) {
        ProofFile file = dutyLogService.proofFile(proofId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }
}
