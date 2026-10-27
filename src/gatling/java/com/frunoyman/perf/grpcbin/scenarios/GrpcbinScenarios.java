package com.frunoyman.perf.grpcbin.scenarios;

import com.frunoyman.perf.core.config.LoadProfile;
import com.frunoyman.perf.grpcbin.actions.GrpcbinActions;
import com.frunoyman.perf.grpcbin.feeders.GrpcbinFeeders;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;

/** What a client of a chunked-transfer service does, written out: one small call, one download, one upload. */
public class GrpcbinScenarios {

    private final GrpcbinActions grpcbin;

    public GrpcbinScenarios(GrpcbinActions grpcbin) {
        this.grpcbin = grpcbin;
    }

    /**
     * The session: a unary echo (the baseline latency of the chain), then the chunked download, then the chunked upload — three
     * calls, think time between them. Every RPC the demo covers is in it, so any profile exercises all three.
     */
    public ChainBuilder transfer(LoadProfile loadProfile) {
        return exec(feed(GrpcbinFeeders.payloads()))
                .exec(grpcbin.dummyUnary())
                .pause(loadProfile.pause())
                .exec(grpcbin.dummyServerStream())
                .pause(loadProfile.pause())
                .exec(grpcbin.dummyBidirectionalStreamStream())
                .pause(loadProfile.pause());
    }

    /**
     * One call per session — the async-vs-blocking contrast run, where the session has to be as small as possible for the
     * injector's own behaviour to be the only thing measured. {@code call} is the action under measurement: the async one, or the
     * blocking counter-example of the same RPC.
     */
    public static ChainBuilder singleCall(ChainBuilder call) {
        return exec(feed(GrpcbinFeeders.payloads())).exec(call);
    }
}
