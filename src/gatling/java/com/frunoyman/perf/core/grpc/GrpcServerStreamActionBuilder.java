package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;
import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A server-streaming RPC as one Gatling action — a chunked download: one request, N messages back, the response time of the
 * <b>whole</b> stream:
 *
 * <pre>{@code
 * exec(
 *         grpc("GRPCBin/DummyServerStream")
 *             .serverStream(GRPCBinGrpc.getDummyServerStreamMethod())
 *             .send(session -> DummyMessage.newBuilder().setFBytes(chunk()).build())
 *             .check(messageCount().is(DOWNLOAD_CHUNKS))
 * )
 * }</pre>
 *
 * The call is the same {@code (request, observer)} one a unary RPC makes, on {@code ClientCalls.asyncServerStreamingCall} — the
 * difference is entirely in the measurement. The messages are counted in {@code onNext} and the call is recorded at
 * {@code onCompleted}, so what the report shows is "how long the whole download took", not "how long it took to open the stream":
 * the mistake that makes a streaming load test look fast and prove nothing.
 */
public final class GrpcServerStreamActionBuilder<Q, R> implements ActionBuilder {

    private final GrpcChannel channel;
    private final String name;
    private final MethodDescriptor<Q, R> method;
    private Function<Session, Q> request;
    private GrpcStreamCheck<R> check = (messages, last, session) -> null;
    private UnaryOperator<CallOptions> callOptions = UnaryOperator.identity();

    private GrpcServerStreamActionBuilder(GrpcChannel channel, String name, MethodDescriptor<Q, R> method) {
        this.channel = channel;
        this.name = name;
        this.method = method;
    }

    /** {@code name} — the request's name in the report and the assertions, the RPC's own ("GRPCBin/DummyServerStream"). */
    static <Q, R> GrpcServerStreamActionBuilder<Q, R> of(GrpcChannel channel, String name, MethodDescriptor<Q, R> method) {
        return new GrpcServerStreamActionBuilder<>(channel, name, method);
    }

    /** The request that opens the stream, built from the session. */
    public GrpcServerStreamActionBuilder<Q, R> send(Function<Session, Q> requestFromSession) {
        this.request = requestFromSession;
        return this;
    }

    /** The stream's check over the messages that came back — several are run in order and the first reason is the KO's. */
    @SafeVarargs
    public final GrpcServerStreamActionBuilder<Q, R> check(GrpcStreamCheck<R>... checks) {
        this.check = GrpcStreamCheck.all(checks);
        return this;
    }

    /** The call's {@link CallOptions}, per call — a token goes here; see {@link GrpcActionBuilder#callOptions}. */
    public GrpcServerStreamActionBuilder<Q, R> callOptions(UnaryOperator<CallOptions> perCall) {
        this.callOptions = perCall;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (request == null) {
            throw new IllegalStateException(name + ": no send(...)");
        }
        GrpcShape.require(name, method, MethodDescriptor.MethodType.SERVER_STREAMING);
        return (context, next) -> new GrpcServerStreamAction<>(name, channel, method, callOptions, request, check,
                context.coreComponents().statsEngine(), context.coreComponents().clock(), next);
    }
}
