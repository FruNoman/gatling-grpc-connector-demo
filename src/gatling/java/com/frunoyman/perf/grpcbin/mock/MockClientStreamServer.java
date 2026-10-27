package com.frunoyman.perf.grpcbin.mock;

import grpcbin.GRPCBinGrpc;
import grpcbin.Grpcbin.DummyMessage;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * grpcbin's {@code DummyClientStream} served in-process, because grpcbin's own is broken server-side
 * ({@code INTERNAL: grpc: error while marshaling: proto: Marshal called with nil} — docs/run-notes.md). Same proto, same generated
 * descriptor, so the action that calls it is exactly the one a working grpcbin would get; only the channel points here.
 * <p>
 * The answer is the upload's receipt: {@code f_int32} = messages received, {@code f_int64} = bytes received, {@code f_string} = the
 * first message's {@code f_string}. It runs in the load generator's JVM, so its latency is no service's latency — it is here to
 * prove the call shape, not to be measured.
 */
public final class MockClientStreamServer {

    private static final Logger LOG = LoggerFactory.getLogger(MockClientStreamServer.class);

    private final int port;
    private Server server;

    public MockClientStreamServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            server = ServerBuilder.forPort(port).addService(new Service()).build().start();
        } catch (IOException e) {
            throw new IllegalStateException("Mock DummyClientStream server on port " + port + " did not start", e);
        }
        LOG.info("Mock GRPCBin/DummyClientStream on port {} (grpcbin's own is broken server-side)", port);
    }

    public void stop() {
        if (server == null) {
            return;
        }
        server.shutdown();
        try {
            if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            server.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class Service extends GRPCBinGrpc.GRPCBinImplBase {

        @Override
        public StreamObserver<DummyMessage> dummyClientStream(StreamObserver<DummyMessage> receipt) {
            return new StreamObserver<>() {

                private int messages;
                private long bytes;
                private String first = "";

                @Override
                public void onNext(DummyMessage chunk) {
                    if (messages == 0) {
                        first = chunk.getFString();
                    }
                    messages++;
                    bytes += chunk.getFBytes().size();
                }

                @Override
                public void onError(Throwable error) {
                    // the client cancelled; nothing to answer
                }

                @Override
                public void onCompleted() {
                    receipt.onNext(DummyMessage.newBuilder().setFInt32(messages).setFInt64(bytes).setFString(first).build());
                    receipt.onCompleted();
                }
            };
        }
    }
}
