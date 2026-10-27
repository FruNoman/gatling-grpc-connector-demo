package com.frunoyman.perf.core.grpc;

import io.grpc.MethodDescriptor;

/**
 * The connector's DSL — a feature's actions class extends this, and a gRPC call is written as the RPC itself:
 *
 * <pre>{@code
 * public class GrpcbinActions extends GrpcDsl {
 *
 *     public GrpcbinActions(GrpcChannel channel, GrpcbinSettings settings) {
 *         super(channel);
 *     }
 *
 *     public ChainBuilder dummyUnary() {
 *         return exec(
 *                 grpc("GRPCBin/DummyUnary", GRPCBinGrpc.getDummyUnaryMethod())
 *                     .request(session -> DummyMessage.newBuilder().setFString(session.getString("payload")).build())
 *                     .check((response, session) -> response.getFString().equals(session.getString("payload")) ? null : "echo differs")
 *         );
 *     }
 * }
 * }</pre>
 *
 * No stub supplier, no method reference, no stub type in the feature package: protoc's generated
 * {@code static MethodDescriptor<Q, R> getXxxMethod()} already carries the marshallers, the full method name <b>and</b> the RPC's
 * {@link MethodDescriptor.MethodType}, and the generated stub's own method is nothing but
 * {@code ClientCalls.asyncUnaryCall(getChannel().newCall(getXxxMethod(), getCallOptions()), request, responseObserver)} — which is
 * what these actions do themselves, on the run's {@link GrpcChannel}. {@code Q} and {@code R} are inferred from the descriptor.
 * <p>
 * The shape the method reference used to guarantee at compile time is checked at simulation start instead ({@link GrpcShape}).
 */
public abstract class GrpcDsl {

    private final GrpcChannel channel;

    protected GrpcDsl(GrpcChannel channel) {
        this.channel = channel;
    }

    /** A unary RPC ({@link GrpcActionBuilder}) — {@code name} is the request's name in the report and the assertions. */
    protected final <Q, R> GrpcActionBuilder<Q, R> grpc(String name, MethodDescriptor<Q, R> method) {
        return GrpcActionBuilder.of(channel, name, method);
    }

    /** A server-streaming RPC, timed as a whole stream ({@link GrpcServerStreamActionBuilder}). */
    protected final <Q, R> GrpcServerStreamActionBuilder<Q, R> serverStream(String name, MethodDescriptor<Q, R> method) {
        return GrpcServerStreamActionBuilder.of(channel, name, method);
    }

    /** A bidirectional- or client-streaming RPC, sent under flow control ({@link GrpcBidiStreamActionBuilder}). */
    protected final <Q, R> GrpcBidiStreamActionBuilder<Q, R> bidiStream(String name, MethodDescriptor<Q, R> method) {
        return GrpcBidiStreamActionBuilder.of(channel, name, method);
    }

    /** The unary counter-example, on the blocking call ({@link GrpcBlockingActionBuilder}) — never for a real service. */
    protected final <Q, R> GrpcBlockingActionBuilder<Q, R> blockingGrpc(String name, MethodDescriptor<Q, R> method) {
        return GrpcBlockingActionBuilder.of(channel, name, method);
    }

    /** The server-streaming counter-example, drained on the Gatling thread ({@link GrpcBlockingStreamActionBuilder}). */
    protected final <Q, R> GrpcBlockingStreamActionBuilder<Q, R> blockingServerStream(String name, MethodDescriptor<Q, R> method) {
        return GrpcBlockingStreamActionBuilder.of(channel, name, method);
    }
}
