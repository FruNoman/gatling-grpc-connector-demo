package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;
import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A unary gRPC call as a Gatling action — Gatling's own gRPC support is Gatling Enterprise; this is the free way, on the same
 * Gatling and in the same scenarios as the HTTP requests:
 *
 * <pre>{@code
 * exec(
 *         grpc("GRPCBin/DummyUnary", GRPCBinGrpc.getDummyUnaryMethod())
 *             .request(session -> DummyMessage.newBuilder().setFString(session.getString("payload")).build())
 *             .check((response, session) -> response.getFString().equals(session.getString("payload")) ? null : "echo differs")
 * )
 * }</pre>
 *
 * The RPC is named by protoc's generated descriptor getter and nothing else ({@link GrpcDsl}): no stub, no method reference, and
 * the request and response types inferred from it. The call goes out through {@code ClientCalls.asyncUnaryCall} — the
 * <b>async</b> path, the one the generated {@code newStub} takes, never {@code newBlockingStub}: a blocking call holds one of
 * Gatling's few engine threads and the run applies a fraction of the planned load with a healthy p95
 * ({@link GrpcBlockingActionBuilder} exists only to measure that). {@link #check}: null = OK, else the KO's reason.
 */
public final class GrpcActionBuilder<Q, R> implements ActionBuilder {

    private final GrpcChannel channel;
    private final String name;
    private final MethodDescriptor<Q, R> method;
    private Function<Session, Q> request;
    private GrpcCheck<R> check = (response, session) -> null;
    private UnaryOperator<CallOptions> callOptions = UnaryOperator.identity();

    private GrpcActionBuilder(GrpcChannel channel, String name, MethodDescriptor<Q, R> method) {
        this.channel = channel;
        this.name = name;
        this.method = method;
    }

    /** {@code name} — the request's name in the report and the assertions, the RPC's own ("GRPCBin/DummyUnary"). */
    static <Q, R> GrpcActionBuilder<Q, R> of(GrpcChannel channel, String name, MethodDescriptor<Q, R> method) {
        return new GrpcActionBuilder<>(channel, name, method);
    }

    /** The request message, built from the session. */
    public GrpcActionBuilder<Q, R> request(Function<Session, Q> requestFromSession) {
        this.request = requestFromSession;
        return this;
    }

    /** The answer's check ({@link GrpcCheck}). */
    public GrpcActionBuilder<Q, R> check(GrpcCheck<R> reasonOrNull) {
        this.check = reasonOrNull;
        return this;
    }

    /**
     * The one escape hatch the generated stub used to be: the {@link CallOptions} of every call of this action, on top of the
     * channel's deadline, applied <b>per call</b> inside the action.
     * <p>
     * This is where a token goes against a real service —
     * {@code .callOptions(options -> options.withCallCredentials(new BearerToken(tokens)))}, a {@code CallCredentials} that reads
     * the run's token store, as the HTTP protocol's header does. Nothing here may capture a deadline or anything else that is
     * absolute: the operator is called once per call, on whatever the channel just built ({@link GrpcChannel#callOptions}).
     * <p>
     * Static metadata (an API key, a tenant header) is smaller as a {@code ClientInterceptor} on the channel instead — one place
     * for the whole run, no per-action wiring.
     */
    public GrpcActionBuilder<Q, R> callOptions(UnaryOperator<CallOptions> perCall) {
        this.callOptions = perCall;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (request == null) {
            throw new IllegalStateException(name + ": no request(...)");
        }
        GrpcShape.require(name, method, MethodDescriptor.MethodType.UNARY);
        return (context, next) -> new GrpcUnaryAction<>(name, channel, method, callOptions, request, check,
                context.coreComponents().statsEngine(), context.coreComponents().clock(), next);
    }
}
