package wil_connect.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import wil_connect.support.ApiTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/**
 * checkAccess (4.6.3): each role only reaches its own part of the API.
 */
class SecurityRulesTest extends ApiTest {

    @Test
    void noLoginGivesJson401WithoutTheBrowserLoginBox() throws Exception {
        mvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.message").value("Your session has ended. Please log in again."));
    }

    @Test
    void wrongPasswordOnARequestGives401() throws Exception {
        mvc.perform(get("/api/notifications").with(httpBasic("john.smith@richfield.co.za", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void studentCannotUseOtherRolesApis() throws Exception {
        mvc.perform(get("/api/employers/placements").with(JOHN)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this page."));
        mvc.perform(get("/api/supervisors/duty-logs").with(JOHN)).andExpect(status().isForbidden());
        mvc.perform(get("/api/coordinator/employers").with(JOHN)).andExpect(status().isForbidden());
    }

    @Test
    void supervisorCannotUseStudentOrCoordinatorApis() throws Exception {
        mvc.perform(get("/api/students/duty-logs").with(MICHAEL)).andExpect(status().isForbidden());
        mvc.perform(get("/api/coordinator/sign-offs").with(MICHAEL)).andExpect(status().isForbidden());
    }

    @Test
    void employerCannotUseCoordinatorApis() throws Exception {
        mvc.perform(get("/api/coordinator/student-progress").with(NEXUS)).andExpect(status().isForbidden());
    }

    @Test
    void eachRoleReachesItsOwnApi() throws Exception {
        mvc.perform(get("/api/students/duty-logs").with(JOHN)).andExpect(status().isOk());
        mvc.perform(get("/api/supervisors/duty-logs").with(MICHAEL)).andExpect(status().isOk());
        mvc.perform(get("/api/employers/me").with(NEXUS)).andExpect(status().isOk());
        mvc.perform(get("/api/coordinator/employers").with(COORDINATOR)).andExpect(status().isOk());
    }

    @Test
    void publicEndpointsNeedNoLogin() throws Exception {
        mvc.perform(get("/api/auth/employers")).andExpect(status().isOk());
    }

    @Test
    void frontendOnLocalhostPassesTheCorsCheck() throws Exception {
        mvc.perform(options("/api/students/duty-logs")
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:5510")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:5510"));
    }

    @Test
    void otherWebsitesFailTheCorsCheck() throws Exception {
        mvc.perform(options("/api/students/duty-logs")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownPathGives404NotA500() throws Exception {
        mvc.perform(get("/api/students/duty-logs/").with(JOHN)).andExpect(status().isNotFound());
    }
}
