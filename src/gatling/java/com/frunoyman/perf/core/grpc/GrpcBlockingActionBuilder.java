package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;
import io.grpc.MethodDescriptor;

import java.util.function.Function;

/**
 * The same unary call made <b>blocking</b> — the counter-example, kept because it is the version everyone writes first and
 * because the cost of it is only visible when the two run side by side ({@code docs/run-notes.md}):
 *
 * <pre>{@code
 * exec(
 *         grpc("GRPCBin/DummyUnary")
 *             .blockingUnary(GRPCBinGrpc.getDummyUnaryMethod())
 *             .send(session -> DummyMessage.newBuilder().setFString(session.getString("payload")).build())
 * )
 * }</pre>
 *
 * Same descriptor as {@link GrpcActionBuilder}, one call away from it: {@code ClientCalls.blockingUnaryCall} instead of
 * {@code asyncUnaryCall} — which is also the whole difference between the generated {@code newBlockingStub} and {@code newStub}.
 * The call waits for its answer on the thread that runs the action — one of Gatling's few engine threads. The injector itself then
 * stalls: the run applies a fraction of the planned load and still reports a healthy p95, which is a green report of a load that
 * never happened. Use {@link GrpcActionBuilder} for anything real; the escape hatch for credentials is there, not here.
 */
public final class GrpcBlockingActionBuilder<Q, R> implements ActionBuilder {

    private final GrpcChannel channel;
    private final String name;
    private final MethodDescriptor<Q, R> method;
    private Function<Session, Q> request;
    private GrpcCheck<R> check = (response, session) -> null;

    private GrpcBlockingActionBuilder(GrpcChannel channel, String name, MethodDescriptor<Q, R> method) {
        this.channel = channel;
        this.name = name;
        this.method = method;
    }

    /** {@code name} — the same request name as the async action's, so the two runs' reports compare line by line. */
    static <Q, R> GrpcBlockingActionBuilder<Q, R> of(GrpcChannel channel, String name, MethodDescriptor<Q, R> method) {
        return new GrpcBlockingActionBuilder<>(channel, name, method);
    }

    /** The request message, built from the session. */
    public GrpcBlockingActionBuilder<Q, R> send(Function<Session, Q> requestFromSession) {
        this.request = requestFromSession;
        return this;
    }

    /** The answer's check ({@link GrpcCheck}) — several are run in order and the first reason is the KO's. */
    @SafeVarargs
    public final GrpcBlockingActionBuilder<Q, R> check(GrpcCheck<R>... checks) {
        this.check = GrpcCheck.all(checks);
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (request == null) {
            throw new IllegalStateException(name + ": no send(...)");
        }
        GrpcShape.require(name, method, MethodDescriptor.MethodType.UNARY);
        return (context, next) -> new GrpcBlockingUnaryAction<>(name, channel, method, request, check,
                context.coreComponents().statsEngine(), context.coreComponents().clock(), next);
    }
}
