package wil_connect.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import wil_connect.dto.DutyLogRequest;
import wil_connect.security.CurrentUser;
import wil_connect.service.DutyLogViews.LogView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * submitDutyLog (4.6.5): new logs, corrections and proof of work files.
 *
 * <p>Rules: one log per day inside the placement period and not in the future,
 * 0.5 to 12 hours, a task of 10 to 1000 characters, up to 3 JPG, PNG or PDF
 * files of 5 MB each. A pending or rejected log can be corrected; a signed log
 * only within 24 hours of its sign off. A correction goes back to Pending.</p>
 */
@Service
public class DutyLogService {

    static final int MAX_FILES = 3;
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    static final int CORRECTION_WINDOW_HOURS = 24;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "application/pdf", ".pdf");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".pdf");

    private final JdbcTemplate jdbc;
    private final DutyLogViews views;
    private final CurrentUser currentUser;
    private final NotificationService notificationService;
    private final Path uploadDir;

    public DutyLogService(JdbcTemplate jdbc, DutyLogViews views, CurrentUser currentUser,
                          NotificationService notificationService,
                          @Value("${wil.upload.dir:uploads/proof}") String uploadDir) {
        this.jdbc = jdbc;
        this.views = views;
        this.currentUser = currentUser;
        this.notificationService = notificationService;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private record ActivePlacement(int placementId, LocalDate startDate, LocalDate endDate,
                                   int supervisorUserId, String studentName) {
    }

    /** My Logs: every log of the student, newest first. */
    public List<LogView> myLogs() {
        int studentId = currentUser.studentId();
        return views.find("p.student_id = :studentId", "d.log_date DESC",
                new MapSqlParameterSource("studentId", studentId));
    }

    @Transactional
    public LogView submit(DutyLogRequest form, List<MultipartFile> files) {
        ActivePlacement placement = activePlacement();
        List<MultipartFile> uploads = checkFiles(files, 0);
        checkDate(form.getLogDate(), placement, null);

        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO DUTY_LOG (placement_id, log_date, task_description, skills_learnt, hours_worked, status)
                    VALUES (?, ?, ?, ?, ?, 'Pending')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, placement.placementId());
            ps.setObject(2, form.getLogDate());
            ps.setString(3, form.getTaskDescription().trim());
            ps.setString(4, skills(form));
            ps.setBigDecimal(5, form.getHoursWorked());
            return ps;
        }, key);
        int logId = key.getKey().intValue();

        storeProofs(logId, form.getLogDate(), uploads);
        notifySupervisor(placement);
        return views.one(logId);
    }

    @Transactional
    public LogView correct(int logId, DutyLogRequest form, List<MultipartFile> files) {
        ActivePlacement placement = activePlacement();
        LogView log = views.one(logId);
        if (log == null || log.placementId() != placement.placementId()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This log could not be found");
        }
        if ("Signed".equals(log.status()) && log.signedAt() != null
                && log.signedAt().plusHours(CORRECTION_WINDOW_HOURS).isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This day was signed off more than 24 hours ago and is locked");
        }
        List<MultipartFile> uploads = checkFiles(files, log.proofs().size());
        checkDate(form.getLogDate(), placement, logId);

        jdbc.update("""
                UPDATE DUTY_LOG
                SET log_date = ?, task_description = ?, skills_learnt = ?, hours_worked = ?,
                    status = 'Pending', supervisor_comment = NULL
                WHERE log_id = ?
                """, form.getLogDate(), form.getTaskDescription().trim(), skills(form), form.getHoursWorked(), logId);

        storeProofs(logId, form.getLogDate(), uploads);
        notifySupervisor(placement);
        return views.one(logId);
    }

    /** Logs of one placement, for the coordinator and for the student who owns it. */
    public List<LogView> placementLogs(int placementId) {
        CurrentUser.Account account = currentUser.account();
        boolean allowed = account.is("COORDINATOR")
                || (account.is("STUDENT") && owns("SELECT 1 FROM PLACEMENT p JOIN STUDENT s ON s.student_id = p.student_id WHERE p.placement_id = ? AND s.user_id = ?", placementId, account.userId()))
                || (account.is("SUPERVISOR") && owns("SELECT 1 FROM PLACEMENT p JOIN SUPERVISOR v ON v.supervisor_id = p.supervisor_id WHERE p.placement_id = ? AND v.user_id = ?", placementId, account.userId()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this placement");
        }
        return views.find("d.placement_id = :placementId", "d.log_date ASC",
                new MapSqlParameterSource("placementId", placementId));
    }

    /** A proof file for its student, the student's supervisor or a coordinator. */
    public ProofFile proofFile(int proofId) {
        CurrentUser.Account account = currentUser.account();
        List<String> paths = jdbc.query("""
                SELECT pw.file_path
                FROM PROOF_OF_WORK pw
                JOIN DUTY_LOG d ON d.log_id = pw.log_id
                JOIN PLACEMENT p ON p.placement_id = d.placement_id
                JOIN STUDENT s ON s.student_id = p.student_id
                JOIN SUPERVISOR v ON v.supervisor_id = p.supervisor_id
                WHERE pw.proof_id = ?
                  AND (? = 'COORDINATOR' OR s.user_id = ? OR v.user_id = ?)
                """, (rs, i) -> rs.getString(1), proofId, account.role(), account.userId(), account.userId());
        if (paths.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This file could not be found");
        }
        String stored = paths.get(0);
        Path file = resolveStored(stored);
        if (file == null || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This file is missing on the server");
        }
        return new ProofFile(file, DutyLogViews.fileName(stored), DutyLogViews.fileType(stored));
    }

    public record ProofFile(Path path, String fileName, String contentType) {
    }

    // ---------- rules ----------

    private ActivePlacement activePlacement() {
        int studentId = currentUser.studentId();
        return jdbc.query("""
                        SELECT p.placement_id, p.start_date, p.end_date, v.user_id AS supervisor_user_id,
                               CONCAT(s.first_name, ' ', s.last_name) AS student_name
                        FROM PLACEMENT p
                        JOIN SUPERVISOR v ON v.supervisor_id = p.supervisor_id
                        JOIN STUDENT s ON s.student_id = p.student_id
                        WHERE p.student_id = ? AND p.status = 'Active'
                        ORDER BY p.start_date DESC LIMIT 1
                        """, (rs, i) -> new ActivePlacement(rs.getInt("placement_id"),
                        rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                        rs.getInt("supervisor_user_id"), rs.getString("student_name")), studentId)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No active placement found"));
    }

    private void checkDate(LocalDate date, ActivePlacement placement, Integer ownLogId) {
        if (date.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The date cannot be in the future");
        }
        if (date.isBefore(placement.startDate()) || date.isAfter(placement.endDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The date is outside your placement period");
        }
        Boolean clash = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM DUTY_LOG WHERE placement_id = ? AND log_date = ? AND log_id <> ?)
                """, Boolean.class, placement.placementId(), date, ownLogId == null ? -1 : ownLogId);
        if (Boolean.TRUE.equals(clash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a log for this date");
        }
    }

    private List<MultipartFile> checkFiles(List<MultipartFile> files, int alreadyAttached) {
        List<MultipartFile> uploads = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (alreadyAttached + uploads.size() > MAX_FILES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can attach up to 3 files");
        }
        for (MultipartFile file : uploads) {
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each file must be 5 MB or smaller");
            }
            if (!EXTENSIONS.containsKey(file.getContentType()) || !ALLOWED_EXTENSIONS.contains(extension(file))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPG, PNG or PDF files are allowed");
            }
            if (!startsLikeItsType(file)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The file " + safeName(file.getOriginalFilename()) + " is not a real JPG, PNG or PDF");
            }
        }
        return uploads;
    }

    /** Checks the first bytes, so a renamed file of another type is refused. */
    private static boolean startsLikeItsType(MultipartFile file) {
        byte[] head = new byte[4];
        try (InputStream in = file.getInputStream()) {
            if (in.readNBytes(head, 0, 4) < 4) return false;
        } catch (IOException e) {
            return false;
        }
        return switch (file.getContentType()) {
            case "image/jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
            case "image/png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "application/pdf" -> head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            default -> false;
        };
    }

    // ---------- files ----------

    /**
     * Saves each file as uploads/proof/{date}_{random}_{name} and adds its PROOF_OF_WORK row.
     * If the transaction rolls back, the files written here are deleted again.
     */
    private void storeProofs(int logId, LocalDate logDate, List<MultipartFile> uploads) {
        if (uploads.isEmpty()) return;
        List<Path> written = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) written.forEach(DutyLogService::deleteQuietly);
            }
        });
        try {
            Files.createDirectories(uploadDir);
            for (MultipartFile file : uploads) {
                String random = UUID.randomUUID().toString().substring(0, 8);
                String name = logDate + "_" + random + "_" + safeName(file.getOriginalFilename());
                Path target = uploadDir.resolve(name).normalize();
                if (!target.startsWith(uploadDir)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file name is not allowed");
                }
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, target);
                }
                written.add(target);
                jdbc.update("INSERT INTO PROOF_OF_WORK (log_id, file_path, upload_date) VALUES (?, ?, ?)",
                        logId, "uploads/proof/" + name, LocalDateTime.now());
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The file could not be saved. Try again.");
        }
    }

    /** Stored paths start with uploads/proof/; only files inside the upload folder are served. */
    private Path resolveStored(String stored) {
        String name = Paths.get(stored).getFileName().toString();
        Path file = uploadDir.resolve(name).normalize();
        return file.startsWith(uploadDir) ? file : null;
    }

    private static String safeName(String original) {
        String name = original == null ? "file" : Paths.get(original).getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_").replace(' ', '_');
        if (name.length() > 80) name = name.substring(name.length() - 80);
        return name.isBlank() ? "file" : name;
    }

    private static String extension(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A left over file does no harm; it has no database row.
        }
    }

    // ---------- helpers ----------

    private static String skills(DutyLogRequest form) {
        return form.getSkillsLearnt() == null ? "" : form.getSkillsLearnt().trim();
    }

    private void notifySupervisor(ActivePlacement placement) {
        // Table 4.7.14
        notificationService.sendNotification(placement.supervisorUserId(),
                "A new log from " + placement.studentName() + " needs sign off.");
    }

    private boolean owns(String sql, int placementId, int userId) {
        return !jdbc.query(sql, (rs, i) -> 1, placementId, userId).isEmpty();
    }
}
