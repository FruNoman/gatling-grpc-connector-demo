package com.frunoyman.perf.grpcbin.config;

import com.frunoyman.perf.core.config.Environment;
import com.typesafe.config.Config;

/**
 * The target under test ({@code grpcbin} in application.conf): where it listens, plaintext or TLS, the deadline of every call, and
 * the size of the chunked shapes.
 *
 * @param chunkBytes   bytes in one message of a stream
 * @param uploadChunks how many messages one upload stream sends
 */
public record GrpcbinSettings(String target, boolean plaintext, int deadlineMs, int chunkBytes, int uploadChunks) {

    public static GrpcbinSettings load() {
        Config grpcbin = Environment.block("grpcbin");
        return new GrpcbinSettings(Environment.required(grpcbin, "target"), grpcbin.getBoolean("plaintext"),
                grpcbin.getInt("deadline-ms"), grpcbin.getInt("chunk-bytes"), grpcbin.getInt("upload-chunks"));
    }
}
