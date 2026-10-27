package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;
import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;

import java.util.function.UnaryOperator;

/**
 * A bidirectional-streaming RPC as one Gatling action — a chunked upload: N messages out, the answers read as they come, one
 * request recorded when the stream is closed on both sides:
 *
 * <pre>{@code
 * exec(
 *         grpc("GRPCBin/DummyBidirectionalStreamStream")
 *             .bidiStream(GRPCBinGrpc.getDummyBidirectionalStreamStreamMethod())
 *             .send(uploadChunks, (session, index) -> DummyMessage.newBuilder().setFInt32(index).setFBytes(chunk()).build())
 *             .check(messageCount().is(uploadChunks))
 * )
 * }</pre>
 *
 * A <b>client-streaming</b> RPC goes through this builder too, as {@code grpc(name).clientStream(descriptor)} — same shape from
 * the caller's side, it simply answers with one message instead of N (check it with {@code last(response(…)…)}) — and the
 * descriptor is what tells the two apart: the action sends a {@code CLIENT_STREAMING} descriptor
 * through {@code ClientCalls.asyncClientStreamingCall} and a {@code BIDI_STREAMING} one through {@code asyncBidiStreamingCall},
 * which is how many responses gRPC requests up front. The old method reference could not express that difference at all.
 * <p>
 * The chunks are sent under gRPC's flow control, never in a blind loop: the action installs an on-ready handler and sends while
 * the stream can take it ({@link GrpcBidiStreamAction}). Nothing blocks a Gatling thread, and a slow reader slows the upload down
 * instead of filling the generator's heap with buffered chunks.
 */
public final class GrpcBidiStreamActionBuilder<Q, R> implements ActionBuilder {

    /** The {@code index}-th message of this session's stream — built when the stream is ready to take it. */
    @FunctionalInterface
    public interface Message<Q> {
        Q at(Session session, int index);
    }

    private final GrpcChannel channel;
    private final String name;
    private final MethodDescriptor<Q, R> method;
    private final MethodDescriptor.MethodType shape;
    private int count = -1;
    private Message<Q> message;
    private GrpcStreamCheck<R> check = (messages, last, session) -> null;
    private UnaryOperator<CallOptions> callOptions = UnaryOperator.identity();

    private GrpcBidiStreamActionBuilder(GrpcChannel channel, String name, MethodDescriptor<Q, R> method,
                                        MethodDescriptor.MethodType shape) {
        this.channel = channel;
        this.name = name;
        this.method = method;
        this.shape = shape;
    }

    /**
     * {@code name} — the request's name in the report and the assertions, the RPC's own; {@code shape} — {@code BIDI_STREAMING}
     * for {@code .bidiStream(…)}, {@code CLIENT_STREAMING} for {@code .clientStream(…)}, checked against the descriptor at start.
     */
    static <Q, R> GrpcBidiStreamActionBuilder<Q, R> of(GrpcChannel channel, String name, MethodDescriptor<Q, R> method,
                                                       MethodDescriptor.MethodType shape) {
        return new GrpcBidiStreamActionBuilder<>(channel, name, method, shape);
    }

    /**
     * How many messages the stream sends and how each one is built. A fixed count comes from the run's config; a count that differs
     * per session belongs in the session, read inside {@code message}.
     */
    public GrpcBidiStreamActionBuilder<Q, R> send(int messageCount, Message<Q> messageAtIndex) {
        this.count = messageCount;
        this.message = messageAtIndex;
        return this;
    }

    /** The stream's check over the answers that came back — several are run in order and the first reason is the KO's. */
    @SafeVarargs
    public final GrpcBidiStreamActionBuilder<Q, R> check(GrpcStreamCheck<R>... checks) {
        this.check = GrpcStreamCheck.all(checks);
        return this;
    }

    /** The call's {@link CallOptions}, per call — a token goes here; see {@link GrpcActionBuilder#callOptions}. */
    public GrpcBidiStreamActionBuilder<Q, R> callOptions(UnaryOperator<CallOptions> perCall) {
        this.callOptions = perCall;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (message == null || count < 0) {
            throw new IllegalStateException(name + ": no send(count, ...)");
        }
        GrpcShape.require(name, method, shape);
        return (context, next) -> new GrpcBidiStreamAction<>(name, channel, method, callOptions, count, message, check,
                context.coreComponents().statsEngine(), context.coreComponents().clock(), next);
    }
}
