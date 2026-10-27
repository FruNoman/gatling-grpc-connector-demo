package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;
import io.grpc.stub.StreamObserver;

import java.util.function.Supplier;

/**
 * A bidirectional-streaming RPC as one Gatling action — a chunked upload: N messages out, the answers read as they come, one
 * request recorded when the stream is closed on both sides:
 *
 * <pre>{@code
 * exec(
 *         bidiStream("GRPCBin/DummyBidirectionalStreamStream", this::stub, GRPCBinStub::dummyBidirectionalStreamStream)
 *             .messages(uploadChunks, (session, index) -> DummyMessage.newBuilder().setFInt32(index).setFBytes(chunk()).build())
 *             .check((echoes, last, session) -> echoes == uploadChunks ? null : echoes + " echoes of " + uploadChunks)
 * )
 * }</pre>
 *
 * A <b>client-streaming</b> RPC has the same shape in the generated async stub (an observer in, the request observer out) and goes
 * through this builder too — it simply answers with one message instead of N.
 * <p>
 * The chunks are sent under gRPC's flow control, never in a blind loop: the action installs an on-ready handler and sends while the
 * stream can take it ({@link GrpcBidiStreamAction}). Nothing blocks a Gatling thread, and a slow reader slows the upload down
 * instead of filling the generator's heap with buffered chunks.
 */
public final class GrpcBidiStreamActionBuilder<S, Q, R> implements ActionBuilder {

    /** An async stub's streaming-request method: {@code GRPCBinStub::dummyBidirectionalStreamStream}. */
    @FunctionalInterface
    public interface Call<S, Q, R> {
        StreamObserver<Q> invoke(S stub, StreamObserver<R> responses);
    }

    /** The {@code index}-th message of this session's stream — built when the stream is ready to take it. */
    @FunctionalInterface
    public interface Message<Q> {
        Q at(Session session, int index);
    }

    private final String name;
    private final Supplier<S> stub;
    private final Call<S, Q, R> call;
    private int count = -1;
    private Message<Q> message;
    private GrpcStreamCheck<R> check = (messages, last, session) -> null;

    private GrpcBidiStreamActionBuilder(String name, Supplier<S> stub, Call<S, Q, R> call) {
        this.name = name;
        this.stub = stub;
        this.call = call;
    }

    /** {@code name} — the request's name in the report and the assertions, the RPC's own. */
    public static <S, Q, R> GrpcBidiStreamActionBuilder<S, Q, R> bidiStream(String name, Supplier<S> stub, Call<S, Q, R> call) {
        return new GrpcBidiStreamActionBuilder<>(name, stub, call);
    }

    /**
     * How many messages the stream sends and how each one is built. A fixed count comes from the run's config; a count that differs
     * per session belongs in the session, read inside {@code message}.
     */
    public GrpcBidiStreamActionBuilder<S, Q, R> messages(int messageCount, Message<Q> messageAtIndex) {
        this.count = messageCount;
        this.message = messageAtIndex;
        return this;
    }

    /** The stream's check over the answers that came back. */
    public GrpcBidiStreamActionBuilder<S, Q, R> check(GrpcStreamCheck<R> reasonOrNull) {
        this.check = reasonOrNull;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (message == null || count < 0) {
            throw new IllegalStateException(name + ": no messages(count, ...)");
        }
        return (context, next) -> new GrpcBidiStreamAction<>(name, stub, call, count, message, check,
                context.coreComponents().statsEngine(), context.coreComponents().clock(), next);
    }
}
