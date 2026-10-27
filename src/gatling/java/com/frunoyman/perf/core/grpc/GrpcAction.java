package com.frunoyman.perf.core.grpc;

import com.typesafe.scalalogging.Logger;
import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import io.gatling.javaapi.core.Session;

import java.util.function.Function;

/**
 * What every gRPC action in this connector shares: its name in the report, the {@link GrpcCall} that measures one call, and the
 * boilerplate Gatling's Scala {@code Action} trait asks of a Java class. A subclass implements {@code execute} — the RPC shape —
 * and nothing else.
 */
abstract class GrpcAction implements Action {

    private final String name;
    private final StatsEngine stats;
    private final Clock clock;
    private final Action next;
    private final Logger logger = Logger.apply(getClass());

    GrpcAction(String name, StatsEngine stats, Clock clock, Action next) {
        this.name = name;
        this.stats = stats;
        this.clock = clock;
        this.next = next;
    }

    @Override
    public final String name() {
        return name;
    }

    /** A call about to go out: measured from here, recorded once it is over. */
    final GrpcCall sent(io.gatling.core.session.Session session) {
        return GrpcCall.sent(name, stats, clock, session, next);
    }

    /**
     * The request message built from the session — a failure here is Gatling's "request crash" (a missing feeder key, a bad id), not
     * a KO of the service: the user is failed and moves on, and the reason names the action.
     */
    final <Q> Q message(Function<Session, Q> request, Session javaSession, io.gatling.core.session.Session session) {
        try {
            return request.apply(javaSession);
        } catch (RuntimeException e) {
            stats.logRequestCrash(session.scenario(), session.groups(), name, "request: " + e.getMessage());
            next.$bang(session.markAsFailed());
            return null;
        }
    }

    @Override
    public final Logger logger() {
        return logger;
    }

    /** The setter of Scala's {@code StrictLogging} field — required by the trait, unused. */
    @SuppressWarnings({"checkstyle:MethodName", "unused"})
    public void com$typesafe$scalalogging$StrictLogging$_setter_$logger_$eq(Logger ignored) {
        // the logger is the final field above
    }
}
