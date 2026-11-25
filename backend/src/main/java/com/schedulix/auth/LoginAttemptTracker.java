package com.schedulix.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptTracker {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final class AttemptState {
        int failures;
        Instant lockedUntil;
    }

    private final ConcurrentHashMap<String, AttemptState> states = new ConcurrentHashMap<>();

    public boolean isLocked(String email, String clientIp) {
        String key = buildKey(email, clientIp);
        AttemptState state = states.get(key);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            if (state.lockedUntil == null) {
                return false;
            }
            if (Instant.now().isAfter(state.lockedUntil)) {
                state.failures = 0;
                state.lockedUntil = null;
                return false;
            }
            return true;
        }
    }

    /**
     * Records a failed login attempt. Returns true on the exact attempt that triggers lockout.
     */
    public boolean recordFailure(String email, String clientIp) {
        String key = buildKey(email, clientIp);
        AttemptState state = states.computeIfAbsent(key, k -> new AttemptState());
        synchronized (state) {
            Instant now = Instant.now();
            if (state.lockedUntil != null && now.isAfter(state.lockedUntil)) {
                state.failures = 0;
                state.lockedUntil = null;
            }
            state.failures++;
            if (state.failures >= MAX_FAILURES && state.lockedUntil == null) {
                state.lockedUntil = now.plus(LOCK_DURATION);
                return true;
            }
            return false;
        }
    }

    public void clear(String email, String clientIp) {
        states.remove(buildKey(email, clientIp));
    }

    public void clearAll() {
        states.clear();
    }

    private String buildKey(String email, String clientIp) {
        return email + "|" + clientIp;
    }
}