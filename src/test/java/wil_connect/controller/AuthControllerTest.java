package wil_connect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import wil_connect.entity.UserAccount;
import wil_connect.repository.UserAccountRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    // TEST 1: Successful registration
    @Test
    void shouldRegisterStudentSuccessfully() throws Exception {

        String requestBody = """
                {
                    "email": "teststudent@wilconnect.co.za",
                    "password": "TestPass123!"
                }
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("teststudent@wilconnect.co.za"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.userId").isNumber());
        UserAccount user = userAccountRepository
                .findByEmail("teststudent@wilconnect.co.za")
                .orElseThrow();

        assertEquals("teststudent@wilconnect.co.za", user.getEmail());
        assertEquals("STUDENT", user.getRole().name());

        assertTrue(passwordEncoder.matches("TestPass123!", user.getPassword()));
    }
}