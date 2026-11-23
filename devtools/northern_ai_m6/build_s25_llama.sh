#!/usr/bin/env bash
# Galaxy S25 arm64-v8a native runtime compile proof.
# This is NOT yet connected to the Northern Circuit AI Android chat Activity.
set -euo pipefail

UPSTREAM_SHA="f4f6f2e7db76e98c95cc9b7fc608ea669c806044"
: "${ANDROID_NDK:?ANDROID_NDK must be the pinned Android NDK root}"
: "${OUT_DIR:?OUT_DIR must be an output directory}"

WORK="${RUNNER_TEMP:-/tmp}/northern-m6-llama-source"
mkdir -p "$WORK" "$OUT_DIR"
git -C "$WORK" init -q
git -C "$WORK" remote add origin https://github.com/ggml-org/llama.cpp.git
git -C "$WORK" fetch -q --depth 1 origin "$UPSTREAM_SHA"
git -C "$WORK" checkout -q --detach FETCH_HEAD
test "$(git -C "$WORK" rev-parse HEAD)" = "$UPSTREAM_SHA"

cmake -S "$WORK" -B "$WORK/build-s25" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-33 \
  -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF \
  -DGGML_LLAMAFILE=OFF \
  -DLLAMA_OPENSSL=OFF \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_SERVER=OFF \
  -DLLAMA_BUILD_COMMON=OFF \
  -DBUILD_SHARED_LIBS=ON

cmake --build "$WORK/build-s25" --target llama --parallel 2
find "$WORK/build-s25" -type f -name 'lib*.so' -print
mapfile -t LIBS < <(find "$WORK/build-s25" -type f -name 'lib*.so')
test "${#LIBS[@]}" -ge 1
for lib in "${LIBS[@]}"; do
  cp "$lib" "$OUT_DIR/$(basename "$lib")"
done
test -s "$OUT_DIR/libllama.so"
cp "$WORK/LICENSE" "$OUT_DIR/LLAMA_CPP_LICENSE.txt"
for lib in "$OUT_DIR"/*.so; do
  file "$lib" | grep -q 'ARM aarch64'
done
( cd "$OUT_DIR" && sha256sum *.so > SHA256SUMS.txt && sha256sum -c SHA256SUMS.txt )
python3 - "$OUT_DIR" "$UPSTREAM_SHA" <<'PY'
import json, pathlib, sys
out = pathlib.Path(sys.argv[1])
(out / "BUILD_EVIDENCE.json").write_text(json.dumps({
    "upstream_repo": "ggml-org/llama.cpp",
    "upstream_sha": sys.argv[2],
    "target_device": "Galaxy S25",
    "target_abi": "arm64-v8a",
    "android_api_min": 33,
    "engine_libraries_compiled": True,
    "linked_into_northern_ai_apk": False,
    "native_inference_tested_on_s25": False,
    "notes": "First native build gate only; full JNI, model import and in-app inference remain unimplemented."
}, indent=2) + "\n")
PY
