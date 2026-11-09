# ---------- build stage: compile Daisy with sbt ----------
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

CMD ["/bin/bash"]
