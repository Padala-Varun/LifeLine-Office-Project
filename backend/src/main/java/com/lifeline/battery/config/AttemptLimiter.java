package com.lifeline.battery.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Slows down guessing: a limited number of tries per key, then a wait. A correct try clears the count. */
@Component
public class AttemptLimiter {

    static final int MAX_ATTEMPTS = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private record Window(Instant endsAt, int attempts) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public AttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts the try before it is checked, so parallel guesses cannot slip through. */
    public boolean allow(String key) {
        Instant now = clock.instant();
        Window window = windows.compute(key, (k, old) -> old == null || !now.isBefore(old.endsAt())
                ? new Window(now.plus(WINDOW), 1)
                : new Window(old.endsAt(), old.attempts() + 1));
        return window.attempts() <= MAX_ATTEMPTS;
    }

    public void succeeded(String key) {
        windows.remove(key);
    }

    @Scheduled(fixedDelayString = "PT15M")
    void forgetFinishedWindows() {
        Instant now = clock.instant();
        windows.values().removeIf(window -> !now.isBefore(window.endsAt()));
    }
}
