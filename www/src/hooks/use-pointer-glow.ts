import { useEffect, type RefObject } from 'react';

/** Soft hero spotlight that follows the pointer — disabled for touch and reduced motion. */
export function usePointerGlow<T extends HTMLElement>(ref: RefObject<T | null>) {
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    if (window.matchMedia('(pointer: coarse)').matches) return;

    const onMove = (event: PointerEvent) => {
      const rect = node.getBoundingClientRect();
      const x = ((event.clientX - rect.left) / Math.max(rect.width, 1)) * 100;
      const y = ((event.clientY - rect.top) / Math.max(rect.height, 1)) * 100;
      node.style.setProperty('--glow-x', `${x}%`);
      node.style.setProperty('--glow-y', `${y}%`);
    };

    node.addEventListener('pointermove', onMove, { passive: true });
    return () => node.removeEventListener('pointermove', onMove);
  }, [ref]);
}
