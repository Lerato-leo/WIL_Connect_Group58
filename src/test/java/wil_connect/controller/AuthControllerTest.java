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
import wil_connect.enums.Role;
import wil_connect.repository.UserAccountRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRegisterStudentSuccessfully() throws Exception {

        String requestBody = """
                {
                    "email": "teststudent@wilconnect.co.za",
                    "password": "password"
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

        assertTrue(passwordEncoder.matches("password!", user.getPassword()));
    }

    // TEST 2: Successful login
    @Test
    void shouldLoginStudentSuccessfully() throws Exception {
        String requestBody = """
                {
                    "email": "sarah.jones@richfield.co.za",
                    "password": "password"
                }
                """;

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value("sarah.jones@richfield.co.za"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"));
    }
}