#!/usr/bin/env sh
# Host-only smoke tests for source-only GATT overlay policy.
# Does not compile the Momentum firmware, enable the service, or flash a Flipper.
set -eu
BASE=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
: "${CC:=cc}"
for name in ai_satellite_queue ai_satellite_session ai_satellite_trust ai_satellite_frame ai_satellite_replay; do
    "$CC" -std=c11 -O1 -Wall -Wextra -Werror -pedantic \
       "$BASE/tests/${name}_test.c" -o "$TMP/$name"
    "$TMP/$name"
done
python3 -m unittest discover -s "$BASE/tests" -p 'test_*.py'
echo "Host overlay tests passed; Momentum firmware and hardware NOT verified"
