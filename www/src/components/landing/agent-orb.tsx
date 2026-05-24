
import { StitchLogo } from './stitch-logo';

type AgentOrbProps = {
  active?: boolean;
  size?: number;
  onClick?: () => void;
};

export function AgentOrb({ active = false, size = 56, onClick }: AgentOrbProps) {
  return (
    <button
      type="button"
      className={`agent-orb ${active ? 'agent-orb--active' : ''}`}
      style={{ width: size, height: size }}
      onClick={onClick}
      aria-label="Agent orb"
    >
      <span className="agent-orb__ring" />
      <span className="agent-orb__core">
        <StitchLogo size={size * 0.45} />
      </span>
    </button>
  );
}
