package com.frunoyman.perf.core.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The run's own summary, logged at the end of {@code after()} — the numbers to paste into a run note, and the place where the two
 * counts that must agree sit next to each other: this table and Gatling's own statistics table in the report. A gap between them
 * is a bug in the counting (or in the window it counts), not a detail.
 */
public final class RunSummaryLog {

    private static final Logger LOG = LoggerFactory.getLogger(RunSummaryLog.class);
    private static final String ROW = "  %-46s %6d %6d %6d %8d %7d %7d %7d";

    private RunSummaryLog() {
    }

    public static void log(RunStats.Summary summary) {
        LOG.info("Run's own counting over {} s:", summary.duration().toSeconds());
        LOG.info(String.format("  %-46s %6s %6s %6s %8s %7s %7s %7s", "request", "sent", "ok", "ko", "messages", "p50", "p95", "max"));
        summary.perRequest().forEach((name, numbers) -> LOG.info(row(name, numbers)));
        LOG.info(row("ALL", summary.global()));
        long unanswered = summary.global().sent() - summary.global().answered();
        if (unanswered > 0) {
            LOG.info("  {} calls were still out when the run ended — sent, never answered, counted as neither OK nor KO", unanswered);
        }
    }

    private static String row(String name, RunStats.Numbers n) {
        return String.format(ROW, name, n.sent(), n.ok(), n.ko(), n.messages(), n.p50(), n.p95(), n.max());
    }
}
