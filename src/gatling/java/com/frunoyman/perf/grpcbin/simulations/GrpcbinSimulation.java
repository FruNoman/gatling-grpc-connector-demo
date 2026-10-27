package com.frunoyman.perf.grpcbin.simulations;

import com.frunoyman.perf.core.config.LoadProfile;
import com.frunoyman.perf.core.grpc.GrpcChannel;
import com.frunoyman.perf.core.simulation.Injections;
import com.frunoyman.perf.core.simulation.Limits;
import com.frunoyman.perf.core.simulation.Sessions;
import com.frunoyman.perf.core.stats.RunStats;
import com.frunoyman.perf.core.stats.RunSummaryLog;
import com.frunoyman.perf.grpcbin.actions.GrpcbinActions;
import com.frunoyman.perf.grpcbin.config.GrpcbinSettings;
import com.frunoyman.perf.grpcbin.scenarios.GrpcbinScenarios;
import io.gatling.javaapi.core.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * grpcbin's three RPC shapes under one load profile ({@code -Dprofile}) — the demo's main run. There is no Gatling protocol: each
 * action carries its own call, and the channel is built in {@code before()} and closed in {@code after()}.
 * <p>
 * The numbers come from the load model ({@code docs/load-model.md}): the headline run is 50 concurrent users
 * ({@code -Dprofile=concurrency -Dusers=50}), because the question this demo answers is what N simultaneous clients get out of the
 * service — and the connector.
 */
public class GrpcbinSimulation extends Simulation {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcbinSimulation.class);
    /** 50 users looping the three calls with think time between them. */
    private static final LoadProfile.Defaults DEFAULTS = new LoadProfile.Defaults(50, 2, Duration.ofMillis(200), 3);
    /**
     * A limit per request name, from the baseline run (docs/run-notes.md, 2026-10-27) plus a margin. They differ by two orders of
     * magnitude, which is the whole argument against one global percentile: the download is slow <b>by design</b> — grpcbin drips
     * one message per second, so ten chunks take ten seconds — while the upload of the same ten chunks is a matter of milliseconds.
     */
    private static final int UNARY_P95_MS = 500;
    private static final int DOWNLOAD_P95_MS = 11_000;
    private static final int UPLOAD_P95_MS = 1_000;

    private final GrpcbinSettings settings = GrpcbinSettings.load();
    private final LoadProfile loadProfile = LoadProfile.fromSystemProperties(DEFAULTS);
    private final GrpcChannel channel = new GrpcChannel(settings.target(), settings.plaintext(), settings.deadlineMs());
    private final GrpcbinScenarios scenarios = new GrpcbinScenarios(new GrpcbinActions(channel, settings));
    private final Limits limits = Limits.of(loadProfile, p95Limits());

    {
        setUp(Injections.of(Sessions.of("grpcbin chunked transfer", loadProfile, scenarios.transfer(loadProfile)), loadProfile))
                .maxDuration(loadProfile.maxDuration())
                .assertions(limits.assertions());
    }

    @Override
    public void before() {
        RunStats.start();
        channel.start();
        LOG.info("{} against {} — {} chunks of {} bytes per upload — plan:", loadProfile.profile(), channel,
                settings.uploadChunks(), settings.chunkBytes());
        Injections.plan(loadProfile).forEach(line -> LOG.info("  {}", line));
        limits.described().forEach(line -> LOG.info("  limit: {}", line));
    }

    @Override
    public void after() {
        RunStats.stop();
        channel.stop();
        RunSummaryLog.log(RunStats.summary());
    }

    /** A p95 per request name: a unary echo, a ten-second drip and a 640 KB upload in one percentile would hide each other. */
    private static Map<String, Integer> p95Limits() {
        Map<String, Integer> p95Ms = new LinkedHashMap<>();
        p95Ms.put("GRPCBin/DummyUnary", UNARY_P95_MS);
        p95Ms.put("GRPCBin/DummyServerStream", DOWNLOAD_P95_MS);
        p95Ms.put("GRPCBin/DummyBidirectionalStreamStream", UPLOAD_P95_MS);
        return p95Ms;
    }
}
