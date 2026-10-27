package com.frunoyman.perf.core.simulation;

import com.frunoyman.perf.core.config.LoadProfile;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.incrementUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;

/**
 * Each profile's injection — the load model, apart from the scenario — and its plan in words, logged at the start: the wall-clock
 * time of every phase, to read the report's charts against. The plan is also what the run's result is compared with: a run that
 * applied less than its plan measured the load generator, not the service.
 */
public final class Injections {

    private Injections() {
    }

    public static PopulationBuilder of(ScenarioBuilder scenario, LoadProfile p) {
        return switch (p.profile()) {
            case SMOKE, CONCURRENCY -> scenario.injectClosed(
                    rampConcurrentUsers(1).to(p.users()).during(p.rampUp()),
                    constantConcurrentUsers(p.users()).during(p.steadyState()));
            case LOAD -> scenario.injectOpen(
                    rampUsersPerSec(0.1).to(p.rate()).during(p.rampUp()),
                    constantUsersPerSec(p.rate()).during(p.steadyState()));
            case STRESS -> scenario.injectOpen(
                    incrementUsersPerSec(p.step())
                            .times(p.steps())
                            .eachLevelLasting(p.steadyState())
                            .separatedByRampsLasting(LoadProfile.TRANSITION)
                            .startingFrom(p.rate()));
        };
    }

    /** The plan from now on: one line per phase, with its wall-clock start and the load it means. */
    public static List<String> plan(LoadProfile p) {
        LocalTime at = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<String> plan = new ArrayList<>();
        switch (p.profile()) {
            case SMOKE, CONCURRENCY -> {
                plan.add(at + "  ramp 1 → " + p.users() + " concurrent users in " + p.rampUp().toSeconds() + " s");
                plan.add(at.plus(p.rampUp()) + "  hold " + p.users() + " concurrent users for " + p.steadyState().toSeconds() + " s");
            }
            case LOAD -> {
                plan.add(at + "  ramp 0 → " + rate(p, p.rate()) + " in " + p.rampUp().toSeconds() + " s");
                plan.add(at.plus(p.rampUp()) + "  hold " + rate(p, p.rate()) + " for " + p.steadyState().toSeconds() + " s");
            }
            case STRESS -> {
                Duration levelStart = Duration.ZERO;
                for (int level = 0; level < p.steps(); level++) {
                    plan.add(at.plus(levelStart) + "  level " + (level + 1) + ": " + rate(p, p.rate() + level * p.step())
                            + " for " + p.steadyState().toSeconds() + " s");
                    levelStart = levelStart.plus(p.steadyState()).plus(LoadProfile.TRANSITION);
                }
            }
        }
        return plan;
    }

    /** In a steady open model requests per second = sessions per second × requests per session, whatever a session's length. */
    private static String rate(LoadProfile p, double sessionsPerSec) {
        return String.format("%.2f sessions/s (≈ %.1f req/s)", sessionsPerSec, sessionsPerSec * p.requestsPerSession());
    }
}
