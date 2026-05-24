import { useEffect, useRef, useState } from 'react';

type UseInViewOptions = {
  rootMargin?: string;
  threshold?: number;
  once?: boolean;
};

export function useInView<T extends HTMLElement>({
  rootMargin = '0px 0px -8% 0px',
  threshold = 0.12,
  once = true,
}: UseInViewOptions = {}) {
  const ref = useRef<T | null>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setVisible(true);
      return;
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry?.isIntersecting) return;
        setVisible(true);
        if (once) observer.disconnect();
      },
      { rootMargin, threshold },
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, [once, rootMargin, threshold]);

  return { ref, visible };
}
