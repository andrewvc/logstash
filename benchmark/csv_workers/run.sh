#!/usr/bin/env bash
# Benchmark: CSV output with pipeline.workers=1 vs pipeline.workers=4
#
# The CSV output plugin (concurrency :shared) encodes events to CSV *outside*
# the @io_mutex and serializes file writes *inside* it. This benchmark measures
# whether more pipeline workers improve throughput by parallelizing the encoding
# phase, or whether mutex contention on the write phase negates the gains.
#
# Usage: bash benchmark/csv_workers/run.sh [event_count]
#   event_count defaults to 500000
#
# Output files: /tmp/ls_bench_w1.csv and /tmp/ls_bench_w4.csv
# Logstash logs: /tmp/ls_bench_w1.log and /tmp/ls_bench_w4.log

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOGSTASH_BIN="$REPO_ROOT/bin/logstash"
CONF="$SCRIPT_DIR/pipeline.conf"
SETTINGS_DIR="$SCRIPT_DIR"
EVENT_COUNT="${1:-500000}"

# Patch event count into a temp config so it's parameterizable without editing pipeline.conf.
TMP_CONF="$(mktemp /tmp/ls_bench_XXXXXX.conf)"
trap 'rm -f "$TMP_CONF"' EXIT
sed "s/count => 500000/count => $EVENT_COUNT/" "$CONF" > "$TMP_CONF"

echo "Logstash CSV output benchmark: pipeline.workers 1 vs 4"
echo "======================================================="
echo "Events per run : $EVENT_COUNT"
echo "Config         : $CONF"
echo "Logstash       : $LOGSTASH_BIN"
echo ""

results_w1=""
results_w4=""

for W in 1 4; do
  OUTPUT_FILE="/tmp/ls_bench_w${W}.csv"
  DATA_DIR="/tmp/ls_bench_data_w${W}"
  LOG_FILE="/tmp/ls_bench_w${W}.log"

  rm -f  "$OUTPUT_FILE"
  rm -rf "$DATA_DIR"

  echo "--- workers=$W (logs -> $LOG_FILE) ---"
  START=$(date +%s)

  LS_BENCH_OUTPUT="$OUTPUT_FILE" \
    "$LOGSTASH_BIN" \
      -f "$TMP_CONF" \
      -w "$W" \
      --path.settings "$SETTINGS_DIR" \
      --path.data    "$DATA_DIR" \
      >"$LOG_FILE" 2>&1

  END=$(date +%s)
  ELAPSED=$(( END - START ))

  if [ ! -f "$OUTPUT_FILE" ]; then
    echo "  ERROR: output file not created. Check $LOG_FILE for details."
    continue
  fi

  LINES=$(wc -l < "$OUTPUT_FILE" | tr -d ' ')
  if [ "$ELAPSED" -gt 0 ]; then
    EPS=$(( LINES / ELAPSED ))
  else
    EPS="$LINES"
  fi

  RESULT="events=$LINES  elapsed=${ELAPSED}s  EPS=${EPS}/s"
  echo "  $RESULT"
  echo ""

  if [ "$W" -eq 1 ]; then
    results_w1="$RESULT"
  else
    results_w4="$RESULT"
  fi
done

echo "======================================================="
echo "Summary"
echo "======================================================="
echo "  workers=1  $results_w1"
echo "  workers=4  $results_w4"
