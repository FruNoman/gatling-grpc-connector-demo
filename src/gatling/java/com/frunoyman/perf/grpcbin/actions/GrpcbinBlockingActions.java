package com.frunoyman.perf.grpcbin.actions;

import com.frunoyman.perf.core.grpc.GrpcChannel;
import com.frunoyman.perf.grpcbin.config.GrpcbinSettings;
import com.google.protobuf.ByteString;
import grpcbin.GRPCBinGrpc;
import grpcbin.GRPCBinGrpc.GRPCBinBlockingStub;
import grpcbin.Grpcbin.DummyMessage;
import io.gatling.javaapi.core.ChainBuilder;

import java.util.concurrent.TimeUnit;

import static com.frunoyman.perf.core.grpc.GrpcBlockingActionBuilder.blockingGrpc;
import static com.frunoyman.perf.core.grpc.GrpcBlockingStreamActionBuilder.blockingServerStream;
import static io.gatling.javaapi.core.CoreDsl.exec;

/**
 * The same calls over the blocking stub — the counter-example, run only by {@code GrpcbinInjectorSimulation -Dblocking=true} to
 * measure what a blocking call costs the injector (docs/run-notes.md). The requests and the checks are the async actions', so the
 * two runs differ in exactly one thing.
 */
public class GrpcbinBlockingActions {

    /** grpcbin's own number: ten messages, one per second — see {@link GrpcbinActions}. */
    private static final int DOWNLOAD_CHUNKS = 10;

    private final GrpcChannel channel;
    private final int deadlineMs;
    private final ByteString chunk;

    public GrpcbinBlockingActions(GrpcChannel channel, GrpcbinSettings settings) {
        this.channel = channel;
        this.deadlineMs = settings.deadlineMs();
        this.chunk = GrpcbinActions.randomChunk(settings.chunkBytes());
    }

    /** {@code GRPCBin/DummyUnary}, waited for on the Gatling thread that runs the action. */
    public ChainBuilder dummyUnary() {
        return exec(
                blockingGrpc("GRPCBin/DummyUnary", this::stub, GRPCBinBlockingStub::dummyUnary)
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
                blockingServerStream("GRPCBin/DummyServerStream", this::stub, GRPCBinBlockingStub::dummyServerStream)
                    .request(session -> DummyMessage.newBuilder()
                            .setFString(session.getString("payload"))
                            .setFBytes(chunk)
                            .build())
                    .check((messages, last, session) -> messages == DOWNLOAD_CHUNKS
                            ? null
                            : messages + " chunks downloaded of " + DOWNLOAD_CHUNKS)
        );
    }

    /** A fresh blocking stub per call — its deadline counts from this call. */
    private GRPCBinBlockingStub stub() {
        return GRPCBinGrpc.newBlockingStub(channel.get()).withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
    }
}
