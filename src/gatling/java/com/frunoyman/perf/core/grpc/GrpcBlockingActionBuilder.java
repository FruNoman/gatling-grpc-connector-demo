package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The same unary call over the <b>blocking</b> stub — the counter-example, kept because it is the version everyone writes first and
 * because the cost of it is only visible when the two run side by side ({@code docs/run-notes.md}):
 *
 * <pre>{@code
 * exec(
 *         blockingGrpc("GRPCBin/DummyUnary", this::blockingStub, GRPCBinBlockingStub::dummyUnary)
 *             .request(session -> DummyMessage.newBuilder().setFString(session.getString("payload")).build())
 * )
 * }</pre>
 *
 * The call waits for its answer on the thread that runs the action — one of Gatling's few engine threads. The injector itself then
 * stalls: the run applies a fraction of the planned load and still reports a healthy p95, which is a green report of a load that
 * never happened. Use {@link GrpcActionBuilder} for anything real.
 */
public final class GrpcBlockingActionBuilder<S, Q, R> implements ActionBuilder {

    /** A blocking stub's unary method: {@code GRPCBinBlockingStub::dummyUnary}. */
    @FunctionalInterface
    public interface Call<S, Q, R> {
        R invoke(S stub, Q request);
    }

    private final String name;
    private final Supplier<S> stub;
    private final Call<S, Q, R> call;
    private Function<Session, Q> request;
    private GrpcCheck<R> check = (response, session) -> null;

    private GrpcBlockingActionBuilder(String name, Supplier<S> stub, Call<S, Q, R> call) {
        this.name = name;
        this.stub = stub;
        this.call = call;
    }

    /** {@code name} — the same request name as the async action's, so the two runs' reports compare line by line. */
    public static <S, Q, R> GrpcBlockingActionBuilder<S, Q, R> blockingGrpc(String name, Supplier<S> stub, Call<S, Q, R> call) {
        return new GrpcBlockingActionBuilder<>(name, stub, call);
    }

    /** The request message, built from the session. */
    public GrpcBlockingActionBuilder<S, Q, R> request(Function<Session, Q> requestFromSession) {
        this.request = requestFromSession;
        return this;
    }

    /** The answer's check ({@link GrpcCheck}). */
    public GrpcBlockingActionBuilder<S, Q, R> check(GrpcCheck<R> reasonOrNull) {
        this.check = reasonOrNull;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (request == null) {
            throw new IllegalStateException(name + ": no request(...)");
        }
        return (context, next) -> new GrpcBlockingUnaryAction<>(name, stub, call, request, check,
                context.coreComponents().statsEngine(), context.coreComponents().clock(), next);
    }
}
