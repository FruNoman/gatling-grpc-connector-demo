package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;
import io.grpc.stub.StreamObserver;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A unary gRPC call as a Gatling action — Gatling's own gRPC support is Gatling Enterprise; this is the free way, on the same
 * Gatling and in the same scenarios as the HTTP requests:
 *
 * <pre>{@code
 * exec(
 *         grpc("GRPCBin/DummyUnary", this::stub, GRPCBinStub::dummyUnary)
 *             .request(session -> DummyMessage.newBuilder().setFString(session.getString("payload")).build())
 *             .check((response, session) -> response.getFString().equals(session.getString("payload")) ? null : "echo differs")
 * )
 * }</pre>
 *
 * The stub is the generated <b>async</b> one ({@code newStub}, never {@code newBlockingStub}: a blocking call holds one of Gatling's
 * few engine threads and the run applies a fraction of the planned load with a healthy p95 — {@link GrpcBlockingActionBuilder}
 * exists only to measure that). {@code stub} is called per request, so a deadline set in it ({@code withDeadlineAfter}) is fresh
 * each time. {@link #check}: null = OK, else the KO's reason.
 */
public final class GrpcActionBuilder<S, Q, R> implements ActionBuilder {

    /**
     * An async stub's method that takes one request and answers through an observer — a unary RPC, and a server-streaming one as
     * well ({@link GrpcServerStreamActionBuilder} measures that shape): {@code GRPCBinStub::dummyUnary}.
     */
    @FunctionalInterface
    public interface Call<S, Q, R> {
        void invoke(S stub, Q request, StreamObserver<R> responses);
    }

    private final String name;
    private final Supplier<S> stub;
    private final Call<S, Q, R> call;
    private Function<Session, Q> request;
    private GrpcCheck<R> check = (response, session) -> null;

    private GrpcActionBuilder(String name, Supplier<S> stub, Call<S, Q, R> call) {
        this.name = name;
        this.stub = stub;
        this.call = call;
    }

    /** {@code name} — the request's name in the report and the assertions, the RPC's own ("GRPCBin/DummyUnary"). */
    public static <S, Q, R> GrpcActionBuilder<S, Q, R> grpc(String name, Supplier<S> stub, Call<S, Q, R> call) {
        return new GrpcActionBuilder<>(name, stub, call);
    }

    /** The request message, built from the session. */
    public GrpcActionBuilder<S, Q, R> request(Function<Session, Q> requestFromSession) {
        this.request = requestFromSession;
        return this;
    }

    /** The answer's check ({@link GrpcCheck}). */
    public GrpcActionBuilder<S, Q, R> check(GrpcCheck<R> reasonOrNull) {
        this.check = reasonOrNull;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (request == null) {
            throw new IllegalStateException(name + ": no request(...)");
        }
        return (context, next) -> new GrpcUnaryAction<>(name, stub, call, request, check, context.coreComponents().statsEngine(),
                context.coreComponents().clock(), next);
    }
}
