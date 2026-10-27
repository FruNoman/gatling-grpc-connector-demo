package com.frunoyman.perf.core.grpc;

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A server-streaming RPC as one Gatling action — a chunked download: one request, N messages back, the response time of the
 * <b>whole</b> stream:
 *
 * <pre>{@code
 * exec(
 *         serverStream("GRPCBin/DummyServerStream", this::stub, GRPCBinStub::dummyServerStream)
 *             .request(session -> DummyMessage.newBuilder().setFBytes(chunk()).build())
 *             .check((messages, last, session) -> messages == DOWNLOAD_CHUNKS ? null : messages + " chunks of " + DOWNLOAD_CHUNKS)
 * )
 * }</pre>
 *
 * The shape the generated async stub gives it is the unary one ({@link GrpcActionBuilder.Call}: request in, observer out) — the
 * difference is entirely in the measurement. The messages are counted in {@code onNext} and the call is recorded at
 * {@code onCompleted}, so what the report shows is "how long the whole download took", not "how long it took to open the stream":
 * the mistake that makes a streaming load test look fast and prove nothing.
 */
public final class GrpcServerStreamActionBuilder<S, Q, R> implements ActionBuilder {

    private final String name;
    private final Supplier<S> stub;
    private final GrpcActionBuilder.Call<S, Q, R> call;
    private Function<Session, Q> request;
    private GrpcStreamCheck<R> check = (messages, last, session) -> null;

    private GrpcServerStreamActionBuilder(String name, Supplier<S> stub, GrpcActionBuilder.Call<S, Q, R> call) {
        this.name = name;
        this.stub = stub;
        this.call = call;
    }

    /** {@code name} — the request's name in the report and the assertions, the RPC's own ("GRPCBin/DummyServerStream"). */
    public static <S, Q, R> GrpcServerStreamActionBuilder<S, Q, R> serverStream(String name, Supplier<S> stub,
                                                                               GrpcActionBuilder.Call<S, Q, R> call) {
        return new GrpcServerStreamActionBuilder<>(name, stub, call);
    }

    /** The request that opens the stream, built from the session. */
    public GrpcServerStreamActionBuilder<S, Q, R> request(Function<Session, Q> requestFromSession) {
        this.request = requestFromSession;
        return this;
    }

    /** The stream's check over the messages that came back. */
    public GrpcServerStreamActionBuilder<S, Q, R> check(GrpcStreamCheck<R> reasonOrNull) {
        this.check = reasonOrNull;
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        if (request == null) {
            throw new IllegalStateException(name + ": no request(...)");
        }
        return (context, next) -> new GrpcServerStreamAction<>(name, stub, call, request, check,
                context.coreComponents().statsEngine(), context.coreComponents().clock(), next);
    }
}
