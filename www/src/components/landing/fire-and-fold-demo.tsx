
import { useCallback, useEffect, useState } from 'react';
import { AgentOrb } from './agent-orb';
import { DemoShell } from './demo-shell';
import { FoldableDevice } from './foldable-device';
import { SpaceCanvas } from './space-canvas';
import './demo-tokens.css';
import './demos.css';

type Step = 0 | 1 | 2 | 3;

const STEPS: { title: string; caption: string }[] = [
  { title: 'Fire the prompt', caption: '1 · fire the prompt' },
  { title: 'Folded — progress', caption: '2 · folded — progress' },
  { title: 'Complete', caption: '3 · complete' },
  { title: 'Unfold onto the result', caption: '4 · unfold onto the result' },
];

export function FireAndFoldDemo() {
  const [step, setStep] = useState<Step>(0);
  const [prompt, setPrompt] = useState('make it bolder');
  const [progress, setProgress] = useState(0);
  const [autoPlay, setAutoPlay] = useState(false);

  const fire = useCallback(() => {
    setStep(1);
    setProgress(0);
  }, []);

  useEffect(() => {
    if (step !== 1) return;
    const id = setInterval(() => {
      setProgress((p) => {
        if (p >= 100) {
          clearInterval(id);
          setStep(2);
          return 100;
        }
        return p + 4;
      });
    }, 80);
    return () => clearInterval(id);
  }, [step]);

  useEffect(() => {
    if (!autoPlay) return;
    let i = 0;
    const seq: Step[] = [0, 1, 2, 3];
    const id = setInterval(() => {
      i += 1;
      if (i >= seq.length) {
        setAutoPlay(false);
        clearInterval(id);
        return;
      }
      const next = seq[i]!;
      setStep(next);
      if (next === 1) setProgress(0);
      if (next === 2) setProgress(100);
    }, 2000);
    return () => clearInterval(id);
  }, [autoPlay]);

  return (
    <div className="demo">
      <DemoShell className="demo-stage--fire">
        <div className="fire-flow">
          <div className={`fire-step ${step === 0 ? 'fire-step--active' : ''}`}>
            <FoldableDevice
              posture="flat"
              topPanel={
                <div className="weaver-inner">
                  <header className="weaver-inner__bar">
                    <AgentOrb size={28} />
                    <span>Stitch</span>
                  </header>
                  <SpaceCanvas variant="a" />
                  <form
                    className="weaver-inner__composer"
                    onSubmit={(e) => {
                      e.preventDefault();
                      fire();
                    }}
                  >
                    <input
                      value={prompt}
                      onChange={(e) => setPrompt(e.target.value)}
                      aria-label="Prompt"
                    />
                    <button type="submit">Send</button>
                  </form>
                  <p className="weaver-inner__hint">↻ keeps running if you fold</p>
                </div>
              }
            />
          </div>

          <span className="fire-arrow" aria-hidden>
            →
          </span>

          <div className={`fire-step ${step === 1 ? 'fire-step--active' : ''}`}>
            <FoldableDevice
              posture="folded-cover"
              topPanel={null}
              coverPanel={
                <CoverScreen
                  mode="progress"
                  progress={progress}
                  onTap={() => step === 2 && setStep(3)}
                />
              }
            />
          </div>

          <span className="fire-arrow" aria-hidden>
            →
          </span>

          <div className={`fire-step ${step === 2 ? 'fire-step--active' : ''}`}>
            <FoldableDevice
              posture="folded-cover"
              topPanel={null}
              coverPanel={
                <CoverScreen mode="ready" onTap={() => setStep(3)} />
              }
            />
          </div>

          <span className="fire-arrow" aria-hidden>
            →
          </span>

          <div className={`fire-step ${step === 3 ? 'fire-step--active' : ''}`}>
            <FoldableDevice
              posture="flat"
              topPanel={
                <div className="weaver-results">
                  <SpaceCanvas variant="b" title="Wallet home" />
                  <div className="weaver-results__grid">
                    {['Trip summary', 'Notifications', 'Profile'].map((t) => (
                      <div key={t} className="result-card">
                        <div className="result-card__thumb" />
                        <span>{t}</span>
                      </div>
                    ))}
                  </div>
                </div>
              }
            />
          </div>
        </div>

        <p className="demo-caption">{STEPS[step].caption}</p>

        <div className="fire-controls">
          <div className="fire-steps" role="tablist">
            {STEPS.map((s, i) => (
              <button
                key={s.caption}
                type="button"
                role="tab"
                aria-selected={step === i}
                className={`fire-steps__btn ${step === i ? 'fire-steps__btn--on' : ''}`}
                onClick={() => {
                  setStep(i as Step);
                  if (i === 1) setProgress(step === 2 ? 100 : 35);
                }}
              >
                {i + 1}
              </button>
            ))}
          </div>
          <button
            type="button"
            className="fire-play"
            onClick={() => {
              setStep(0);
              setProgress(0);
              setAutoPlay(true);
              setTimeout(() => fire(), 400);
            }}
          >
            Play flow
          </button>
          {step === 0 && (
            <button type="button" className="fire-play fire-play--primary" onClick={fire}>
              Fire prompt
            </button>
          )}
        </div>
      </DemoShell>
    </div>
  );
}

function CoverScreen({
  mode,
  progress = 0,
  onTap,
}: {
  mode: 'progress' | 'ready';
  progress?: number;
  onTap?: () => void;
}) {
  return (
    <button type="button" className="cover-screen" onClick={onTap}>
      <header className="cover-screen__head">
        <AgentOrb size={24} active={mode === 'progress'} />
        <span>Stitch</span>
      </header>
      {mode === 'progress' ? (
        <>
          <p>Generating…</p>
          <div className="cover-screen__bar">
            <span style={{ width: `${progress}%` }} />
          </div>
        </>
      ) : (
        <>
          <p className="cover-screen__ready">Design ready</p>
          <p className="cover-screen__sub">tap to open</p>
          <span className="cover-screen__open">Open ›</span>
        </>
      )}
    </button>
  );
}
