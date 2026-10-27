package com.frunoyman.perf.core.simulation;

import com.frunoyman.perf.core.config.LoadProfile;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * A scenario's session as Gatling runs it under a profile: a closed profile loops it per user for the whole run, an open one gives
 * each arriving user exactly one. The session itself — which calls in which order — is the service's scenario class.
 */
public final class Sessions {

    private Sessions() {
    }

    public static ScenarioBuilder of(String name, LoadProfile loadProfile, ChainBuilder session) {
        return loadProfile.profile().closed() ? scenario(name).forever().on(session) : scenario(name).exec(session);
    }
}
