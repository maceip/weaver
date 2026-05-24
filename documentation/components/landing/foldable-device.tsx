import type { CSSProperties, ReactNode } from 'react';

export type DevicePosture = 'flat' | 'folding' | 'folded-cover' | 'tabletop';

type FoldableDeviceProps = {
  posture?: DevicePosture;
  /** 0 = closed, 1 = fully open (for folding posture) */
  foldProgress?: number;
  topPanel: ReactNode;
  bottomPanel?: ReactNode;
  coverPanel?: ReactNode;
  className?: string;
  style?: CSSProperties;
};

/**
 * Responsive foldable shell. Panels are %/flex sized so the demo scales in any container.
 */
export function FoldableDevice({
  posture = 'flat',
  foldProgress = 1,
  topPanel,
  bottomPanel,
  coverPanel,
  className = '',
  style,
}: FoldableDeviceProps) {
  const isTabletop = posture === 'tabletop';
  const isCover = posture === 'folded-cover';
  const isFolding = posture === 'folding';
  const foldDeg = 180 - foldProgress * 70;

  return (
    <div
      className={`foldable-device foldable-device--${posture} ${className}`}
      style={
        {
          ...style,
          '--fold-deg': `${foldDeg}deg`,
          '--fold-progress': foldProgress,
        } as CSSProperties
      }
    >
      <div className="foldable-device__bezel">
      {isCover ? (
        <div className="foldable-device__cover">{coverPanel}</div>
      ) : (
        <div className={`foldable-device__inner ${isTabletop ? 'foldable-device__inner--tabletop' : ''}`}>
          <div
            className="foldable-device__top"
            style={
              isFolding
                ? { transform: `rotateX(${(1 - foldProgress) * 28}deg)` }
                : undefined
            }
          >
            {topPanel}
          </div>
          {(bottomPanel || isTabletop) && (
            <>
              <div className="foldable-device__hinge" aria-hidden />
              <div className="foldable-device__bottom">{bottomPanel}</div>
            </>
          )}
        </div>
      )}
      </div>
    </div>
  );
}
