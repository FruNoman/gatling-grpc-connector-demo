package com.frunoyman.perf.core.grpc;

import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import io.gatling.javaapi.core.Session;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One unary call made the wrong way on purpose: the blocking stub is called inside {@code execute}, so the Gatling engine thread
 * that runs this action waits out the whole round trip. Measured against {@link GrpcUnaryAction} in {@code docs/run-notes.md}.
 */
final class GrpcBlockingUnaryAction<S, Q, R> extends GrpcAction {

    private final Supplier<S> stub;
    private final GrpcBlockingActionBuilder.Call<S, Q, R> call;
    private final Function<Session, Q> request;
    private final GrpcCheck<R> check;

    GrpcBlockingUnaryAction(String name, Supplier<S> stub, GrpcBlockingActionBuilder.Call<S, Q, R> call,
                            Function<Session, Q> request, GrpcCheck<R> check, StatsEngine stats, Clock clock, Action next) {
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
        R response;
        try {
            response = call.invoke(stub.get(), message);
        } catch (RuntimeException e) {
            measured.failed(e, 0);
            return;
        }
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
}
