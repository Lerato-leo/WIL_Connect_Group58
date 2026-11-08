package wil_connect.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceReportService {

    private final JdbcTemplate jdbc;
    private final AttendanceService attendanceService;
    private final Path proofDirectory;

    public ComplianceReportService(JdbcTemplate jdbc, AttendanceService attendanceService,
            @Value("${wil.proof-directory:uploads/proof}") String proofDirectory) {
        this.jdbc = jdbc;
        this.attendanceService = attendanceService;
        this.proofDirectory = Path.of(proofDirectory).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public Optional<ReportDownload> generateComplianceReport(int placementId) throws IOException {
        List<Placement> placements = jdbc.query("""
                SELECT s.student_number, CONCAT(s.first_name, ' ', s.last_name) AS student_name,
                       e.employer_name, CONCAT(v.first_name, ' ', v.last_name) AS supervisor_name,
                       p.start_date, p.end_date
                FROM PLACEMENT p
                JOIN STUDENT s ON s.student_id = p.student_id
                JOIN EMPLOYER e ON e.employer_id = p.employer_id
                JOIN SUPERVISOR v ON v.supervisor_id = p.supervisor_id
                WHERE p.placement_id = ?
                """, (row, index) -> new Placement(row.getString("student_number"),
                        row.getString("student_name"), row.getString("employer_name"),
                        row.getString("supervisor_name"), row.getObject("start_date", LocalDate.class),
                        row.getObject("end_date", LocalDate.class)), placementId);

        List<Log> logs = jdbc.query("""
                SELECT d.log_date, d.task_description, d.hours_worked,
                       so.signoff_date, so.verification_method
                FROM DUTY_LOG d
                LEFT JOIN SIGN_OFF so ON so.signoff_id = (
                    SELECT latest.signoff_id FROM SIGN_OFF latest
                    WHERE latest.log_id = d.log_id
                    ORDER BY latest.signoff_date DESC, latest.signoff_id DESC LIMIT 1
                )
                WHERE d.placement_id = ? AND d.status = 'Signed'
                ORDER BY d.log_date, d.log_id
                """, (row, index) -> new Log(row.getObject("log_date", LocalDate.class),
                        row.getString("task_description"),
                        row.getBigDecimal("hours_worked").toPlainString(),
                        row.getObject("signoff_date", LocalDateTime.class),
                        row.getString("verification_method")), placementId);

        if (placements.isEmpty() || logs.isEmpty()) {
            return Optional.empty();
        }

        List<Proof> evidence = jdbc.query("""
                SELECT d.log_date, proof.file_path
                FROM PROOF_OF_WORK proof JOIN DUTY_LOG d ON d.log_id = proof.log_id
                WHERE d.placement_id = ? AND d.status = 'Signed'
                ORDER BY d.log_date, proof.proof_id
                """, (row, index) -> new Proof(
                        row.getObject("log_date", LocalDate.class), row.getString("file_path")),
                placementId);

        var placement = placements.get(0);
        var progress = attendanceService.calculateAttendance(placementId);
        LocalDate today = LocalDate.now();

        try (PDDocument document = new PDDocument()) {
            try (ReportPage page = new ReportPage(document)) {
                page.heading("WIL Connect | Compliance Report");
                page.line("Report date: " + today);
                page.heading("Student and placement");
                page.line("Student: " + placement.studentName() + " (" + placement.studentNumber() + ")");
                page.line("Employer: " + placement.employer() + "   |   Supervisor: " + placement.supervisor());
                page.line("Placement: " + placement.start() + " to " + placement.end());
                page.heading("Hours summary");
                page.line("Signed hours: " + progress.signedHours().toPlainString()
                        + " / " + AttendanceService.REQUIRED_HOURS.toPlainString()
                        + "   |   Days completed: " + progress.daysCompleted()
                        + "   |   Progress: " + progress.percentage().toPlainString() + "%");
                page.heading("Signed duty logs");
                page.tableHeader();
                for (Log log : logs) {
                    String signedOn = log.signedOn() == null ? "-" :
                            log.signedOn().toString().replace('T', ' ');
                    page.tableRow(log.date().toString(), log.task(), log.hours(), signedOn,
                            log.method() == null ? "-" : log.method());
                }

                page.heading("Proof of work");
                if (evidence.isEmpty()) {
                    page.line("No proof of work files are attached to the signed logs.");
                }
                for (Proof proof : evidence) {
                    addProof(document, page, proof);
                }
                page.line("Generated by WIL Connect from signed logs only.");
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            String studentNumber = placement.studentNumber().replaceAll("[^A-Za-z0-9_-]", "_");
            return Optional.of(new ReportDownload(
                    "WIL_Report_" + studentNumber + "_" + today + ".pdf", output.toByteArray()));
        }
    }

    private void addProof(PDDocument document, ReportPage page, Proof proof) throws IOException {
        String stored = proof.path().replace('\\', '/');
        String name = stored.substring(stored.lastIndexOf('/') + 1);
        String lowerName = name.toLowerCase(Locale.ROOT);
        boolean image = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png");
        if (!image) {
            page.line(proof.date() + " | " + name);
            return;
        }

        PDImageXObject pdfImage;
        try {
            Path original = Path.of(stored);
            Path candidate = original.isAbsolute() ? original : original.toAbsolutePath();
            if (original.getNameCount() == 1) {
                candidate = proofDirectory.resolve(original);
            }
            Path root = proofDirectory.toRealPath();
            Path file = candidate.toRealPath();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                page.line(proof.date() + " | " + name + " (image unavailable)");
                return;
            }
            pdfImage = PDImageXObject.createFromFileByContent(file.toFile(), document);
        } catch (IOException | IllegalArgumentException ex) {
            page.line(proof.date() + " | " + name + " (image unavailable)");
            return;
        }

        page.image(proof.date() + " | " + name, pdfImage);
    }

    public record ReportDownload(String filename, byte[] content) {
    }

    private record Placement(String studentNumber, String studentName, String employer,
                             String supervisor, LocalDate start, LocalDate end) {
    }

    private record Log(LocalDate date, String task, String hours, LocalDateTime signedOn,
                       String method) {
    }

    private record Proof(LocalDate date, String path) {
    }

    private static final class ReportPage implements AutoCloseable {
        private static final float MARGIN = 40;
        private static final float BOTTOM = 48;
        private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
        private static final float[] COLUMNS = {67, 220, 36, 115, 67};
        private static final Color TEAL = new Color(15, 105, 100);
        private static final Color PALE_TEAL = new Color(232, 246, 244);

        private final PDDocument document;
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        private ReportPage(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
            text("WIL Connect  |  Page " + document.getNumberOfPages(),
                    regular, 9, TEAL, MARGIN, 26);
        }

        private void heading(String value) throws IOException {
            if (y - 48 < BOTTOM) {
                newPage();
            }
            y -= 12;
            text(value, bold, 14, TEAL, MARGIN, y);
            y -= 20;
        }

        private void line(String value) throws IOException {
            for (String part : wrap(value, regular, 10, WIDTH)) {
                if (y - 15 < BOTTOM) {
                    newPage();
                }
                text(part, regular, 10, Color.DARK_GRAY, MARGIN, y);
                y -= 15;
            }
        }

        private void tableHeader() throws IOException {
            if (y - 38 < BOTTOM) {
                newPage();
            }
            stream.setNonStrokingColor(TEAL);
            stream.addRect(MARGIN, y - 22, 505, 22);
            stream.fill();
            String[] labels = {"Log date", "Task done", "Hours", "Signed on", "Method"};
            float x = MARGIN;
            for (int i = 0; i < labels.length; i++) {
                text(labels[i], bold, 9, Color.WHITE, x + 5, y - 14);
                x += COLUMNS[i];
            }
            y -= 22;
        }

        private void tableRow(String... values) throws IOException {
            List<List<String>> cells = new ArrayList<>();
            int maxLines = 0;
            for (int i = 0; i < values.length; i++) {
                List<String> lines = wrap(values[i], regular, 9, COLUMNS[i] - 10);
                cells.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }

            for (int offset = 0; offset < maxLines; ) {
                int capacity = (int) ((y - BOTTOM - 8) / 12);
                if (capacity < 1) {
                    newPage();
                    tableHeader();
                    capacity = (int) ((y - BOTTOM - 8) / 12);
                }
                int linesToDraw = Math.min(capacity, maxLines - offset);
                float height = linesToDraw * 12 + 8;
                stream.setNonStrokingColor(PALE_TEAL);
                stream.addRect(MARGIN, y - height, 505, height - 1);
                stream.fill();
                float x = MARGIN;
                for (int i = 0; i < cells.size(); i++) {
                    List<String> cell = cells.get(i);
                    for (int line = 0; line < linesToDraw && offset + line < cell.size(); line++) {
                        text(cell.get(offset + line), regular, 9, Color.DARK_GRAY,
                                x + 5, y - 12 - 12 * line);
                    }
                    x += COLUMNS[i];
                }
                y -= height;
                offset += linesToDraw;
            }
        }

        private void image(String caption, PDImageXObject image) throws IOException {
            float scale = Math.min(WIDTH / image.getWidth(), 420f / image.getHeight());
            float height = scale * image.getHeight();
            if (y - height - 42 < BOTTOM) {
                newPage();
            }
            line(caption);
            if (y - height - 12 < BOTTOM) {
                newPage();
            }
            stream.drawImage(image, MARGIN, y - height, scale * image.getWidth(), height);
            y -= height + 12;
        }

        private void text(String value, PDFont font, int size, Color color, float x, float y)
                throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, y);
            stream.showText(value);
            stream.endText();
        }

        private List<String> wrap(String input, PDFont font, int size, float width)
                throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            float used = 0;
            int breakAt = -1;
            String clean = input.replaceAll("\\s+", " ").strip();
            for (int offset = 0; offset < clean.length(); ) {
                int codePoint = clean.codePointAt(offset);
                offset += Character.charCount(codePoint);
                String character = new String(Character.toChars(codePoint));
                float advance;
                try {
                    advance = font.getStringWidth(character) * size / 1000;
                } catch (IllegalArgumentException ex) {
                    character = "?";
                    advance = font.getStringWidth(character) * size / 1000;
                }
                if (used + advance > width && !line.isEmpty()) {
                    if (breakAt >= 0) {
                        lines.add(line.substring(0, breakAt));
                        line.delete(0, breakAt + 1);
                        used = font.getStringWidth(line.toString()) * size / 1000;
                    } else {
                        lines.add(line.toString());
                        line.setLength(0);
                        used = 0;
                    }
                    breakAt = -1;
                }
                if (character.equals(" ")) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    breakAt = line.length();
                }
                line.append(character);
                used += advance;
            }
            if (!line.isEmpty() || lines.isEmpty()) {
                lines.add(line.toString().stripTrailing());
            }
            return lines;
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                PDPageContentStream current = stream;
                stream = null;
                current.close();
            }
        }
    }
}
