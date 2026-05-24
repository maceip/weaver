# Weaver site (GitHub Pages)

## URL

After Pages is enabled on `maceip/weaver` and `main` deploys:

**https://maceip.github.io/weaver/**

## Enable Pages (one-time)

1. GitHub → **maceip/weaver** → **Settings** → **Pages**
2. Source: **GitHub Actions** (the `Documentation` workflow deploys `documentation/out`)

## Local preview

```bash
cd documentation
npm install
npm run dev
# open http://localhost:3000/weaver
```

## Interactive feature demos

Below-the-fold sections are **built from components**, not static images:

| Feature | Component | Interactions |
|---------|-----------|--------------|
| Tabletop Studio | `tabletop-studio-demo.tsx` | Version filmstrip, prompt, agent orb, callout highlights |
| Fold-to-Compare | `fold-to-compare-demo.tsx` | Hinge angle slider (110–180°), A/B onion-skin blend, lock |
| Fire-and-Fold | `fire-and-fold-demo.tsx` | Fire prompt, cover progress, step tabs, play flow |

Shared primitives: `foldable-device.tsx`, `space-canvas.tsx`, `agent-orb.tsx` — all CSS/vector, resize with the container (`clamp`, `cqi`, `%`).
