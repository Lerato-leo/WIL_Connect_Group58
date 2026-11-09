package wil_connect.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import wil_connect.entity.UserAccount;
import wil_connect.enums.Role;
import wil_connect.generated.model.RegisterRequest;
import wil_connect.repository.UserAccountRepository;
import wil_connect.security.AttemptLimiter;
import wil_connect.security.CurrentUser;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * registerUser (4.6.2) and login (4.6.3).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z ']{0,49}$");
    private static final Pattern STUDENT_NUMBER = Pattern.compile("^\\d{6,10}$");
    private static final Pattern PHONE = Pattern.compile("^0\\d{9}$");
    private static final Pattern PIN = Pattern.compile("^\\d{4,6}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;
    private final AttemptLimiter attemptLimiter;
    private final CurrentUser currentUser;

    /**
     * Saves the account and the row of its role (STUDENT, SUPERVISOR or EMPLOYER)
     * in one transaction, so an account never exists without its details.
     * Only a logged in coordinator can create another coordinator.
     */
    @Transactional
    public UserAccount register(RegisterRequest request) {
        Role role = Role.fromString(request.getRole().getValue());
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String password = request.getPassword();

        if (role == Role.COORDINATOR
                && currentUser.find().map(a -> !a.is("COORDINATOR")).orElse(true)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a coordinator can create a coordinator account");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw bad("Password must have at least 8 characters, a letter and a number");
        }
        if (userAccountRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered");
        }

        switch (role) {
            case STUDENT -> checkStudent(request);
            case SUPERVISOR -> checkSupervisor(request);
            case EMPLOYER -> checkEmployer(request);
            case COORDINATOR -> { /* the account is all a coordinator needs */ }
        }

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user = userAccountRepository.saveAndFlush(user);
        int userId = user.getUserId();

        switch (role) {
            case STUDENT -> jdbc.update("""
                    INSERT INTO STUDENT (user_id, student_number, first_name, last_name)
                    VALUES (?, ?, ?, ?)
                    """, userId, request.getStudentNumber().trim(),
                    request.getFirstName().trim(), request.getLastName().trim());
            case SUPERVISOR -> jdbc.update("""
                    INSERT INTO SUPERVISOR (user_id, first_name, last_name, phone, verification_pin_hash, employer_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, userId, request.getFirstName().trim(), request.getLastName().trim(),
                    request.getPhone().trim(), passwordEncoder.encode(request.getPin()), request.getEmployerId());
            case EMPLOYER -> {
                String employerName = request.getEmployerName().trim();
                jdbc.update("""
                        INSERT INTO EMPLOYER (user_id, employer_name, address, contact_email, contact_number, status)
                        VALUES (?, ?, ?, ?, ?, 'Pending')
                        """, userId, employerName, request.getAddress().trim(),
                        request.getContactEmail().trim(), request.getContactNumber().trim());
                // Table 4.7.14: the coordinator is told a new employer needs approval.
                coordinatorUserIds().forEach(id ->
                        notificationService.sendNotification(id, employerName + " is waiting for approval."));
            }
            case COORDINATOR -> { }
        }
        return user;
    }

    /**
     * Checks the password, locking the email for 15 minutes after 5 wrong tries.
     * A wrong email and a wrong password give the same message.
     */
    public UserAccount login(String email, String password) {
        String key = "login:" + email.trim().toLowerCase(Locale.ROOT);
        attemptLimiter.checkNotLocked(key, "account");
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email.trim(), password));
        } catch (AuthenticationException e) {
            attemptLimiter.recordFailure(key);
            throw e;
        }
        attemptLimiter.reset(key);
        return userAccountRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    }

    /** The name shown in the portal: the person's name, or the company name for an employer. */
    public String displayName(UserAccount user) {
        String sql = switch (user.getRole()) {
            case STUDENT -> "SELECT CONCAT(first_name, ' ', last_name) FROM STUDENT WHERE user_id = ?";
            case SUPERVISOR -> "SELECT CONCAT(first_name, ' ', last_name) FROM SUPERVISOR WHERE user_id = ?";
            case EMPLOYER -> "SELECT employer_name FROM EMPLOYER WHERE user_id = ?";
            case COORDINATOR -> null;
        };
        if (sql == null) return null;
        return jdbc.query(sql, (row, i) -> row.getString(1), user.getUserId()).stream().findFirst().orElse(null);
    }

    /** Approved employers a new supervisor can choose from (Table 4.7.4). */
    public List<EmployerOption> approvedEmployers() {
        return jdbc.query("""
                SELECT employer_id, employer_name FROM EMPLOYER
                WHERE status = 'Approved'
                ORDER BY employer_name
                """, (row, i) -> new EmployerOption(row.getInt("employer_id"), row.getString("employer_name")));
    }

    public record EmployerOption(int employerId, String employerName) {
    }

    private void checkStudent(RegisterRequest r) {
        checkNames(r);
        if (r.getStudentNumber() == null || !STUDENT_NUMBER.matcher(r.getStudentNumber().trim()).matches()) {
            throw bad("Enter a valid student number");
        }
        Boolean taken = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM STUDENT WHERE student_number = ?)",
                Boolean.class, r.getStudentNumber().trim());
        if (Boolean.TRUE.equals(taken)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student number is already registered");
        }
    }

    private void checkSupervisor(RegisterRequest r) {
        checkNames(r);
        if (r.getPhone() == null || !PHONE.matcher(r.getPhone().trim()).matches()) {
            throw bad("Enter a valid phone number");
        }
        if (r.getPin() == null || !PIN.matcher(r.getPin()).matches()) {
            throw bad("The PIN must be 4 to 6 digits");
        }
        Boolean approved = r.getEmployerId() != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM EMPLOYER WHERE employer_id = ? AND status = 'Approved')",
                Boolean.class, r.getEmployerId()));
        if (!approved) {
            throw bad("Select your employer");
        }
    }

    private void checkEmployer(RegisterRequest r) {
        String name = r.getEmployerName() == null ? "" : r.getEmployerName().trim();
        String address = r.getAddress() == null ? "" : r.getAddress().trim();
        if (name.length() < 2 || name.length() > 100) throw bad("Enter the employer name");
        if (address.length() < 5 || address.length() > 255) throw bad("Enter the address");
        if (r.getContactEmail() == null || !EMAIL.matcher(r.getContactEmail().trim()).matches()) {
            throw bad("Enter a valid email");
        }
        if (r.getContactNumber() == null || !PHONE.matcher(r.getContactNumber().trim()).matches()) {
            throw bad("Enter a valid phone number");
        }
    }

    private void checkNames(RegisterRequest r) {
        if (r.getFirstName() == null || !NAME.matcher(r.getFirstName().trim()).matches()) {
            throw bad("Enter your first name");
        }
        if (r.getLastName() == null || !NAME.matcher(r.getLastName().trim()).matches()) {
            throw bad("Enter your last name");
        }
    }

    private List<Integer> coordinatorUserIds() {
        return jdbc.query("SELECT user_id FROM USER_ACCOUNT WHERE UPPER(role) = 'COORDINATOR'",
                (row, i) -> row.getInt(1));
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
