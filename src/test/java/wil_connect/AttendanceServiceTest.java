package wil_connect;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import wil_connect.service.AttendanceService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AttendanceServiceTest {

    @Autowired
    private AttendanceService attendanceService;

    @Test
    void countsSignedHoursAndKeepsPendingHoursSeparate() {
        var progress = attendanceService.calculateAttendance(1);

        assertThat(progress.signedHours()).isEqualByComparingTo("96");
        assertThat(progress.daysCompleted()).isEqualTo(12);
        assertThat(progress.pendingHours()).isEqualByComparingTo("7.5");
        assertThat(progress.percentage()).isEqualByComparingTo("64");
        assertThat(progress.remainingHours()).isEqualByComparingTo("54");
        assertThat(progress.status()).isEqualTo("IN PROGRESS");
    }
}
