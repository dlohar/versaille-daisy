#!/bin/bash
# Summarize result folders produced by run.sh and the suite scripts (run_small.sh, run_acc.sh, run_vcas.sh) as CSV.

source "$(dirname "$0")/common.sh"

dirs=("$@")
[ ${#dirs[@]} -eq 0 ] && dirs=("$DAISY_HOME"/results/*/)

echo "result,abs_error,daisy_time_ms,latency_min,latency_max,est_clock_ns,BRAM,DSP,FF,LUT"
for d in "${dirs[@]}"; do
  d="${d%/}"
  [ -f "$d/daisy.log" ] || continue
  rpt=$(ls "$d"/*_csynth.rpt 2>/dev/null | head -1)
  if [ -n "$rpt" ]; then hls=$(csynth_summary "$rpt"); else hls=",,,,,,"; fi
  echo "$(basename "$d"),$(daisy_summary "$d/daisy.log"),$hls"
done
