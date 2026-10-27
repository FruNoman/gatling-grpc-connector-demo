package com.frunoyman.perf.core.grpc;

import com.frunoyman.perf.core.stats.RunStats;
import io.gatling.commons.stats.KO$;
import io.gatling.commons.stats.OK$;
import io.gatling.commons.stats.Status;
import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.stats.StatsEngine;
import scala.Option;

/**
 * One measured gRPC call, however many messages it carries — the one place where this connector writes into Gatling, so the unary,
 * server-stream and bidi-stream actions each only decide <i>when</i> a call is over and <i>why</i> it failed.
 * <p>
 * Created when the call goes on the wire ({@link #sent}) and finished exactly once ({@link #ok} / {@link #ko} / {@link #failed}):
 * a stream can report {@code onCompleted} and then a late {@code onError} (a deadline firing after the last message), and a request
 * logged twice is a request that never happened. Recording hands the session on through Gatling's event loop, as an HTTP response
 * does — the answer itself arrives on a gRPC thread.
 * <p>
 * This is Gatling's internal Scala API ({@code StatsEngine.logResponse}, {@code Action.!}), not the documented Java DSL: the
 * Gatling version is pinned in build.gradle.kts and one smoke run proves the connector after every upgrade.
 */
final class GrpcCall {

    private final String name;
    private final StatsEngine stats;
    private final Clock clock;
    private final io.gatling.core.session.Session session;
    private final Action next;
    private final long start;
    private boolean recorded;

    private GrpcCall(String name, StatsEngine stats, Clock clock, io.gatling.core.session.Session session, Action next) {
        this.name = name;
        this.stats = stats;
        this.clock = clock;
        this.session = session;
        this.next = next;
        this.start = clock.nowMillis();
    }

    /** The call is on the wire: the clock starts and the run's own counting sees it sent. */
    static GrpcCall sent(String name, StatsEngine stats, Clock clock, io.gatling.core.session.Session session, Action next) {
        RunStats.sent(name);
        return new GrpcCall(name, stats, clock, session, next);
    }

    /** Answered as expected; {@code messages} is how many came back (1 for a unary call) — Gatling's report has no room for those. */
    void ok(int messages) {
        record(OK$.MODULE$, null, messages);
    }

    /** The answer came and the check refused it: {@code reason} is what the report's error table shows. */
    void ko(String reason, int messages) {
        record(KO$.MODULE$, reason, messages);
    }

    /** The call itself failed — the KO names the gRPC status code and description, so the error table reads like HTTP's. */
    void failed(Throwable error, int messages) {
        io.grpc.Status status = io.grpc.Status.fromThrowable(error);
        ko(status.getCode() + (status.getDescription() == null ? "" : ": " + status.getDescription()), messages);
    }

    private synchronized void record(Status status, String reason, int messages) {
        if (recorded) {
            return;
        }
        recorded = true;
        long end = clock.nowMillis();
        stats.logResponse(session.scenario(), session.groups(), name, start, end, status, Option.empty(), Option.apply(reason));
        boolean ok = status == OK$.MODULE$;
        RunStats.answered(name, ok, (int) (end - start), messages);
        io.gatling.core.session.Session after = ok ? session : session.markAsFailed();
        session.eventLoop().execute(() -> next.$bang(after));
    }
}
