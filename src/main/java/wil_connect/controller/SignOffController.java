package wil_connect.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wil_connect.service.DutyLogViews.LogView;
import wil_connect.service.SignOffService;
import wil_connect.service.SignOffService.RejectDetails;
import wil_connect.service.SignOffService.SignOffDetails;
import wil_connect.service.SignOffService.SignedLog;
import wil_connect.service.SignOffService.SupervisedPlacement;

import java.util.List;

/**
 * C5: supervisor review and sign off (signOffLog, 4.6.6). Supervisors only (SecurityConfig).
 */
@RestController
@RequestMapping("/api/supervisors")
public class SignOffController {

    private final SignOffService signOffService;

    public SignOffController(SignOffService signOffService) {
        this.signOffService = signOffService;
    }

    /** Logs of my students with a status (Pending by default), oldest first. */
    @GetMapping("/duty-logs")
    public ResponseEntity<List<LogView>> logs(@RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(signOffService.logs(status));
    }

    @GetMapping("/duty-logs/{logId}")
    public ResponseEntity<LogView> log(@PathVariable("logId") int logId) {
        return ResponseEntity.ok(signOffService.logForReview(logId));
    }

    /** Body: { "verificationMethod": "PIN", "pin": "1234" } or { "verificationMethod": "Signature", "signatureData": "data:image/png;base64,..." } */
    @PostMapping("/duty-logs/{logId}/sign-off")
    public ResponseEntity<LogView> signOff(@PathVariable("logId") int logId, @RequestBody SignOffDetails details) {
        return ResponseEntity.ok(signOffService.signOff(logId, details));
    }

    /** Body: { "supervisorComment": "reason, 5 to 300 characters" } */
    @PutMapping("/duty-logs/{logId}/reject")
    public ResponseEntity<LogView> reject(@PathVariable("logId") int logId, @RequestBody RejectDetails details) {
        return ResponseEntity.ok(signOffService.reject(logId, details));
    }

    @GetMapping("/sign-offs")
    public ResponseEntity<List<SignedLog>> history() {
        return ResponseEntity.ok(signOffService.history());
    }

    @GetMapping("/placements")
    public ResponseEntity<List<SupervisedPlacement>> students() {
        return ResponseEntity.ok(signOffService.students());
    }
}
