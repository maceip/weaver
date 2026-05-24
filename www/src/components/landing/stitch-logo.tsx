
import { useId } from 'react';

type StitchLogoProps = {
  size?: number;
  className?: string;
};

/** Stylized Stitch mark — vector, scales cleanly. */
export function StitchLogo({ size = 32, className = '' }: StitchLogoProps) {
  const gradId = useId();
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 48 48"
      className={className}
      aria-hidden
    >
      <defs>
        <linearGradient id={gradId} x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#fbbf24" />
          <stop offset="35%" stopColor="#a855f7" />
          <stop offset="70%" stopColor="#3b82f6" />
          <stop offset="100%" stopColor="#22d3ee" />
        </linearGradient>
      </defs>
      <path
        d="M12 8c8-4 16 2 20 10 2 5 1 12-4 16-3 2-8 4-12 2M28 40c-6 4-14-2-18-10-2-5-1-12 4-16 3-2 8-4 12-2"
        fill="none"
        stroke={`url(#${gradId})`}
        strokeWidth="4"
        strokeLinecap="round"
      />
      <circle cx="36" cy="10" r="3" fill="#fbbf24" opacity="0.9" />
    </svg>
  );
}
