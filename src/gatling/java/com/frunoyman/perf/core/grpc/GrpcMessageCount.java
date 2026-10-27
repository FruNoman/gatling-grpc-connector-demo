package com.frunoyman.perf.core.grpc;

/**
 * {@code messageCount()} — how many messages a stream delivered, the check that catches a server closing the stream early:
 *
 * <pre>{@code
 * .check(messageCount().is(DOWNLOAD_CHUNKS))
 * }</pre>
 */
public final class GrpcMessageCount {

    static final GrpcMessageCount INSTANCE = new GrpcMessageCount();

    private GrpcMessageCount() {
    }

    /** Exactly {@code expected} messages; the KO reason says how many came. */
    public <R> GrpcStreamCheck<R> is(int expected) {
        return (messages, last, session) -> messages == expected
                ? null
                : messages + " messages, expected " + expected;
    }
}
