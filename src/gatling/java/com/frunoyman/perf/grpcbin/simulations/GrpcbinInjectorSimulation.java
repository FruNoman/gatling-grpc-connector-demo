package com.frunoyman.perf.grpcbin.simulations;

import com.frunoyman.perf.core.config.LoadProfile;
import com.frunoyman.perf.core.grpc.GrpcChannel;
import com.frunoyman.perf.core.simulation.Injections;
import com.frunoyman.perf.core.simulation.Limits;
import com.frunoyman.perf.core.simulation.Sessions;
import com.frunoyman.perf.core.stats.RunStats;
import com.frunoyman.perf.core.stats.RunSummaryLog;
import com.frunoyman.perf.grpcbin.actions.GrpcbinActions;
import com.frunoyman.perf.grpcbin.actions.GrpcbinBlockingActions;
import com.frunoyman.perf.grpcbin.config.GrpcbinSettings;
import com.frunoyman.perf.grpcbin.scenarios.GrpcbinScenarios;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

/**
 * One call per session at a planned arrival rate — the simulation that measures the <b>load generator</b>, not the service, and the
 * evidence behind the connector's one hard rule: the async stub, never the blocking one.
 * <p>
 * {@code -Dblocking=true} swaps the async action for the blocking one; {@code -Dcall=unary|download} chooses how slow the call is.
 * Open profile on purpose: in a closed model a stalled injector simply sends less and nothing looks wrong, while an open model has
 * a plan to miss. Read the two runs' <b>applied</b> count against the planned one — not their p95, which stays healthy either way
 * ({@code docs/run-notes.md}).
 */
public class GrpcbinInjectorSimulation extends Simulation {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcbinInjectorSimulation.class);
    /** One call per session, so sessions/s is calls/s; 2/s of a ten-second download means 20 of them open at once. */
    private static final LoadProfile.Defaults DEFAULTS = new LoadProfile.Defaults(10, 2, Duration.ZERO, 1);
    private static final int UNARY_P95_MS = 500;
    private static final int DOWNLOAD_P95_MS = 11_000;

    private final boolean blocking = Boolean.getBoolean("blocking");
    private final boolean download = !"unary".equals(System.getProperty("call", "download"));
    private final GrpcbinSettings settings = GrpcbinSettings.load();
    private final LoadProfile loadProfile = LoadProfile.fromSystemProperties(DEFAULTS);
    private final GrpcChannel channel = new GrpcChannel(settings.target(), settings.plaintext());
    private final Limits limits = Limits.of(loadProfile, Map.of(request(), download ? DOWNLOAD_P95_MS : UNARY_P95_MS));

    {
        setUp(Injections.of(Sessions.of("grpcbin " + (download ? "download" : "unary echo"), loadProfile,
                GrpcbinScenarios.singleCall(call())), loadProfile))
                .maxDuration(loadProfile.maxDuration())
                .assertions(limits.assertions());
    }

    @Override
    public void before() {
        RunStats.start();
        channel.start();
        LOG.info("{} against {} — {} — {} stub — plan:", loadProfile.profile(), channel, request(), blocking ? "BLOCKING" : "async");
        Injections.plan(loadProfile).forEach(line -> LOG.info("  {}", line));
    }

    @Override
    public void after() {
        RunStats.stop();
        channel.stop();
        RunSummaryLog.log(RunStats.summary());
    }

    /** The call under measurement: the async action, or the blocking counter-example of the same RPC. */
    private ChainBuilder call() {
        if (blocking) {
            GrpcbinBlockingActions actions = new GrpcbinBlockingActions(channel, settings);
            return download ? actions.dummyServerStream() : actions.dummyUnary();
        }
        GrpcbinActions actions = new GrpcbinActions(channel, settings);
        return download ? actions.dummyServerStream() : actions.dummyUnary();
    }

    private String request() {
        return download ? "GRPCBin/DummyServerStream" : "GRPCBin/DummyUnary";
    }
}
