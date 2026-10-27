package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A server-streaming call over the <b>blocking</b> stub — the counter-example that shows what the async connector buys, and the
 * one that shows it honestly: the cost of a blocking call is {@code rate × latency} against the engine's threads, so it only
 * appears on a call slow enough to hold one ({@code docs/run-notes.md}).
 *
 * <pre>{@code
 * exec(
 *         blockingServerStream("GRPCBin/DummyServerStream", this::blockingStub, GRPCBinBlockingStub::dummyServerStream)
 *             .request(session -> DummyMessage.newBuilder().setFBytes(chunk()).build())
 *             .check((messages, last, session) -> messages == DOWNLOAD_CHUNKS ? null : "short stream")
 * )
 * }</pre>
 *
 * The blocking stub hands back an {@link Iterator}, and draining it is what takes the time: one of Gatling's few engine threads
 * sits in it for the whole download. Use {@link GrpcServerStreamActionBuilder} for anything real.
 */
public final class GrpcBlockingStreamActionBuilder<S, Q, R> implements ActionBuilder {

    /** A blocking stub's server-streaming method: {@code GRPCBinBlockingStub::dummyServerStream}. */
    @FunctionalInterface
    public interface Call<S, Q, R> {
        Iterator<R> invoke(S stub, Q request);
    }

    private final String name;
    private final Supplier<S> stub;
    private final Call<S, Q, R> call;
    private Function<Session, Q> request;
    private GrpcStreamCheck<R> check = (messages, last, session) -> null;

    private GrpcBlockingStreamActionBuilder(String name, Supplier<S> stub, Call<S, Q, R> call) {
        this.name = name;
        this.stub = stub;
        this.call = call;
    }

    /** {@code name} — the same request name as the async action's, so the two runs' reports compare line by line. */
    public static <S, Q, R> GrpcBlockingStreamActionBuilder<S, Q, R> blockingServerStream(String name, Supplier<S> stub,
                                                                                          Call<S, Q, R> call) {
        return new GrpcBlockingStreamActionBuilder<>(name, stub, call);
    }

    /** The request that opens the stream, built from the session. */
    public GrpcBlockingStreamActionBuilder<S, Q, R> request(Function<Session, Q> requestFromSession) {
        this.request = requestFromSession;
        return this;
    }

    /** The stream's check over the messages that came back. */
    public GrpcBlockingStreamActionBuilder<S, Q, R> check(GrpcStreamCheck<R> reasonOrNull) {
        this.check = reasonOrNull;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (request == null) {
            throw new IllegalStateException(name + ": no request(...)");
        }
        return (context, next) -> new GrpcBlockingStreamAction<>(name, stub, call, request, check,
                context.coreComponents().statsEngine(), context.coreComponents().clock(), next);
    }
}
