package wil_connect.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import wil_connect.support.ApiTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * submitDutyLog (4.6.5): daily logs, corrections and proof of work files.
 * Seed: John has 12 signed logs, log 13 Rejected (28 October), log 14 Pending (29 October).
 */
class DutyLogControllerTest extends ApiTest {

    private static final String LOGS = "/api/students/duty-logs";
    private static final String TASK = "Tested the duty log upload against the database.";

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("proof", name, "application/pdf", "%PDF-1.4\n% test\n".getBytes(StandardCharsets.US_ASCII));
    }

    private static MockMultipartFile png(String name) {
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        return new MockMultipartFile("proof", name, "image/png", bytes);
    }

    private final String today = LocalDate.now().toString();

    @BeforeEach
    void openPlacement() {
        openJohnsPlacementUntilNextMonth();
    }

    private MockMultipartHttpServletRequestBuilder form(HttpMethod method, String url, String date, String hours,
                                                        String task, String skills, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder b = multipart(method, url);
        for (MockMultipartFile f : files) b.file(f);
        b.param("logDate", date).param("hoursWorked", hours).param("taskDescription", task);
        if (skills != null) b.param("skillsLearnt", skills);
        return b;
    }

    private MvcResult submit(RequestPostProcessor who, String date, String hours, String task, MockMultipartFile... files) throws Exception {
        return mvc.perform(form(HttpMethod.POST, LOGS, date, hours, task, "", files).with(who)).andReturn();
    }

    // ---------- submit ----------

    @Test
    void submitSavesAPendingLogWithItsProofAndTellsTheSupervisor() throws Exception {
        String told = "SELECT COUNT(*) FROM NOTIFICATION WHERE user_id = ? AND message = 'A new log from John Smith needs sign off.'";
        int before = count(told, MICHAEL_USER); // the seed already has one
        MvcResult result = submit(JOHN, today, "7.5", TASK, pdf("timesheet.pdf"));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode log = json(result);
        assertThat(log.path("status").asText()).isEqualTo("Pending");
        assertThat(log.path("hoursWorked").decimalValue()).isEqualByComparingTo("7.5");
        assertThat(log.path("studentName").asText()).isEqualTo("John Smith");
        assertThat(log.path("proofs").get(0).path("fileName").asText()).isEqualTo("timesheet.pdf");
        assertThat(log.path("proofs").get(0).path("fileType").asText()).isEqualTo("application/pdf");

        String stored = text("SELECT file_path FROM PROOF_OF_WORK WHERE log_id = ?", log.path("logId").asInt());
        assertThat(stored).startsWith("uploads/proof/" + today + "_").endsWith("_timesheet.pdf");
        assertThat(Files.exists(Path.of("target/test-uploads/proof").resolve(Path.of(stored).getFileName()))).isTrue();
        assertThat(count(told, MICHAEL_USER)).isEqualTo(before + 1);
    }

    @Test
    void skillsLearntIsOptional() throws Exception {
        MvcResult result = mvc.perform(form(HttpMethod.POST, LOGS, today, "4", TASK, null).with(JOHN)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(text("SELECT skills_learnt FROM DUTY_LOG WHERE log_id = ?", json(result).path("logId").asInt())).isEmpty();
    }

    @Test
    void hoursMustBeBetweenHalfAnHourAnd12() throws Exception {
        MvcResult tooMany = submit(JOHN, today, "12.5", TASK);
        assertThat(tooMany.getResponse().getStatus()).isEqualTo(400);
        assertThat(message(tooMany)).isEqualTo("Hours must be between 0.5 and 12");
        assertThat(submit(JOHN, today, "0.25", TASK).getResponse().getStatus()).isEqualTo(400);
        assertThat(submit(JOHN, today, "12", TASK).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void taskMustBe10To1000Characters() throws Exception {
        MvcResult shortTask = submit(JOHN, today, "4", "Too short");
        assertThat(shortTask.getResponse().getStatus()).isEqualTo(400);
        assertThat(message(shortTask)).isEqualTo("Describe the task in 10 to 1000 characters");
        assertThat(submit(JOHN, today, "4", "x".repeat(1001)).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void skillsMayNotPass500Characters() throws Exception {
        MvcResult result = mvc.perform(form(HttpMethod.POST, LOGS, today, "4", TASK, "s".repeat(501)).with(JOHN)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void hoursThatAreNotANumberGiveAPlainMessage() throws Exception {
        MvcResult result = submit(JOHN, today, "eight", TASK);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(message(result)).isEqualTo("Some of the details are not valid. Check the form and try again.");
    }

    @Test
    void dateCannotBeInTheFuture() throws Exception {
        MvcResult result = submit(JOHN, LocalDate.now().plusDays(1).toString(), "4", TASK);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(message(result)).isEqualTo("The date cannot be in the future");
    }

    @Test
    void dateMustBeInsideThePlacement() throws Exception {
        MvcResult result = submit(JOHN, "2026-08-31", "4", TASK);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(message(result)).isEqualTo("The date is outside your placement period");
    }

    @Test
    void oneLogPerDay() throws Exception {
        MvcResult result = submit(JOHN, "2026-10-29", "4", TASK);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(message(result)).isEqualTo("You already have a log for this date");
    }

    @Test
    void studentWithoutAnActivePlacementCannotLog() throws Exception {
        MvcResult result = submit(SARAH, today, "4", TASK);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(message(result)).isEqualTo("No active placement found");
    }

    // ---------- files ----------

    @Test
    void atMostThreeFiles() throws Exception {
        MvcResult result = submit(JOHN, today, "4", TASK, pdf("a.pdf"), pdf("b.pdf"), pdf("c.pdf"), pdf("d.pdf"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(message(result)).isEqualTo("You can attach up to 3 files");
        assertThat(submit(JOHN, today, "4", TASK, pdf("a.pdf"), png("b.png"), pdf("c.pdf")).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void onlyJpgPngOrPdf() throws Exception {
        MockMultipartFile text = new MockMultipartFile("proof", "notes.txt", "text/plain", "hello".getBytes());
        MvcResult result = submit(JOHN, today, "4", TASK, text);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(message(result)).isEqualTo("Only JPG, PNG or PDF files are allowed");
    }

    @Test
    void aRenamedFileOfAnotherTypeIsRefused() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("proof", "photo.png", "image/png", "not really a picture".getBytes());
        MvcResult result = submit(JOHN, today, "4", TASK, fake);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(message(result)).isEqualTo("The file photo.png is not a real JPG, PNG or PDF");
    }

    @Test
    void eachFileAtMost5Mb() throws Exception {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        big[0] = '%'; big[1] = 'P'; big[2] = 'D'; big[3] = 'F';
        MvcResult result = submit(JOHN, today, "4", TASK, new MockMultipartFile("proof", "big.pdf", "application/pdf", big));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(message(result)).isEqualTo("Each file must be 5 MB or smaller");
    }

    @Test
    void fileNamesCannotEscapeTheUploadFolder() throws Exception {
        MvcResult result = submit(JOHN, today, "4", TASK, pdf("../../evil.pdf"));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String stored = text("SELECT file_path FROM PROOF_OF_WORK WHERE log_id = ?", json(result).path("logId").asInt());
        assertThat(stored).startsWith("uploads/proof/").doesNotContain("..");
    }

    // ---------- corrections ----------

    @Test
    void rejectedLogCanBeCorrectedAndGoesBackToPending() throws Exception {
        String told = "SELECT COUNT(*) FROM NOTIFICATION WHERE user_id = ? AND message LIKE 'A new log from John Smith%'";
        int before = count(told, MICHAEL_USER);
        MvcResult result = mvc.perform(form(HttpMethod.PUT, LOGS + "/13", "2026-10-28", "8",
                "Attended the weekly project stand up and sent the minutes.", "Agile", pdf("timesheet.pdf")).with(JOHN)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode log = json(result);
        assertThat(log.path("status").asText()).isEqualTo("Pending");
        assertThat(log.path("supervisorComment").isNull()).isTrue();
        assertThat(log.path("proofs").size()).isEqualTo(1);
        assertThat(count(told, MICHAEL_USER)).isEqualTo(before + 1);
    }

    @Test
    void signedLogCanBeCorrectedWithin24HoursOfSignOff() throws Exception {
        jdbc.update("UPDATE DUTY_LOG SET status = 'Signed' WHERE log_id = 14");
        jdbc.update("INSERT INTO SIGN_OFF (log_id, supervisor_id, signoff_date, verification_method) VALUES (14, 1, ?, 'PIN')",
                LocalDateTime.now().minusHours(2));
        MvcResult result = mvc.perform(form(HttpMethod.PUT, LOGS + "/14", "2026-10-29", "7", TASK, "SQL").with(JOHN)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).path("status").asText()).isEqualTo("Pending");
    }

    @Test
    void signedLogIsLockedAfter24Hours() throws Exception {
        MvcResult result = mvc.perform(form(HttpMethod.PUT, LOGS + "/1", "2026-09-01", "8", TASK, "").with(JOHN)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(message(result)).isEqualTo("This day was signed off more than 24 hours ago and is locked");
    }

    @Test
    void correctionCannotMoveOntoAnotherLogsDate() throws Exception {
        MvcResult result = mvc.perform(form(HttpMethod.PUT, LOGS + "/14", "2026-10-28", "7", TASK, "").with(JOHN)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void filesAddUpToThreeAcrossCorrections() throws Exception {
        // Log 14 already has 2 proofs in the seed.
        MvcResult result = mvc.perform(form(HttpMethod.PUT, LOGS + "/14", "2026-10-29", "7.5", TASK, "",
                pdf("a.pdf"), pdf("b.pdf")).with(JOHN)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(message(result)).isEqualTo("You can attach up to 3 files");
    }

    @Test
    void studentCannotCorrectAnotherStudentsLog() throws Exception {
        jdbc.update("INSERT INTO PLACEMENT (student_id, employer_id, supervisor_id, start_date, end_date, status) VALUES (2, 1, 2, '2026-09-01', CURRENT_DATE + INTERVAL 30 DAY, 'Active')");
        MvcResult result = mvc.perform(form(HttpMethod.PUT, LOGS + "/14", "2026-10-29", "7.5", TASK, "").with(SARAH)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    // ---------- reading ----------

    @Test
    void myLogsAreNewestFirstWithCommentsAndProofs() throws Exception {
        mvc.perform(get(LOGS).with(JOHN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(14))
                .andExpect(jsonPath("$[0].logDate").value("2026-10-29"))
                .andExpect(jsonPath("$[0].proofs.length()").value(2))
                .andExpect(jsonPath("$[1].status").value("Rejected"))
                .andExpect(jsonPath("$[1].supervisorComment").value("Please attach the timesheet as proof for this day."))
                .andExpect(jsonPath("$[2].status").value("Signed"))
                .andExpect(jsonPath("$[2].verificationMethod").value("PIN"))
                .andExpect(jsonPath("$[2].signedAt").value("2026-10-28T08:30:00"));
    }

    @Test
    void placementLogsForTheCoordinatorAndTheOwnerOnly() throws Exception {
        mvc.perform(get("/api/placements/1/duty-logs").with(COORDINATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].logDate").value("2026-09-01"));
        mvc.perform(get("/api/placements/1/duty-logs").with(JOHN)).andExpect(status().isOk());
        mvc.perform(get("/api/placements/1/duty-logs").with(MICHAEL)).andExpect(status().isOk());
        mvc.perform(get("/api/placements/1/duty-logs").with(SARAH)).andExpect(status().isForbidden());
        mvc.perform(get("/api/placements/1/duty-logs").with(LINDA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/placements/1/duty-logs").with(NEXUS)).andExpect(status().isForbidden());
    }

    @Test
    void proofFileOpensForTheStudentTheirSupervisorAndTheCoordinator() throws Exception {
        MvcResult result = submit(JOHN, today, "4", TASK, pdf("timesheet.pdf"));
        int proofId = json(result).path("proofs").get(0).path("proofId").asInt();
        String url = "/api/proofs/" + proofId + "/file";

        mvc.perform(get(url).with(JOHN)).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("%PDF")));
        mvc.perform(get(url).with(MICHAEL)).andExpect(status().isOk());
        mvc.perform(get(url).with(COORDINATOR)).andExpect(status().isOk());
        mvc.perform(get(url).with(LINDA)).andExpect(status().isNotFound());
        mvc.perform(get(url).with(SARAH)).andExpect(status().isNotFound());
        mvc.perform(get(url).with(NEXUS)).andExpect(status().isNotFound());
    }

    @Test
    void missingProofFileGivesAPlainMessage() throws Exception {
        // The seed rows point at files that are not on disk.
        mvc.perform(get("/api/proofs/1/file").with(JOHN)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This file is missing on the server"));
    }
}
