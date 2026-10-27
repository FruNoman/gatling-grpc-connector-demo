package com.frunoyman.perf.core.grpc;

import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import io.gatling.javaapi.core.Session;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;

import java.util.function.Function;

/**
 * One unary call made the wrong way on purpose: {@code ClientCalls.blockingUnaryCall} is called inside {@code execute}, so the
 * Gatling engine thread that runs this action waits out the whole round trip. Measured against {@link GrpcUnaryAction} in
 * {@code docs/run-notes.md} — the only difference between the two actions is this one call.
 */
final class GrpcBlockingUnaryAction<Q, R> extends GrpcAction {

    private final GrpcChannel channel;
    private final MethodDescriptor<Q, R> method;
    private final Function<Session, Q> request;
    private final GrpcCheck<R> check;

    GrpcBlockingUnaryAction(String name, GrpcChannel channel, MethodDescriptor<Q, R> method, Function<Session, Q> request,
                            GrpcCheck<R> check, StatsEngine stats, Clock clock, Action next) {
        super(name, stats, clock, next);
        this.channel = channel;
        this.method = method;
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
            response = ClientCalls.blockingUnaryCall(channel.get(), method, channel.callOptions(), message);
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
