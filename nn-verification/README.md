# NN Verification
We already provide the NN specifications extracted from KeYmaera X below.
Thus, to verify an NN you simply have to run the corresponding specification on the corresponding NN.

## Adaptive Cruise Control (Continuous)
Verifying the network with perturbation:
```bash
$ cd ~/nn-verification/acc-cont
$ NCubeV formula-fallback fixed-1 mapping model.onnx /tmp/result-acc-cont
```

## Adaptive Cruise Control (Pruned)
Verifying the original network with perturbation:
```bash
$ cd ~/nn-verification/acc-pruned
$ NCubeV formula-fallback fixed-1 mapping model-dim-fix.onnx /tmp/result-acc-cont
```

Verifying the equivalence of the original model and the pruned model (fast):
```bash
$ cd ~/nn-verification/acc-pruned
$ VeryDiff --epsilon 0.55 model.onnx model-pruned.onnx diff-bounds.vnnlib
```

Verifying the pruned network from scratch (slower):
```bash
$ cd ~/nn-verification/acc-pruned
$ NCubeV formula-fallback fixed-0.45 mapping model-pruned-dim-fix.onnx /tmp/acc-pruned-direct
```

## Adaptive Cruise Control (Discrete)
Verifying the network with perturbation:
```bash
$ cd ~/nn-verification/acc-disc
$ NCubeV formula-num fixed mapping-num model.onnx /tmp/result-acc-disc
```

## Vertical Airborne Collision Avoidance (DNC)
Verifying the network with perturbation:
```bash
$ cd ~/nn-verification/vcas-dnc
$ NCubeV property-dnc-compressed fixed-0.0025 mapping model.onnx /tmp/result-vcas-dnc
```

## Vertical Airborne Collision Avoidance (DND)
Verifying the network with perturbation:
```bash
$ cd ~/nn-verification/vcas-dnd
$ NCubeV property-dnd-compressed fixed-0.0025 mapping model.onnx /tmp/result-vcas-dnd
```
