import type { ReactNode } from 'react';
import { Reveal } from './reveal';

type FeatureSectionProps = {
  index: string;
  title: string;
  subtitle: string;
  meta: string;
  description: string;
  children: ReactNode;
  revealDelay?: number;
};

export function FeatureSection({
  index,
  title,
  subtitle,
  meta,
  description,
  children,
  revealDelay = 0,
}: FeatureSectionProps) {
  return (
    <Reveal delay={revealDelay}>
      <section className="landing-feature" id={title.toLowerCase().replace(/\s+/g, '-')}>
        <div className="landing-feature-head">
          <p className="landing-feature-kicker">
            {subtitle} · <span className="landing-feature-series">Stitch foldables</span>
          </p>
          <h2 className="landing-feature-title">{title}</h2>
          <div className="landing-feature-banner">
            <strong>
              {index} — {title}
            </strong>
            <span>{meta}</span>
          </div>
          <p className="landing-feature-desc">{description}</p>
        </div>
        <div className="landing-feature-visual landing-feature-visual--interactive">{children}</div>
      </section>
    </Reveal>
  );
}
