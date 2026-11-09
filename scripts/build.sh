#!/bin/bash
set -e
source "$(dirname "$0")/common.sh"
cd "$DAISY_HOME"
command -v sbt >/dev/null || { echo "sbt not found (needs sbt and Java 8)"; exit 1; }
if [ "$1" = "--clean" ]; then sbt -batch clean; fi
sbt -batch compile script
echo "Built: $DAISY_HOME/daisy"
