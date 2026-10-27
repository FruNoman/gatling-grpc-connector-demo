package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.Session;

/**
 * A streaming call's check: null when the stream was right, else the reason — the KO's message in the report. {@code messages} is
 * how many messages came back and {@code last} is the final one (null for a stream that delivered none); the count is the half a
 * unary check cannot express, and the half that catches a server closing the stream early. The session is the one the call was made
 * with.
 * <p>
 * Usually built, not written: {@code messageCount().is(10)} ({@link GrpcMessageCount}).
 */
@FunctionalInterface
public interface GrpcStreamCheck<R> {

    String reasonOrNull(int messages, R last, Session session);

    /**
     * A unary check run on the stream's last message — a client stream's one answer:
     * {@code last(response(DummyMessage::getFInt32).is(10))}. A stream that delivered nothing fails with that as the reason.
     */
    static <R> GrpcStreamCheck<R> last(GrpcCheck<R> check) {
        return (messages, last, session) -> last == null ? "no message in the stream" : check.reasonOrNull(last, session);
    }

        /** Every check in order; the first reason wins — what {@code .check(a, b, c)} runs. */
    @SafeVarargs
    static <R> GrpcStreamCheck<R> all(GrpcStreamCheck<R>... checks) {
        return (messages, last, session) -> {
            for (GrpcStreamCheck<R> check : checks) {
                String reason = check.reasonOrNull(messages, last, session);
                if (reason != null) {
                    return reason;
                }
            }
            return null;
        };
    }
}
