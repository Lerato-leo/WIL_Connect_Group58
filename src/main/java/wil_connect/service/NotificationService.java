package wil_connect.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void sendNotification(int userId, String message) {
        jdbc.update("""
                INSERT INTO NOTIFICATION (user_id, message)
                VALUES (?, LEFT(?, 255))
                """, userId, message);
    }

    public List<NotificationItem> getNotifications(int userId) {
        return jdbc.query("""
                SELECT notification_id, message, is_read, created_at
                FROM NOTIFICATION
                WHERE user_id = ?
                ORDER BY created_at DESC, notification_id DESC
                """, (row, index) -> new NotificationItem(
                row.getInt("notification_id"),
                row.getString("message"),
                row.getBoolean("is_read"),
                row.getObject("created_at", LocalDateTime.class)
        ), userId);
    }

    public long getUnreadCount(int userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM NOTIFICATION
                WHERE user_id = ? AND is_read = FALSE
                """, Long.class, userId);
    }

    public void markAsRead(int userId, int notificationId) {
        jdbc.update("""
                UPDATE NOTIFICATION
                SET is_read = TRUE
                WHERE user_id = ? AND notification_id = ?
                """, userId, notificationId);
    }

    public record NotificationItem(
            int notificationId,
            String message,
            boolean read,
            LocalDateTime createdAt) {
    }
}
