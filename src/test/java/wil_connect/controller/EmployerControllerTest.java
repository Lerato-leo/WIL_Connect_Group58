package wil_connect.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import wil_connect.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The employer's company details and its supervisors.
 */
class EmployerControllerTest extends ApiTest {

    @Test
    void companyDetailsOfTheLoggedInEmployer() throws Exception {
        mvc.perform(get("/api/employers/me").with(NEXUS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerId").value(1))
                .andExpect(jsonPath("$.employerName").value("Nexus Tech Solutions"))
                .andExpect(jsonPath("$.status").value("Approved"));
        mvc.perform(get("/api/employers/me").with(BRIGHTWORKS)).andExpect(jsonPath("$.status").value("Pending"));
    }

    @Test
    void employerCanUpdateItsDetailsButNotItsStatus() throws Exception {
        mvc.perform(put("/api/employers/me").with(BRIGHTWORKS).contentType(MediaType.APPLICATION_JSON).content("""
                        {"employerName":"BrightWorks Group","address":"45 Loop Street, Cape Town",
                         "contactEmail":"jobs@brightworks.co.za","contactNumber":"0215550111","status":"Approved"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerName").value("BrightWorks Group"))
                .andExpect(jsonPath("$.status").value("Pending"));
        assertThat(text("SELECT contact_email FROM EMPLOYER WHERE employer_id = 2")).isEqualTo("jobs@brightworks.co.za");
    }

    @Test
    void invalidDetailsAreRefused() throws Exception {
        mvc.perform(put("/api/employers/me").with(NEXUS).contentType(MediaType.APPLICATION_JSON).content("""
                        {"employerName":"Nexus","address":"12 Rivonia Road","contactEmail":"hr@nexustech.co.za","contactNumber":"123"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a valid phone number"));
        mvc.perform(put("/api/employers/me").with(NEXUS).contentType(MediaType.APPLICATION_JSON).content("""
                        {"employerName":"Nexus","address":"12 Rivonia Road","contactEmail":"not an email","contactNumber":"0115550100"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supervisorsWithTheirActiveStudents() throws Exception {
        mvc.perform(get("/api/employers/supervisors").with(NEXUS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].lastName").value("Brown"))
                .andExpect(jsonPath("$[0].email").value("michael.brown@richfield.co.za"))
                .andExpect(jsonPath("$[0].activeStudents").value(1))
                .andExpect(jsonPath("$[1].lastName").value("Davis"))
                .andExpect(jsonPath("$[1].activeStudents").value(0));
        mvc.perform(get("/api/employers/supervisors").with(BRIGHTWORKS)).andExpect(jsonPath("$.length()").value(0));
    }
}
