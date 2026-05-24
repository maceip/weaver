'use client';

import type { ReactNode } from 'react';

type DemoShellProps = {
  children: ReactNode;
  className?: string;
  hint?: string;
};

/** Resizable stage — drag the corner to prove vector layout scales. */
export function DemoShell({ children, className = '', hint }: DemoShellProps) {
  return (
    <div className={`demo-shell ${className}`}>
      <div className="demo-shell__resize">
        {children}
        <span className="demo-shell__grip" aria-hidden />
      </div>
      <p className="demo-shell__hint">{hint ?? 'Drag the corner to resize · all UI is vector/CSS'}</p>
    </div>
  );
}
