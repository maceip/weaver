
import { useCallback, useRef, useState, type CSSProperties } from 'react';
import { AgentOrb } from './agent-orb';
import { DemoShell } from './demo-shell';
import { FoldableDevice } from './foldable-device';
import { SpaceCanvas } from './space-canvas';
import './demo-tokens.css';
import './demos.css';

const VERSIONS = ['v1', 'v2', 'v3', 'v4', 'v5'] as const;

export function TabletopStudioDemo() {
  const [selected, setSelected] = useState(3);
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [dragOverCanvas, setDragOverCanvas] = useState(false);
  const [prompt, setPrompt] = useState('');
  const [orbActive, setOrbActive] = useState(false);
  const [hoverCallout, setHoverCallout] = useState<string | null>(null);
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const canvasRef = useRef<HTMLDivElement>(null);

  const variant = selected % 2 === 0 ? 'a' : 'b';

  const onCanvasPointerMove = useCallback((e: React.PointerEvent) => {
    const el = canvasRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const x = ((e.clientX - r.left) / r.width - 0.5) * 8;
    const y = ((e.clientY - r.top) / r.height - 0.5) * -6;
    setTilt({ x, y });
  }, []);

  const deck = (
    <div className="control-deck">
      <p className="control-deck__label">Versions</p>
      <div className="filmstrip" role="listbox" aria-label="Design versions">
        {VERSIONS.map((v, i) => (
          <button
            key={v}
            type="button"
            role="option"
            aria-selected={selected === i}
            draggable={false}
            className={`filmstrip__thumb ${selected === i ? 'filmstrip__thumb--active' : ''} ${dragIndex === i ? 'filmstrip__thumb--drag' : ''}`}
            onPointerDown={(e) => {
              e.currentTarget.setPointerCapture(e.pointerId);
              setDragIndex(i);
            }}
            onPointerUp={(e) => {
              e.currentTarget.releasePointerCapture(e.pointerId);
              if (dragOverCanvas && dragIndex !== null) setSelected(dragIndex);
              setDragIndex(null);
              setDragOverCanvas(false);
            }}
            onPointerMove={(e) => {
              if (dragIndex === null) return;
              const canvas = canvasRef.current?.getBoundingClientRect();
              if (!canvas) return;
              const over =
                e.clientX >= canvas.left &&
                e.clientX <= canvas.right &&
                e.clientY >= canvas.top &&
                e.clientY <= canvas.bottom;
              setDragOverCanvas(over);
            }}
          >
            <span className={`filmstrip__preview filmstrip__preview--${i % 2 === 0 ? 'a' : 'b'}`} />
            <span>{v}</span>
          </button>
        ))}
      </div>
      {dragIndex !== null && (
        <p className="filmstrip__drag-hint">
          {dragOverCanvas ? 'Release on canvas to apply' : 'Drag thumbnail up to canvas'}
        </p>
      )}
      <label className="prompt-bar">
        <span className="prompt-bar__sparkle" aria-hidden>
          ✦
        </span>
        <input
          type="text"
          placeholder="Describe a change…"
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && setOrbActive(true)}
        />
      </label>
      <div className="control-deck__tools">
        {['Compose', 'Refine', 'Layout', 'Style', 'More'].map((t) => (
          <button key={t} type="button" className="tool-chip">
            {t}
          </button>
        ))}
        <AgentOrb active={orbActive} size={52} onClick={() => setOrbActive((v) => !v)} />
      </div>
    </div>
  );

  return (
    <div className="demo">
      <DemoShell className="demo-stage--tabletop">
        <div className="tabletop-layout">
          <div
            className="tabletop-device-wrap"
            style={
              {
                '--tilt-x': `${tilt.x}deg`,
                '--tilt-y': `${tilt.y}deg`,
              } as CSSProperties
            }
            onMouseEnter={() => setHoverCallout('canvas')}
            onMouseLeave={() => {
              setHoverCallout(null);
              setTilt({ x: 0, y: 0 });
            }}
          >
            <FoldableDevice
              posture="tabletop"
              topPanel={
                <div
                  ref={canvasRef}
                  className={`canvas-drop ${dragOverCanvas ? 'canvas-drop--over' : ''}`}
                  onPointerMove={onCanvasPointerMove}
                  onPointerLeave={() => setTilt({ x: 0, y: 0 })}
                >
                  <SpaceCanvas variant={variant as 'a' | 'b'} />
                  {dragIndex !== null && dragOverCanvas && (
                    <div className="canvas-drop__ghost">
                      <SpaceCanvas variant={dragIndex % 2 === 0 ? 'a' : 'b'} compact />
                      <span>Drop to preview {VERSIONS[dragIndex]}</span>
                    </div>
                  )}
                </div>
              }
              bottomPanel={deck}
            />
          </div>
          <aside className="callouts">
            {CALLOUTS.map((c) => (
              <button
                key={c.id}
                type="button"
                className={`callout ${hoverCallout === c.id ? 'callout--hot' : ''}`}
                onMouseEnter={() => setHoverCallout(c.id)}
                onMouseLeave={() => setHoverCallout(null)}
              >
                <span className="callout__icon">{c.icon}</span>
                <span>
                  <strong>{c.title}</strong>
                  <span>{c.body}</span>
                </span>
              </button>
            ))}
          </aside>
        </div>
        <p className="demo-caption">Drag v1–v5 onto the canvas · move pointer on canvas for parallax · send a prompt</p>
      </DemoShell>
    </div>
  );
}

const CALLOUTS = [
  { id: 'canvas', icon: '▣', title: 'Canvas — above the hinge', body: 'Design on the top panel; layout reclamps on split.' },
  { id: 'hinge', icon: '◎', title: 'Hinge — occluded seam', body: 'No touch targets on the physical hinge.' },
  { id: 'filmstrip', icon: '▤', title: 'Version filmstrip', body: 'Drag up to preview, revert, or branch.' },
  { id: 'deck', icon: '⌨', title: 'Control deck + IME', body: 'Tools and orb live on the bottom panel.' },
  { id: 'orb', icon: '✦', title: 'Agent orb', body: 'Progress ring when a prompt is running.' },
] as const;
