#!/bin/bash

DAISY_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export DAISY_HOME

# HLS target
HLS_PART="${HLS_PART:-xc7z020clg400-1}"   # PYNQ-Z1 / Zynq-7020
HLS_CLOCK="${HLS_CLOCK:-10}"              # clock period in ns

# Build Daisy and generate the ./daisy.
ensure_daisy() {
  if [ ! -x "$DAISY_HOME/daisy" ]; then
    echo ">> building Daisy (first run)"
    (cd "$DAISY_HOME" && sbt -batch compile script) || { echo "build failed"; exit 1; }
  fi
}

# Find Vitis HLS (or Vivado HLS).
find_hls() {
  HLS=""
  local c d
  for c in vitis_hls vivado_hls; do
    command -v "$c" >/dev/null 2>&1 && { HLS="$c"; return 0; }
  done
  for d in "$VITIS_HLS_HOME" "$XILINX_HLS" "$XILINX_VITIS"; do
    if [ -n "$d" ] && [ -f "$d/settings64.sh" ]; then
      set +eu; source "$d/settings64.sh" >/dev/null 2>&1; set -e
      for c in vitis_hls vivado_hls; do
        command -v "$c" >/dev/null 2>&1 && { HLS="$c"; return 0; }
      done
    fi
  done
  return 1
}

hls_not_found_msg() {
  echo "Vitis HLS not found. Either put vitis_hls in PATH, e.g." >&2
  echo "  source <Xilinx>/Vitis_HLS/2023.1/settings64.sh" >&2
  echo "or set VITIS_HLS_HOME to the Vitis HLS installation directory." >&2
}

# synthesize <dir> <cpp file (in dir)> <top function> <project name>
# runs csynth in <dir> and copies the report to <dir>/<top>_csynth.rpt
synthesize() {
  local dir="$1" cpp="$2" fnc="$3" proj="$4"
  echo ">> HLS synthesis of $dir/$cpp ($HLS, part $HLS_PART, clock ${HLS_CLOCK}ns)"
  ( cd "$dir" && "$HLS" "$DAISY_HOME/scripts/hls_compile.tcl" \
      "$proj" "$fnc" "$cpp" "$HLS_PART" "$HLS_CLOCK" > hls.log 2>&1 ) || {
    echo "HLS synthesis failed, see $dir/hls.log" >&2; return 1; }
  local rpt="$dir/$proj/solution/syn/report/${fnc}_csynth.rpt"
  [ -f "$rpt" ] || { echo "no synthesis report found, see $dir/hls.log" >&2; return 1; }
  cp "$rpt" "$dir/${fnc}_csynth.rpt"
  echo "Synthesis report: $dir/${fnc}_csynth.rpt"
}

# csynth_summary <report>  ->  "latency_min,latency_max,est_clock_ns,BRAM,DSP,FF,LUT"
csynth_summary() {
  awk '
    /^\+ Timing:/   { tm = 1 }
    tm && /\|ap_clk/ { split($0, f, "|"); clk = f[4]; gsub(/[ ns]/, "", clk); tm = 0 }
    /^\+ Latency:/  { lat = 1; next }
    lat && /^ *\|/ && $0 !~ /Latency|min/ {
      split($0, f, "|"); lmin = f[2]; lmax = f[3]; gsub(/ /, "", lmin); gsub(/ /, "", lmax); lat = 0 }
    /^\|Total/ && !tot {
      split($0, f, "|"); for (i = 3; i <= 6; i++) gsub(/ /, "", f[i])
      bram = f[3]; dsp = f[4]; ff = f[5]; lut = f[6]; tot = 1 }
    END { printf "%s,%s,%s,%s,%s,%s,%s\n", lmin, lmax, clk, bram, dsp, ff, lut }
  ' "$1"
}

# daisy_summary <daisy.log>  ->  "max_abs_error,time_ms"
daisy_summary() {
  sed 's/\x1b\[[0-9;]*m//g' "$1" | awk '
    /#res[0-9]+: / { v = $NF + 0; if (!seen || v > err) { err = v; seen = 1; s = $NF } }
    /Absolute error: / { for (i = 1; i <= NF; i++) if ($i == "error:") { s = $(i + 1); seen = 1 } }
    /error estimated by the tuning: / { tuned = $NF }
    / total: / { for (i = 1; i <= NF; i++) if ($i == "total:") t = $(i + 1) }
    END { printf "%s,%s\n", (tuned != "" ? tuned : (seen ? s : "NA")), t }
  '
}

# ---------- helpers for the suite scripts (run_small.sh, run_acc.sh, run_vcas.sh) ----------
suite_begin() {
  SUITE_NAME="$1"; shift
  SUITE_OUT=""; SUITE_ARGS=(); SUITE_FAILED=()
  local a
  for a in "$@"; do
    case $a in
      --out=*) SUITE_OUT="$(realpath -m "${a#*=}")" ;;
      -h|--help) echo "usage: scripts/run_${SUITE_NAME}.sh [--out=<dir>] [scripts/run.sh options, e.g. --xilinx]"; exit 0 ;;
      *) SUITE_ARGS+=("$a") ;;
    esac
  done
  SUITE_OUT="${SUITE_OUT:-$DAISY_HOME/results/${SUITE_NAME}-$(date +%Y%m%d-%H%M%S)}"
  mkdir -p "$SUITE_OUT"
  for a in "${SUITE_ARGS[@]}"; do
    if [ "$a" = "--xilinx" ]; then find_hls || { hls_not_found_msg; exit 1; }; fi
  done
  ensure_daisy
}

run_variants() {
  local file="$1"; shift
  local name kind code dir opts
  name="$(basename "$file" .scala)"
  for kind in uniform mixed; do
    for code in unrolled loops; do
      opts=()
      [ "$kind" = mixed ] && opts+=(--mixed)
      [ "$code" = loops ] && opts+=(--loops)
      dir="$SUITE_OUT/${name}-${kind}-${code}"
      echo "=============== $name  $kind  $code  $*"
      if ! "$DAISY_HOME/scripts/run.sh" "$DAISY_HOME/$file" "$@" "${opts[@]}" "${SUITE_ARGS[@]}" \
            --out="$dir" > "$dir.log" 2>&1; then
        echo "   FAILED (see $dir.log)"
        SUITE_FAILED+=("${name}-${kind}-${code}")
      fi
    done
  done
}

# writes <out>/summary.csv, prints it
suite_end() {
  "$DAISY_HOME/scripts/report.sh" "$SUITE_OUT"/*/ > "$SUITE_OUT/summary.csv"
  echo
  column -s, -t < "$SUITE_OUT/summary.csv" 2>/dev/null || cat "$SUITE_OUT/summary.csv"
  echo
  echo "Summary: $SUITE_OUT/summary.csv"
  [ ${#SUITE_FAILED[@]} -eq 0 ] || { echo "Failed: ${SUITE_FAILED[*]}"; exit 1; }
}
