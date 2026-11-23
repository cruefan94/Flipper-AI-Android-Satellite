#!/usr/bin/env sh
# S25 Northern Circuit AI — local-only, no-root smoke/performance check.
# Requires a separately running llama.cpp server on 127.0.0.1:9931.
# Never sends data to cloud or writes private conversations to disk.
set -eu
RAM_PLUS_GB="${1:-8}"
case "$RAM_PLUS_GB" in
  0|2|4|6|8) ;;
  *) printf 'Usage: sh %s [RAM_Plus_GB: 0|2|4|6|8]\n' "$0" >&2; exit 2 ;;
esac
if ! command -v curl >/dev/null 2>&1; then
  printf 'Please install curl in Termux (pkg install curl).\n' >&2
  exit 2
fi
url="http://127.0.0.1:9931/v1/chat/completions"
mem_available_kb() {
  if [ -r /proc/meminfo ]; then
    awk '/^MemAvailable:/ {print $2; found=1; exit} END {if(!found) print "unknown"}' /proc/meminfo
  else
    printf 'unknown\n'
  fi
}
payload='{"model":"flipper-local","messages":[{"role":"user","content":"In two short sentences, explain how a resistor works."}],"max_tokens":96,"stream":false,"temperature":0}'
printf 'Northern Circuit AI — Galaxy S25 local smoke test\n'
printf 'RAM Plus setting (user-reported; not auto-detected): %s GB\n' "$RAM_PLUS_GB"
printf 'Testing only: %s\n' "$url"
printf 'Physical RAM model: 12 GB; RAM Plus is storage-backed, not physical RAM.\n'
printf 'Available system RAM before (kB): %s\n' "$(mem_available_kb)"
printf 'Start the local llama-server separately before running this test.\n'
i=1
while [ "$i" -le 3 ]; do
  # /dev/null prevents storing model-generated output or sensitive material.
  outcome="$(curl --noproxy '*' --silent --show-error --connect-timeout 3 --max-time 180 \
    --output /dev/null --write-out '%{http_code} %{time_total}' \
    --header 'Content-Type: application/json' --data "$payload" "$url")" || {
      printf 'Trial %s: failed (server unavailable, stopped or timed out).\n' "$i"
      exit 1
    }
  code="${outcome%% *}"
  latency="${outcome#* }"
  if [ "$code" != "200" ]; then
    printf 'Trial %s: HTTP %s (not a successful local inference).\n' "$i" "$code"
    exit 1
  fi
  printf 'Trial %s: HTTP 200, total request time %s seconds, available RAM %s kB\n' \
    "$i" "$latency" "$(mem_available_kb)"
  i=$((i + 1))
done
printf 'Three HTTP requests completed. Confirm response content in Android MODELS test.\n'
printf 'Record phone heat, battery drop, crashes, responsiveness and background app reloads manually.\n'
printf 'HTTP 200 by itself is not proof that the model generated a useful answer.\n'
