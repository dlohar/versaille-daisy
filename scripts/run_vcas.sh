#!/bin/bash
source "$(dirname "$0")/common.sh"
suite_begin vcas "$@"

run_variants testcases/vcas/VertCASVer1.scala  --precision=Fixed64 --min-bits=40
run_variants testcases/vcas/VertCASVer2.scala  --precision=Fixed64 --min-bits=40

suite_end
