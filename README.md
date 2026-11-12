# VerSAILLE-Daisy

Guaranteeing the safety of an NN control system based on the approach presented in our paper consists of the following steps:

- Proving the safety of a dL-based model of the CPS:  
  This is discussed in [/keymaera/README.md](/keymaera/README.md)
- Proving the safety of the NN:  
  This is discussed in [/nn-verification/README.md](/nn-verification/README.md)
- Optionally: Proving the equivalence of a pruned NN:  
  This is discussed in [/nn-verification/README.md](/nn-verification/README.md)
- Synthesizing an implementation of the NN:  
  This is discussed in [Daisy.md](Daisy.md)

## Setup
You can either build the docker container yourself using the Docker file in this directory (see [/README-docker.md](/README-docker.md)) or pull and run it from docker hub:

```
docker run -p 8888:8888 samweb/versaille-daisy
```

After starting the container, open the following link in your browser: http://127.0.0.1:8888/lab?token=versaille

From there you should be able to access a terminal where you can run verification tasks (NCubeV and VeryDiff) and synthesis tasks (Daisy) as described in the appended files
