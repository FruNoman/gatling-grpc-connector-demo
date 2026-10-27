package com.frunoyman.perf.core.config;

import java.time.Duration;

/**
 * A run's shape and its pass criteria — system properties ({@code -Dprofile=concurrency -Dusers=50}) over each simulation's
 * defaults ({@link Defaults}, the numbers of the approved load model in {@code docs/load-model.md}); a runner only sets them. In
 * the open profiles a rate is <b>sessions per second</b>, not requests: a session is {@code requestsPerSession} calls.
 *
 * @param users            closed profiles: concurrent users, each looping sessions
 * @param rate             open profiles: sessions per second (stress: the first level)
 * @param step             stress: the rate added per level
 * @param steps            stress: how many levels
 * @param pause            think time between the calls of a session
 * @param requestsPerSession what one session sends — to turn sessions per second into requests per second
 * @param maxFailedPercent the failed share a run may show and still pass; the p95 limits live next to the request names they
 *                         belong to, in the simulation
 */
public record LoadProfile(Profile profile, int users, double rate, Duration rampUp, Duration steadyState, double step, int steps,
                          Duration pause, int requestsPerSession, double maxFailedPercent) {

    /** The ramp between two stress levels. */
    public static final Duration TRANSITION = Duration.ofSeconds(10);
    /** Open profiles: how long the users started last may finish their session before maxDuration cuts them. */
    private static final Duration DRAIN = Duration.ofSeconds(30);

    /** A simulation's defaults — its load model's numbers. */
    public record Defaults(int users, double rate, Duration pause, int requestsPerSession) {
    }

    public static LoadProfile fromSystemProperties(Defaults defaults) {
        Profile profile = Profile.valueOf(property("profile", "smoke").toUpperCase());
        return new LoadProfile(
                profile,
                Integer.parseInt(property("users", String.valueOf(defaults.users()))),
                Double.parseDouble(property("rate", String.valueOf(defaults.rate()))),
                seconds("rampUp", 15),
                seconds("steadyState", profile.defaultSteadyState().toSeconds()),
                Double.parseDouble(property("step", "1")),
                Integer.parseInt(property("steps", "5")),
                Duration.ofMillis(Long.parseLong(property("pauseMs", String.valueOf(defaults.pause().toMillis())))),
                defaults.requestsPerSession(),
                Double.parseDouble(property("maxFailedPercent", "1")));
    }

    /** How long sessions are injected. */
    public Duration injectionDuration() {
        return switch (profile) {
            case SMOKE, CONCURRENCY, LOAD -> rampUp.plus(steadyState);
            case STRESS -> steadyState.multipliedBy(steps).plus(TRANSITION.multipliedBy(steps - 1L));
        };
    }

    /** The run's limit: the injection, plus time for the last open sessions to finish. */
    public Duration maxDuration() {
        return profile.closed() ? injectionDuration() : injectionDuration().plus(DRAIN);
    }

    /** "50 users" / "2.0 sessions/s × 3 calls" — the load in the words the plan and the summary use. */
    public String shape() {
        return profile.closed() ? users + " users" : rate + " sessions/s × " + requestsPerSession + " calls";
    }

    private static String property(String name, String fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Duration seconds(String name, long fallback) {
        return Duration.ofSeconds(Long.parseLong(property(name, String.valueOf(fallback))));
    }
}
