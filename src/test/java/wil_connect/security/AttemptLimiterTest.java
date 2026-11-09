package wil_connect.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lockout after 5 wrong tries for 15 minutes (4.6.3 and 4.6.6). Pure unit test with a movable clock.
 */
class AttemptLimiterTest {

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-09T08:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final AttemptLimiter limiter = new AttemptLimiter(clock);

    private void fail(String key, int times) {
        for (int i = 0; i < times; i++) limiter.recordFailure(key);
    }

    @Test
    void fourWrongTriesDoNotLock() {
        fail("pin:1", 4);
        assertThatCode(() -> limiter.checkNotLocked("pin:1", "PIN")).doesNotThrowAnyException();
    }

    @Test
    void fifthWrongTryLocksWith429AndAPlainMessage() {
        fail("pin:1", 5);
        assertThatThrownBy(() -> limiter.checkNotLocked("pin:1", "PIN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getReason()).isEqualTo("Too many wrong attempts. Your PIN is locked for 15 more minutes.");
                });
    }

    @Test
    void lockEndsAfter15Minutes() {
        fail("pin:1", 5);
        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> limiter.checkNotLocked("pin:1", "PIN")).isInstanceOf(ResponseStatusException.class);
        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.checkNotLocked("pin:1", "PIN")).doesNotThrowAnyException();
    }

    @Test
    void countingStartsAgainAfterALock() {
        fail("pin:1", 5);
        clock.advance(Duration.ofMinutes(16));
        fail("pin:1", 4);
        assertThatCode(() -> limiter.checkNotLocked("pin:1", "PIN")).doesNotThrowAnyException();
    }

    @Test
    void resetAfterASuccessClearsTheCount() {
        fail("login:a@b.co.za", 4);
        limiter.reset("login:a@b.co.za");
        fail("login:a@b.co.za", 4);
        assertThatCode(() -> limiter.checkNotLocked("login:a@b.co.za", "account")).doesNotThrowAnyException();
    }

    @Test
    void keysAreIndependent() {
        fail("pin:1", 5);
        assertThatCode(() -> limiter.checkNotLocked("pin:2", "PIN")).doesNotThrowAnyException();
    }
}
