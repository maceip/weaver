// Custom element <pixel-fold-showtime> registered from /3d/src/pixel-fold-showtime.js
import type { DetailedHTMLProps, HTMLAttributes } from 'react'

declare module 'react' {
  namespace JSX {
    interface IntrinsicElements {
      'pixel-fold-showtime': DetailedHTMLProps<
        HTMLAttributes<HTMLElement> & {
          'model-url'?: string
          'environment-url'?: string
          'draco-decoder-path'?: string
          'ktx2-transcoder-path'?: string
          'fallback-image-url'?: string
          'exit-mode'?: string
        },
        HTMLElement
      >
    }
  }
}
