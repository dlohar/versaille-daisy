# Synthesis with Daisy

The synthesis of the NN requires transforming the given ONNX into a scala file.
This can, for example be done based on [this script](https://github.com/dlohar/Aster/blob/main/scripts/qnn/read-onnx.py)

Subsequently, Daisy can be used to synthesize an NN with the desired precision.  
To this end, the scala files for the case studies from the paper and Daisy are available in the provided Docker container.

## Requirements

- Java 8
- sbt 0.13.15 (`project/build.properties`)
- Optional: Vitis HLS (or Vivado HLS) in `PATH` for synthesis

Note: Vitis HLS runs only on x86-64 Linux. On macOS, Daisy works but `--xilinx` does not.

## Build

```bash
sbt compile script
```

## Run

Run from this directory, or set `DAISY_HOME` to this directory: `library/` is loaded from there.

```bash
# fully unrolled code, uniform Fixed<n> (e.g. Fixed32, Fixed40, Fixed64),
# interval ranges, affine errors, dead neuron removal
./daisy --qnn --qnn-prune-dead --codegen --lang=C --apfixed --precision=Fixed<n> <network.scala>

# same, but with loops
./daisy --qnn --qnn-prune-dead --codegen --lang=C --apfixed --precision=Fixed<n> --codegenNNOriginal <network.scala>

# mixed-precision tuning (bit widths from --min-bits, default 30, up to Fixed<n>),
# unrolled / with loops
./daisy --qnn --codegen --lang=C --apfixed --mixed-tuning --precision=Fixed<n> <network.scala>
./daisy --qnn --codegen --lang=C --apfixed --mixed-tuning --precision=Fixed<n> --codegenNNOriginal <network.scala>
./daisy --qnn --codegen --lang=C --apfixed --mixed-tuning --precision=Fixed32 --min-bits=8 <network.scala>
```

The generated code goes to `output/<ObjectName>.cpp`.

```bash
scripts/run.sh <network.scala> [--mixed] [--loops] [--precision=Fixed32] \
               [--range=interval|affine] [--error=interval|affine] \
               [--min-bits=N] [--no-prune] [--skip-zeros] [--xilinx] [--fnc=nn1] [--out=<dir>]

scripts/run_small.sh [--out=<dir>] [run.sh options, e.g. --xilinx]    # same for run_acc.sh, run_vcas.sh
```

Vitis HLS is only needed for `--xilinx` / `synth.sh`. It is found in `PATH`, or via
`VITIS_HLS_HOME` (e.g. `export VITIS_HLS_HOME=/tools/Xilinx/Vitis_HLS/2023.1`, whose
`settings64.sh` is sourced). Target: `HLS_PART` (default `xc7z020clg400-1`, PYNQ-Z1) and
`HLS_CLOCK` in ns (default 10). Numbers are csynth estimates.