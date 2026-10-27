# Load model — grpcbin (2026-10-27)

What each run answers, before any simulation is read. The target is [grpcbin](https://github.com/moul/grpcbin) in Docker, so
nothing here is a production SLO — the point is that the numbers exist, have a source, and decide the run's exit code.

Goal:

- Does a hand-written Gatling connector measure a gRPC call correctly at all — unary, server stream, bidi stream? → **smoke**
- What do N simultaneous clients get out of the three shapes, and does the connector hold at N = 50? → **concurrency**
- Does the load generator apply what was planned, with the async call and with the blocking one? → **injector**
- Where does it bend? → **stress** (shape in place, not run; see "Not covered")

## Profiles

| Profile | Shape | Duration | Verdict |
|---|---|---|---|
| `smoke` | closed, 3 users looping the session | 5 s ramp + 25 s | 0 failed calls + p95 per request |
| `concurrency` | closed, ramp 1 → 50 concurrent users, then hold | 15 s ramp + 60 s | ≤ 1 % failed + p95 per request |
| `load` (injector) | open, ramp to 8 downloads/s, then hold | 5 s ramp + 20 s | ≤ 1 % failed + p95; **read the applied count against the plan** |
| `stress` | open, stairs of rate, 5 levels | 30 s per level | none — the result is the level where it bends |

**Closed for the headline run, on purpose.** The default is open (clients arrive whatever the service does, so a slow service
cannot lower its own load), and `design-load-model` keeps closed for the one case where the number of simultaneous clients *is*
the question. That is exactly this demo's question — "50 concurrent users" — so `concurrency` is closed and says so. The
injector runs are open, because a stalled generator is only visible against a plan it can miss.

## Scenario

Session = three calls with 200 ms think time between them:

1. `GRPCBin/DummyUnary` — a unary echo, the baseline latency of the chain;
2. `GRPCBin/DummyServerStream` — a chunked download: one request, ten 64 KB messages back;
3. `GRPCBin/DummyBidirectionalStreamStream` — a chunked upload: ten 64 KB messages out, each echoed.

So one session = 3 requests, and in the open profiles requests/s ≈ sessions/s × 3. Every RPC the demo covers is in every session,
so any profile exercises all of them.

**The mix is a guess, and that is a property of the target, not a shortcut**: grpcbin is an echo server with no clients and no
logs, so there is no production shape to copy. Against a real service the mix would come from replayed logs or weights from APM,
in that order of preference.

## SLO

| Request | p95 limit | Source |
|---|---|---|
| `GRPCBin/DummyUnary` | 500 ms | baseline run 2026-10-27 (p95 10 ms at 50 users) + a wide margin |
| `GRPCBin/DummyServerStream` | 11 000 ms | the server's own pacing: grpcbin sends one message per second, so ten chunks take ~10.1 s (measured, also with grpcurl) |
| `GRPCBin/DummyBidirectionalStreamStream` | 1 000 ms | baseline run 2026-10-27 (p95 32 ms at 50 users) + a margin |
| failed requests | 0 for smoke, ≤ 1 % otherwise | — |

**A p95 per request, never one global percentile**: these three differ by two orders of magnitude, and a global p95 of the
session would be nothing but the download. A threshold loosened to make a run green is a finding, not a fix — the download's
11 s is not a loosened threshold but the server's documented behaviour, and the deadline (15 s) was raised to clear it after the
first run turned every download into a `DEADLINE_EXCEEDED`.

## Data

grpcbin echoes whatever it is sent, so there is no real data to sample (against a real service: a random sample from a live
source per run, loaded in `before()`, with a minimum count that fails the run). Here:

- a feeder hands every session iteration a fresh `payloadId` / `payload`, and the unary check compares the echo with it — the one
  correctness statement an echo server allows;
- one random 64 KB block is generated per run and reused by every chunk, so the run measures the service and not our RNG;
- no identities, no auth, no charged calls, nothing to clean up afterwards.

## Environment

Local Docker: `moul/grpcbin:latest`, published on `localhost:9000` (plaintext). The image is amd64-only and runs under emulation
on Apple silicon, which is part of every number here — the latencies are not a statement about grpcbin on real hardware. Nobody
else uses it, so there is no window to agree and no rate ceiling to respect.

Pre-flight before the first run: port 9000 free, image present, `docker compose ps` healthy, one `grpcurl` probe per RPC the
simulation sends (that is what caught the broken `DummyClientStream` and the server's one-message-per-second pacing).

## Not covered

- **`spike` and `soak`** — the two profiles the core list has and this demo leaves out: both answer questions about a service
  (burst recovery, degradation over hours) that an echo container in Docker cannot answer honestly.
- **`stress`** — the injection and the no-assertions verdict are implemented, but no stress run is recorded: against a 10 s
  drip the breaking point found would be grpcbin's emulation overhead, not a capacity statement.
- **`GRPCBin/DummyClientStream`** — client streaming is broken server-side in grpcbin (`INTERNAL: grpc: error while marshaling:
  proto: Marshal called with nil`), re-verified with grpcurl on 2026-10-27. The connector's streaming-request builder covers the
  shape; the demo loads the bidi RPC instead.
- **TLS** — the `public` config block points at `grpcb.in:9001` (the public instance of the same server) and the channel supports
  it, but no run is recorded against it.
- **Auth** — grpcbin needs none. Against a real service the token goes through the builder's `.callOptions(…)` hatch as a
  `CallCredentials` (static metadata: a channel interceptor), fetched once beside the run, never as a request in the statistics.
  That hatch is the connector's answer to the stub having been where per-user auth used to live; it is the one place a call can be
  changed, and it cannot read the session.
- **Chained calls** — the predecessor demo passed values from one response into the next request; this connector has no `saveAs`,
  so a chain would be written by hand in the check. Not built here.
