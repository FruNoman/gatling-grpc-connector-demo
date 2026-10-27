package com.frunoyman.perf.grpcbin.actions;

import com.frunoyman.perf.core.grpc.GrpcChannel;
import com.frunoyman.perf.core.grpc.GrpcDsl;
import com.frunoyman.perf.grpcbin.config.GrpcbinSettings;
import com.google.protobuf.ByteString;
import grpcbin.GRPCBinGrpc;
import grpcbin.Grpcbin.DummyMessage;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.exec;

/**
 * The fourth RPC shape — client streaming, the classic batch upload: N chunks out, one receipt back. grpcbin's own
 * {@code DummyClientStream} is broken server-side, so this class takes the channel to the in-process mock
 * ({@code MockClientStreamServer}); the descriptor is grpcbin's, the call is exactly the one a working grpcbin would get.
 */
public class GrpcbinClientStreamActions extends GrpcDsl {

    private final int uploadChunks;
    private final ByteString chunk;

    public GrpcbinClientStreamActions(GrpcChannel mockChannel, GrpcbinSettings settings) {
        super(mockChannel);
        this.uploadChunks = settings.uploadChunks();
        this.chunk = GrpcbinActions.randomChunk(settings.chunkBytes());
    }

    /** {@code GRPCBin/DummyClientStream} — a batch upload: every chunk must be in the receipt, and the receipt must be ours. */
    public ChainBuilder dummyClientStream() {
        return exec(
                grpc("GRPCBin/DummyClientStream")
                    .clientStream(GRPCBinGrpc.getDummyClientStreamMethod())
                    .send(uploadChunks, (session, index) -> DummyMessage.newBuilder()
                            .setFString(session.getString("payload"))
                            .setFInt32(index)
                            .setFBytes(chunk)
                            .build())
                    .check(
                            messageCount().is(1),
                            last(response(DummyMessage::getFInt32).is(uploadChunks)),
                            last(response(DummyMessage::getFString).isEL("#{payload}"))
                    )
        );
    }
}
