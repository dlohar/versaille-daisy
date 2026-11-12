# VerSAILLE-Daisy (Docker)

## Requirements

- Docker
- Only for FPGA synthesis (optional): Vitis HLS 2023.1 (it is not part of the image)

Memory constraint: Daisy uses up to 2 GB. For the large networks give Docker at least 6 GB and set `-e DAISY_JAVA_OPTS="-Xms1G -Xmx4G"` in `docker run`

Note: Vitis HLS runs only on x86-64 Linux. On macOS, Daisy works but `--xilinx` does not.

## Build the image

```bash
git clone https://github.com/dlohar/versaille-daisy.git
cd versaille-daisy
docker build -t versaille-daisy .
```

## Start a container

```bash
docker run -p 8888:8888 versaille-daisy
```
After starting the container, open the following link in your browser: http://127.0.0.1:8888/lab?token=versaille

To copy data from the docker container to your computer use the following instructions: 
```bash
docker cp <container name>:/<File I want to copy> ./<where I want to copy it to>
```

To run Vitis HLS, mount Xilinx installation and tell Daisy where Vitis HLS is:

```bash
docker run \
  -v /path/to/Xilinx:/tools/Xilinx:ro \
  -e VITIS_HLS_HOME=/tools/Xilinx/Vitis_HLS/2023.1 \
  versaille-daisy
```

## Run Daisy

```bash
# uniform precision Fixed<n> (e.g. Fixed32, Fixed40, Fixed64), fully unrolled code
./daisy --qnn --qnn-prune-dead --codegen --lang=C --apfixed --precision=Fixed<n> <network.scala>

# same, with loops
./daisy --qnn --qnn-prune-dead --codegen --lang=C --apfixed --precision=Fixed<n> --codegenNNOriginal <network.scala>

# mixed-precision tuning with bit widths from --min-bits (default 30) up to Fixed<n>
./daisy --qnn --qnn-prune-dead --codegen --lang=C --apfixed --mixed-tuning --precision=Fixed<n> --min-bits=<m> <network.scala>
```

Example:

```bash
./daisy --qnn --qnn-prune-dead --codegen --lang=C --apfixed --mixed-tuning \
        --precision=Fixed32 --min-bits=8 testcases/small/SmallNN.scala
```

## Running benchmarks

One network:

```bash
scripts/run.sh <network.scala> [--mixed] [--loops] [--precision=Fixed32] \
               [--range=interval|affine] [--error=interval|affine] \
               [--min-bits=N] [--no-prune] [--skip-zeros] [--xilinx] [--fnc=nn1] [--out=<dir>]
```

One suite (each network with uniform and mixed precision, as unrolled code and with loops):

```bash
scripts/run_small.sh             # small examples (seconds)
scripts/run_acc.sh               # ACC networks
scripts/run_vcas.sh              # VCAS networks
scripts/run_small.sh --xilinx    # also synthesize with Vitis HLS
```