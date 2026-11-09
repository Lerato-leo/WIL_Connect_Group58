package wil_connect.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AttendanceService {

    private static final BigDecimal REQUIRED_HOURS = BigDecimal.valueOf(150);
    private final JdbcTemplate jdbc;

    public AttendanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AttendanceProgress calculateAttendance(int placementId) {
        String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN status = 'Signed'
                        THEN hours_worked END), 0) AS signed_hours,
                    COUNT(DISTINCT CASE WHEN status = 'Signed'
                        THEN log_date END) AS days_completed,
                    COALESCE(SUM(CASE WHEN status = 'Pending'
                        THEN hours_worked END), 0) AS pending_hours
                FROM DUTY_LOG
                WHERE placement_id = ?
                """;

        return jdbc.queryForObject(sql, (row, index) -> {
            BigDecimal signedHours = row.getBigDecimal("signed_hours");
            BigDecimal percentage = signedHours.multiply(BigDecimal.valueOf(100))
                    .divide(REQUIRED_HOURS, 1, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(100));
            BigDecimal remainingHours = REQUIRED_HOURS.subtract(signedHours)
                    .max(BigDecimal.ZERO);
            String status = remainingHours.signum() == 0 ? "COMPLETE" : "IN PROGRESS";

            return new AttendanceProgress(
                    signedHours, row.getInt("days_completed"),
                    row.getBigDecimal("pending_hours"), percentage,
                    remainingHours, status);
        }, placementId);
    }

    public record AttendanceProgress(
            BigDecimal signedHours,
            int daysCompleted,
            BigDecimal pendingHours,
            BigDecimal percentage,
            BigDecimal remainingHours,
            String status) {
    }
}
