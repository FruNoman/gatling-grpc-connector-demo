package com.frunoyman.perf.core.grpc;

import io.grpc.MethodDescriptor;

import java.util.Arrays;

/**
 * The guarantee the old DSL got from the compiler, now checked at simulation start.
 * <p>
 * {@code grpc(name, Stub::method)} could not be given a streaming RPC: the method reference would not typecheck. A
 * {@link MethodDescriptor} typechecks for every shape — it carries its {@link MethodDescriptor.MethodType} as data, not as a type
 * — so the builder checks it in {@code asScala()}, which Gatling calls while the scenario is being built. A wrong-shape RPC
 * therefore fails before the first user is injected, with the RPC's full name in the message, instead of failing as every call of
 * a 75-second run.
 */
final class GrpcShape {

    private GrpcShape() {
    }

    static void require(String name, MethodDescriptor<?, ?> method, MethodDescriptor.MethodType... accepted) {
        for (MethodDescriptor.MethodType shape : accepted) {
            if (method.getType() == shape) {
                return;
            }
        }
        throw new IllegalStateException(name + ": " + method.getFullMethodName() + " is " + method.getType()
                + ", this builder sends " + Arrays.toString(accepted));
    }
}
