import { useEffect, useId, useRef, useState, type FormEvent } from 'react';

const NOTIFY_EMAIL = import.meta.env.VITE_BETA_NOTIFY_EMAIL as string | undefined;
const SIGNUP_ENDPOINT = import.meta.env.VITE_BETA_WAITLIST_ENDPOINT as string | undefined;

function signupEndpoint(): string | null {
  if (SIGNUP_ENDPOINT) return SIGNUP_ENDPOINT;
  if (NOTIFY_EMAIL) return `https://formsubmit.co/ajax/${encodeURIComponent(NOTIFY_EMAIL)}`;
  return null;
}

type PlayStoreBetaProps = {
  className?: string;
};

export function PlayStoreBeta({ className = '' }: PlayStoreBetaProps) {
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [error, setError] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const titleId = useId();
  const descId = useId();

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    inputRef.current?.focus();
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  const close = () => {
    setOpen(false);
    setStatus('idle');
    setError('');
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const trimmed = email.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
      setError('Enter a valid email address.');
      setStatus('error');
      return;
    }

    setStatus('loading');
    setError('');

    const endpoint = signupEndpoint();
    try {
      if (endpoint) {
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            email: trimmed,
            _subject: 'Stitch Play beta access request',
            _template: 'table',
            source: 'stitch.secure.build',
          }),
        });
        if (!response.ok) throw new Error('Request failed');
      } else {
        const mail = `mailto:?subject=${encodeURIComponent('Stitch beta access')}&body=${encodeURIComponent(trimmed)}`;
        window.location.href = mail;
      }
      setStatus('success');
      setEmail('');
    } catch {
      setStatus('error');
      setError('Could not send — try again or email us directly.');
    }
  };

  return (
    <>
      <div className={`play-beta ${className}`.trim()}>
        <button
          type="button"
          className="play-beta__badge"
          aria-disabled="true"
          aria-describedby="play-beta-tip"
          onClick={() => setOpen(true)}
        >
          <span className="play-beta__icon" aria-hidden>
            <PlayIcon />
          </span>
          <span className="play-beta__copy">
            <span className="play-beta__kicker">GET IT ON</span>
            <span className="play-beta__title">Google Play</span>
          </span>
          <span className="play-beta__lock" aria-hidden>
            Beta
          </span>
        </button>
        <p id="play-beta-tip" className="play-beta__tip" role="tooltip">
          Beta test only — email for access
        </p>
      </div>

      {open ? (
        <div className="play-beta-modal" role="presentation" onClick={close}>
          <div
            className="play-beta-modal__panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            aria-describedby={descId}
            onClick={(event) => event.stopPropagation()}
          >
            <button type="button" className="play-beta-modal__close" aria-label="Close" onClick={close}>
              ×
            </button>
            <h2 id={titleId}>Play Store beta</h2>
            <p id={descId}>
              Stitch on Google Play is invite-only while we beta test. Leave your email and we&apos;ll reach out with
              access.
            </p>

            {status === 'success' ? (
              <p className="play-beta-modal__success">Thanks — we&apos;ll email you when a slot opens.</p>
            ) : (
              <form className="play-beta-modal__form" onSubmit={onSubmit}>
                <label className="play-beta-modal__label" htmlFor="beta-email">
                  Email
                </label>
                <div className="play-beta-modal__row">
                  <input
                    ref={inputRef}
                    id="beta-email"
                    type="email"
                    name="email"
                    autoComplete="email"
                    placeholder="you@company.com"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    required
                    disabled={status === 'loading'}
                  />
                  <button type="submit" className="landing-btn landing-btn-primary" disabled={status === 'loading'}>
                    {status === 'loading' ? 'Sending…' : 'Request access'}
                  </button>
                </div>
                {error ? <p className="play-beta-modal__error">{error}</p> : null}
              </form>
            )}
          </div>
        </div>
      ) : null}
    </>
  );
}

function PlayIcon() {
  return (
    <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor">
      <path d="M3.6 1.8c-.3.2-.6.6-.6 1.1v18.2c0 .5.3.9.7 1.1.4.2.9.1 1.3-.2l14.8-8.6c.4-.3.7-.7.7-1.1s-.3-.8-.7-1.1L4.3 1.9c-.4-.3-.9-.4-1.3-.2z" />
    </svg>
  );
}
