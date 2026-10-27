package com.frunoyman.perf.core.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/**
 * One channel per target for the whole run: built in the simulation's {@code before()}, closed in {@code after()} — stubs are cheap
 * and made per call, the channel (connections, HTTP/2 streams) is not. The actions read it at call time, so they can be built in
 * the simulation's constructor, before the channel exists.
 */
public final class GrpcChannel {

    private final String target;
    private final boolean plaintext;
    private volatile ManagedChannel channel;

    /** {@code target}: {@code host:port}; {@code plaintext} for a local target, TLS otherwise. */
    public GrpcChannel(String target, boolean plaintext) {
        this.target = target;
        this.plaintext = plaintext;
    }

    public void start() {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target);
        channel = (plaintext ? builder.usePlaintext() : builder.useTransportSecurity()).build();
    }

    public ManagedChannel get() {
        ManagedChannel current = channel;
        if (current == null) {
            throw new IllegalStateException("gRPC channel to " + target + " not started — GrpcChannel.start() belongs in before()");
        }
        return current;
    }

    public void stop() {
        ManagedChannel current = channel;
        channel = null;
        if (current == null) {
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitTermination(5, TimeUnit.SECONDS)) {
                current.shutdownNow();
            }
        } catch (InterruptedException e) {
            current.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        return target + (plaintext ? " (plaintext)" : " (TLS)");
    }
}
