package wil_connect.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import wil_connect.support.ApiTest;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sample files for the seed proof rows, so the demo data opens like real uploads.
 */
class SampleProofFilesTest extends ApiTest {

    @Autowired
    private SampleProofFiles sampleProofFiles;

    @TempDir
    Path folder;

    @Test
    void createsAReadableFileForEverySeedProof() throws Exception {
        assertThat(sampleProofFiles.createMissing(folder)).isEqualTo(5);

        byte[] pdf = Files.readAllBytes(folder.resolve("2026-09-07_timesheet.pdf"));
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThat(ImageIO.read(folder.resolve("2026-09-01_site_photo.jpg").toFile()).getWidth()).isEqualTo(800);
    }

    @Test
    void neverOverwritesAFileThatIsAlreadyThere() throws Exception {
        Files.writeString(folder.resolve("2026-09-07_timesheet.pdf"), "%PDF real upload");
        assertThat(sampleProofFiles.createMissing(folder)).isEqualTo(4);
        assertThat(Files.readString(folder.resolve("2026-09-07_timesheet.pdf"))).isEqualTo("%PDF real upload");
        assertThat(sampleProofFiles.createMissing(folder)).isZero();
    }
}
