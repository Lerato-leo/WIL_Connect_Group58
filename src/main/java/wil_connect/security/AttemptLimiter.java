package wil_connect.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks a login or a supervisor PIN for 15 minutes after 5 wrong tries (4.6.3 and 4.6.6).
 *
 * <p>The counters are kept in memory, so a restart clears them. That is acceptable
 * while the system runs on one server.</p>
 */
@Component
public class AttemptLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(15);

    private record Entry(int failures, Instant lockedUntil) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public AttemptLimiter() {
        this(Clock.systemUTC());
    }

    AttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Throws 429 while the key is locked. */
    public void checkNotLocked(String key, String what) {
        Entry entry = entries.get(key);
        if (entry != null && entry.lockedUntil() != null && clock.instant().isBefore(entry.lockedUntil())) {
            long seconds = Duration.between(clock.instant(), entry.lockedUntil()).getSeconds();
            long minutes = Math.max(1, (seconds + 59) / 60); // rounded up: "15 more minutes" right after locking
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong attempts. Your " + what + " is locked for " + minutes + " more minutes.");
        }
    }

    public void recordFailure(String key) {
        entries.compute(key, (k, old) -> {
            int failures = (old == null || old.lockedUntil() != null ? 0 : old.failures()) + 1;
            return failures >= MAX_ATTEMPTS
                    ? new Entry(0, clock.instant().plus(LOCK_TIME))
                    : new Entry(failures, null);
        });
    }

    public void reset(String key) {
        entries.remove(key);
    }
}
