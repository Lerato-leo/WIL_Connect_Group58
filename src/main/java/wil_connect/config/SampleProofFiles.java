package wil_connect.config;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import wil_connect.service.DutyLogViews;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * seed.sql points PROOF_OF_WORK at files that are not in the repository. On start up this
 * creates a labelled sample file for every proof row whose file is missing, so the demo
 * data opens like real uploads. Real uploads are never touched.
 *
 * <p>Turn it off with {@code wil.upload.create-missing-samples=false}.</p>
 */
@Component
public class SampleProofFiles implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleProofFiles.class);

    private final JdbcTemplate jdbc;
    private final Path uploadDir;
    private final boolean enabled;

    public SampleProofFiles(JdbcTemplate jdbc,
                            @Value("${wil.upload.dir:uploads/proof}") String uploadDir,
                            @Value("${wil.upload.create-missing-samples:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        try {
            int created = createMissing(uploadDir);
            if (created > 0) log.info("Created {} sample proof files for the seed data in {}", created, uploadDir);
        } catch (Exception e) {
            // The app works without them; a missing file shows "This file is missing on the server".
            log.warn("Could not create sample proof files: {}", e.getMessage());
        }
    }

    /** Creates the missing files in the folder and returns how many were made. */
    public int createMissing(Path folder) throws IOException {
        List<String> stored = jdbc.query("SELECT file_path FROM PROOF_OF_WORK", (rs, i) -> rs.getString(1));
        Files.createDirectories(folder);
        int created = 0;
        for (String path : stored) {
            String name = Paths.get(path).getFileName().toString();
            Path file = folder.resolve(name).normalize();
            if (!file.startsWith(folder) || Files.exists(file)) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            String shown = DutyLogViews.fileName(path); // the name the screens show
            if (lower.endsWith(".pdf")) writePdf(file, shown);
            else if (lower.endsWith(".png")) writeImage(file, shown, "png");
            else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) writeImage(file, shown, "jpg");
            else continue;
            created++;
        }
        return created;
    }

    private static void writePdf(Path file, String name) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream text = new PDPageContentStream(document, page)) {
                text.beginText();
                text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 22);
                text.newLineAtOffset(72, 720);
                text.showText("Sample proof of work");
                text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                text.newLineAtOffset(0, -28);
                text.showText(name);
                text.newLineAtOffset(0, -20);
                text.showText("Created for the WIL Connect demo data.");
                text.endText();
            }
            document.save(file.toFile());
        }
    }

    private static void writeImage(Path file, String name, String format) throws IOException {
        BufferedImage image = new BufferedImage(800, 450, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(29, 29, 32));
        g.fillRect(0, 0, 800, 450);
        g.setColor(new Color(27, 191, 168));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{8f, 8f}, 0f));
        g.drawRoundRect(24, 24, 752, 402, 24, 24);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        centre(g, "Sample proof of work", 215);
        g.setColor(new Color(161, 161, 170));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        centre(g, name, 255);
        g.dispose();
        ImageIO.write(image, format, file.toFile());
    }

    private static void centre(Graphics2D g, String text, int y) {
        int width = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (800 - width) / 2, y);
    }
}
