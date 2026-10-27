package com.frunoyman.perf.core.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * The run's own numbers per request, counted by the gRPC actions themselves — Gatling 3.16 hands its statistics to no one, so a
 * summary in the log (or a chat message, a dashboard, a CI comment) has to count for itself. Here it also carries what Gatling's
 * report has no room for: how many <b>messages</b> a streaming call brought back.
 * <p>
 * Two lessons are built in. Gatling's virtual users keep sending until the simulation's {@code after()} returns, after Gatling has
 * stopped counting, so {@link #stop()} is the first line of every {@code after()} — anything later counts in neither. And a request
 * sent without an answer (the run ended under it) is counted as sent, never as a failure: that is what turns a green run red for
 * nothing. The two numbers are reported side by side, so the gap is visible instead of guessed.
 * <p>
 * One run per JVM ({@code gatlingRun}): {@link #start} resets it in {@code before()}.
 */
public final class RunStats {

    private static final Map<String, Request> REQUESTS = new ConcurrentHashMap<>();
    private static volatile Instant startedAt = Instant.now();
    private static volatile Instant endedAt;
    private static volatile boolean counting = true;

    private RunStats() {
    }

    /** A new run: everything counted from here until {@link #stop()}. */
    public static void start() {
        REQUESTS.clear();
        startedAt = Instant.now();
        endedAt = null;
        counting = true;
    }

    /** The run is over — the first line of the simulation's {@code after()}, before the channel is closed. */
    public static void stop() {
        if (counting) {
            counting = false;
            endedAt = Instant.now();
        }
    }

    /** A call is on the wire. */
    public static void sent(String requestName) {
        if (counting) {
            request(requestName).sent.increment();
        }
    }

    /** A call is over: OK or KO, how long it took, and how many messages it carried (1 for a unary call). */
    public static void answered(String requestName, boolean ok, int millis, int messages) {
        if (!counting) {
            return;
        }
        Request request = request(requestName);
        if (ok) {
            request.ok.increment();
        }
        request.messages.add(messages);
        request.record(millis);
    }

    public static Summary summary() {
        Instant end = endedAt == null ? Instant.now() : endedAt;
        Map<String, Numbers> perRequest = new LinkedHashMap<>();
        List<Integer> allTimes = new ArrayList<>();
        long sent = 0;
        long ok = 0;
        long messages = 0;
        for (String name : REQUESTS.keySet().stream().sorted().toList()) {
            Request request = REQUESTS.get(name);
            List<Integer> times = request.times();
            perRequest.put(name, Numbers.of(request.sent.sum(), request.ok.sum(), request.messages.sum(), times));
            allTimes.addAll(times);
            sent += request.sent.sum();
            ok += request.ok.sum();
            messages += request.messages.sum();
        }
        return new Summary(startedAt, end, Numbers.of(sent, ok, messages, allTimes), perRequest);
    }

    private static Request request(String name) {
        return REQUESTS.computeIfAbsent(name, key -> new Request());
    }

    public record Summary(Instant start, Instant end, Numbers global, Map<String, Numbers> perRequest) {

        public Duration duration() {
            return Duration.between(start, end);
        }
    }

    /**
     * One request's numbers. {@code answered} is how many calls got an answer, so {@code sent - answered} is what the run's end cut
     * off; percentiles are over the answers that came, OK and KO alike.
     */
    public record Numbers(long sent, long answered, long ok, long ko, long messages, int p50, int p95, int p99, int max) {

        static Numbers of(long sent, long ok, long messages, List<Integer> times) {
            List<Integer> sorted = times.stream().sorted().toList();
            long answered = sorted.size();
            return new Numbers(sent, answered, ok, Math.max(answered - ok, 0), messages, percentile(sorted, 50),
                    percentile(sorted, 95), percentile(sorted, 99), percentile(sorted, 100));
        }

        public double errorPercent() {
            return answered == 0 ? 0 : 100.0 * ko / answered;
        }

        /** Nearest-rank percentile; 0 for no data. */
        private static int percentile(List<Integer> sorted, int percent) {
            if (sorted.isEmpty()) {
                return 0;
            }
            int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
            return sorted.get(Math.min(Math.max(rank, 1), sorted.size()) - 1);
        }
    }

    private static final class Request {

        private final LongAdder sent = new LongAdder();
        private final LongAdder ok = new LongAdder();
        private final LongAdder messages = new LongAdder();
        private final List<Integer> times = new ArrayList<>();

        void record(int millis) {
            synchronized (times) {
                times.add(millis);
            }
        }

        List<Integer> times() {
            synchronized (times) {
                return new ArrayList<>(times);
            }
        }
    }
}
