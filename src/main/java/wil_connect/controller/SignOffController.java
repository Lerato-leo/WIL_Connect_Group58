package wil_connect.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import wil_connect.dto.DutyLogResponse;
import wil_connect.dto.SignOffRequest;
import wil_connect.service.SignOffService;

import java.util.List;

/**
 * C5 – Supervisor sign-off endpoints.
 */
@RestController
@RequestMapping("/api/sign-offs")
public class SignOffController {

    private final SignOffService signOffService;

    public SignOffController(SignOffService signOffService) {
        this.signOffService = signOffService;
    }

    /** Pending logs waiting for the logged-in supervisor (oldest first). */
    @GetMapping("/pending")
    public ResponseEntity<List<DutyLogResponse>> pending(
            @AuthenticationPrincipal Long supervisorId) {
        return ResponseEntity.ok(signOffService.getPendingLogs(supervisorId));
    }

    /**
     * Perform sign-off or rejection.
     * Body example for PIN:
     * { "logId": 1, "decision": "SIGNED", "verificationMethod": "PIN", "pin": "1234" }
     * Body example for signature:
     * { "logId": 1, "decision": "SIGNED", "verificationMethod": "SIGNATURE", "signatureData": "data:image/png;base64,..." }
     * Body example for rejection:
     * { "logId": 1, "decision": "REJECTED", "rejectionReason": "Hours claimed are incorrect" }
     */
    @PostMapping
    public ResponseEntity<DutyLogResponse> signOff(
            @AuthenticationPrincipal Long supervisorId,
            @Valid @RequestBody SignOffRequest request) {
        return ResponseEntity.ok(signOffService.signOffLog(supervisorId, request));
    }
}
