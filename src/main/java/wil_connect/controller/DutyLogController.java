package wil_connect.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import wil_connect.dto.DutyLogRequest;
import wil_connect.dto.DutyLogResponse;
import wil_connect.service.DutyLogService;

import java.util.List;

/**
 * C4 – Daily duty log endpoints.
 */
@RestController
@RequestMapping("/api/duty-logs")
public class DutyLogController {

    private final DutyLogService dutyLogService;

    public DutyLogController(DutyLogService dutyLogService) {
        this.dutyLogService = dutyLogService;
    }

    /**
     * Submit or correct a duty log (multipart).
     * Part "data" = JSON DutyLogRequest
     * Part "files" = optional proof files (max 3, JPG/PNG/PDF, 5 MB each)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DutyLogResponse> submit(
            @AuthenticationPrincipal Long studentId,
            @Valid @RequestPart("data") DutyLogRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        DutyLogResponse response = dutyLogService.submitDutyLog(studentId, request, files);
        return ResponseEntity.ok(response);
    }

    /** Student's own log history (newest first). */
    @GetMapping("/mine")
    public ResponseEntity<List<DutyLogResponse>> myLogs(
            @AuthenticationPrincipal Long studentId) {
        return ResponseEntity.ok(dutyLogService.getLogsForStudent(studentId));
    }
}
