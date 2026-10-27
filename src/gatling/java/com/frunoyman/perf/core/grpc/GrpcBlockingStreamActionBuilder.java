package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;
import io.grpc.MethodDescriptor;

import java.util.Iterator;
import java.util.function.Function;

/**
 * A server-streaming call made <b>blocking</b> — the counter-example that shows what the async connector buys, and the one that
 * shows it honestly: the cost of a blocking call is {@code rate × latency} against the engine's threads, so it only appears on a
 * call slow enough to hold one ({@code docs/run-notes.md}).
 *
 * <pre>{@code
 * exec(
 *         blockingServerStream("GRPCBin/DummyServerStream", GRPCBinGrpc.getDummyServerStreamMethod())
 *             .request(session -> DummyMessage.newBuilder().setFBytes(chunk()).build())
 *             .check((messages, last, session) -> messages == DOWNLOAD_CHUNKS ? null : "short stream")
 * )
 * }</pre>
 *
 * {@code ClientCalls.blockingServerStreamingCall} hands back an {@link Iterator}, and draining it is what takes the time: one of
 * Gatling's few engine threads sits in it for the whole download. Use {@link GrpcServerStreamActionBuilder} for anything real.
 */
public final class GrpcBlockingStreamActionBuilder<Q, R> implements ActionBuilder {

    private final GrpcChannel channel;
    private final String name;
    private final MethodDescriptor<Q, R> method;
    private Function<Session, Q> request;
    private GrpcStreamCheck<R> check = (messages, last, session) -> null;

    private GrpcBlockingStreamActionBuilder(GrpcChannel channel, String name, MethodDescriptor<Q, R> method) {
        this.channel = channel;
        this.name = name;
        this.method = method;
    }

    /** {@code name} — the same request name as the async action's, so the two runs' reports compare line by line. */
    static <Q, R> GrpcBlockingStreamActionBuilder<Q, R> of(GrpcChannel channel, String name, MethodDescriptor<Q, R> method) {
        return new GrpcBlockingStreamActionBuilder<>(channel, name, method);
    }

    /** The request that opens the stream, built from the session. */
    public GrpcBlockingStreamActionBuilder<Q, R> request(Function<Session, Q> requestFromSession) {
        this.request = requestFromSession;
        return this;
    }

    /** The stream's check over the messages that came back. */
    public GrpcBlockingStreamActionBuilder<Q, R> check(GrpcStreamCheck<R> reasonOrNull) {
        this.check = reasonOrNull;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (request == null) {
            throw new IllegalStateException(name + ": no request(...)");
        }
        GrpcShape.require(name, method, MethodDescriptor.MethodType.SERVER_STREAMING);
        return (context, next) -> new GrpcBlockingStreamAction<>(name, channel, method, request, check,
                context.coreComponents().statsEngine(), context.coreComponents().clock(), next);
    }
}
