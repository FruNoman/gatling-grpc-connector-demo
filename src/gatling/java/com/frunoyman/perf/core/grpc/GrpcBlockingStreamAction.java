package com.frunoyman.perf.core.grpc;

import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import io.gatling.javaapi.core.Session;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Drains a server stream on the Gatling thread that runs the action — the measurement behind the rule that a load test's calls are
 * never blocking. The recorded response time is the same whole-stream time the async action records, which is the point: the two
 * runs differ in what the <b>injector</b> managed to apply, not in what the service answered.
 */
final class GrpcBlockingStreamAction<S, Q, R> extends GrpcAction {

    private final Supplier<S> stub;
    private final GrpcBlockingStreamActionBuilder.Call<S, Q, R> call;
    private final Function<Session, Q> request;
    private final GrpcStreamCheck<R> check;

    GrpcBlockingStreamAction(String name, Supplier<S> stub, GrpcBlockingStreamActionBuilder.Call<S, Q, R> call,
                             Function<Session, Q> request, GrpcStreamCheck<R> check, StatsEngine stats, Clock clock, Action next) {
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
        int messages = 0;
        R last = null;
        try {
            Iterator<R> stream = call.invoke(stub.get(), message);
            while (stream.hasNext()) {
                last = stream.next();
                messages++;
            }
        } catch (RuntimeException e) {
            measured.failed(e, messages);
            return;
        }
        String reason;
        try {
            reason = check.reasonOrNull(messages, last, javaSession);
        } catch (RuntimeException e) {
            reason = "check: " + e.getMessage();
        }
        if (reason == null) {
            measured.ok(messages);
        } else {
            measured.ko(reason, messages);
        }
    }
}
