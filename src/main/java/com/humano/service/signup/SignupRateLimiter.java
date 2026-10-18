package com.humano.service.signup;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * A small in-memory sliding window for the public sign-up endpoints, per caller and action. Good
 * enough for one instance; several instances behind a load balancer each keep their own window.
 */
@Component
public class SignupRateLimiter {

    private static final int MAX_KEYS = 10_000;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** Refuses with 429 when {@code key} was used {@code limit} times within {@code window}. */
    public void check(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Instant since = now.minus(window);
        if (hits.size() > MAX_KEYS) {
            hits.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().peekLast().isBefore(since));
        }
        Deque<Instant> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(since)) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, please try again later");
            }
            times.addLast(now);
        }
    }
}
