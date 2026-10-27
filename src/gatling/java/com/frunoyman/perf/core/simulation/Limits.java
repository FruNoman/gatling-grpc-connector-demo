package com.frunoyman.perf.core.simulation;

import com.frunoyman.perf.core.config.LoadProfile;
import com.frunoyman.perf.core.config.Profile;
import io.gatling.javaapi.core.Assertion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;

/**
 * A run's limits as data — the assertions that decide its exit code: the failed share (smoke: not one failed call; stress: no limit,
 * its result is the level where it bent) and a p95 <b>per request name</b>, never one global percentile over a unary echo and a
 * 640 KB stream. A request the run may not reach is never asserted: an assertion on a request with no samples fails a clean run.
 */
public record Limits(Long maxFailedCount, Double maxFailedPercent, Map<String, Integer> p95Ms) {

    /** {@code p95Ms}: the requests this simulation asserts and each one's limit — written out in the simulation. */
    public static Limits of(LoadProfile loadProfile, Map<String, Integer> p95Ms) {
        Profile profile = loadProfile.profile();
        if (profile == Profile.STRESS) {
            return new Limits(null, null, Map.of());
        }
        Long maxFailedCount = profile == Profile.SMOKE ? 0L : null;
        Double maxFailedPercent = profile == Profile.SMOKE ? null : loadProfile.maxFailedPercent();
        return new Limits(maxFailedCount, maxFailedPercent, new LinkedHashMap<>(p95Ms));
    }

    public List<Assertion> assertions() {
        List<Assertion> assertions = new ArrayList<>();
        if (maxFailedCount != null) {
            assertions.add(global().failedRequests().count().is(maxFailedCount));
        }
        if (maxFailedPercent != null) {
            assertions.add(global().failedRequests().percent().lte(maxFailedPercent));
        }
        p95Ms.forEach((request, limit) -> assertions.add(details(request).responseTime().percentile(95.0).lt(limit)));
        return assertions;
    }

    /** The limits in words, for the plan the run logs before it starts. */
    public List<String> described() {
        List<String> described = new ArrayList<>();
        if (maxFailedCount != null) {
            described.add("failed calls: " + maxFailedCount);
        }
        if (maxFailedPercent != null) {
            described.add("failed: ≤ " + maxFailedPercent + " %");
        }
        p95Ms.forEach((request, limit) -> described.add(request + " p95 < " + limit + " ms"));
        return described;
    }
}
