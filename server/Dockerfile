# Playwright's image ships a matching Chromium + all the system libs it needs.
# Pin to the same Playwright version as package.json.
FROM mcr.microsoft.com/playwright:v1.49.1-noble

WORKDIR /app
ENV NODE_ENV=production

# Install deps against the lockfile-free manifest, then build.
COPY package.json ./
RUN npm install --omit=dev=false

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# Drop dev dependencies from the final layer.
RUN npm prune --omit=dev

EXPOSE 8080
# pwuser is the non-root user provided by the Playwright base image.
USER pwuser
CMD ["node", "dist/index.js"]
