# Stitch marketing site (GitHub Pages)

Standalone Vite + React + TypeScript site deployed to **https://maceip.github.io/stitch/**.

The CI workflow also copies the static documentation export into `public/docs/` so `/stitch/docs/` keeps working.

## Local dev

```bash
cd www
npm install
npm run dev
# open http://localhost:5173/stitch/
```

## Production build

```bash
cd documentation && npm install && npm run build
cd ../www
npm install
mkdir -p public/docs && cp -R ../documentation/out/. public/docs/
npm run build
# output in www/dist
```
