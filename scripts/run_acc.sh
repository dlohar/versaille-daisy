#!/bin/bash
source "$(dirname "$0")/common.sh"
suite_begin acc "$@"

run_variants testcases/acc/ContACC.scala       --precision=Fixed64 --min-bits=30
run_variants testcases/acc/DiscreteACC.scala   --precision=Fixed64 --min-bits=30
run_variants testcases/acc/PrunnedACCNN.scala  --precision=Fixed64 --min-bits=30

suite_end
