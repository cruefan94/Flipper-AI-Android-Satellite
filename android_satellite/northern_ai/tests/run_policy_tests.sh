#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../../" && pwd)
SRC="$ROOT/northern_ai/src/main/java/com/northerncircuitstudios/ai"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" "$SRC/AssistantPolicy.java" "$SRC/CloudModePolicy.java" \
  "$ROOT/northern_ai/tests/AssistantPolicyTest.java" "$ROOT/northern_ai/tests/CloudModePolicyTest.java"
java -cp "$OUT" AssistantPolicyTest
java -cp "$OUT" CloudModePolicyTest
python3 -m unittest discover -s "$ROOT/northern_ai/tests" -p 'test_*.py' -v
