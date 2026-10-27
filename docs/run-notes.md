# Run notes

Every number below is from a run on this machine on **2026-10-27**: Gatling 3.16.0, grpc-java 1.68.0, Java 21, 10 CPU cores,
`moul/grpcbin:latest` in Docker Desktop (amd64 image under emulation on Apple silicon), target `localhost:9000` plaintext.
Reports under `build/reports/gatling/<run>/index.html`.

## The headline run — 50 concurrent users, green

```bash
docker compose up -d
./gradlew gatlingRun -Dsimulation=GrpcbinSimulation -Dprofile=concurrency -Dusers=50 -DrampUp=15 -DsteadyState=60
```

Report: `build/reports/gatling/grpcbinsimulation-20261027152303723/`

**Planned vs applied** — the first thing to read, and the symptom that catches a blocking call stalling the generator:

| | Planned | Applied |
|---|---|---|
| concurrency | ramp 1 → 50 users in 15 s, hold 50 for 60 s | 17 → 33 → **50**, held for 14 of the 16 console snapshots (the two lower ones are the ramp) |
| sessions | ≈ 317 (3 375 user-seconds ÷ a 10.65 s session) | **339 started**, 292 completed — the rest cut by the 75 s limit mid-download |
| requests | ≈ 950 | **921 OK, 0 KO** |
| wall clock | 75 s | 75 s (1 m 22 s including Gradle, compile and report) |

Per request, from the report's statistics table:

| Request | total | OK | KO | p50 | p95 | p99 | max | rps |
|---|---|---|---|---|---|---|---|---|
| `GRPCBin/DummyUnary` | 339 | 339 | 0 | 3 ms | **10 ms** | 27 ms | 76 ms | 4.52 |
| `GRPCBin/DummyServerStream` | 292 | 292 | 0 | 10 032 ms | **10 051 ms** | 10 060 ms | 10 143 ms | 3.89 |
| `GRPCBin/DummyBidirectionalStreamStream` | 290 | 290 | 0 | 18 ms | **32 ms** | 36 ms | 160 ms | 3.87 |
| Global | 921 | 921 | 0 | 18 ms | 10 044 ms | 10 053 ms | 10 143 ms | 12.28 |

Assertions, all green:

```
Global: percentage of failed events is less than or equal to 1.0 : true (actual : 0.0)
GRPCBin/DummyUnary: 95th percentile of response time is less than 500.0 : true (actual : 10.0)
GRPCBin/DummyServerStream: 95th percentile of response time is less than 11000.0 : true (actual : 10051.0)
GRPCBin/DummyBidirectionalStreamStream: 95th percentile of response time is less than 1000.0 : true (actual : 32.0)
```

The global p95 of 10 044 ms is the download and nothing else — which is why the verdict is made of per-request limits.

**Reproduced from a clean build on the merged commit** (`./gradlew clean gatlingRun …`, report
`grpcbinsimulation-20261027153621519/`): 922 requests, 0 KO, 12.29 req/s, 50 users held for 14 of 16 snapshots, every assertion
green — p95 unary 6 ms, download 10 057 ms, upload 29 ms. Own counting 967 sent / 922 answered / 45 cut, 6 169 messages. Two
runs an hour apart agree to within a request and a few milliseconds, which is what makes the numbers above a baseline rather
than an anecdote.

### The connector's own counting, against Gatling's report

The actions count for themselves (`core/stats/RunStats`), because Gatling 3.16 hands its statistics to nobody and because a
streaming call has a number Gatling's report has no column for: how many messages came back.

```
Run's own counting over 75 s:
  request                                          sent     ok     ko messages     p50     p95     max
  GRPCBin/DummyBidirectionalStreamStream            291    290      0     2900      18      32     160
  GRPCBin/DummyServerStream                         336    292      0     2920   10032   10051   10143
  GRPCBin/DummyUnary                                339    339      0      339       3      10      76
  ALL                                               966    921      0     6159      18   10044   10143
  45 calls were still out when the run ended — sent, never answered, counted as neither OK nor KO
```

**921 answered = 921 requests in Gatling's report**, and p50/p95/max agree per request. The messages confirm the streams were
whole, not merely opened: 292 downloads × 10 = 2 920, 290 uploads × 10 = 2 900, 339 unary calls × 1. The 45 unanswered calls are
the open downloads the run's time limit cut — counted as sent, never as failures, which is the difference between an honest
summary and a run that turns red for nothing.

## The async-vs-blocking contrast — a green report of a load that never happened

`GrpcbinInjectorSimulation` sends one call per session at a planned rate, async or blocking (`-Dblocking=true`), on the unary
echo or on the download (`-Dcall=`). Open profile, because a stalled generator is only visible against a plan.

```bash
./gradlew gatlingRun -Dsimulation=GrpcbinInjectorSimulation -Dprofile=load -Dcall=download -Drate=8 -DrampUp=5 -DsteadyState=20
./gradlew gatlingRun -Dsimulation=GrpcbinInjectorSimulation -Dprofile=load -Dcall=download -Drate=8 -DrampUp=5 -DsteadyState=20 -Dblocking=true
```

| Variant | Planned | Applied | p95 | Assertions | Run took |
|---|---|---|---|---|---|
| async call (`StreamObserver`) | 180 downloads (8/s × 20 s + ramp), ~80 open at once | **180 of 180** | 10 056 ms | green | 35 s |
| **blocking call** (`Iterator`, drained in the action) | the same 180 | **99 of 180** (+20 still out at the end) | 10 061 ms | green | **55 s** |

The blocking run lost 45 % of its plan, took 20 s longer than its own injection, and reported the same latency and the same
green assertions. That is the whole argument: p95 says nothing about a load that was never applied — the applied count does.

### …and the condition the rule depends on

The stall is `rate × latency` against the engine's threads, not "blocking is slow". The same comparison on calls that are fast
locally shows no gap at all:

| Call | Rate | Concurrency | Async applied | Blocking applied |
|---|---|---|---|---|
| unary echo (~1 ms) | 500/s | ~1 | 11 250 of 11 250 | 11 250 of 11 250 |
| unary echo (~1 ms) | 5 000/s | ~5 | 62 500 of 62 500 | 62 500 of 62 500 |
| download (~10.1 s) | 2/s | ~20 | 65 of 65 | 65 of 65 |
| download (~10.1 s) | 8/s | ~80 | 180 of 180 | **99 of 180** |

With 10 cores, blocking survived 20 concurrent calls and collapsed at 80. Writing the blocking version is still wrong — it puts
a ceiling into the generator that nothing in the report reveals, and the ceiling moves with the machine, the latency and the
rate. But a run that shows no gap has not proven the connector is non-blocking: the gap only appears above the ceiling.

## What the target itself does (verified, not assumed)

- **`DummyServerStream` sends ten messages, one per second.** The first run set a 10 s deadline and every download died as
  `DEADLINE_EXCEEDED: CallOptions deadline exceeded after 9.999…s` — 6 of 6 KO. Timed independently with grpcurl: 10.31 s and
  10.34 s for the whole stream against 0.21 s for a unary call (both including container start). So the deadline went to 15 s and
  the download got a p95 limit of its own. The "chunked download" of this demo is really a ten-second drip — which, for once,
  makes it a good streaming target: timing the *whole* stream instead of its opening is the difference between 10 051 ms and
  ~5 ms.
- **`DummyClientStream` is still broken server-side**, as the predecessor demo documented in 2026-09. Re-verified from scratch
  with grpcurl (no Gatling involved):
  ```
  $ grpcurl -plaintext -d @ grpcbin:9000 grpcbin.GRPCBin/DummyClientStream
  ERROR:
    Code: Internal
    Message: grpc: error while marshaling: proto: Marshal called with nil
  ```
  `DummyUnary`, `DummyServerStream` and `DummyBidirectionalStreamStream` all answer correctly. The bidi RPC stands in for the
  chunked upload, as it did in the predecessor.

## Smoke run (the one that runs after every change)

```bash
./gradlew gatlingRun -Dsimulation=GrpcbinSimulation -Dprofile=smoke -Dusers=3 -DrampUp=5 -DsteadyState=25
```

21 requests, 0 KO, p95: unary 81 ms, download 10 069 ms, upload 34 ms; own counting 24 sent / 21 answered / 3 cut, 129 messages
(9 + 60 + 60 = exactly ten per stream). Green, and the assertion output names every limit — the cheapest proof that the
connector still works after a Gatling upgrade, which matters because it is built on Gatling's internal API.
