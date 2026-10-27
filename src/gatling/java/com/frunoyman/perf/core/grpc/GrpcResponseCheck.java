package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.internal.Expressions;

import java.util.Objects;
import java.util.function.Function;

/**
 * {@code response(DummyMessage::getFString)} — one value of the answer, compared with what it should be. Each {@code is} gives a
 * {@link GrpcCheck} whose KO reason says both values, so the error table reads {@code response is 'x', expected 'y'} without the
 * call site writing that sentence:
 *
 * <pre>{@code
 * .check(response(DummyMessage::getFString).isEL("#{payload}"))
 * }</pre>
 */
public final class GrpcResponseCheck<R, T> {

    private final Function<R, T> extract;

    GrpcResponseCheck(Function<R, T> extract) {
        this.extract = extract;
    }

    /** Equal to a constant. */
    public GrpcCheck<R> is(T expected) {
        return is(session -> expected);
    }

    /** Equal to a value of the session the call was made with — the half of a check a response-only one cannot express. */
    public GrpcCheck<R> is(Function<Session, T> expected) {
        return (response, session) -> {
            T actual = extract.apply(response);
            Object wanted = expected.apply(session);
            return Objects.equals(actual, wanted) ? null : "response is '" + actual + "', expected '" + wanted + "'";
        };
    }

    /**
     * Equal to a Gatling Expression Language string resolved against the session — {@code "#{payload}"}, as Gatling's own
     * {@code isEL}. The expression is parsed once, here, so a typo fails the simulation at start and not every call of the run.
     */
    @SuppressWarnings("unchecked")
    public GrpcCheck<R> isEL(String expression) {
        Function<Session, Object> resolve = Expressions.expressionToJavaFunction(Expressions.toAnyExpression(expression));
        return is(session -> (T) resolve.apply(session));
    }
}
