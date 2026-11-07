package wil_connect.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class GlobalExceptionHandlerTest {
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestExceptionController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturn401WhenAuthenticationFails() throws Exception {
        mockMvc.perform(post("/test/authentication"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                                   .value("Invalid email or password"));
    }

    @Test
    void shouldReturn403WhenAccessIsDenied() throws Exception {
        mockMvc.perform(post("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                                   .value("You do not have permission to access this resource"));
    }

    @Test
    void shouldReturn404WhenResourceIsNotFound() throws Exception {

        mockMvc.perform(post("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                                   .value("Student not found"));
    }

    @Test
    void shouldReturn500ForUnexpectedException() throws Exception {

        mockMvc.perform(post("/test/general-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                                   .value("An unexpected error occurred"));
    }

    // Test controller used to trigger exceptions
    @RestController
    static class TestExceptionController {

        @PostMapping("/test/authentication")
        public void authentication() {
            throw new BadCredentialsException("Invalid credentials");
        }

        @PostMapping("/test/access-denied")
        public void accessDenied() {
            throw new AccessDeniedException("Access denied");
        }

        @PostMapping("/test/not-found")
        public void notFound() {
            throw new EntityNotFoundException("Student not found");
        }


        @PostMapping("/test/general-error")
        public void generalError() {
            throw new RuntimeException("Unexpected error");
        }
    }

}