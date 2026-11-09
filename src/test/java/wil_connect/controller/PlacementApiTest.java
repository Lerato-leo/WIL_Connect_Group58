package wil_connect.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import wil_connect.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * searchEmployers, sendPlacementRequest and respondToRequest (4.6.4) through the HTTP API.
 * Seed: request 1 John to Nexus Accepted, request 2 Sarah to Nexus Meeting Requested,
 * request 3 John to BrightWorks Rejected; placement 1 John at Nexus, Active.
 */
class PlacementApiTest extends ApiTest {

    private static final int SARAH_USER = 3;
    private static final int NEXUS_USER = 6;

    @Test
    void searchShowsApprovedEmployersWithTheirStatusText() throws Exception {
        mvc.perform(get("/api/students/employers").with(JOHN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("Approved"));
        mvc.perform(get("/api/students/employers").param("search", "nexus").with(JOHN)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/students/employers").param("search", "bright").with(JOHN)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void studentSendsARequestAndTheEmployerIsTold() throws Exception {
        jdbc.update("UPDATE PLACEMENT_REQUEST SET status = 'Rejected' WHERE request_id = 2");
        String told = "SELECT COUNT(*) FROM NOTIFICATION WHERE user_id = ? AND message = 'New placement request from Sarah Jones.'";
        int before = count(told, NEXUS_USER); // the seed already has one
        mvc.perform(post("/api/students/placement-requests").with(SARAH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employerId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Pending"))
                .andExpect(jsonPath("$.employerName").value("Nexus Tech Solutions"));
        assertThat(count(told, NEXUS_USER)).isEqualTo(before + 1);
    }

    @Test
    void studentRequestsShowTheStatusText() throws Exception {
        mvc.perform(get("/api/students/placement-requests").with(SARAH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("Meeting Requested"));
    }

    @Test
    void employerRequestsCanBeFilteredByStatus() throws Exception {
        mvc.perform(get("/api/employers/placement-requests").with(NEXUS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/employers/placement-requests").param("status", "Meeting Requested").with(NEXUS))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentFirstName").value("Sarah"))
                .andExpect(jsonPath("$[0].status").value("Meeting Requested"));
    }

    @Test
    void acceptCreatesAnActivePlacementAndTellsTheStudent() throws Exception {
        mvc.perform(put("/api/employers/placement-requests/2/accept").with(NEXUS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":2,\"startDate\":\"2026-11-10\",\"endDate\":\"2027-02-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Active"))
                .andExpect(jsonPath("$.studentName").value("Sarah Jones"))
                .andExpect(jsonPath("$.supervisorName").value("Linda Davis"));
        assertThat(count("SELECT COUNT(*) FROM NOTIFICATION WHERE user_id = ? AND message = 'Your placement request was updated: Accepted.'", SARAH_USER)).isEqualTo(1);
        mvc.perform(get("/api/students/placements").with(SARAH)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void meetingMessageReachesTheStudent() throws Exception {
        jdbc.update("UPDATE PLACEMENT_REQUEST SET status = 'Pending' WHERE request_id = 2");
        mvc.perform(put("/api/employers/placement-requests/2/meeting").with(NEXUS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Come in on Monday at 10.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Meeting Requested"));
        assertThat(count("SELECT COUNT(*) FROM NOTIFICATION WHERE user_id = ? AND message = 'Your placement request was updated: Meeting Requested. Come in on Monday at 10.'", SARAH_USER)).isEqualTo(1);
    }

    @Test
    void rejectTellsTheStudent() throws Exception {
        mvc.perform(put("/api/employers/placement-requests/2/reject").with(NEXUS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("Rejected"));
        assertThat(count("SELECT COUNT(*) FROM NOTIFICATION WHERE user_id = ? AND message = 'Your placement request was updated: Rejected.'", SARAH_USER)).isEqualTo(1);
    }

    @Test
    void anotherEmployerCannotAnswerTheRequest() throws Exception {
        mvc.perform(put("/api/employers/placement-requests/2/reject").with(BRIGHTWORKS)).andExpect(status().isForbidden());
    }

    @Test
    void placementsForTheStudentAndTheEmployer() throws Exception {
        mvc.perform(get("/api/students/placements").with(JOHN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employerName").value("Nexus Tech Solutions"))
                .andExpect(jsonPath("$[0].supervisorName").value("Michael Brown"))
                .andExpect(jsonPath("$[0].status").value("Active"));
        mvc.perform(get("/api/students/placements").with(SARAH)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/employers/placements").with(NEXUS))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].studentNumber").value("20240123"));
        mvc.perform(get("/api/employers/placements").with(BRIGHTWORKS)).andExpect(jsonPath("$.length()").value(0));
    }
}
