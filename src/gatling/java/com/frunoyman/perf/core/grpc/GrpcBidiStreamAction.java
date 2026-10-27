package com.frunoyman.perf.core.grpc;

import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import io.gatling.javaapi.core.Session;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

import java.util.function.Supplier;

/**
 * Sends a stream of messages and records one request when the call is over — the recorded time is the whole upload, from the first
 * chunk to the server's last answer.
 * <p>
 * The chunks go out under gRPC's flow control. Two things force that shape, and both are easy to get wrong:
 * <ul>
 *   <li>the generated async stub <b>starts the call</b> before it hands the request observer back, and the handler can only be
 *       installed before the call starts — the one place left is {@link ClientResponseObserver#beforeStart}, which gRPC calls with
 *       the typed request stream in hand;</li>
 *   <li>sending all the chunks in a blind {@code for} loop compiles and passes a small test — and then buffers the whole upload of
 *       every virtual user in the load generator's heap the moment the server reads slower than the load sends.</li>
 * </ul>
 * {@link #sendWhileReady} is called on a gRPC thread, by the on-ready handler, and is the only place that writes to the stream, so
 * it is also the only thing that needs a lock. Nothing here blocks a Gatling thread.
 */
final class GrpcBidiStreamAction<S, Q, R> extends GrpcAction {

    private final Supplier<S> stub;
    private final GrpcBidiStreamActionBuilder.Call<S, Q, R> call;
    private final int count;
    private final GrpcBidiStreamActionBuilder.Message<Q> message;
    private final GrpcStreamCheck<R> check;

    GrpcBidiStreamAction(String name, Supplier<S> stub, GrpcBidiStreamActionBuilder.Call<S, Q, R> call, int count,
                         GrpcBidiStreamActionBuilder.Message<Q> message, GrpcStreamCheck<R> check, StatsEngine stats, Clock clock,
                         Action next) {
        super(name, stats, clock, next);
        this.stub = stub;
        this.call = call;
        this.count = count;
        this.message = message;
        this.check = check;
    }

    @Override
    public void execute(io.gatling.core.session.Session session) {
        Session javaSession = new Session(session);
        GrpcCall measured = sent(session);
        ClientResponseObserver<Q, R> responses = new ClientResponseObserver<>() {

            private ClientCallStreamObserver<Q> requests;
            private int sent;
            private boolean completed;
            private int messages;
            private R last;

            @Override
            public void beforeStart(ClientCallStreamObserver<Q> requestStream) {
                requests = requestStream;
                requestStream.setOnReadyHandler(this::sendWhileReady);
            }

            /** Sends what the stream can take now, and half-closes it once the last message is out. */
            private synchronized void sendWhileReady() {
                try {
                    while (sent < count && requests.isReady()) {
                        requests.onNext(message.at(javaSession, sent));
                        sent++;
                    }
                    if (sent == count && !completed) {
                        completed = true;
                        requests.onCompleted();
                    }
                } catch (RuntimeException e) {
                    completed = true;
                    requests.cancel("message " + sent + " could not be sent", e);
                    measured.ko("request: " + e.getMessage(), messages);
                }
            }

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
            call.invoke(stub.get(), responses);
        } catch (RuntimeException e) {
            measured.failed(e, 0);
        }
    }
}
