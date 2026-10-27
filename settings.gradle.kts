plugins {
    // resolves the Java 21 toolchain without a hand-installed JDK (the machine's default may be another version)
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "gatling-grpc-connector-demo"
