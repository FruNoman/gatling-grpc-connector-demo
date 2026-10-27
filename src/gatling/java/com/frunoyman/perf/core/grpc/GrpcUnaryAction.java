package com.frunoyman.perf.core.grpc;

import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import io.gatling.javaapi.core.Session;
import io.grpc.stub.StreamObserver;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Sends one unary call and records it as Gatling records an HTTP request — the report, the assertions and the error table see it
 * under its name. The answer comes on a gRPC thread; the session goes on on its own event loop ({@link GrpcCall}).
 */
final class GrpcUnaryAction<S, Q, R> extends GrpcAction {

    private final Supplier<S> stub;
    private final GrpcActionBuilder.Call<S, Q, R> call;
    private final Function<Session, Q> request;
    private final GrpcCheck<R> check;

    GrpcUnaryAction(String name, Supplier<S> stub, GrpcActionBuilder.Call<S, Q, R> call, Function<Session, Q> request,
                    GrpcCheck<R> check, StatsEngine stats, Clock clock, Action next) {
        super(name, stats, clock, next);
        this.stub = stub;
        this.call = call;
        this.request = request;
        this.check = check;
    }

    @Override
    public void execute(io.gatling.core.session.Session session) {
        Session javaSession = new Session(session);
        Q message = message(request, javaSession, session);
        if (message == null) {
            return;
        }
        GrpcCall measured = sent(session);
        StreamObserver<R> responses = new StreamObserver<>() {

            private R response;

            @Override
            public void onNext(R value) {
                response = value;
            }

            @Override
            public void onError(Throwable error) {
                measured.failed(error, 0);
            }

            @Override
            public void onCompleted() {
                String reason;
                try {
                    reason = check.reasonOrNull(response, javaSession);
                } catch (RuntimeException e) {
                    reason = "check: " + e.getMessage();
                }
                if (reason == null) {
                    measured.ok(1);
                } else {
                    measured.ko(reason, 1);
                }
            }
        };
        try {
            call.invoke(stub.get(), message, responses);
        } catch (RuntimeException e) {
            responses.onError(e);
        }
    }
}
