package com.frunoyman.perf.grpcbin.actions;

import com.frunoyman.perf.core.grpc.GrpcChannel;
import com.frunoyman.perf.core.grpc.GrpcDsl;
import com.frunoyman.perf.grpcbin.config.GrpcbinSettings;
import com.google.protobuf.ByteString;
import grpcbin.GRPCBinGrpc;
import grpcbin.Grpcbin.DummyMessage;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.exec;

/**
 * The same calls made blocking — the counter-example, run only by {@code GrpcbinInjectorSimulation -Dblocking=true} to measure
 * what a blocking call costs the injector (docs/run-notes.md). The descriptors, the requests and the checks are the async
 * actions', so the two runs differ in exactly one thing: {@code blockingUnaryCall} / {@code blockingServerStreamingCall} instead
 * of their async counterparts.
 */
public class GrpcbinBlockingActions extends GrpcDsl {

    /** grpcbin's own number: ten messages, one per second — see {@link GrpcbinActions}. */
    private static final int DOWNLOAD_CHUNKS = 10;

    private final ByteString chunk;

    public GrpcbinBlockingActions(GrpcChannel channel, GrpcbinSettings settings) {
        super(channel);
        this.chunk = GrpcbinActions.randomChunk(settings.chunkBytes());
    }

    /** {@code GRPCBin/DummyUnary}, waited for on the Gatling thread that runs the action. */
    public ChainBuilder dummyUnary() {
        return exec(
                blockingGrpc("GRPCBin/DummyUnary", GRPCBinGrpc.getDummyUnaryMethod())
                    .request(session -> DummyMessage.newBuilder()
                            .setFString(session.getString("payload"))
                            .setFInt32(session.getInt("payloadId"))
                            .build())
                    .check((response, session) -> response.getFString().equals(session.getString("payload"))
                            ? null
                            : "echo '" + response.getFString() + "' is not the request")
        );
    }

    /** {@code GRPCBin/DummyServerStream}, drained on the Gatling thread that runs the action — ten seconds of it. */
    public ChainBuilder dummyServerStream() {
        return exec(
                blockingServerStream("GRPCBin/DummyServerStream", GRPCBinGrpc.getDummyServerStreamMethod())
                    .request(session -> DummyMessage.newBuilder()
                            .setFString(session.getString("payload"))
                            .setFBytes(chunk)
                            .build())
                    .check((messages, last, session) -> messages == DOWNLOAD_CHUNKS
                            ? null
                            : messages + " chunks downloaded of " + DOWNLOAD_CHUNKS)
        );
    }
}
