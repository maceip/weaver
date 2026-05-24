import type { ReactNode } from 'react';
import { useInView } from '../hooks/use-in-view';

type RevealProps = {
  children: ReactNode;
  className?: string;
  delay?: number;
};

export function Reveal({ children, className = '', delay = 0 }: RevealProps) {
  const { ref, visible } = useInView<HTMLDivElement>();

  return (
    <div
      ref={ref}
      className={`reveal ${visible ? 'reveal--visible' : ''} ${className}`.trim()}
      style={{ transitionDelay: `${delay}ms` }}
    >
      {children}
    </div>
  );
}
