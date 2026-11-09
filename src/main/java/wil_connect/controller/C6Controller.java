package wil_connect.controller;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import wil_connect.service.AttendanceService;
import wil_connect.service.AttendanceService.AttendanceProgress;
import wil_connect.service.NotificationService;
import wil_connect.service.NotificationService.NotificationItem;

@RestController
@RequestMapping("/api")
public class C6Controller {

    private final JdbcTemplate jdbc;
    private final AttendanceService attendance;
    private final NotificationService notifications;

    public C6Controller(JdbcTemplate jdbc, AttendanceService attendance,
                        NotificationService notifications) {
        this.jdbc = jdbc;
        this.attendance = attendance;
        this.notifications = notifications;
    }

    @GetMapping("/placements/{placementId}/attendance")
    public ResponseEntity<AttendanceProgress> getAttendance(
            @PathVariable("placementId") int placementId, Principal principal) {
        Boolean allowed = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM PLACEMENT p
                    JOIN USER_ACCOUNT a ON a.email = ?
                    LEFT JOIN STUDENT s ON s.student_id = p.student_id
                    LEFT JOIN SUPERVISOR v ON v.supervisor_id = p.supervisor_id
                    WHERE p.placement_id = ?
                      AND (
                          UPPER(a.role) = 'COORDINATOR'
                          OR (UPPER(a.role) = 'STUDENT' AND s.user_id = a.user_id)
                          OR (UPPER(a.role) = 'SUPERVISOR' AND v.user_id = a.user_id)
                      )
                )
                """, Boolean.class, principal.getName(), placementId);

        if (!Boolean.TRUE.equals(allowed)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(attendance.calculateAttendance(placementId));
    }

    @GetMapping("/notifications")
    public ResponseEntity<NotificationInbox> getNotifications(Principal principal) {
        return findUserId(principal)
                .map(userId -> ResponseEntity.ok(new NotificationInbox(
                        notifications.getNotifications(userId),
                        notifications.getUnreadCount(userId))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable("notificationId") int notificationId, Principal principal) {
        Optional<Integer> userId = findUserId(principal);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        notifications.markAsRead(userId.get(), notificationId);
        return ResponseEntity.noContent().build();
    }

    private Optional<Integer> findUserId(Principal principal) {
        return jdbc.query("""
                SELECT user_id FROM USER_ACCOUNT WHERE email = ?
                """, (row, index) -> row.getInt("user_id"), principal.getName())
                .stream()
                .findFirst();
    }

    public record NotificationInbox(List<NotificationItem> items, long unreadCount) {
    }
}
