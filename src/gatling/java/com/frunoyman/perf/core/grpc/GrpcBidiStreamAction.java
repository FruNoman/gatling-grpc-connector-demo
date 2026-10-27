package com.frunoyman.perf.core.grpc;

import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import io.gatling.javaapi.core.Session;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ClientResponseObserver;

import java.util.function.UnaryOperator;

/**
 * Sends a stream of messages and records one request when the call is over — the recorded time is the whole upload, from the first
 * chunk to the server's last answer.
 * <p>
 * The chunks go out under gRPC's flow control. Two things force that shape, and both are easy to get wrong:
 * <ul>
 *   <li>{@code ClientCalls.asyncBidiStreamingCall} <b>starts the call</b> before it returns the request observer, and the handler
 *       can only be installed before the call starts — the one place left is {@link ClientResponseObserver#beforeStart}, which
 *       gRPC calls with the typed request stream in hand (which is also why the observer it returns is ignored here);</li>
 *   <li>sending all the chunks in a blind {@code for} loop compiles and passes a small test — and then buffers the whole upload of
 *       every virtual user in the load generator's heap the moment the server reads slower than the load sends.</li>
 * </ul>
 * {@link #sendWhileReady} is called on a gRPC thread, by the on-ready handler, and is the only place that writes to the stream, so
 * it is also the only thing that needs a lock. Nothing here blocks a Gatling thread.
 */
final class GrpcBidiStreamAction<Q, R> extends GrpcAction {

    private final GrpcChannel channel;
    private final MethodDescriptor<Q, R> method;
    private final UnaryOperator<CallOptions> callOptions;
    private final int count;
    private final GrpcBidiStreamActionBuilder.Message<Q> message;
    private final GrpcStreamCheck<R> check;

    GrpcBidiStreamAction(String name, GrpcChannel channel, MethodDescriptor<Q, R> method, UnaryOperator<CallOptions> callOptions,
                         int count, GrpcBidiStreamActionBuilder.Message<Q> message, GrpcStreamCheck<R> check, StatsEngine stats,
                         Clock clock, Action next) {
        super(name, stats, clock, next);
        this.channel = channel;
        this.method = method;
        this.callOptions = callOptions;
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
            ClientCall<Q, R> call = channel.newCall(method, callOptions);
            if (method.getType() == MethodDescriptor.MethodType.CLIENT_STREAMING) {
                ClientCalls.asyncClientStreamingCall(call, responses);
            } else {
                ClientCalls.asyncBidiStreamingCall(call, responses);
            }
        } catch (RuntimeException e) {
            measured.failed(e, 0);
        }
    }
}
