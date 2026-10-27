package com.frunoyman.perf.core.grpc;

import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import io.gatling.javaapi.core.Session;
import io.grpc.stub.StreamObserver;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Opens a server stream, counts its messages and records one request when the stream ends — the recorded time is the whole
 * download. A stream that dies half way is a KO with the gRPC status and the messages it did deliver.
 */
final class GrpcServerStreamAction<S, Q, R> extends GrpcAction {

    private final Supplier<S> stub;
    private final GrpcActionBuilder.Call<S, Q, R> call;
    private final Function<Session, Q> request;
    private final GrpcStreamCheck<R> check;

    GrpcServerStreamAction(String name, Supplier<S> stub, GrpcActionBuilder.Call<S, Q, R> call, Function<Session, Q> request,
                           GrpcStreamCheck<R> check, StatsEngine stats, Clock clock, Action next) {
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

            private int messages;
            private R last;

            @Override
            public void onNext(R value) {
                messages++;
                last = value;
            }

            @Override
            public void onError(Throwable error) {
                measured.failed(error, messages);
            }

            @Override
            public void onCompleted() {
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
        };
        try {
            call.invoke(stub.get(), message, responses);
        } catch (RuntimeException e) {
            responses.onError(e);
        }
    }
}
