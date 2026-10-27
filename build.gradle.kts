// A Gatling (Java DSL) load test of a gRPC service with no Gatling Enterprise and no community gRPC library: the connector is
// src/gatling/java/.../core/grpc (a custom Gatling action over the generated async stub). The verdict comes from the
// simulation's own assertions — `./gradlew gatlingRun -Dsimulation=…` locally, any runner only sets properties and reads the
// exit code.
plugins {
    java
    checkstyle
    id("io.gatling.gradle") version "3.16.0"
    // gRPC stubs from src/gatling/proto — generated into the gatling source set, nothing hand-written
    id("com.google.protobuf") version "0.9.4"
}

java {
    toolchain {
        // the machine's default JDK may be newer; the connector touches Gatling's internals, so the version is pinned
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// The connector calls Gatling's internal Scala API, so these three move together and are proven together (docs/run-notes.md).
val grpc = "1.68.0"
val protobufVersion = "4.28.2"

dependencies {
    gatlingImplementation("io.grpc:grpc-netty-shaded:$grpc")
    gatlingImplementation("io.grpc:grpc-protobuf:$grpc")
    gatlingImplementation("io.grpc:grpc-stub:$grpc")
    gatlingImplementation("com.google.protobuf:protobuf-java:$protobufVersion")
    // the @javax.annotation.Generated on protoc-gen-grpc-java's stubs (not in the JDK since 11)
    gatlingCompileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpc"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

checkstyle {
    toolVersion = "10.12.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
}

// The run's parameters (-Dprofile=concurrency -Dusers=50 …, -D<env>.<path>=… config overrides) are passed on to the Gatling
// process; the simulation is named by its class: -Dsimulation=GrpcbinSimulation, found under **/simulations.
val runParameters = listOf(
    "env", "profile", "users", "rate", "rampUp", "steadyState", "step", "steps", "pauseMs",
    "blocking", "call", "maxFailedPercent",
)

tasks.withType<io.gatling.gradle.GatlingRunTask>().configureEach {
    val forwarded = System.getProperties().stringPropertyNames()
        .filter { it in runParameters || it.startsWith("local.") || it.startsWith("public.") }
        .associateWith { System.getProperty(it) }
    systemProperties = (systemProperties ?: emptyMap<String, Any>()) + forwarded
    System.getProperty("simulation")?.let { simulation ->
        val sources = file("src/gatling/java")
        val source = fileTree(sources).matching { include("**/simulations/$simulation.java") }.files.singleOrNull()
            ?: throw GradleException("No simulation $simulation under src/gatling/java/**/simulations")
        simulationClassName = source.relativeTo(sources).path.removeSuffix(".java").replace('/', '.')
    }
}

// generated gRPC stubs are not ours to style
tasks.withType<Checkstyle>().configureEach {
    exclude("**/proto/**", "**/grpcbin/GRPCBin*", "**/grpcbin/Grpcbin*")
}
