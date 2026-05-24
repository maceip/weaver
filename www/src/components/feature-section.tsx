import type { ReactNode } from 'react';

type FeatureSectionProps = {
  index: string;
  title: string;
  subtitle: string;
  meta: string;
  description: string;
  children: ReactNode;
};

export function FeatureSection({
  index,
  title,
  subtitle,
  meta,
  description,
  children,
}: FeatureSectionProps) {
  return (
    <section className="landing-feature" id={title.toLowerCase().replace(/\s+/g, '-')}>
      <div className="landing-feature-head">
        <p className="landing-feature-kicker">{subtitle}</p>
        <h2 className="landing-feature-title">Stitch · Foldable Features</h2>
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
  );
}
