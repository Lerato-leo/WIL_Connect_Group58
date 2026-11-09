package wil_connect.controller;

import org.junit.jupiter.api.Test;
import wil_connect.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The coordinator's views (C7), the compliance report (4.6.9) and notifications (4.6.8).
 */
class CoordinatorApiTest extends ApiTest {

    @Test
    void progressShowsHoursAndLogsWaitingForSignOff() throws Exception {
        mvc.perform(get("/api/coordinator/student-progress").with(COORDINATOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentName").value("John Smith"))
                .andExpect(jsonPath("$[0].attendance.daysCompleted").value(12))
                .andExpect(jsonPath("$[0].unverifiedLogs").value(1))
                // log 14 is dated 29 October, more than 3 days before any run after 1 November 2026
                .andExpect(jsonPath("$[0].logsWaitingOver3Days").value(1))
                .andExpect(jsonPath("$[0].startDate").value("2026-09-01"))
                .andExpect(jsonPath("$[0].endDate").value("2026-11-30"));
    }

    @Test
    void auditListsEverySignOffNewestFirst() throws Exception {
        mvc.perform(get("/api/coordinator/sign-offs").with(COORDINATOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].logId").value(12))
                .andExpect(jsonPath("$[0].logDate").value("2026-10-27"))
                .andExpect(jsonPath("$[0].studentNumber").value("20240123"))
                .andExpect(jsonPath("$[0].supervisorName").value("Michael Brown"))
                .andExpect(jsonPath("$[0].verificationMethod").value("PIN"))
                .andExpect(jsonPath("$[1].verificationMethod").value("Signature"));
    }

    @Test
    void approveOnlyAPendingEmployer() throws Exception {
        mvc.perform(patch("/api/coordinator/employers/2/approve").with(COORDINATOR)).andExpect(status().isNoContent());
        assertThat(text("SELECT status FROM EMPLOYER WHERE employer_id = 2")).isEqualTo("Approved");
        mvc.perform(patch("/api/coordinator/employers/2/approve").with(COORDINATOR)).andExpect(status().isConflict());
        mvc.perform(patch("/api/coordinator/employers/999/approve").with(COORDINATOR)).andExpect(status().isNotFound());
    }

    @Test
    void deactivatedEmployerLeavesTheSearch() throws Exception {
        mvc.perform(patch("/api/coordinator/employers/1/deactivate").with(COORDINATOR)).andExpect(status().isNoContent());
        mvc.perform(get("/api/students/employers").with(JOHN)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/auth/employers")).andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- compliance report ----------

    @Test
    void reportIsAPdfForTheStudentAndTheCoordinator() throws Exception {
        byte[] pdf = mvc.perform(get("/api/placements/1/compliance-report").with(JOHN))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        mvc.perform(get("/api/placements/1/compliance-report").with(COORDINATOR)).andExpect(status().isOk());
    }

    @Test
    void reportReadsLikeTheMockup() throws Exception {
        byte[] pdf = mvc.perform(get("/api/placements/1/compliance-report").with(COORDINATOR))
                .andReturn().getResponse().getContentAsByteArray();
        String text;
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            int pages = document.getNumberOfPages();
            assertThat(text).contains("Page 1 of " + pages).contains("Page " + pages + " of " + pages);
        }
        assertThat(text)
                .contains("Work Integrated Learning Compliance Report")
                .contains("John Smith (20240123)")
                .contains("Placement: 1 September 2026 to 30 November 2026")
                .contains("Signed hours: 96.00 / 150")
                .contains("27 Oct 2026")
                .doesNotContain("2026-09-01");
    }

    @Test
    void reportIsPrivateToTheStudent() throws Exception {
        mvc.perform(get("/api/placements/1/compliance-report").with(SARAH)).andExpect(status().isForbidden());
        mvc.perform(get("/api/placements/1/compliance-report").with(NEXUS)).andExpect(status().isForbidden());
    }

    @Test
    void noReportWithoutSignedLogs() throws Exception {
        jdbc.update("UPDATE DUTY_LOG SET status = 'Pending' WHERE placement_id = 1");
        mvc.perform(get("/api/placements/1/compliance-report").with(COORDINATOR)).andExpect(status().isConflict());
    }

    // ---------- notifications ----------

    @Test
    void notificationsAreNewestFirstWithAnUnreadCount() throws Exception {
        mvc.perform(get("/api/notifications").with(JOHN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].read").value(false));
    }

    @Test
    void markAsReadOnlyChangesYourOwnMessages() throws Exception {
        int johns = jdbc.queryForObject("SELECT notification_id FROM NOTIFICATION WHERE user_id = ? AND is_read = FALSE", Integer.class, JOHN_USER);
        mvc.perform(patch("/api/notifications/" + johns + "/read").with(MICHAEL)).andExpect(status().isNoContent());
        assertThat(count("SELECT COUNT(*) FROM NOTIFICATION WHERE notification_id = ? AND is_read = FALSE", johns)).isEqualTo(1);
        mvc.perform(patch("/api/notifications/" + johns + "/read").with(JOHN)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").with(JOHN)).andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void attendanceIsPrivateToTheStudentTheirSupervisorAndTheCoordinator() throws Exception {
        mvc.perform(get("/api/placements/1/attendance").with(JOHN)).andExpect(status().isOk());
        mvc.perform(get("/api/placements/1/attendance").with(MICHAEL)).andExpect(status().isOk());
        mvc.perform(get("/api/placements/1/attendance").with(COORDINATOR)).andExpect(status().isOk());
        mvc.perform(get("/api/placements/1/attendance").with(SARAH)).andExpect(status().isForbidden());
        mvc.perform(get("/api/placements/1/attendance").with(LINDA)).andExpect(status().isForbidden());
    }
}
