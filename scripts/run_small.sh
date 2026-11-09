#!/bin/bash
source "$(dirname "$0")/common.sh"
suite_begin small "$@"

run_variants testcases/small/SmallNN.scala       --precision=Fixed32 --min-bits=8
run_variants testcases/small/TestNN.scala        --precision=Fixed32 --min-bits=8
run_variants testcases/small/DeadNeuronNN.scala  --precision=Fixed32 --min-bits=8

suite_end
