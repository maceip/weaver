import { useRef } from 'react';
import { FeatureSection } from './components/feature-section';
import { FireAndFoldDemo } from './components/landing/fire-and-fold-demo';
import { FoldToCompareDemo } from './components/landing/fold-to-compare-demo';
import { TabletopStudioDemo } from './components/landing/tabletop-studio-demo';
import { SiteHeader } from './components/site-header';
import { usePointerGlow } from './hooks/use-pointer-glow';
import './landing.css';

const DOCS_URL = import.meta.env.BASE_URL + 'docs/';
const RELEASES_URL = 'https://github.com/maceip/stitch/releases/latest';
const REPO_URL = 'https://github.com/maceip/stitch';

export default function App() {
  const heroRef = useRef<HTMLElement | null>(null);
  usePointerGlow(heroRef);

  return (
    <div className="landing">
      <SiteHeader docsUrl={DOCS_URL} repoUrl={REPO_URL} />

      <section ref={heroRef} className="landing-hero landing-hero--interactive">
        <div className="landing-hero__glow" aria-hidden />
        <div className="landing-hero__content landing-hero-in">
          <span className="landing-eyebrow">Android · Google Stitch · Foldables</span>
          <h1>
            Design on Stitch.
            <span className="iridescent"> Control it natively on foldables.</span>
          </h1>
          <p className="landing-hero-lead">Prompt the layout. Ship the pixel — natively on foldables.</p>
          <p className="landing-hero-body">
            Stitch mirrors the editor canvas, routes prompts through a real WebView session, and adapts the UI to hinge
            posture — tabletop, fold-to-compare, and fire-and-fold handoff to the cover screen.
          </p>
          <div className="landing-cta">
            <a className="landing-btn landing-btn-primary" href={RELEASES_URL}>
              Download APK
            </a>
            <a className="landing-btn landing-btn-secondary" href={DOCS_URL}>
              Read docs
            </a>
          </div>
        </div>
        <div className="landing-hero__visual landing-hero-in landing-hero-in--late" aria-hidden>
          <img src={`${import.meta.env.BASE_URL}hero.webp`} alt="" className="landing-hero__shot" width={420} height={520} />
        </div>
      </section>

      <section className="fold-stage" aria-label="Pixel Fold showtime">
        <pixel-fold-showtime
          model-url="/3d/assets/pixel10_fold_v18.glb"
          environment-url="/3d/assets/studio-softbox.hdr"
          draco-decoder-path="/3d/vendor/three/examples/jsm/libs/draco/gltf/"
          ktx2-transcoder-path="/3d/vendor/three/examples/jsm/libs/basis/"
          fallback-image-url="/hero.webp"
        ></pixel-fold-showtime>
      </section>

      <div className="landing-features" id="features">
        <FeatureSection
          index="01"
          title="Tabletop Studio"
          subtitle="Feature 1 of 3"
          meta="posture: half-open · horizontal hinge"
          description="Top panel becomes the canvas; the bottom flat panel becomes a physical control deck with version filmstrip, prompt input, and the agent orb."
          revealDelay={0}
        >
          <TabletopStudioDemo />
        </FeatureSection>

        <FeatureSection
          index="02"
          title="Fold-to-Compare"
          subtitle="Feature 2 of 3"
          meta="input: continuous hinge angle"
          description="The hinge angle scrubs an A / B onion-skin blend of two selected designs. Calibrated comfort band anchors to the angle on entry; tap to lock the blend."
          revealDelay={80}
        >
          <FoldToCompareDemo />
        </FeatureSection>

        <FeatureSection
          index="03"
          title="Fire-and-Fold"
          subtitle="Feature 3 of 3"
          meta="handoff: inner screen · cover screen"
          description="Fire a prompt, fold the phone, and the cover screen tracks generation to completion — then unfold onto the finished result."
          revealDelay={160}
        >
          <FireAndFoldDemo />
        </FeatureSection>
      </div>

      <footer className="landing-footer">
        <nav className="landing-footer-nav" aria-label="Footer">
          <a href={RELEASES_URL}>Download latest APK</a>
          <a href={DOCS_URL}>Documentation</a>
          <a href={REPO_URL} target="_blank" rel="noopener noreferrer">
            GitHub
          </a>
        </nav>
        <p>
          Stitch · <a href={REPO_URL}>maceip/stitch</a>
        </p>
      </footer>
    </div>
  );
}
