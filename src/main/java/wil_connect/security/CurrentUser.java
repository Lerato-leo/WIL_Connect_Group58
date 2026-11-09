package wil_connect.security;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;

/**
 * Finds who is calling the API from the login, never from an ID sent by the browser.
 *
 * <p>The logged in email leads to USER_ACCOUNT, and its user_id leads to the
 * STUDENT, SUPERVISOR or EMPLOYER row of that person.</p>
 */
@Component
public class CurrentUser {

    private final JdbcTemplate jdbc;

    public CurrentUser(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Account(int userId, String email, String role) {
        public boolean is(String expectedRole) {
            return role.equalsIgnoreCase(expectedRole);
        }
    }

    /** The logged in account, if there is one. */
    public Optional<Account> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return jdbc.query("SELECT user_id, email, role FROM USER_ACCOUNT WHERE email = ?",
                        (row, i) -> new Account(row.getInt("user_id"), row.getString("email"),
                                row.getString("role").toUpperCase(Locale.ROOT)),
                        auth.getName())
                .stream().findFirst();
    }

    public Account account() {
        return find().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Your session has ended. Please log in again."));
    }

    public int studentId() {
        return idOf("SELECT student_id FROM STUDENT WHERE user_id = ?", "Your student details were not found");
    }

    public int supervisorId() {
        return idOf("SELECT supervisor_id FROM SUPERVISOR WHERE user_id = ?", "Your supervisor details were not found");
    }

    public int employerId() {
        return idOf("SELECT employer_id FROM EMPLOYER WHERE user_id = ?", "Your company details were not found");
    }

    private int idOf(String sql, String missing) {
        int userId = account().userId();
        return jdbc.query(sql, (row, i) -> row.getInt(1), userId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, missing));
    }
}
