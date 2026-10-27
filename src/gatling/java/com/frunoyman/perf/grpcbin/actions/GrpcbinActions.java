package com.frunoyman.perf.grpcbin.actions;

import com.frunoyman.perf.core.grpc.GrpcChannel;
import com.frunoyman.perf.core.grpc.GrpcDsl;
import com.frunoyman.perf.grpcbin.config.GrpcbinSettings;
import com.google.protobuf.ByteString;
import grpcbin.GRPCBinGrpc;
import grpcbin.Grpcbin.DummyMessage;
import io.gatling.javaapi.core.ChainBuilder;

import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.exec;

/**
 * grpcbin's RPCs — one method per RPC, named after it, the request built from the session, the answer checked. Reads
 * {@code #{payload}} / {@code #{payloadId}} (the feeder of {@code GrpcbinFeeders}).
 * <p>
 * A call reads as Gatling's own gRPC DSL — {@code grpc(name).unary(descriptor).send(...).check(...)} ({@link GrpcDsl}): no stub,
 * no method reference, and no generated stub type in this package — only {@code GRPCBinGrpc.getXxxMethod()}, which already knows
 * the marshallers, the method's full name and its shape. The deadline comes from the channel's config, per call.
 * <p>
 * grpcbin ({@code moul/grpcbin}) is an echo server built to exercise clients — the streaming counterpart of httpbin — so every
 * check here is an echo check: the answer must be what was sent, and a stream must deliver every message. Its fourth shape,
 * {@code DummyClientStream}, is <b>broken server-side</b> and is deliberately not here: see docs/run-notes.md.
 */
public class GrpcbinActions extends GrpcDsl {

    /**
     * grpcbin's {@code DummyServerStream} answers with the request message 10 times, <b>one per second</b> — the server's own
     * number and the server's own pacing, neither of them a setting (measured 2026-10-27 with grpcurl as well: ~10.1 s per
     * download). A load test of it measures 10 seconds of an open stream, which is why the call's deadline has to clear that and
     * why this request has a p95 limit of its own.
     */
    private static final int DOWNLOAD_CHUNKS = 10;

    private final int uploadChunks;
    private final ByteString chunk;

    public GrpcbinActions(GrpcChannel channel, GrpcbinSettings settings) {
        super(channel);
        this.uploadChunks = settings.uploadChunks();
        this.chunk = randomChunk(settings.chunkBytes());
    }

    /** {@code GRPCBin/DummyUnary} — the echo: the smallest call there is, and the baseline every stream is read against. */
    public ChainBuilder dummyUnary() {
        return exec(
                grpc("GRPCBin/DummyUnary")
                    .unary(GRPCBinGrpc.getDummyUnaryMethod())
                    .send(session -> DummyMessage.newBuilder()
                            .setFString(session.getString("payload"))
                            .setFInt32(session.getInt("payloadId"))
                            .build())
                    .check(response(DummyMessage::getFString).isEL("#{payload}"))
        );
    }

    /** {@code GRPCBin/DummyServerStream} — a chunked download: one request, ten chunks back, timed as a whole. */
    public ChainBuilder dummyServerStream() {
        return exec(
                grpc("GRPCBin/DummyServerStream")
                    .serverStream(GRPCBinGrpc.getDummyServerStreamMethod())
                    .send(session -> DummyMessage.newBuilder()
                            .setFString(session.getString("payload"))
                            .setFBytes(chunk)
                            .build())
                    .check(messageCount().is(DOWNLOAD_CHUNKS))
        );
    }

    /** {@code GRPCBin/DummyBidirectionalStreamStream} — a chunked upload: every chunk out must come back acknowledged. */
    public ChainBuilder dummyBidirectionalStreamStream() {
        return exec(
                grpc("GRPCBin/DummyBidirectionalStreamStream")
                    .bidiStream(GRPCBinGrpc.getDummyBidirectionalStreamStreamMethod())
                    .send(uploadChunks, (session, index) -> DummyMessage.newBuilder()
                            .setFString(session.getString("payload"))
                            .setFInt32(index)
                            .setFBytes(chunk)
                            .build())
                    .check(messageCount().is(uploadChunks))
        );
    }

    /** One random block per run, reused by every chunk: the load must measure the service, not our random generator. */
    static ByteString randomChunk(int bytes) {
        byte[] block = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(block);
        return ByteString.copyFrom(block);
    }
}
