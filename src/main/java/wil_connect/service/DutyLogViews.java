package wil_connect.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads duty logs with their proofs and sign offs in the shape every screen uses
 * (student logs, supervisor review, coordinator views).
 */
@Component
public class DutyLogViews {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final String BASE_SQL = """
            SELECT d.log_id, d.placement_id, d.log_date, d.hours_worked, d.task_description,
                   d.skills_learnt, d.status, d.supervisor_comment,
                   s.first_name AS student_first, s.last_name AS student_last, s.student_number,
                   e.employer_name,
                   v.first_name AS supervisor_first, v.last_name AS supervisor_last
            FROM DUTY_LOG d
            JOIN PLACEMENT p ON p.placement_id = d.placement_id
            JOIN STUDENT s ON s.student_id = p.student_id
            JOIN EMPLOYER e ON e.employer_id = p.employer_id
            JOIN SUPERVISOR v ON v.supervisor_id = p.supervisor_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DutyLogViews(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ProofView(int proofId, String fileName, String fileType, LocalDateTime uploadDate) {
    }

    public record SignOffView(int signoffId, int logId, int supervisorId, String supervisorName,
                              LocalDateTime signoffDate, String verificationMethod) {
    }

    /**
     * One duty log. submittedAt is null because DUTY_LOG has no time stamp column;
     * signedAt and verificationMethod come from the latest SIGN_OFF row of a signed log.
     */
    public record LogView(int logId, int placementId, LocalDate logDate, BigDecimal hoursWorked,
                          String taskDescription, String skillsLearnt, String status, String supervisorComment,
                          LocalDateTime submittedAt, LocalDateTime signedAt, String verificationMethod,
                          List<SignOffView> signOffs, List<ProofView> proofs,
                          String studentName, String studentNumber, String employerName, String supervisorName) {
    }

    /** Logs matching a WHERE clause on d, p (placement) or s (student). */
    public List<LogView> find(String where, String orderBy, MapSqlParameterSource params) {
        List<Row> rows = jdbc.query(BASE_SQL + " WHERE " + where + " ORDER BY " + orderBy, params, DutyLogViews::row);
        if (rows.isEmpty()) return List.of();

        MapSqlParameterSource ids = new MapSqlParameterSource("ids", rows.stream().map(Row::logId).toList());
        Map<Integer, List<ProofView>> proofs = jdbc.query("""
                        SELECT proof_id, log_id, file_path, upload_date FROM PROOF_OF_WORK
                        WHERE log_id IN (:ids) ORDER BY upload_date, proof_id
                        """, ids, (rs, i) -> Map.entry(rs.getInt("log_id"), new ProofView(
                        rs.getInt("proof_id"), fileName(rs.getString("file_path")),
                        fileType(rs.getString("file_path")), rs.getObject("upload_date", LocalDateTime.class))))
                .stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        Map<Integer, List<SignOffView>> signOffs = jdbc.query("""
                        SELECT o.signoff_id, o.log_id, o.supervisor_id, o.signoff_date, o.verification_method,
                               CONCAT(v.first_name, ' ', v.last_name) AS supervisor_name
                        FROM SIGN_OFF o JOIN SUPERVISOR v ON v.supervisor_id = o.supervisor_id
                        WHERE o.log_id IN (:ids) ORDER BY o.signoff_date DESC, o.signoff_id DESC
                        """, ids, (rs, i) -> new SignOffView(rs.getInt("signoff_id"), rs.getInt("log_id"),
                        rs.getInt("supervisor_id"), rs.getString("supervisor_name"),
                        rs.getObject("signoff_date", LocalDateTime.class), rs.getString("verification_method")))
                .stream().collect(Collectors.groupingBy(SignOffView::logId));

        List<LogView> views = new ArrayList<>(rows.size());
        for (Row r : rows) {
            List<SignOffView> history = signOffs.getOrDefault(r.logId(), List.of());
            SignOffView latest = "Signed".equals(r.status()) && !history.isEmpty() ? history.get(0) : null;
            views.add(new LogView(r.logId(), r.placementId(), r.logDate(), r.hoursWorked(), r.taskDescription(),
                    r.skillsLearnt(), r.status(), r.supervisorComment(), null,
                    latest == null ? null : latest.signoffDate(),
                    latest == null ? null : latest.verificationMethod(),
                    history, proofs.getOrDefault(r.logId(), List.of()),
                    r.studentName(), r.studentNumber(), r.employerName(), r.supervisorName()));
        }
        return views;
    }

    public LogView one(int logId) {
        List<LogView> found = find("d.log_id = :logId", "d.log_id", new MapSqlParameterSource("logId", logId));
        return found.isEmpty() ? null : found.get(0);
    }

    /** "27 October 2026", as in the notification messages of Table 4.7.14. */
    public static String longDate(LocalDate date) {
        return date.format(LONG_DATE);
    }

    /** The name the student uploaded: the stored name without its date and random prefix. */
    static String fileName(String filePath) {
        String name = Paths.get(filePath).getFileName().toString();
        return name.replaceFirst("^\\d{4}-\\d{2}-\\d{2}_", "").replaceFirst("^[0-9a-f]{8}_", "");
    }

    static String fileType(String filePath) {
        String lower = filePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }

    /** The DUTY_LOG check allows any case, so show the status the way the screens expect. */
    static String status(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String lower = raw.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record Row(int logId, int placementId, LocalDate logDate, BigDecimal hoursWorked, String taskDescription,
                       String skillsLearnt, String status, String supervisorComment, String studentName,
                       String studentNumber, String employerName, String supervisorName) {
    }

    private static Row row(ResultSet rs, int i) throws SQLException {
        return new Row(rs.getInt("log_id"), rs.getInt("placement_id"), rs.getObject("log_date", LocalDate.class),
                rs.getBigDecimal("hours_worked"), rs.getString("task_description"), rs.getString("skills_learnt"),
                status(rs.getString("status")), rs.getString("supervisor_comment"),
                rs.getString("student_first") + " " + rs.getString("student_last"), rs.getString("student_number"),
                rs.getString("employer_name"),
                rs.getString("supervisor_first") + " " + rs.getString("supervisor_last"));
    }
}
