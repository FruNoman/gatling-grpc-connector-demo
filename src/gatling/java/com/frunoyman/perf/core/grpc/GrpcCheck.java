package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.Session;

/**
 * A unary call's check: null when the answer was right, else the reason — the KO's message in the report, so the error table reads
 * like HTTP's instead of saying "failed".
 * <p>
 * Usually built, not written: {@code response(DummyMessage::getFString).isEL("#{payload}")} ({@link GrpcResponseCheck}). A lambda
 * is the hatch for what that cannot say. The session is the one the call was made with, so a check can compare the answer with
 * what was sent.
 */
@FunctionalInterface
public interface GrpcCheck<R> {

    String reasonOrNull(R response, Session session);

    /** Every check in order; the first reason wins — what {@code .check(a, b, c)} runs. */
    @SafeVarargs
    static <R> GrpcCheck<R> all(GrpcCheck<R>... checks) {
        return (response, session) -> {
            for (GrpcCheck<R> check : checks) {
                String reason = check.reasonOrNull(response, session);
                if (reason != null) {
                    return reason;
                }
            }
            return null;
        };
    }
}
