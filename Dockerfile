# ---------- Daisy build stage ----------
FROM eclipse-temurin:8-jdk-jammy AS build

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# the sbt launcher; it starts the sbt version from project/build.properties (0.13.15)
ARG SBT_VERSION=1.9.9
RUN curl -fsSL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" \
    | tar xz -C /opt
ENV PATH=/opt/sbt/bin:$PATH

WORKDIR /build
# download dependencies first (cached as long as the build definition does not change)
COPY build.sbt ./
COPY project/build.properties project/
RUN sbt -batch update

COPY lib lib
COPY src src
RUN sbt -batch compile package script

# collect the Daisy jar and all jars on its runtime classpath
RUN mkdir -p /out/lib \
 && cp target/scala-2.11/*.jar /out/lib/ \
 && grep -o 'SCALACLASSPATH="[^"]*"' daisy | sed 's/^SCALACLASSPATH="//; s/"$//' \
    | tr ':' '\n' | grep '\.jar$' | xargs -I{} cp {} /out/lib/

# ---------- runtime image ----------
FROM eclipse-temurin:8-jre-jammy

ARG TARGETARCH
RUN apt-get update \
 && apt-get install -y --no-install-recommends locales bsdextrautils \
 && if [ "$TARGETARCH" = "amd64" ] || [ -z "$TARGETARCH" ]; then \
      apt-get install -y --no-install-recommends \
        libtinfo5 libncurses5 libx11-6 libxext6 libxrender1 libxtst6 libxi6 \
        libfreetype6 libfontconfig1; \
    fi \
 && sed -i 's/^# *en_US.UTF-8/en_US.UTF-8/' /etc/locale.gen && locale-gen \
 && rm -rf /var/lib/apt/lists/*

ENV LANG=en_US.UTF-8 \
    DAISY_HOME=/versaille-daisy

WORKDIR /versaille-daisy
COPY --from=build /out/lib lib
COPY library library
COPY testcases testcases
COPY scripts scripts
COPY docker/daisy daisy
COPY README-docker.md README.md
COPY LICENSE LEON_LICENSE ./

# build.sh needs the sources and sbt, which are not in the image
RUN rm -f scripts/build.sh \
 && mkdir -p output results \
 && chmod +x daisy scripts/*.sh \
 && chmod -R a+rwX /versaille-daisy

# ---------- Versaille image + Daisy ----------
FROM samweb/versaille:main@sha256:ffa8c4995733ba7f7e27168c9db1fde635e0245bc7424d771e008abe5d72d613
USER root

COPY --from=eclipse-temurin:8-jre-jammy /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
    DAISY_HOME=/versaille-daisy \
    PATH=/opt/java/openjdk/bin:/versaille-daisy:$PATH

ARG TARGETARCH
RUN apt-get update \
 && apt-get install -y --no-install-recommends bsdextrautils \
 && if [ "$TARGETARCH" = "amd64" ] || [ -z "$TARGETARCH" ]; then \
      apt-get install -y --no-install-recommends \
        libtinfo5 libncurses5 libx11-6 libxext6 libxrender1 libxtst6 libxi6 \
        libfreetype6 libfontconfig1; \
    fi \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build /out/lib    /home/${NB_USER}/versaille-daisy/lib
COPY library                  /home/${NB_USER}/versaille-daisy/library
COPY testcases                /home/${NB_USER}/versaille-daisy/testcases
COPY scripts                  /home/${NB_USER}/versaille-daisy/scripts
COPY docker/daisy             /home/${NB_USER}/versaille-daisy/daisy
COPY README-docker.md         /home/${NB_USER}/versaille-daisy/README.md
COPY LICENSE LEON_LICENSE     /home/${NB_USER}/versaille-daisy/

RUN rm -f /home/${NB_USER}/versaille-daisy/scripts/build.sh \
 && mkdir -p /home/${NB_USER}/versaille-daisy/output /home/${NB_USER}/versaille-daisy/results \
 && chmod +x /home/${NB_USER}/versaille-daisy/daisy /home/${NB_USER}/versaille-daisy/scripts/*.sh \
 && chown -R ${NB_UID}:${NB_GID} /home/${NB_USER}/versaille-daisy \
 && chmod -R a+rwX /home/${NB_USER}/versaille-daisy
ENV DAISY_HOME=/home/${NB_USER}/versaille-daisy

# ---------- VeryDiff installation ----------
ENV VERYDIFF_VERSION=317d9b4e
ENV VERYDIFF_NO_GUROBI=1
# Julia setup for precompilation
ENV JULIA_DEPOT_PATH=/opt/julia \
    JULIA_PKGDIR=/opt/julia \
    JULIA_CPU_TARGET=generic
RUN cd /software &&\
    git clone https://github.com/samysweb/VeryDiff.git VeryDiff &&\
    cd VeryDiff &&\
    git checkout $VERYDIFF_VERSION &&\
    julia --project=. -e 'using Pkg;Pkg.instantiate();Pkg.build();'

ENV PATH=/software/VeryDiff/deps/VeryDiff/bin/:$PATH

COPY nn-verification /home/${NB_USER}/nn-verification

RUN chown -R ${NB_UID}:${NB_GID} /home/${NB_USER}

USER ${NB_UID}
WORKDIR /home/${NB_USER}


CMD ["start-notebook.py", "--NotebookApp.token='versaille'"]