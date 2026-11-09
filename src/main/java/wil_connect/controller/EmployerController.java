package wil_connect.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import wil_connect.security.CurrentUser;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The employer's company details and its supervisors. Employers only (SecurityConfig).
 */
@RestController
@RequestMapping("/api/employers")
public class EmployerController {

    private static final Pattern PHONE = Pattern.compile("^0\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;

    public EmployerController(JdbcTemplate jdbc, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    public record CompanyDetails(int employerId, String employerName, String address,
                                 String contactEmail, String contactNumber, String status) {
    }

    public record CompanyUpdate(String employerName, String address, String contactEmail, String contactNumber) {
    }

    public record SupervisorSummary(int supervisorId, String firstName, String lastName, String phone,
                                    String email, int activeStudents) {
    }

    @GetMapping("/me")
    public ResponseEntity<CompanyDetails> company() {
        return ResponseEntity.ok(load(currentUser.employerId()));
    }

    /** The status is not changed here; only the coordinator approves or deactivates. */
    @PutMapping("/me")
    public ResponseEntity<CompanyDetails> updateCompany(@RequestBody CompanyUpdate update) {
        int employerId = currentUser.employerId();
        String name = trim(update.employerName());
        String address = trim(update.address());
        String email = trim(update.contactEmail());
        String number = trim(update.contactNumber());
        if (name.length() < 2 || name.length() > 100) throw bad("Enter the employer name");
        if (address.length() < 5 || address.length() > 255) throw bad("Enter the address");
        if (!EMAIL.matcher(email).matches() || email.length() > 100) throw bad("Enter a valid email");
        if (!PHONE.matcher(number).matches()) throw bad("Enter a valid phone number");
        jdbc.update("""
                UPDATE EMPLOYER SET employer_name = ?, address = ?, contact_email = ?, contact_number = ?
                WHERE employer_id = ?
                """, name, address, email, number, employerId);
        return ResponseEntity.ok(load(employerId));
    }

    /** Supervisors registered for this company, with how many active students each has. */
    @GetMapping("/supervisors")
    public ResponseEntity<List<SupervisorSummary>> supervisors() {
        int employerId = currentUser.employerId();
        return ResponseEntity.ok(jdbc.query("""
                SELECT v.supervisor_id, v.first_name, v.last_name, v.phone, a.email,
                       (SELECT COUNT(*) FROM PLACEMENT p
                        WHERE p.supervisor_id = v.supervisor_id AND p.status = 'Active') AS active_students
                FROM SUPERVISOR v
                JOIN USER_ACCOUNT a ON a.user_id = v.user_id
                WHERE v.employer_id = ?
                ORDER BY v.last_name, v.first_name
                """, (rs, i) -> new SupervisorSummary(rs.getInt("supervisor_id"), rs.getString("first_name"),
                rs.getString("last_name"), rs.getString("phone"), rs.getString("email"),
                rs.getInt("active_students")), employerId));
    }

    private CompanyDetails load(int employerId) {
        return jdbc.queryForObject("""
                SELECT employer_id, employer_name, address, contact_email, contact_number, status
                FROM EMPLOYER WHERE employer_id = ?
                """, (rs, i) -> new CompanyDetails(rs.getInt("employer_id"), rs.getString("employer_name"),
                rs.getString("address"), rs.getString("contact_email"), rs.getString("contact_number"),
                rs.getString("status")), employerId);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
