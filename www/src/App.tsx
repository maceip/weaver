import { FeatureSection } from './components/feature-section';
import { FireAndFoldDemo } from './components/landing/fire-and-fold-demo';
import { FoldToCompareDemo } from './components/landing/fold-to-compare-demo';
import { TabletopStudioDemo } from './components/landing/tabletop-studio-demo';
import './landing.css';

const DOCS_URL = import.meta.env.BASE_URL + 'docs/';

export default function App() {
  return (
    <div className="landing">
      <header className="landing-header">
        <div className="landing-logo">
          <span className="landing-logo-mark" aria-hidden />
          <span>Stitch</span>
        </div>
        <nav className="landing-nav" aria-label="Primary">
          <a href="#features">Features</a>
          <a href={DOCS_URL}>Docs</a>
          <a href="https://github.com/maceip/stitch" target="_blank" rel="noopener noreferrer">
            GitHub
          </a>
        </nav>
      </header>

      <section className="landing-hero">
        <span className="landing-eyebrow">Android · Google Stitch · Foldables</span>
        <h1>
          Design on Stitch.
          <span className="iridescent"> Control it natively on foldables.</span>
        </h1>
        <p>
          Stitch is the native Android companion for Google Stitch. It mirrors the editor canvas, routes prompts
          through a real WebView session, and adapts the UI to hinge posture — tabletop, fold-to-compare, and
          fire-and-fold handoff to the cover screen.
        </p>
        <div className="landing-cta">
          <a className="landing-btn landing-btn-primary" href="https://github.com/maceip/stitch/releases/latest">
            Download APK
          </a>
          <a className="landing-btn landing-btn-secondary" href={DOCS_URL}>
            Read docs
          </a>
        </div>
      </section>

      {/* Scroll-animated Pixel Fold 3D — sticky inside, 640svh tall outside */}
      <section className="fold-stage" aria-label="Pixel Fold showtime">
        <pixel-fold-showtime
          model-url="/3d/assets/pixel10_fold_v18.glb"
          environment-url="/3d/assets/studio-softbox.hdr"
          draco-decoder-path="/3d/vendor/three/examples/jsm/libs/draco/gltf/"
          ktx2-transcoder-path="/3d/vendor/three/examples/jsm/libs/basis/"
        ></pixel-fold-showtime>
      </section>

      <div className="landing-features" id="features">
        <FeatureSection
          index="01"
          title="Tabletop Studio"
          subtitle="Feature 1 of 3"
          meta="posture: half-open · horizontal hinge"
          description="Top panel becomes the canvas; the bottom flat panel becomes a physical control deck with version filmstrip, prompt input, and the agent orb."
        >
          <TabletopStudioDemo />
        </FeatureSection>

        <FeatureSection
          index="02"
          title="Fold-to-Compare"
          subtitle="Feature 2 of 3"
          meta="input: continuous hinge angle"
          description="The hinge angle scrubs an A / B onion-skin blend of two selected designs. Calibrated comfort band anchors to the angle on entry; tap to lock the blend."
        >
          <FoldToCompareDemo />
        </FeatureSection>

        <FeatureSection
          index="03"
          title="Fire-and-Fold"
          subtitle="Feature 3 of 3"
          meta="handoff: inner screen → cover screen"
          description="Fire a prompt, fold the phone, and the cover screen tracks generation to completion — then unfold onto the finished result."
        >
          <FireAndFoldDemo />
        </FeatureSection>
      </div>

      <footer className="landing-footer">
        <p>
          Stitch · <a href="https://github.com/maceip/stitch">maceip/stitch</a>
        </p>
      </footer>
    </div>
  );
}
