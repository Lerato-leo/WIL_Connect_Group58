package wil_connect.controller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import wil_connect.entity.UserAccount;
import wil_connect.repository.UserAccountRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Registers a student and then logs in with that account, in this order.
 * The account is removed before and after, so the tests can run again on the same database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthControllerTest {

    private static final String EMAIL = "teststudent2@wilconnect.co.za";
    private static final String PASSWORD = "password1"; // register needs a letter and a number

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    /** The STUDENT row points at the account, so it has to go first. */
    private void removeTestAccount() {
        jdbc.update("DELETE FROM STUDENT WHERE user_id IN (SELECT user_id FROM USER_ACCOUNT WHERE email = ?)", EMAIL);
        jdbc.update("DELETE FROM USER_ACCOUNT WHERE email = ?", EMAIL);
    }

    @AfterAll
    void cleanUp() {
        removeTestAccount();
    }

    @Test
    @Order(1)
    void shouldRegisterStudentSuccessfully() throws Exception {
        removeTestAccount();

        // A student needs their name and student number as well as the account details.
        String requestBody = """
                {
                    "email": "teststudent2@wilconnect.co.za",
                    "password": "password1",
                    "role": "STUDENT",
                    "firstName": "Test",
                    "lastName": "Student",
                    "studentNumber": "20240999"
                }
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.userId").isNumber());
        UserAccount user = userAccountRepository
                .findByEmail(EMAIL)
                .orElseThrow();

        assertEquals(EMAIL, user.getEmail());
        assertEquals("STUDENT", user.getRole().name());
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPassword()));

        // The student row is saved together with the account.
        Integer students = jdbc.queryForObject(
                "SELECT COUNT(*) FROM STUDENT WHERE user_id = ? AND student_number = '20240999'",
                Integer.class, user.getUserId());
        assertEquals(1, students);
    }

    // TEST 2: Successful login with the account from test 1
    @Test
    @Order(2)
    void shouldLoginStudentSuccessfully() throws Exception {
        String requestBody = """
                {
                    "email": "teststudent2@wilconnect.co.za",
                    "password": "password1"
                }
                """;

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.name").value("Test Student"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"));
    }
}
