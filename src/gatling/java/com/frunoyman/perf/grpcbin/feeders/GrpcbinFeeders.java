package com.frunoyman.perf.grpcbin.feeders;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The data a session sends. grpcbin is an echo server — there is no data behind it, so nothing here is sampled from a real source
 * (as it would be against a real service, {@code qa-core:design-load-model} §4): each iteration gets a fresh id, and the echo check
 * compares what came back with it. That is the one thing an echo server can actually verify.
 * <p>
 * A Gatling feeder is an {@link Iterator} and nothing more — Gatling pulls from it on many threads at once, so the counter is
 * atomic and the record it hands out is immutable.
 */
public final class GrpcbinFeeders {

    private GrpcbinFeeders() {
    }

    /** An endless feeder of {@code #{payloadId}} / {@code #{payload}} — unique per iteration across all virtual users. */
    public static Iterator<Map<String, Object>> payloads() {
        AtomicLong next = new AtomicLong();
        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Map<String, Object> next() {
                long id = next.incrementAndGet();
                return Map.of("payloadId", (int) id, "payload", "payload-" + id);
            }
        };
    }
}
