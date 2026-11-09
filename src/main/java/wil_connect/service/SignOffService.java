package wil_connect.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import wil_connect.security.AttemptLimiter;
import wil_connect.security.CurrentUser;
import wil_connect.service.AttendanceService.AttendanceProgress;
import wil_connect.service.DutyLogViews.LogView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * signOffLog (4.6.6): the supervisor reviews, signs or rejects the logs of their own students.
 *
 * <p>A sign off needs the supervisor's PIN or a drawn signature and adds a SIGN_OFF row.
 * Five wrong PINs lock signing for 15 minutes. A rejection keeps the reason in
 * DUTY_LOG.supervisor_comment so the student can correct the log.</p>
 */
@Service
public class SignOffService {

    static final int REJECT_MIN = 5;
    static final int REJECT_MAX = 300;
    /** SIGN_OFF.signature_data is TEXT, which holds 65,535 bytes. */
    static final int SIGNATURE_MAX = 65_000;

    private final JdbcTemplate jdbc;
    private final DutyLogViews views;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter attemptLimiter;
    private final NotificationService notificationService;
    private final AttendanceService attendanceService;

    public SignOffService(JdbcTemplate jdbc, DutyLogViews views, CurrentUser currentUser,
                          PasswordEncoder passwordEncoder, AttemptLimiter attemptLimiter,
                          NotificationService notificationService, AttendanceService attendanceService) {
        this.jdbc = jdbc;
        this.views = views;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.attemptLimiter = attemptLimiter;
        this.notificationService = notificationService;
        this.attendanceService = attendanceService;
    }

    public record SignOffDetails(String verificationMethod, String pin, String signatureData) {
    }

    public record RejectDetails(String supervisorComment) {
    }

    /** One row of Signed Logs: a sign off with the log it belongs to. */
    public record SignedLog(int signoffId, LocalDateTime signoffDate, String verificationMethod,
                            int logId, LocalDate logDate, BigDecimal hoursWorked, String taskDescription,
                            String status, String studentName, String studentNumber) {
    }

    /** A student of this supervisor with their hours (My Students). */
    public record SupervisedPlacement(int placementId, int studentId, String studentName, String studentNumber,
                                      int employerId, String employerName, int supervisorId, String supervisorName,
                                      LocalDate startDate, LocalDate endDate, String status,
                                      AttendanceProgress attendance) {
    }

    /** Logs of this supervisor's students with a status, oldest first. */
    public List<LogView> logs(String status) {
        int supervisorId = currentUser.supervisorId();
        String wanted = status == null || status.isBlank() ? "Pending" : status.trim();
        return views.find("p.supervisor_id = :supervisorId AND d.status = :status", "d.log_date ASC, d.log_id ASC",
                new MapSqlParameterSource("supervisorId", supervisorId).addValue("status", wanted));
    }

    public LogView logForReview(int logId) {
        return ownLog(logId, currentUser.supervisorId());
    }

    @Transactional
    public LogView signOff(int logId, SignOffDetails details) {
        int supervisorId = currentUser.supervisorId();
        LogView log = ownLog(logId, supervisorId);
        requirePending(log);

        String method = details == null || details.verificationMethod() == null ? ""
                : details.verificationMethod().trim().toUpperCase(Locale.ROOT);
        if (method.equals("PIN")) {
            checkPin(supervisorId, details.pin());
            jdbc.update("""
                    INSERT INTO SIGN_OFF (log_id, supervisor_id, signoff_date, verification_method, signature_data)
                    VALUES (?, ?, ?, 'PIN', NULL)
                    """, logId, supervisorId, LocalDateTime.now());
        } else if (method.equals("SIGNATURE")) {
            String signature = details.signatureData() == null ? "" : details.signatureData().trim();
            if (!signature.startsWith("data:image/png;base64,") || signature.length() < 40) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please sign in the box");
            }
            if (signature.length() > SIGNATURE_MAX) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The signature is too large. Clear it and sign again.");
            }
            jdbc.update("""
                    INSERT INTO SIGN_OFF (log_id, supervisor_id, signoff_date, verification_method, signature_data)
                    VALUES (?, ?, ?, 'Signature', ?)
                    """, logId, supervisorId, LocalDateTime.now(), signature);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose PIN or signature");
        }

        jdbc.update("UPDATE DUTY_LOG SET status = 'Signed', supervisor_comment = NULL WHERE log_id = ?", logId);
        // Table 4.7.14
        notifyStudent(logId, "Your log for " + DutyLogViews.longDate(log.logDate()) + " was signed off.");
        return views.one(logId);
    }

    @Transactional
    public LogView reject(int logId, RejectDetails details) {
        LogView log = ownLog(logId, currentUser.supervisorId());
        requirePending(log);
        String reason = details == null || details.supervisorComment() == null ? "" : details.supervisorComment().trim();
        if (reason.length() < REJECT_MIN || reason.length() > REJECT_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give a reason for the rejection (5 to 300 characters)");
        }
        jdbc.update("UPDATE DUTY_LOG SET status = 'Rejected', supervisor_comment = ? WHERE log_id = ?", reason, logId);
        // Table 4.7.14
        notifyStudent(logId, "Your log for " + DutyLogViews.longDate(log.logDate()) + " was rejected: " + reason);
        return views.one(logId);
    }

    /** Signed Logs: every sign off by this supervisor, newest first. */
    public List<SignedLog> history() {
        int supervisorId = currentUser.supervisorId();
        record Row(int signoffId, int logId, LocalDateTime date, String method) {
        }
        List<Row> rows = jdbc.query("""
                SELECT signoff_id, log_id, signoff_date, verification_method FROM SIGN_OFF
                WHERE supervisor_id = ? ORDER BY signoff_date DESC, signoff_id DESC
                """, (rs, i) -> new Row(rs.getInt("signoff_id"), rs.getInt("log_id"),
                rs.getObject("signoff_date", LocalDateTime.class), rs.getString("verification_method")), supervisorId);
        if (rows.isEmpty()) return List.of();
        Map<Integer, LogView> logs = views.find("d.log_id IN (:ids)", "d.log_id",
                        new MapSqlParameterSource("ids", rows.stream().map(Row::logId).distinct().toList()))
                .stream().collect(Collectors.toMap(LogView::logId, v -> v));
        List<SignedLog> result = new ArrayList<>(rows.size());
        for (Row r : rows) {
            LogView log = logs.get(r.logId());
            if (log != null) {
                result.add(new SignedLog(r.signoffId(), r.date(), r.method(), log.logId(), log.logDate(),
                        log.hoursWorked(), log.taskDescription(), log.status(), log.studentName(), log.studentNumber()));
            }
        }
        return result;
    }

    /** My Students: this supervisor's placements with their attendance. */
    public List<SupervisedPlacement> students() {
        int supervisorId = currentUser.supervisorId();
        return jdbc.query("""
                SELECT p.placement_id, p.student_id, p.employer_id, p.supervisor_id, p.start_date, p.end_date, p.status,
                       CONCAT(s.first_name, ' ', s.last_name) AS student_name, s.student_number, e.employer_name,
                       CONCAT(v.first_name, ' ', v.last_name) AS supervisor_name
                FROM PLACEMENT p
                JOIN STUDENT s ON s.student_id = p.student_id
                JOIN EMPLOYER e ON e.employer_id = p.employer_id
                JOIN SUPERVISOR v ON v.supervisor_id = p.supervisor_id
                WHERE p.supervisor_id = ?
                ORDER BY p.status = 'Active' DESC, s.last_name, s.first_name
                """, (rs, i) -> new SupervisedPlacement(rs.getInt("placement_id"), rs.getInt("student_id"),
                rs.getString("student_name"), rs.getString("student_number"), rs.getInt("employer_id"),
                rs.getString("employer_name"), rs.getInt("supervisor_id"), rs.getString("supervisor_name"),
                rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                rs.getString("status"), attendanceService.calculateAttendance(rs.getInt("placement_id"))),
                supervisorId);
    }

    // ---------- rules ----------

    private LogView ownLog(int logId, int supervisorId) {
        Boolean mine = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM DUTY_LOG d JOIN PLACEMENT p ON p.placement_id = d.placement_id
                               WHERE d.log_id = ? AND p.supervisor_id = ?)
                """, Boolean.class, logId, supervisorId);
        if (!Boolean.TRUE.equals(mine)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only sign logs of your own students");
        }
        return views.one(logId);
    }

    private static void requirePending(LogView log) {
        if (!"Pending".equals(log.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This log is not waiting for sign off");
        }
    }

    /**
     * A wrong PIN answers 400, not 401: 401 means the login itself has ended.
     */
    private void checkPin(int supervisorId, String pin) {
        String key = "pin:" + supervisorId;
        attemptLimiter.checkNotLocked(key, "PIN");
        String hash = jdbc.queryForObject("SELECT verification_pin_hash FROM SUPERVISOR WHERE supervisor_id = ?",
                String.class, supervisorId);
        if (pin == null || !pin.matches("\\d{4,6}") || !passwordEncoder.matches(pin, hash)) {
            attemptLimiter.recordFailure(key);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect PIN");
        }
        attemptLimiter.reset(key);
    }

    private void notifyStudent(int logId, String message) {
        Integer studentUserId = jdbc.queryForObject("""
                SELECT s.user_id FROM DUTY_LOG d
                JOIN PLACEMENT p ON p.placement_id = d.placement_id
                JOIN STUDENT s ON s.student_id = p.student_id
                WHERE d.log_id = ?
                """, Integer.class, logId);
        notificationService.sendNotification(studentUserId, message);
    }
}
