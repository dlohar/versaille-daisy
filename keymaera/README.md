# KeYmaera Proof Files

The proof files were tested with version 5.1.2 of KeYmaera X.
All files can be copied into a running KeYmaera X instance to re-run proofs there.

## Running example
- Safety of control envelope: robot-safety.kyx  
  (used to derive ModelPlex constraint from control envelope)
- Safety of uni-directional robot: robot-uni-dir-safety.kyx
- Non-robustness of uni-directional robot: robot-uni-dir-not-robust.kyx
- Robustness of robot to output perturbation: robot-robustness.kyx
- Robustness of robot to input and output perturbation: robot-input-robustness.kyx
- Safety of implementation under output perturbation: robot-implementation.tex
- Safety of implementation under input and output perturbation: robot-implementation-input.tex



## Adaptive Cruise Control (Continuous)
The original ACC analysis can be found [here](https://github.com/samysweb/NCubeV/blob/7a1150c0558ebbeca25fe2fb66667c6bee8a36e4/experiments/acc/keymaerax/model.kyx).  
These files were part of the NeurIPS'24 publication.

- Non-Robustness of the original envelope: acc-orig-not-robust.kyx  
- Safety of the newly devised envelope: acc-robust-safety.kyx  
  (used to derive ModelPlex constraint from control envelope)
- Robustness of newly devised envelope: acc-robust-robustness.kyx

## Adaptive Cruise Control (Discrete)
The original ACC analysis can be found [here](https://github.com/samysweb/VerSAILLE/blob/main/keymaerax/acc.kyx)

## Vertical Airborne Collision Avoidance
The VCAS analysis can be found [here](https://github.com/samysweb/NCubeV/tree/7a1150c0558ebbeca25fe2fb66667c6bee8a36e4/experiments/acas/keymaerax).  
These files were part of the NeurIPS'24 publication.