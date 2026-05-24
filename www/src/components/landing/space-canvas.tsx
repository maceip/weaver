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
      aria-label={title ?? (isA ? 'Checkout flow' : 'Settings hub')}
    >
      <div className="space-canvas__stars" />
      <div className="space-canvas__planet" />
      <div className="space-canvas__ship" />
      <div className="space-canvas__sparkles" />
      {!compact && (
        <>
          <p className="space-canvas__title">{title ?? (isA ? 'Checkout flow' : 'Settings hub')}</p>
          <div className="space-canvas__actions">
            <span>Continue</span>
            <span className="space-canvas__ghost">Save changes</span>
          </div>
        </>
      )}
      {compact && <span className="space-canvas__badge">{isA ? 'A' : 'B'}</span>}
    </div>
  );
}
