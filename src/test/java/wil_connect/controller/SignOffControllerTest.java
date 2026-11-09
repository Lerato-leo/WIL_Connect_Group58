package wil_connect.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import wil_connect.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * signOffLog (4.6.6). Seed: Michael supervises John; log 14 (29 October, 7.5 h) is Pending,
 * log 13 is Rejected and logs 1 to 12 are Signed. Michael's PIN is 1234.
 */
class SignOffControllerTest extends ApiTest {

    private static final String SIGNATURE = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private ResultActions signOff(RequestPostProcessor who, int logId, String body) throws Exception {
        return mvc.perform(post("/api/supervisors/duty-logs/" + logId + "/sign-off").with(who)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions reject(int logId, String reason) throws Exception {
        return mvc.perform(put("/api/supervisors/duty-logs/" + logId + "/reject").with(MICHAEL)
                .contentType(MediaType.APPLICATION_JSON).content("{\"supervisorComment\":\"" + reason + "\"}"));
    }

    // ---------- review ----------

    @Test
    void pendingListHasOnlyMyStudentsPendingLogsOldestFirst() throws Exception {
        mvc.perform(get("/api/supervisors/duty-logs").param("status", "Pending").with(MICHAEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].logId").value(14))
                .andExpect(jsonPath("$[0].studentName").value("John Smith"))
                .andExpect(jsonPath("$[0].studentNumber").value("20240123"))
                .andExpect(jsonPath("$[0].proofs.length()").value(2));
        mvc.perform(get("/api/supervisors/duty-logs").with(LINDA)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void supervisorCanOnlyOpenLogsOfTheirOwnStudents() throws Exception {
        mvc.perform(get("/api/supervisors/duty-logs/14").with(MICHAEL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.taskDescription").exists());
        mvc.perform(get("/api/supervisors/duty-logs/14").with(LINDA)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only sign logs of your own students"));
        signOff(LINDA, 14, "{\"verificationMethod\":\"PIN\",\"pin\":\"1234\"}").andExpect(status().isForbidden());
    }

    // ---------- sign off ----------

    @Test
    void signWithPinAddsASignOffRowAndTellsTheStudent() throws Exception {
        signOff(MICHAEL, 14, "{\"verificationMethod\":\"PIN\",\"pin\":\"1234\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Signed"))
                .andExpect(jsonPath("$.verificationMethod").value("PIN"))
                .andExpect(jsonPath("$.signedAt").exists());

        assertThat(count("SELECT COUNT(*) FROM SIGN_OFF WHERE log_id = 14 AND supervisor_id = 1 AND verification_method = 'PIN' AND signature_data IS NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM NOTIFICATION WHERE user_id = ? AND message = 'Your log for 29 October 2026 was signed off.'", JOHN_USER)).isEqualTo(1);
        var attendance = json(mvc.perform(get("/api/placements/1/attendance").with(JOHN)).andReturn());
        assertThat(attendance.path("signedHours").decimalValue()).isEqualByComparingTo("103.5");
        assertThat(attendance.path("pendingHours").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void signWithDrawnSignatureKeepsTheImage() throws Exception {
        signOff(MICHAEL, 14, "{\"verificationMethod\":\"Signature\",\"signatureData\":\"" + SIGNATURE + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationMethod").value("Signature"));
        assertThat(text("SELECT signature_data FROM SIGN_OFF WHERE log_id = 14")).isEqualTo(SIGNATURE);
    }

    @Test
    void wrongPinIs400SoTheSupervisorStaysLoggedIn() throws Exception {
        signOff(MICHAEL, 14, "{\"verificationMethod\":\"PIN\",\"pin\":\"9999\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Incorrect PIN"));
        assertThat(text("SELECT status FROM DUTY_LOG WHERE log_id = 14")).isEqualTo("Pending");
    }

    @Test
    void fiveWrongPinsLockSigningFor15Minutes() throws Exception {
        for (int i = 0; i < 5; i++) signOff(MICHAEL, 14, "{\"verificationMethod\":\"PIN\",\"pin\":\"0000\"}").andExpect(status().isBadRequest());
        signOff(MICHAEL, 14, "{\"verificationMethod\":\"PIN\",\"pin\":\"1234\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many wrong attempts. Your PIN is locked for 15 more minutes."));
    }

    @Test
    void emptySignatureIsRefused() throws Exception {
        signOff(MICHAEL, 14, "{\"verificationMethod\":\"Signature\",\"signatureData\":\"\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please sign in the box"));
    }

    @Test
    void signatureTooLargeForTheColumnIsRefused() throws Exception {
        String huge = "data:image/png;base64," + "A".repeat(70_000);
        signOff(MICHAEL, 14, "{\"verificationMethod\":\"Signature\",\"signatureData\":\"" + huge + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The signature is too large. Clear it and sign again."));
    }

    @Test
    void unknownMethodIsRefused() throws Exception {
        signOff(MICHAEL, 14, "{\"verificationMethod\":\"Stamp\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose PIN or signature"));
    }

    @Test
    void onlyPendingLogsCanBeSigned() throws Exception {
        signOff(MICHAEL, 1, "{\"verificationMethod\":\"PIN\",\"pin\":\"1234\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This log is not waiting for sign off"));
        signOff(MICHAEL, 13, "{\"verificationMethod\":\"PIN\",\"pin\":\"1234\"}").andExpect(status().isConflict());
    }

    // ---------- reject ----------

    @Test
    void rejectKeepsTheReasonForTheStudent() throws Exception {
        reject(14, "Please add the timesheet.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Rejected"))
                .andExpect(jsonPath("$.supervisorComment").value("Please add the timesheet."));
        assertThat(count("SELECT COUNT(*) FROM NOTIFICATION WHERE user_id = ? AND message = 'Your log for 29 October 2026 was rejected: Please add the timesheet.'", JOHN_USER)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM SIGN_OFF WHERE log_id = 14")).isZero();
    }

    @Test
    void rejectReasonMustBe5To300Characters() throws Exception {
        reject(14, "no").andExpect(status().isBadRequest());
        reject(14, "x".repeat(301)).andExpect(status().isBadRequest());
        assertThat(text("SELECT status FROM DUTY_LOG WHERE log_id = 14")).isEqualTo("Pending");
    }

    @Test
    void onlyPendingLogsCanBeRejected() throws Exception {
        reject(1, "Changing my mind").andExpect(status().isConflict());
    }

    // ---------- lists ----------

    @Test
    void historyListsMySignOffsNewestFirst() throws Exception {
        signOff(MICHAEL, 14, "{\"verificationMethod\":\"PIN\",\"pin\":\"1234\"}").andExpect(status().isOk());
        mvc.perform(get("/api/supervisors/sign-offs").with(MICHAEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[0].logId").value(14))
                .andExpect(jsonPath("$[0].studentName").value("John Smith"))
                .andExpect(jsonPath("$[0].verificationMethod").value("PIN"))
                .andExpect(jsonPath("$[1].logId").value(12));
        mvc.perform(get("/api/supervisors/sign-offs").with(LINDA)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void myStudentsShowTheirHours() throws Exception {
        mvc.perform(get("/api/supervisors/placements").with(MICHAEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentName").value("John Smith"))
                .andExpect(jsonPath("$[0].status").value("Active"))
                .andExpect(jsonPath("$[0].attendance.status").value("IN PROGRESS"))
                .andExpect(jsonPath("$[0].attendance.daysCompleted").value(12));
    }
}
