package com.frunoyman.perf.core.grpc;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;

import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * One channel per target for the whole run: built in the simulation's {@code before()}, closed in {@code after()} — a channel
 * (connections, HTTP/2 streams) is expensive, a call is not. The actions read it at call time, so they can be built in the
 * simulation's constructor, before the channel exists.
 * <p>
 * Since the connector dropped the generated stub ({@link GrpcDsl}), this is also where a call's {@link CallOptions} are made: the
 * stub used to carry the deadline, now the channel does, from the target's own config.
 */
public final class GrpcChannel {

    private final String target;
    private final boolean plaintext;
    private final int deadlineMs;
    private volatile ManagedChannel channel;

    /** {@code target}: {@code host:port}; {@code plaintext} for a local target, TLS otherwise; {@code deadlineMs} per call. */
    public GrpcChannel(String target, boolean plaintext, int deadlineMs) {
        this.target = target;
        this.plaintext = plaintext;
        this.deadlineMs = deadlineMs;
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

    /**
     * Fresh {@link CallOptions} for <b>one</b> call, the builder's escape hatch applied last.
     * <p>
     * A deadline is <b>absolute from the moment it is set</b>, not a per-call timeout: {@code withDeadlineAfter} resolves to a
     * point in time there and then. Build these once at startup and share them and every call after the first {@code deadlineMs}
     * of the run dies as {@code DEADLINE_EXCEEDED} — a whole run turning red at a wall-clock moment, with the service innocent.
     * So this is a method, called inside the action, and never a field; the old code got the same guarantee from making a fresh
     * stub per call.
     */
    CallOptions callOptions() {
        return CallOptions.DEFAULT.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
    }

    /** The same, with a builder's {@code .callOptions(…)} escape hatch applied on top. */
    CallOptions callOptions(UnaryOperator<CallOptions> perCall) {
        return perCall.apply(callOptions());
    }

    /** The call protoc's generated stub would have made: {@code getChannel().newCall(getXxxMethod(), getCallOptions())}. */
    <Q, R> ClientCall<Q, R> newCall(MethodDescriptor<Q, R> method, UnaryOperator<CallOptions> perCall) {
        return get().newCall(method, callOptions(perCall));
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
