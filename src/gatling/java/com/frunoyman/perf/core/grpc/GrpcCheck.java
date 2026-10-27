package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.Session;

/**
 * A unary call's check: null when the answer was right, else the reason — the KO's message in the report, so the error table reads
 * like HTTP's instead of saying "failed".
 * <p>
 * The session is the one the call was made with, so a check can compare the answer with what was sent ("the echo differs from the
 * request", "the id in the answer is not the one asked for") — the half of a check that a response-only signature cannot express.
 */
@FunctionalInterface
public interface GrpcCheck<R> {

    String reasonOrNull(R response, Session session);
}
