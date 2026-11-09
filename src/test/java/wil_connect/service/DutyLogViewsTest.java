package wil_connect.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The helpers that turn stored values into what the screens show. Pure unit test.
 */
class DutyLogViewsTest {

    @Test
    void fileNameDropsTheFolderDateAndRandomPrefix() {
        assertThat(DutyLogViews.fileName("uploads/proof/2026-11-09_f8148b2a_timesheet.pdf")).isEqualTo("timesheet.pdf");
    }

    @Test
    void fileNameOfSeedFilesDropsOnlyTheDate() {
        assertThat(DutyLogViews.fileName("uploads/proof/2026-09-01_site_photo.jpg")).isEqualTo("site_photo.jpg");
    }

    @Test
    void fileTypeFollowsTheExtension() {
        assertThat(DutyLogViews.fileType("a/b/report.PDF")).isEqualTo("application/pdf");
        assertThat(DutyLogViews.fileType("a/b/photo.png")).isEqualTo("image/png");
        assertThat(DutyLogViews.fileType("a/b/photo.jpeg")).isEqualTo("image/jpeg");
        assertThat(DutyLogViews.fileType("a/b/photo.jpg")).isEqualTo("image/jpeg");
    }

    @Test
    void statusIsShownTheWayTheScreensExpect() {
        assertThat(DutyLogViews.status("PENDING")).isEqualTo("Pending");
        assertThat(DutyLogViews.status("signed")).isEqualTo("Signed");
        assertThat(DutyLogViews.status("Rejected")).isEqualTo("Rejected");
    }

    @Test
    void longDateMatchesTheNotificationWording() {
        assertThat(DutyLogViews.longDate(LocalDate.of(2026, 10, 27))).isEqualTo("27 October 2026");
        assertThat(DutyLogViews.longDate(LocalDate.of(2026, 9, 1))).isEqualTo("1 September 2026");
    }
}
