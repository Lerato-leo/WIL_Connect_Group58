package wil_connect.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import wil_connect.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * registerUser (4.6.2) and login (4.6.3).
 */
class AuthRegistrationTest extends ApiTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearLoginLocks() {
        attemptLimiter.reset("login:nobody@richfield.co.za");
        attemptLimiter.reset("login:sarah.jones@richfield.co.za");
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        return mvc.perform(request);
    }

    // ---------- register ----------

    @Test
    void studentGetsAccountAndStudentRowTogether() throws Exception {
        register("""
                {"email":"Thandi.Nkosi@richfield.co.za","password":"secret123","role":"STUDENT",
                 "firstName":"Thandi","lastName":"Nkosi","studentNumber":"20245555"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.email").value("thandi.nkosi@richfield.co.za"));

        assertThat(count("""
                SELECT COUNT(*) FROM STUDENT s JOIN USER_ACCOUNT a ON a.user_id = s.user_id
                WHERE a.email = 'thandi.nkosi@richfield.co.za' AND s.student_number = '20245555' AND s.first_name = 'Thandi'
                """)).isEqualTo(1);
    }

    @Test
    void newStudentCanUseTheStudentApiStraightAway() throws Exception {
        register("""
                {"email":"thandi@richfield.co.za","password":"secret123","role":"STUDENT",
                 "firstName":"Thandi","lastName":"Nkosi","studentNumber":"20245555"}
                """).andExpect(status().isCreated());
        mvc.perform(get("/api/students/placement-requests").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .httpBasic("thandi@richfield.co.za", "secret123")))
                .andExpect(status().isOk());
    }

    @Test
    void supervisorRowKeepsThePinAsAHash() throws Exception {
        register("""
                {"email":"sipho@richfield.co.za","password":"secret123","role":"SUPERVISOR",
                 "firstName":"Sipho","lastName":"Dlamini","employerId":1,"phone":"0821112222","pin":"4321"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("SUPERVISOR"));

        String hash = text("""
                SELECT v.verification_pin_hash FROM SUPERVISOR v JOIN USER_ACCOUNT a ON a.user_id = v.user_id
                WHERE a.email = 'sipho@richfield.co.za' AND v.employer_id = 1
                """);
        assertThat(hash).isNotEqualTo("4321");
        assertThat(passwordEncoder.matches("4321", hash)).isTrue();
    }

    @Test
    void supervisorMustChooseAnApprovedEmployer() throws Exception {
        register("""
                {"email":"sipho@richfield.co.za","password":"secret123","role":"SUPERVISOR",
                 "firstName":"Sipho","lastName":"Dlamini","employerId":2,"phone":"0821112222","pin":"4321"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Select your employer"));
    }

    @Test
    void supervisorPinMustBe4To6Digits() throws Exception {
        register("""
                {"email":"sipho@richfield.co.za","password":"secret123","role":"SUPERVISOR",
                 "firstName":"Sipho","lastName":"Dlamini","employerId":1,"phone":"0821112222","pin":"12"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("The PIN must be 4 to 6 digits"));
    }

    @Test
    void employerStartsPendingAndTheCoordinatorIsTold() throws Exception {
        register("""
                {"email":"hr.acme@richfield.co.za","password":"secret123","role":"EMPLOYER",
                 "employerName":"Acme Labs","address":"1 Main Road, Durban",
                 "contactEmail":"hr@acme.co.za","contactNumber":"0315550000"}
                """).andExpect(status().isCreated());

        assertThat(text("SELECT status FROM EMPLOYER WHERE employer_name = 'Acme Labs'")).isEqualTo("Pending");
        assertThat(count("SELECT COUNT(*) FROM NOTIFICATION WHERE user_id = ? AND message = 'Acme Labs is waiting for approval.'",
                COORDINATOR_USER)).isEqualTo(1);
    }

    @Test
    void theRolesDetailsAreRequired() throws Exception {
        register("""
                {"email":"x@richfield.co.za","password":"secret123","role":"STUDENT","firstName":"X","lastName":"Y"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Enter a valid student number"));
        assertThat(count("SELECT COUNT(*) FROM USER_ACCOUNT WHERE email = 'x@richfield.co.za'")).isZero();
    }

    @Test
    void duplicateEmailGives409() throws Exception {
        register("""
                {"email":"john.smith@richfield.co.za","password":"secret123","role":"STUDENT",
                 "firstName":"John","lastName":"Smith","studentNumber":"20249999"}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("This email is already registered"));
    }

    @Test
    void duplicateStudentNumberGives409() throws Exception {
        register("""
                {"email":"other@richfield.co.za","password":"secret123","role":"STUDENT",
                 "firstName":"Other","lastName":"Person","studentNumber":"20240123"}
                """).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This student number is already registered"));
    }

    @Test
    void passwordNeedsALetterAndANumber() throws Exception {
        register("""
                {"email":"x@richfield.co.za","password":"onlyletters","role":"STUDENT",
                 "firstName":"X","lastName":"Y","studentNumber":"20249999"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void publicCannotCreateACoordinator() throws Exception {
        register("""
                {"email":"boss@richfield.co.za","password":"secret123","role":"COORDINATOR"}
                """).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").with(JOHN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"boss@richfield.co.za\",\"password\":\"secret123\",\"role\":\"COORDINATOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanCreateACoordinator() throws Exception {
        mvc.perform(post("/api/auth/register").with(COORDINATOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"boss@richfield.co.za\",\"password\":\"secret123\",\"role\":\"COORDINATOR\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("COORDINATOR"));
    }

    @Test
    void signUpListShowsOnlyApprovedEmployers() throws Exception {
        mvc.perform(get("/api/auth/employers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employerName").value("Nexus Tech Solutions"));
    }

    // ---------- login ----------

    @Test
    void loginReturnsTheNameShownInThePortal() throws Exception {
        login("john.smith@richfield.co.za", "password").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Smith")).andExpect(jsonPath("$.role").value("STUDENT"));
        login("hr.nexus@richfield.co.za", "password").andExpect(jsonPath("$.name").value("Nexus Tech Solutions"));
        login("michael.brown@richfield.co.za", "password").andExpect(jsonPath("$.role").value("SUPERVISOR"));
    }

    @Test
    void wrongEmailAndWrongPasswordGiveTheSameMessage() throws Exception {
        login("nobody@richfield.co.za", "password1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        login("sarah.jones@richfield.co.za", "wrong1234").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void fiveWrongPasswordsLockTheEmail() throws Exception {
        for (int i = 0; i < 5; i++) login("sarah.jones@richfield.co.za", "wrong1234").andExpect(status().isUnauthorized());
        login("sarah.jones@richfield.co.za", "password").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many wrong attempts. Your account is locked for 15 more minutes."));
    }
}
