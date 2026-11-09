#!/bin/bash

set -e
source "$(dirname "$0")/common.sh"

[ $# -lt 1 ] && { sed -n 2,6p "$0"; exit 1; }
[ -f "$1" ] || { echo "file $1 not found"; exit 1; }
cpp="$(realpath "$1")"
fnc="${2:-nn1}"
find_hls || { hls_not_found_msg; exit 1; }

dir="$(dirname "$cpp")"; file="$(basename "$cpp")"
synthesize "$dir" "$file" "$fnc" "hls_${file%.*}"
echo "latency_min,latency_max,est_clock_ns,BRAM,DSP,FF,LUT"
csynth_summary "$dir/${fnc}_csynth.rpt"
