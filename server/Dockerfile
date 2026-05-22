# ── Stage 1: build the Android Key Attestation verifier sidecar ─────────────
# Google's com.android.keyattestation library, vendored under attestation-jvm,
# built into a fat jar the Node server shells out to.
FROM eclipse-temurin:21-jdk AS attestation
WORKDIR /build
COPY attestation-jvm ./attestation-jvm
RUN cd attestation-jvm && ./gradlew shadowJar --no-daemon --console=plain

# ── Stage 2: the server ─────────────────────────────────────────────────────
# Playwright's image ships a matching Chromium + all the system libs it needs.
# Pin to the same Playwright version as package.json.
FROM mcr.microsoft.com/playwright:v1.49.1-noble

WORKDIR /app
ENV NODE_ENV=production

# A JRE to run the attestation sidecar jar.
RUN apt-get update \
  && apt-get install -y --no-install-recommends openjdk-21-jre-headless \
  && rm -rf /var/lib/apt/lists/*

# Install deps against the lockfile-free manifest, then build.
COPY package.json ./
RUN npm install --omit=dev=false

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# Drop dev dependencies from the final layer.
RUN npm prune --omit=dev

# The attestation verifier jar, built in stage 1.
COPY --from=attestation /build/attestation-jvm/build/libs/weaver-attestation-verifier.jar ./attestation-verifier.jar
ENV WEAVER_ATTESTATION_JAR=/app/attestation-verifier.jar

EXPOSE 8080
# pwuser is the non-root user provided by the Playwright base image.
USER pwuser
CMD ["node", "dist/index.js"]
