package com.frunoyman.perf.core.grpc;

import io.grpc.MethodDescriptor;

/**
 * {@code grpc(name)} — the request's name, waiting for its RPC, the way Gatling's own gRPC DSL reads:
 * {@code grpc("GRPCBin/DummyUnary").unary(GRPCBinGrpc.getDummyUnaryMethod())}. The name is what the report and the assertions
 * show; the descriptor is the RPC. The method picked here is the shape the call is made in, and the descriptor's own
 * {@link MethodDescriptor.MethodType} is checked against it at simulation start ({@link GrpcShape}).
 */
public final class GrpcRequest {

    private final GrpcChannel channel;
    private final String name;

    GrpcRequest(GrpcChannel channel, String name) {
        this.channel = channel;
        this.name = name;
    }

    /** A unary RPC ({@link GrpcActionBuilder}). */
    public <Q, R> GrpcActionBuilder<Q, R> unary(MethodDescriptor<Q, R> method) {
        return GrpcActionBuilder.of(channel, name, method);
    }

    /** A server-streaming RPC, timed as a whole stream ({@link GrpcServerStreamActionBuilder}). */
    public <Q, R> GrpcServerStreamActionBuilder<Q, R> serverStream(MethodDescriptor<Q, R> method) {
        return GrpcServerStreamActionBuilder.of(channel, name, method);
    }

    /** A bidirectional- or client-streaming RPC, sent under flow control ({@link GrpcBidiStreamActionBuilder}). */
    public <Q, R> GrpcBidiStreamActionBuilder<Q, R> bidiStream(MethodDescriptor<Q, R> method) {
        return GrpcBidiStreamActionBuilder.of(channel, name, method);
    }

    /** The unary counter-example, on the blocking call ({@link GrpcBlockingActionBuilder}) — never for a real service. */
    public <Q, R> GrpcBlockingActionBuilder<Q, R> blockingUnary(MethodDescriptor<Q, R> method) {
        return GrpcBlockingActionBuilder.of(channel, name, method);
    }

    /** The server-streaming counter-example, drained on the Gatling thread ({@link GrpcBlockingStreamActionBuilder}). */
    public <Q, R> GrpcBlockingStreamActionBuilder<Q, R> blockingServerStream(MethodDescriptor<Q, R> method) {
        return GrpcBlockingStreamActionBuilder.of(channel, name, method);
    }
}
