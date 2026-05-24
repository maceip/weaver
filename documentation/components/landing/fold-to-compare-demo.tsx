'use client';

import { useMemo, useState, type CSSProperties } from 'react';
import { DemoShell } from './demo-shell';
import { FoldableDevice } from './foldable-device';
import { SpaceCanvas } from './space-canvas';
import { usePointerHinge } from './use-pointer-hinge';
import './demo-tokens.css';
import './demos.css';

const MIN_ANGLE = 110;
const MAX_ANGLE = 180;

type Stage = 'flat' | 'folding' | 'blend';

function stageFor(angle: number): Stage {
  if (angle >= 175) return 'flat';
  if (angle >= 125) return 'folding';
  return 'blend';
}

function DevicePreview({ angle, active }: { angle: number; active: boolean }) {
  const stage = stageFor(angle);
  const blend = (MAX_ANGLE - angle) / (MAX_ANGLE - MIN_ANGLE);

  return (
    <div className={`compare-thumb ${active ? 'compare-thumb--active' : ''}`}>
      {stage === 'flat' ? (
        <div className="compare-thumb__split">
          <SpaceCanvas variant="a" compact />
          <SpaceCanvas variant="b" compact />
        </div>
      ) : (
        <div className="compare-thumb__blend" style={{ '--blend': Math.min(1, Math.max(0, blend)) } as CSSProperties}>
          <SpaceCanvas variant="a" compact />
          <SpaceCanvas variant="b" compact />
        </div>
      )}
      <span className="compare-thumb__label">{angle}°</span>
    </div>
  );
}

export function FoldToCompareDemo() {
  const [angle, setAngle] = useState(145);
  const [locked, setLocked] = useState(false);
  const { trackRef, onPointerDown, fill } = usePointerHinge(angle, setAngle, MIN_ANGLE, MAX_ANGLE, locked);

  const blend = useMemo(() => {
    const t = (MAX_ANGLE - angle) / (MAX_ANGLE - MIN_ANGLE);
    return Math.min(1, Math.max(0, t));
  }, [angle]);

  const stage = stageFor(angle);
  const foldProgress = (angle - MIN_ANGLE) / (MAX_ANGLE - MIN_ANGLE);

  const label =
    stage === 'flat'
      ? 'FLAT · 180° — A | B side by side'
      : stage === 'folding'
        ? `FOLDING · ~${angle}° — panes converge`
        : `~${angle}° · ONION-SKIN BLEND`;

  const mainPanel =
    stage === 'flat' ? (
      <div className="split-canvas">
        <SpaceCanvas variant="a" />
        <div className="split-canvas__hinge" />
        <SpaceCanvas variant="b" />
      </div>
    ) : (
      <div className="blend-canvas" style={{ '--blend': blend } as CSSProperties}>
        <SpaceCanvas variant="a" className="blend-canvas__a" />
        <SpaceCanvas variant="b" className="blend-canvas__b" />
        <div className="blend-canvas__veil" />
      </div>
    );

  return (
    <div className="demo">
      <DemoShell className="demo-stage--compare" hint="Resize the box · drag the hinge track · use the slider">
        <div className="compare-triptych" aria-hidden>
          <DevicePreview angle={180} active={stage === 'flat'} />
          <DevicePreview angle={135} active={stage === 'folding'} />
          <DevicePreview angle={110} active={stage === 'blend'} />
        </div>

        <div className="compare-main">
          <FoldableDevice
            posture={stage === 'flat' ? 'flat' : 'folding'}
            foldProgress={stage === 'flat' ? 1 : 1 - foldProgress}
            topPanel={mainPanel}
            className="foldable-device--hero"
          />
          <div
            ref={trackRef}
            className="hinge-track"
            onPointerDown={onPointerDown}
            role="slider"
            aria-valuemin={MIN_ANGLE}
            aria-valuemax={MAX_ANGLE}
            aria-valuenow={angle}
            aria-label="Simulated hinge angle"
            tabIndex={locked ? -1 : 0}
          >
            <div className="hinge-track__fill" style={{ width: `${fill}%` }} />
            <div className="hinge-track__thumb" style={{ left: `${fill}%` }} />
            <span className="hinge-track__label">hinge</span>
          </div>
        </div>

        <p className="demo-caption">{label}</p>

        <div className="compare-controls">
          <p className="compare-controls__hint">calibrated comfort band — anchored to the angle on entry</p>
          <div className="blend-slider">
            <span className="blend-slider__end">A</span>
            <input
              type="range"
              min={MIN_ANGLE}
              max={MAX_ANGLE}
              value={angle}
              disabled={locked}
              onChange={(e) => setAngle(Number(e.target.value))}
              aria-label="Hinge angle slider"
              className="blend-slider__input"
            />
            <span className="blend-slider__end">B</span>
          </div>
          <div className="blend-slider__meta">
            <span>{angle}°</span>
            <button
              type="button"
              className={`blend-lock ${locked ? 'blend-lock--on' : ''}`}
              onClick={() => setLocked((v) => !v)}
            >
              {locked ? 'Locked' : 'Tap to lock'}
            </button>
            <span>{Math.round(blend * 100)}% B</span>
          </div>
        </div>
      </DemoShell>
    </div>
  );
}
