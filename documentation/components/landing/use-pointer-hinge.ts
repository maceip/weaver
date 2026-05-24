'use client';

import { useCallback, useRef } from 'react';

export function usePointerHinge(
  angle: number,
  setAngle: (n: number) => void,
  min: number,
  max: number,
  disabled = false,
) {
  const trackRef = useRef<HTMLDivElement>(null);

  const onPointerDown = useCallback(
    (e: React.PointerEvent) => {
      if (disabled) return;
      const el = trackRef.current;
      if (!el) return;
      el.setPointerCapture(e.pointerId);

      const update = (clientX: number) => {
        const rect = el.getBoundingClientRect();
        const t = 1 - (clientX - rect.left) / rect.width;
        const next = Math.round(min + t * (max - min));
        setAngle(Math.min(max, Math.max(min, next)));
      };

      update(e.clientX);

      const move = (ev: PointerEvent) => update(ev.clientX);
      const up = () => {
        el.releasePointerCapture(e.pointerId);
        window.removeEventListener('pointermove', move);
        window.removeEventListener('pointerup', up);
      };

      window.addEventListener('pointermove', move);
      window.addEventListener('pointerup', up);
    },
    [disabled, min, max, setAngle],
  );

  const fill = ((angle - min) / (max - min)) * 100;

  return { trackRef, onPointerDown, fill };
}
