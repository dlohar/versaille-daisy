#!/bin/bash
# Run Daisy on one network: analysis + ap_fixed code generation, optionally HLS synthesis.
#
# usage: scripts/run.sh <input.scala> [options]
#   --mixed            mixed-precision tuning (default: uniform precision)
#   --loops            generate code with loops (default: fully unrolled)
#   --precision=FixedN fixed-point precision; with --mixed the upper bound (default: Fixed32)
#   --min-bits=N       with --mixed: lowest bit width considered, e.g. 8 (default: 30)
#   --range=interval|affine   range analysis method (default: interval)
#   --error=interval|affine   error analysis method (default: affine)
#   --no-prune         do not remove dead neurons
#   --skip-zeros       remove multiplications by zero weights and additions of zero biases
#   --xilinx           run Vitis HLS (or Vivado HLS) C synthesis on the generated code
#   --fnc=<name>       top function name for HLS (default: nn1)
#   --out=<dir>        where to put results (default: results/<name>-<kind>-<mode>-<prec>)
# environment: VITIS_HLS_HOME (Vitis HLS install dir), HLS_PART, HLS_CLOCK (ns)
set -e
source "$(dirname "$0")/common.sh"

[ $# -lt 1 ] && { sed -n 2,17p "$0"; exit 1; }
[ -f "$1" ] || { echo "input file $1 not found"; exit 1; }
input="$(realpath "$1")"; shift
name="$(basename "$input" .scala)"
prec=Fixed32; range=interval; error=affine; loops=""; prune="--qnn-prune-dead"
xilinx=0; minprec=""; fnc=nn1; out=""; mixed=""; skip=""
for a in "$@"; do
  case $a in
    --mixed) mixed="--mixed-tuning" ;;
    --loops) loops="--codegenNNOriginal" ;;
    --precision=*) prec="${a#*=}" ;;
    --min-bits=*) minprec="--min-bits=${a#*=}" ;;
    --range=*) range="${a#*=}" ;;
    --error=*) error="${a#*=}" ;;
    --no-prune) prune="" ;;
    --skip-zeros) skip="--qnn-skip-zeros" ;;
    --xilinx) xilinx=1 ;;
    --fnc=*) fnc="${a#*=}" ;;
    --out=*) out="${a#*=}" ;;
    *) echo "unknown option $a"; exit 1 ;;
  esac
done
mode=$([ -n "$loops" ] && echo loops || echo unrolled)
kind=$([ -n "$mixed" ] && echo mixed || echo uniform)
case "$out" in
  "") out="$DAISY_HOME/results/${name}-${kind}-${mode}-${prec}" ;;
  /*) ;;
  *) out="$PWD/$out" ;;
esac
mkdir -p "$out"

# check for Vitis before spending time on the analysis
if [ $xilinx -eq 1 ]; then find_hls || { hls_not_found_msg; exit 1; }; fi

ensure_daisy
cd "$DAISY_HOME"
./daisy --qnn $prune $skip --codegen --lang=C --apfixed --precision=$prec \
  --rangeMethod=$range --errorMethod=$error $mixed $minprec $loops "$input"
cp last.log "$out/daisy.log"

# the generated file is named after the object in the input file
obj=$(grep -m1 -oP 'object\s+\K\w+' "$input")
[ -f "output/${obj}.cpp" ] || { echo "Daisy did not generate code, see $out/daisy.log"; exit 1; }
mv "output/${obj}.cpp" "$out/${name}.cpp"
echo "Generated code: $out/${name}.cpp"

if [ $xilinx -eq 1 ]; then
  synthesize "$out" "${name}.cpp" "$fnc" "hls_${name}"
fi
