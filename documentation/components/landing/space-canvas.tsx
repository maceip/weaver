type SpaceCanvasProps = {
  variant: 'a' | 'b';
  title?: string;
  compact?: boolean;
  className?: string;
};

export function SpaceCanvas({ variant, title, compact, className = '' }: SpaceCanvasProps) {
  const isA = variant === 'a';
  return (
    <div
      className={`space-canvas space-canvas--${variant} ${compact ? 'space-canvas--compact' : ''} ${className}`}
      role="img"
      aria-label={title ?? (isA ? 'Design A' : 'Design B')}
    >
      <div className="space-canvas__stars" />
      <div className="space-canvas__planet" />
      <div className="space-canvas__ship" />
      <div className="space-canvas__sparkles" />
      {!compact && (
        <>
          <p className="space-canvas__title">{title ?? (isA ? 'Explore New Horizons' : 'Starship Vector-7')}</p>
          <div className="space-canvas__actions">
            <span>Get Started</span>
            <span className="space-canvas__ghost">Learn More</span>
          </div>
        </>
      )}
      {compact && <span className="space-canvas__badge">{isA ? 'A' : 'B'}</span>}
    </div>
  );
}
