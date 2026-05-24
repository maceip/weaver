import { useEffect, useState } from 'react';
import { useScrollY } from '../hooks/use-scroll-y';

type SiteHeaderProps = {
  docsUrl: string;
  repoUrl: string;
};

export function SiteHeader({ docsUrl, repoUrl }: SiteHeaderProps) {
  const scrolled = useScrollY(16);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    document.body.style.overflow = menuOpen ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [menuOpen]);

  const closeMenu = () => setMenuOpen(false);

  return (
    <header className={`landing-header ${scrolled ? 'landing-header--scrolled' : ''}`}>
      <div className="landing-header__inner">
        <a href="#" className="landing-logo" onClick={closeMenu}>
          <img src={`${import.meta.env.BASE_URL}logo.webp`} alt="" className="landing-logo-img" width={36} height={36} />
          <span>Stitch</span>
        </a>

        <button
          type="button"
          className="landing-nav-toggle"
          aria-expanded={menuOpen}
          aria-controls="site-nav"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span className="landing-nav-toggle__bar" aria-hidden />
          <span className="landing-nav-toggle__bar" aria-hidden />
          <span className="sr-only">{menuOpen ? 'Close menu' : 'Open menu'}</span>
        </button>

        <nav id="site-nav" className={`landing-nav ${menuOpen ? 'landing-nav--open' : ''}`} aria-label="Primary">
          <a href="#features" onClick={closeMenu}>
            Features
          </a>
          <a href={docsUrl} onClick={closeMenu}>
            Docs
          </a>
          <a href={repoUrl} target="_blank" rel="noopener noreferrer" onClick={closeMenu}>
            GitHub
          </a>
        </nav>
      </div>
    </header>
  );
}
