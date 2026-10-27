#!/usr/bin/env bash
# Fetches this repo's .proto files from a PINNED commit of their upstream source — never from a moving branch — so it is
# always possible to answer "which version of grpcbin's API are we actually testing against?" without guessing. The pin
# below is the single source of truth for what is vendored in src/gatling/proto/, and bumping it is a one-line, reviewable
# change. The file is vendored verbatim: no java_package option is added locally, so the diff stays readable.
#
# Usage:
#   ./scripts/update-protos.sh           # re-fetch the pinned version (verify nothing drifted)
#   ./scripts/update-protos.sh --latest  # print upstream's current HEAD, then fetch it
set -euo pipefail

cd "$(dirname "$0")/.."

# repo, path within it, and the exact commit the proto is vendored from
GRPCBIN_REPO="moul/pb"
GRPCBIN_PATH="grpcbin/grpcbin.proto"
GRPCBIN_COMMIT="bca18df4138cc423a9f8513f9c7d5f71f1ea4b35"

if [[ "${1:-}" == "--latest" ]]; then
  echo "[update-protos] resolving latest commit for ${GRPCBIN_REPO}@master..."
  GRPCBIN_COMMIT=$(curl -sf "https://api.github.com/repos/${GRPCBIN_REPO}/commits/master" | grep -m1 '"sha"' | cut -d'"' -f4)
  echo "[update-protos] latest is ${GRPCBIN_COMMIT} — update GRPCBIN_COMMIT in this script to that value,"
  echo "[update-protos] review the diff, and commit both the script change and the new .proto together."
fi

echo "[update-protos] fetching ${GRPCBIN_PATH} @ ${GRPCBIN_COMMIT}"
curl -sf "https://raw.githubusercontent.com/${GRPCBIN_REPO}/${GRPCBIN_COMMIT}/${GRPCBIN_PATH}" \
  -o "src/gatling/proto/grpcbin.proto"

echo "[update-protos] done — review with 'git diff src/gatling/proto/' before committing"
