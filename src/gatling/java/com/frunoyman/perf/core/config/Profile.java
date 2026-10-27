package com.frunoyman.perf.core.config;

import java.time.Duration;

/**
 * The shapes a run can take ({@code -Dprofile}) — what question each one answers, independent of the service:
 * <ul>
 *   <li>{@code SMOKE} — does the chain work under concurrency at all (every change to the tests); closed, a few users;</li>
 *   <li>{@code CONCURRENCY} — what N simultaneous clients get: throughput, latency, where it stops growing; closed, N users
 *       looping, because the question <i>is</i> the number of clients;</li>
 *   <li>{@code LOAD} — does the service hold an arrival rate (the regression gate); open, sessions per second;</li>
 *   <li>{@code STRESS} — where it breaks and what breaks first; open, stairs of rate, no pass / fail — the knee is the result.</li>
 * </ul>
 * Closed only where the number of clients is the question: a slow system makes a closed loop send less, which hides its own
 * overload. Everything else is open — clients arrive whatever the service does. {@code SPIKE} and {@code SOAK} belong in the same
 * list and are deliberately out of this demo ({@code docs/load-model.md}).
 */
public enum Profile {

    SMOKE(true, Duration.ofSeconds(30)),
    CONCURRENCY(true, Duration.ofSeconds(60)),
    LOAD(false, Duration.ofSeconds(60)),
    STRESS(false, Duration.ofSeconds(30));

    private final boolean closed;
    private final Duration defaultSteadyState;

    Profile(boolean closed, Duration defaultSteadyState) {
        this.closed = closed;
        this.defaultSteadyState = defaultSteadyState;
    }

    /** Closed: a fixed number of users each looping sessions; open: new sessions arrive at a rate, each user one session. */
    public boolean closed() {
        return closed;
    }

    /** The plateau of smoke / concurrency / load, one level of stress. */
    public Duration defaultSteadyState() {
        return defaultSteadyState;
    }
}
