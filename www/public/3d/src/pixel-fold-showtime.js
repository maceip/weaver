import * as THREE from "three";
import { DRACOLoader } from "three/addons/loaders/DRACOLoader.js";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { KTX2Loader } from "three/addons/loaders/KTX2Loader.js";
import { RGBELoader } from "three/addons/loaders/RGBELoader.js";
import { RoomEnvironment } from "three/addons/environments/RoomEnvironment.js";
import { RectAreaLightUniformsLib } from "three/addons/lights/RectAreaLightUniformsLib.js";

const clamp = (value, min = 0, max = 1) => Math.min(max, Math.max(min, value));
const mix = (a, b, t) => a + (b - a) * t;
const smoother = (edge0, edge1, value) => {
  const t = clamp((value - edge0) / (edge1 - edge0));
  return t * t * t * (t * (t * 6 - 15) + 10);
};

class PixelFoldShowtime extends HTMLElement {
  static observedAttributes = [
    "model-url",
    "environment-url",
    "draco-decoder-path",
    "ktx2-transcoder-path",
    "fallback-image-url",
    "exit-mode",
  ];

  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this.modelUrl = "./assets/pixel10_fold_v18.glb";
    this.environmentUrl = "./assets/studio-softbox.hdr";
    this.dracoDecoderPath = "./vendor/three/examples/jsm/libs/draco/gltf/";
    this.ktx2TranscoderPath = "./vendor/three/examples/jsm/libs/basis/";
    this.fallbackImageUrl = "/hero.webp";
    this.exitMode = "fog";
    this.progress = 0;
    this.smoothProgress = 0;
    this.pointer = new THREE.Vector2();
    this.targetPointer = new THREE.Vector2();
    this.meshMaterials = [];
    this.innerOnlyMeshes = [];
    this.screenZoomShellMeshes = [];
    this.screenZoomCoreMeshes = [];
    this.innerPresentationMaterials = [];
    this.satinNormalTexture = null;
    this.satinRoughnessTexture = null;
    this.screenSheenTexture = null;
    this.clock = new THREE.Clock();
    this.frame = 0;
    this.reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  }

  connectedCallback() {
    this.modelUrl = this.getAttribute("model-url") || this.modelUrl;
    this.environmentUrl = this.getAttribute("environment-url") || this.environmentUrl;
    this.dracoDecoderPath = this.getAttribute("draco-decoder-path") || this.dracoDecoderPath;
    this.ktx2TranscoderPath = this.getAttribute("ktx2-transcoder-path") || this.ktx2TranscoderPath;
    this.fallbackImageUrl = this.getAttribute("fallback-image-url") || this.fallbackImageUrl;
    this.exitMode = this.getAttribute("exit-mode") || this.exitMode;
    this.renderShell();
    if (!this.canUseWebGL()) {
      this.enableFallback("webgl");
      return;
    }
    this.mountThree();
    if (this.hasAttribute("data-error")) return;
    this.bindEvents();
    this.loadModel();
    this.updateScroll();
    this.animate();
  }

  disconnectedCallback() {
    cancelAnimationFrame(this.frame);
    this.resizeObserver?.disconnect();
    window.removeEventListener("scroll", this.handleScroll);
    window.removeEventListener("pointermove", this.handlePointerMove);
    this.environmentTexture?.dispose();
    this.satinNormalTexture?.dispose();
    this.satinRoughnessTexture?.dispose();
    this.screenSheenTexture?.dispose();
    this.dracoLoader?.dispose();
    this.ktx2Loader?.dispose();
    this.renderer?.dispose();
  }

  attributeChangedCallback(name, oldValue, newValue) {
    if (name === "model-url" && oldValue !== newValue && newValue) {
      this.modelUrl = newValue;
    }
    if (name === "environment-url" && oldValue !== newValue && newValue) {
      this.environmentUrl = newValue;
      if (this.renderer) this.loadEnvironment();
    }
    if (name === "draco-decoder-path" && oldValue !== newValue && newValue) {
      this.dracoDecoderPath = newValue;
    }
    if (name === "ktx2-transcoder-path" && oldValue !== newValue && newValue) {
      this.ktx2TranscoderPath = newValue;
    }
    if (name === "fallback-image-url" && oldValue !== newValue && newValue) {
      this.fallbackImageUrl = newValue;
      const img = this.shadowRoot?.querySelector(".fallback-hero");
      if (img) img.src = newValue;
    }
    if (name === "exit-mode" && oldValue !== newValue && newValue) {
      this.exitMode = newValue;
    }
  }

  renderShell() {
    this.shadowRoot.innerHTML = `
      <style>
        :host {
          display: block;
          position: relative;
          color: #15181d;
          min-height: 280svh;
          background:
            radial-gradient(circle at 20% 14%, rgba(255,255,255,0.92), rgba(255,255,255,0) 25rem),
            radial-gradient(circle at 78% 68%, rgba(134,149,164,0.34), rgba(134,149,164,0) 34rem),
            linear-gradient(145deg, #f2f4f6 0%, #d3d9df 46%, #f7f8f9 100%);
          overflow: clip;
        }

        @media (min-width: 540px) {
          :host:not([data-error]) {
            min-height: 420svh;
          }
        }

        @media (min-width: 768px) {
          :host:not([data-error]) {
            min-height: 520svh;
          }
        }

        @media (min-width: 1024px) {
          :host:not([data-error]) {
            min-height: 640svh;
          }
        }

        :host([data-error]) {
          min-height: auto;
        }

        :host([data-reduced-motion]) {
          min-height: 100svh;
        }

        .stage {
          position: sticky;
          top: 0;
          height: 100svh;
          min-height: 620px;
          overflow: hidden;
          isolation: isolate;
        }

        canvas {
          display: block;
          width: 100%;
          height: 100%;
        }

        .wash,
        .grain,
        .haze {
          position: absolute;
          inset: 0;
          pointer-events: none;
        }

        .wash {
          z-index: -3;
          background:
            linear-gradient(104deg, rgba(255,255,255,0.82), rgba(255,255,255,0) 34%),
            linear-gradient(260deg, rgba(104,117,130,0.18), rgba(104,117,130,0) 46%);
          transform: translate3d(var(--wash-x, 0px), var(--wash-y, 0px), 0) scale(1.08);
        }

        .haze {
          z-index: -2;
          background:
            radial-gradient(circle at var(--fog-x, 50%) 52%, rgba(255,255,255,0.72), rgba(255,255,255,0) 18rem),
            radial-gradient(circle at 50% 50%, rgba(172,184,194,0.42), rgba(172,184,194,0) 34rem);
          opacity: var(--haze-opacity, 0.2);
          filter: blur(14px);
        }

        .smoke-cloud {
          position: absolute;
          left: 50%;
          top: 50%;
          z-index: 1;
          width: min(68vw, 760px);
          aspect-ratio: 1.55;
          pointer-events: none;
          opacity: var(--smoke-opacity, 0);
          transform: translate3d(-50%, -50%, 0) scale(var(--smoke-scale, 0.4));
          filter: blur(22px);
          mix-blend-mode: multiply;
          background:
            radial-gradient(circle at 31% 48%, rgba(91,100,112,0.36), rgba(91,100,112,0) 34%),
            radial-gradient(circle at 47% 38%, rgba(126,136,148,0.32), rgba(126,136,148,0) 28%),
            radial-gradient(circle at 61% 56%, rgba(78,88,101,0.34), rgba(78,88,101,0) 31%),
            radial-gradient(circle at 73% 44%, rgba(147,156,166,0.26), rgba(147,156,166,0) 27%);
          will-change: opacity, transform;
        }

        :host([exit-mode="screen-zoom"]) .smoke-cloud {
          opacity: 0;
        }

        .grain {
          z-index: 2;
          opacity: 0.018;
          mix-blend-mode: multiply;
          background-image:
            repeating-linear-gradient(0deg, rgba(20,24,29,0.18) 0 1px, transparent 1px 3px),
            repeating-linear-gradient(90deg, rgba(255,255,255,0.12) 0 1px, transparent 1px 5px);
        }

        .brand {
          position: absolute;
          left: clamp(24px, 5vw, 72px);
          top: clamp(22px, 5vw, 56px);
          z-index: 3;
          font-size: clamp(18px, 2vw, 28px);
          font-weight: 650;
          letter-spacing: 0;
          color: rgba(25,29,34,0.82);
          text-wrap: balance;
        }

        :host([exit-mode="screen-zoom"]) .brand {
          opacity: calc(1 - var(--screen-zoom, 0));
          transform: translate3d(0, calc(var(--screen-zoom, 0) * -18px), 0);
        }

        :host([exit-mode="screen-zoom"]) .meter {
          opacity: calc(1 - var(--screen-zoom, 0));
        }

        .meter {
          position: absolute;
          right: clamp(20px, 4vw, 48px);
          bottom: clamp(22px, 4vw, 44px);
          z-index: 3;
          width: min(180px, 28vw);
          height: 2px;
          background: rgba(25,29,34,0.16);
          overflow: hidden;
        }

        .meter span {
          display: block;
          height: 100%;
          width: calc(var(--progress, 0) * 100%);
          background: rgba(25,29,34,0.72);
        }

        .transition-sheen {
          display: none;
          position: absolute;
          inset: 0;
          z-index: 1;
          pointer-events: none;
          opacity: var(--screen-zoom, 0);
          background:
            linear-gradient(110deg, rgba(255,255,255,0) 28%, rgba(255,255,255,0.28) 45%, rgba(255,255,255,0) 61%),
            radial-gradient(circle at 50% 50%, rgba(255,255,255,0), rgba(232,237,242,0.5) 76%);
          transform: translate3d(calc((1 - var(--screen-zoom, 0)) * -26vw), 0, 0);
          mix-blend-mode: screen;
        }

        .screen-takeover {
          display: none;
          position: absolute;
          left: 50%;
          top: 50%;
          z-index: 4;
          width: min(32vw, 410px);
          height: min(72vh, 760px);
          border-radius: var(--screen-radius, 34px);
          background:
            linear-gradient(128deg, rgba(255,255,255,0.20), rgba(255,255,255,0) 32%),
            radial-gradient(circle at 62% 76%, rgba(84,91,104,0.30), rgba(84,91,104,0) 34%),
            linear-gradient(180deg, #16181f 0%, #090b10 100%);
          box-shadow:
            0 0 0 1px rgba(255,255,255,0.18) inset,
            0 34px 90px rgba(14,18,24,0.22);
          opacity: var(--screen-takeover-opacity, 0);
          pointer-events: none;
          transform:
            translate3d(calc(-50% + var(--screen-shift-x, 0px)), calc(-50% + var(--screen-shift-y, 0px)), 0)
            scale(var(--screen-scale, 0.42));
          transform-origin: center;
          overflow: hidden;
          will-change: transform, opacity, border-radius;
        }

        .screen-takeover::before {
          content: "";
          position: absolute;
          inset: -8%;
          background:
            linear-gradient(116deg, rgba(255,255,255,0) 30%, rgba(255,255,255,0.24) 42%, rgba(255,255,255,0) 56%),
            linear-gradient(180deg, rgba(52,58,70,0), rgba(111,123,145,0.16));
          opacity: var(--screen-sheen, 0);
          transform: translate3d(calc((1 - var(--screen-zoom, 0)) * -46%), 0, 0);
        }

        :host([exit-mode="screen-zoom"]) .screen-takeover {
          display: block;
          inset: 0;
          left: 0;
          top: 0;
          width: auto;
          height: auto;
          border-radius: 0;
          background:
            linear-gradient(125deg, rgba(135,143,154,0.94), rgba(70,77,88,0.98) 42%, rgba(18,22,30,1) 100%);
          box-shadow: none;
          opacity: var(--screen-fill-opacity, 0);
          transform: none;
        }

        :host([exit-mode="screen-zoom"]) .screen-takeover::before {
          display: none;
        }

        :host([data-error]) .stage {
          position: relative;
          height: min(72svh, 560px);
          min-height: 420px;
        }

        :host([data-error]) canvas,
        :host([data-error]) .scroll-hint,
        :host([data-error]) .meter {
          display: none;
        }

        .scroll-hint {
          position: absolute;
          left: 50%;
          bottom: clamp(20px, 4vw, 40px);
          z-index: 3;
          transform: translateX(-50%);
          padding: 0.45rem 0.9rem;
          border-radius: 999px;
          background: rgba(255, 255, 255, 0.82);
          border: 1px solid rgba(25, 29, 34, 0.08);
          font-size: 0.85rem;
          font-weight: 600;
          color: rgba(25, 29, 34, 0.78);
          letter-spacing: 0.01em;
          backdrop-filter: blur(8px);
          -webkit-backdrop-filter: blur(8px);
        }

        :host(:not([data-error])) .brand,
        :host(:not([data-error])) .scroll-hint,
        :host(:not([data-error])) .meter {
          opacity: 0;
          visibility: hidden;
        }

        .fallback-hero {
          display: none;
          position: absolute;
          inset: clamp(16px, 4vw, 48px);
          z-index: 2;
          width: calc(100% - clamp(32px, 8vw, 96px));
          height: calc(100% - clamp(32px, 8vw, 96px));
          margin: auto;
          object-fit: contain;
          object-position: center;
          filter: drop-shadow(0 24px 48px rgba(15, 17, 21, 0.12));
        }

        :host([data-error]) .fallback-hero {
          display: block;
        }

        .fallback {
          position: absolute;
          inset: auto clamp(24px, 6vw, 80px) clamp(32px, 8vw, 96px);
          z-index: 4;
          max-width: 34rem;
          font-size: 15px;
          line-height: 1.45;
          color: rgba(25,29,34,0.82);
          visibility: hidden;
        }

        :host([data-error]) .fallback {
          visibility: visible;
          inset: auto clamp(24px, 6vw, 80px) clamp(18px, 4vw, 28px);
          text-align: center;
          max-width: none;
          left: clamp(24px, 6vw, 80px);
          right: clamp(24px, 6vw, 80px);
          font-size: 14px;
          color: rgba(25,29,34,0.62);
        }
      </style>
      <div class="stage">
        <div class="wash"></div>
        <div class="haze"></div>
        <canvas part="canvas"></canvas>
        <div class="smoke-cloud" aria-hidden="true"></div>
        <div class="transition-sheen"></div>
        <div class="screen-takeover" aria-hidden="true"></div>
        <div class="brand">Pixel 10 Pro Fold</div>
        <div class="scroll-hint">Scroll to fold the Pixel 10 Pro Fold</div>
        <div class="meter" aria-hidden="true"><span></span></div>
        <div class="grain"></div>
        <img class="fallback-hero" alt="" decoding="async" />
        <div class="fallback">Interactive 3D preview unavailable — showing a still frame.</div>
      </div>
    `;

    this.stage = this.shadowRoot.querySelector(".stage");
    this.canvas = this.shadowRoot.querySelector("canvas");
    const fallbackHero = this.shadowRoot.querySelector(".fallback-hero");
    if (fallbackHero) fallbackHero.src = this.fallbackImageUrl;
    if (this.reducedMotion.matches) this.setAttribute("data-reduced-motion", "");
    this.reducedMotion.addEventListener?.("change", (event) => {
      if (event.matches) this.setAttribute("data-reduced-motion", "");
      else this.removeAttribute("data-reduced-motion");
    });
  }

  canUseWebGL() {
    try {
      const canvas = document.createElement("canvas");
      return Boolean(canvas.getContext("webgl2") || canvas.getContext("webgl"));
    } catch {
      return false;
    }
  }

  enableFallback() {
    if (this.hasAttribute("data-error")) return;
    this.setAttribute("data-error", "");
    cancelAnimationFrame(this.frame);
    window.removeEventListener("scroll", this.handleScroll);
    window.removeEventListener("pointermove", this.handlePointerMove);
    this.resizeObserver?.disconnect();
    const img = this.shadowRoot?.querySelector(".fallback-hero");
    if (img) img.src = this.fallbackImageUrl;
  }

  mountThree() {
    try {
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0xdce1e6);
    this.scene.fog = new THREE.FogExp2(0xd9dee4, 0.011);

    this.camera = new THREE.PerspectiveCamera(38, 1, 0.01, 60);
    this.camera.position.set(0, 0.1, 6.4);

    this.renderer = new THREE.WebGLRenderer({
      canvas: this.canvas,
      antialias: true,
      alpha: false,
      powerPreference: "high-performance",
    });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.18;
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.VSMShadowMap;
    if ("useLegacyLights" in this.renderer) this.renderer.useLegacyLights = false;
    this.satinNormalTexture = this.createSatinNormalTexture();
    this.satinRoughnessTexture = this.createSatinRoughnessTexture();
    this.screenSheenTexture = this.createScreenSheenTexture();

    const pmrem = new THREE.PMREMGenerator(this.renderer);
    this.scene.environment = pmrem.fromScene(new RoomEnvironment(this.renderer), 0.04).texture;
    pmrem.dispose();
    this.loadEnvironment();

    RectAreaLightUniformsLib.init();

    this.scene.add(new THREE.HemisphereLight(0xf8fbff, 0x6f7985, 0.24));

    this.keyLight = new THREE.DirectionalLight(0xffffff, 1.34);
    this.keyLight.position.set(3.2, 4.4, 5.2);
    this.keyLight.castShadow = true;
    this.keyLight.shadow.mapSize.set(1024, 1024);
    this.keyLight.shadow.radius = 3.5;
    this.keyLight.shadow.camera.near = 1;
    this.keyLight.shadow.camera.far = 12;
    this.keyLight.shadow.camera.left = -3;
    this.keyLight.shadow.camera.right = 3;
    this.keyLight.shadow.camera.top = 3;
    this.keyLight.shadow.camera.bottom = -3;
    this.scene.add(this.keyLight);

    this.rimLight = new THREE.DirectionalLight(0xcfe2ff, 1.06);
    this.rimLight.position.set(-4.2, 1.4, -2.8);
    this.scene.add(this.rimLight);

    this.cameraBumpStripLight = new THREE.RectAreaLight(0xffffff, 3.2, 2.45, 0.24);
    this.cameraBumpStripLight.position.set(-1.85, 2.35, 3.0);
    this.cameraBumpStripLight.lookAt(-0.25, 0.18, 0);
    this.scene.add(this.cameraBumpStripLight);

    this.edgeGrazingLight = new THREE.RectAreaLight(0xe2edff, 3.25, 0.18, 3.2);
    this.edgeGrazingLight.position.set(2.2, -0.9, 2.8);
    this.edgeGrazingLight.lookAt(0.1, 0.0, 0);
    this.scene.add(this.edgeGrazingLight);

    this.backSilkLight = new THREE.RectAreaLight(0xffffff, 3.1, 5.2, 0.52);
    this.backSilkLight.position.set(-0.65, 2.8, 2.45);
    this.backSilkLight.lookAt(-0.15, 0.0, 0);
    this.scene.add(this.backSilkLight);

    this.lensGlintLight = new THREE.RectAreaLight(0xffffff, 4.4, 1.0, 0.08);
    this.lensGlintLight.position.set(-1.2, 1.0, 3.35);
    this.lensGlintLight.lookAt(-0.42, 0.18, 0);
    this.scene.add(this.lensGlintLight);

    this.showGroup = new THREE.Group();
    this.phoneGroup = new THREE.Group();
    this.showGroup.add(this.phoneGroup);
    this.scene.add(this.showGroup);

    this.createFogCloud();
    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.stage);
    this.resize();
    } catch {
      this.enableFallback();
    }
  }

  bindEvents() {
    this.handleScroll = () => this.updateScroll();
    this.handlePointerMove = (event) => {
      const rect = this.getBoundingClientRect();
      this.targetPointer.x = ((event.clientX - rect.left) / Math.max(rect.width, 1) - 0.5) * 2;
      this.targetPointer.y = ((event.clientY - rect.top) / Math.max(rect.height, 1) - 0.5) * 2;
    };

    window.addEventListener("scroll", this.handleScroll, { passive: true });
    window.addEventListener("pointermove", this.handlePointerMove, { passive: true });
  }

  loadModel() {
    const loader = new GLTFLoader();
    this.dracoLoader?.dispose();
    this.dracoLoader = new DRACOLoader();
    this.dracoLoader.setDecoderPath(this.dracoDecoderPath);
    loader.setDRACOLoader(this.dracoLoader);
    this.ktx2Loader?.dispose();
    this.ktx2Loader = new KTX2Loader();
    this.ktx2Loader.setTranscoderPath(this.ktx2TranscoderPath);
    this.ktx2Loader.detectSupport(this.renderer);
    loader.setKTX2Loader(this.ktx2Loader);
    loader.load(
      this.modelUrl,
      (gltf) => this.prepareModel(gltf.scene),
      undefined,
      () => {
        this.enableFallback();
      },
    );
  }

  loadEnvironment() {
    if (!this.renderer || !this.environmentUrl) return;
    const loader = new RGBELoader();
    loader.load(
      this.environmentUrl,
      (texture) => {
        texture.mapping = THREE.EquirectangularReflectionMapping;
        const pmrem = new THREE.PMREMGenerator(this.renderer);
        const envMap = pmrem.fromEquirectangular(texture).texture;
        texture.dispose();
        pmrem.dispose();
        this.environmentTexture?.dispose();
        this.environmentTexture = envMap;
        this.scene.environment = envMap;
      },
      undefined,
      () => {
        // RoomEnvironment remains active as a fallback when the HDRI is unavailable.
      },
    );
  }

  prepareModel(asset) {
    this.removeRejectedAssetMeshes(asset);
    asset.updateWorldMatrix(true, true);
    const initialBox = new THREE.Box3().setFromObject(asset);
    const center = initialBox.getCenter(new THREE.Vector3());
    asset.position.sub(center);
    asset.updateWorldMatrix(true, true);

    this.leftPivot = new THREE.Group();
    this.rightPivot = new THREE.Group();
    this.hingeGroup = new THREE.Group();
    this.phoneGroup.add(asset, this.leftPivot, this.rightPivot, this.hingeGroup);
    this.modelRoot = asset;

    const hingeBox = this.getNamedBox(asset, (name) => this.isSpineMeshName(name));
    const centeredBox = new THREE.Box3().setFromObject(asset);
    const hingeCenter = hingeBox?.getCenter(new THREE.Vector3()) || new THREE.Vector3(0, 0, 0);
    this.hingeCenter = hingeCenter.clone();
    const size = centeredBox.getSize(new THREE.Vector3());
    this.baseScale = 2.15 / Math.max(size.x, size.z, 0.001);
    this.createInnerCreaseBridge(asset, hingeCenter);

    this.leftPivot.position.copy(hingeCenter);
    this.rightPivot.position.copy(hingeCenter);
    this.hingeGroup.position.copy(hingeCenter);
    this.leftPivot.updateWorldMatrix(true, false);
    this.rightPivot.updateWorldMatrix(true, false);
    this.hingeGroup.updateWorldMatrix(true, false);

    const meshes = [];
    asset.traverse((node) => {
      if (!node.isMesh) return;
      node.castShadow = true;
      node.receiveShadow = true;
      this.prepareGeometry(node);
      if (this.isInnerOnlyMesh(node.name)) {
        this.innerOnlyMeshes.push(node);
      }
      if (this.isScreenZoomCoreMesh(node.name)) {
        this.screenZoomCoreMeshes.push(node);
      } else {
        this.screenZoomShellMeshes.push(node);
      }
      meshes.push(node);
      const materials = Array.isArray(node.material) ? node.material : [node.material];
      materials.forEach((material) => {
        if (!material) return;
        material = material.clone();
      });
      this.prepareMaterial(node);
    });

    meshes.forEach((mesh) => {
      const bucket = this.bucketForMesh(mesh, hingeCenter);
      if (bucket === "left") this.leftPivot.attach(mesh);
      if (bucket === "right") this.rightPivot.attach(mesh);
      if (bucket === "hinge") this.hingeGroup.attach(mesh);
    });

    meshes.forEach((mesh) => this.addSurfaceAccents(mesh));

    this.phoneGroup.rotation.x = Math.PI / 2;
    this.phoneGroup.scale.setScalar(this.baseScale * 0.34);
    this.phoneGroup.position.set(0, -0.02, 0);
    this.applyFold(0.96);
    this.removeAttribute("data-error");
  }

  removeRejectedAssetMeshes(asset) {
    const rejected = [];
    asset.traverse((node) => {
      if (!node.isMesh) return;
      const name = node.name.toLowerCase();
      const materialNames = (Array.isArray(node.material) ? node.material : [node.material])
        .map((material) => material?.name?.toLowerCase() || "");
      if (
        /^phone_\d/.test(name) ||
        name.startsWith("phone2_") ||
        (name.startsWith("phone_") && materialNames.some((materialName) => materialName.startsWith("material_0"))) ||
        name.startsWith("hinge_segment_seam_") ||
        name.startsWith("hinge_shadow_groove_") ||
        name.startsWith("hinge_spine_antenna_band_") ||
        name.startsWith("folded_middle_antenna_band_") ||
        name.startsWith("hinge_endcap_ring_") ||
        name.startsWith("hinge_pin_dark_") ||
        name.startsWith("restored_antenna_face_") ||
        name === "showtime_flash_diffuser_face" ||
        name === "g_logo_deboss_shadow" ||
        name === "g_logo_emboss_highlight"
      ) {
        rejected.push(node);
      }
    });
    rejected.forEach((node) => node.parent?.remove(node));
  }

  prepareGeometry(mesh) {
    if (!mesh.geometry) return;
    mesh.geometry.computeVertexNormals();
    mesh.geometry.normalizeNormals?.();
    if (mesh.geometry.attributes.normal) {
      mesh.geometry.attributes.normal.needsUpdate = true;
    }
  }

  addSurfaceAccents(mesh) {
    const name = mesh.name.toLowerCase();
    if (this.isDisplayGlassMesh(name)) {
      this.addGlassSheen(mesh, name.includes("cover_screen") ? 0.2 : 0.14);
    }
  }

  createInnerCreaseBridge(asset, hingeCenter) {
    const screenBox = this.getNamedBox(asset, (name) => name.includes("screen_inner_"));
    if (!screenBox) return;
    const screenSize = screenBox.getSize(new THREE.Vector3());
    const screenCenter = screenBox.getCenter(new THREE.Vector3());
    const material = new THREE.MeshPhysicalMaterial({
      name: "runtime_inner_screen_crease_bridge",
      color: 0x121a26,
      metalness: 0,
      roughness: 0.12,
      clearcoat: 0.85,
      clearcoatRoughness: 0.08,
      envMapIntensity: 2.7,
      transparent: true,
      opacity: 0,
      depthWrite: false,
      side: THREE.DoubleSide,
    });
    const bridge = new THREE.Mesh(
      new THREE.PlaneGeometry(Math.max(screenSize.x * 0.035, 0.034), screenSize.y * 0.92),
      material,
    );
    bridge.name = "runtime_inner_screen_crease_bridge";
    bridge.position.set(hingeCenter.x, screenCenter.y, screenBox.max.z + 0.003);
    bridge.renderOrder = 9;
    bridge.visible = false;
    bridge.castShadow = false;
    bridge.receiveShadow = false;
    this.phoneGroup.add(bridge);
    this.innerCreaseBridge = bridge;
    this.innerPresentationMaterials.push(material);
    this.screenZoomCoreMeshes.push(bridge);
  }

  addGlassSheen(mesh, opacity) {
    if (!mesh.geometry || !this.screenSheenTexture) return;
    mesh.geometry.computeBoundingBox();
    const box = mesh.geometry.boundingBox;
    if (!box) return;
    const size = box.getSize(new THREE.Vector3());
    if (size.x < 0.05 || size.y < 0.05) return;
    const center = box.getCenter(new THREE.Vector3());
    const material = new THREE.MeshBasicMaterial({
      map: this.screenSheenTexture,
      color: 0xffffff,
      transparent: true,
      opacity,
      depthWrite: false,
      depthTest: true,
      side: THREE.DoubleSide,
      blending: THREE.AdditiveBlending,
      toneMapped: false,
    });
    const sheen = new THREE.Mesh(new THREE.PlaneGeometry(size.x * 0.94, size.y * 0.94), material);
    sheen.name = `${mesh.name}_studio_glass_sheen`;
    sheen.position.set(center.x, center.y, box.max.z + 0.0018);
    sheen.renderOrder = 12;
    sheen.castShadow = false;
    sheen.receiveShadow = false;
    mesh.add(sheen);
    this.meshMaterials.push(material);
  }

  prepareMaterial(mesh) {
    const clone = (material) => {
      if (!material) return material;
      let next = material.clone();
      const name = mesh.name.toLowerCase();
      const materialName = material.name?.toLowerCase() || "";
      const ensurePhysical = () => {
        if (next.isMeshPhysicalMaterial) return;
        const source = next;
        const physical = new THREE.MeshPhysicalMaterial({
          name: source.name,
          color: source.color ? source.color.clone() : new THREE.Color(0xffffff),
          metalness: source.metalness ?? 0,
          roughness: source.roughness ?? 0.5,
          transparent: source.transparent,
          opacity: source.opacity,
          side: source.side,
          depthTest: source.depthTest,
          depthWrite: source.depthWrite,
          alphaTest: source.alphaTest,
          envMapIntensity: source.envMapIntensity ?? 1,
        });
        physical.aoMap = source.aoMap ?? null;
        physical.aoMapIntensity = source.aoMapIntensity ?? 1;
        physical.map = source.map ?? null;
        physical.normalMap = source.normalMap ?? null;
        physical.roughnessMap = source.roughnessMap ?? null;
        physical.metalnessMap = source.metalnessMap ?? null;
        next = physical;
      };
      const clearTextureMaps = () => {
        [
          "map",
          "normalMap",
          "roughnessMap",
          "metalnessMap",
          "alphaMap",
          "bumpMap",
          "displacementMap",
          "emissiveMap",
        ].forEach((key) => {
          if (key in next) next[key] = null;
        });
      };
      const setMoonstone = () => {
        ensurePhysical();
        clearTextureMaps();
        next.color?.set(0xb0b7c1);
        next.metalness = 0;
        next.roughness = 0.28;
        next.envMapIntensity = 3.85;
        next.aoMapIntensity = next.aoMap ? 0.78 : 1;
        next.normalMap = this.satinNormalTexture;
        next.roughnessMap = this.satinRoughnessTexture;
        if (next.normalScale) next.normalScale.set(0.003, 0.003);
        next.emissive?.set(0x000000);
        if ("emissiveIntensity" in next) next.emissiveIntensity = 0;
        if ("clearcoat" in next) next.clearcoat = 1.0;
        if ("clearcoatRoughness" in next) next.clearcoatRoughness = 0.18;
        if ("ior" in next) next.ior = 1.46;
        if ("specularIntensity" in next) next.specularIntensity = 1.0;
        next.specularColor?.set(0xf8fbff);
      };

      next.envMapIntensity = name.includes("screen") ? 1.35 : 1.2;

      if (
        materialName.includes("pixel_porcelain") ||
        materialName.includes("showtime_matte_grey_back_layer") ||
        materialName.includes("camera_bump") ||
        name.includes("visor_body") ||
        name.includes("back_grey_layer")
      ) {
        setMoonstone();
      } else if (name.includes("lens_top") || name.includes("lens_bottom")) {
        ensurePhysical();
        clearTextureMaps();
        next.color?.set(0x010203);
        next.metalness = 0;
        next.roughness = 0.08;
        if ("clearcoat" in next) next.clearcoat = 1.0;
        if ("clearcoatRoughness" in next) next.clearcoatRoughness = 0.035;
        if ("ior" in next) next.ior = 1.62;
        if ("specularIntensity" in next) next.specularIntensity = 1.0;
        next.specularColor?.set(0xffffff);
        next.envMapIntensity = 4.15;
      } else if (
        name.includes("flash") ||
        materialName.includes("flash_white_clean") ||
        materialName.includes("showtime_flash")
      ) {
        ensurePhysical();
        clearTextureMaps();
        next.color?.set(0xfffff6);
        next.metalness = 0;
        next.roughness = 0.16;
        next.emissive?.set(0xfff4d0);
        if ("emissiveIntensity" in next) next.emissiveIntensity = 0.58;
        if ("clearcoat" in next) next.clearcoat = 0.15;
        if ("clearcoatRoughness" in next) next.clearcoatRoughness = 0.08;
        next.envMapIntensity = 1.9;
      } else if (
        !name.includes("front_camera") &&
        !name.includes("camera_mic") &&
        !name.includes("bottom_mic") &&
        !materialName.includes("camera_hole_black") &&
        (materialName.includes("screen_glass") ||
          name.includes("cover_screen") ||
          name.includes("inner_screen") ||
          name.includes("screen_outer") ||
          name.includes("screen_inner"))
      ) {
        ensurePhysical();
        clearTextureMaps();
        next.color?.set(0x101722);
        next.metalness = 0;
        next.roughness = 0.085;
        next.aoMapIntensity = next.aoMap ? 0.7 : 1;
        next.emissive?.set(0x020308);
        if ("emissiveIntensity" in next) next.emissiveIntensity = 0.035;
        if ("clearcoat" in next) next.clearcoat = 1.0;
        if ("clearcoatRoughness" in next) next.clearcoatRoughness = 0.035;
        if ("ior" in next) next.ior = 1.58;
        if ("specularIntensity" in next) next.specularIntensity = 1.0;
        next.envMapIntensity = 3.25;
      } else if (materialName.includes("screen_bezel_black") || name.includes("bezel")) {
        ensurePhysical();
        clearTextureMaps();
        next.color?.set(0x090a0d);
        next.metalness = 0;
        next.roughness = 0.28;
        next.aoMapIntensity = next.aoMap ? 0.72 : 1;
        if ("clearcoat" in next) next.clearcoat = 0.28;
        if ("clearcoatRoughness" in next) next.clearcoatRoughness = 0.18;
        next.envMapIntensity = 0.9;
      } else if (
        name.includes("front_camera") ||
        name.includes("camera_mic") ||
        name.includes("bottom_mic") ||
        materialName.includes("camera_hole_black")
      ) {
        clearTextureMaps();
        next.color?.set(0x030405);
        next.metalness = 0;
        next.roughness = 0.78;
        next.envMapIntensity = 0.08;
      } else if (name.includes("antenna")) {
        clearTextureMaps();
        next.color?.set(0x4f5c68);
        next.metalness = 0.06;
        next.roughness = 0.56;
        next.envMapIntensity = 0.62;
      } else if (this.isSpineMeshName(name) || name.includes("hinge") || materialName.includes("hinge_aluminum")) {
        ensurePhysical();
        clearTextureMaps();
        next.color?.set(0xf0f4f8);
        next.metalness = 0.96;
        next.roughness = 0.075;
        next.aoMapIntensity = next.aoMap ? 0.62 : 1;
        if ("clearcoat" in next) next.clearcoat = 0.22;
        if ("clearcoatRoughness" in next) next.clearcoatRoughness = 0.07;
        if ("specularIntensity" in next) next.specularIntensity = 1.0;
        next.envMapIntensity = 6.1;
      } else if (materialName.includes("frame_aluminum")) {
        ensurePhysical();
        clearTextureMaps();
        next.color?.set(0xf3f6f9);
        next.metalness = 0.98;
        next.roughness = 0.065;
        next.aoMapIntensity = next.aoMap ? 0.58 : 1;
        if ("clearcoat" in next) next.clearcoat = 0.18;
        if ("clearcoatRoughness" in next) next.clearcoatRoughness = 0.075;
        if ("specularIntensity" in next) next.specularIntensity = 1.0;
        next.envMapIntensity = 6.4;
      } else if (name.includes("g_logo") || materialName.includes("g_logo")) {
        ensurePhysical();
        clearTextureMaps();
        next.color?.set(0xd3d9e2);
        next.metalness = 0.02;
        next.roughness = 0.44;
        if ("clearcoat" in next) next.clearcoat = 0.04;
        if ("clearcoatRoughness" in next) next.clearcoatRoughness = 0.52;
        next.envMapIntensity = 1.05;
      }

      if ("flatShading" in next) next.flatShading = false;
      next.needsUpdate = true;
      this.meshMaterials.push(next);
      return next;
    };

    mesh.material = Array.isArray(mesh.material)
      ? mesh.material.map((material) => clone(material))
      : clone(mesh.material);
  }

  isInnerOnlyMesh(name) {
    const lower = name.toLowerCase();
    return lower.includes("inner_front_camera") || lower.includes("screen_inner_") || lower.includes("inner_bezel_screen_inner");
  }

  isDisplayGlassMesh(name) {
    const lower = name.toLowerCase();
    return (
      lower.includes("cover_screen") ||
      lower.includes("screen_inner_left_flat") ||
      lower.includes("screen_inner_right_flat")
    );
  }

  isScreenZoomCoreMesh(name) {
    const lower = name.toLowerCase();
    return (
      lower.includes("screen_inner_") ||
      lower.includes("inner_bezel_screen_inner") ||
      this.isSpineMeshName(lower) ||
      lower.startsWith("frame_")
    );
  }

  isSpineMeshName(name) {
    const lower = name.toLowerCase();
    return lower.startsWith("spine_") || lower.includes("spine_flange") || lower.includes("spine_recess");
  }

  bucketForMesh(mesh, hingeCenter) {
    const name = mesh.name.toLowerCase();
    if (this.isSpineMeshName(name)) return "hinge";
    if (/_l\d/.test(name) || name.includes("_left") || name.endsWith("left")) return "left";
    if (/_r\d/.test(name) || name.includes("_right") || name.endsWith("right")) return "right";
    if (
      name.includes("body_left") ||
      name.includes("back_grey") ||
      name.includes("visor") ||
      name.includes("lens") ||
      name.includes("flash") ||
      name.includes("camera_mic") ||
      name.includes("g_logo") ||
      name.includes("sim_tray")
    ) {
      return "left";
    }
    if (
      name.includes("body_right") ||
      name.includes("cover_") ||
      name.includes("front_camera") ||
      name.includes("inner_front") ||
      name.includes("usb") ||
      name.includes("speaker") ||
      name.includes("bottom_mic") ||
      name.includes("power") ||
      name.includes("volume") ||
      name.includes("button")
    ) {
      return "right";
    }

    if (name.includes("antenna") || name.includes("screen") || name.includes("bezel") || name.includes("body")) {
      const box = new THREE.Box3().setFromObject(mesh);
      const center = box.getCenter(new THREE.Vector3());
      return center.x < hingeCenter.x ? "left" : "right";
    }

    return "hinge";
  }

  getNamedBox(root, predicate) {
    const box = new THREE.Box3();
    let hasMatch = false;
    root.traverse((node) => {
      if (!node.isMesh || !predicate(node.name.toLowerCase())) return;
      box.expandByObject(node);
      hasMatch = true;
    });
    return hasMatch ? box : null;
  }

  createFogCloud() {
    const texture = this.createFogTexture();
    this.fogCloud = new THREE.Group();
    this.fogSprites = [];

    const rng = this.seededRandom(42);
    for (let i = 0; i < 96; i += 1) {
      const material = new THREE.SpriteMaterial({
        map: texture,
        color: new THREE.Color().setHSL(0.58, 0.12, mix(0.46, 0.78, rng())),
        transparent: true,
        opacity: 0,
        depthWrite: false,
        depthTest: false,
        blending: THREE.NormalBlending,
      });
      const sprite = new THREE.Sprite(material);
      const radius = Math.pow(rng(), 0.72) * 1.35;
      const angle = rng() * Math.PI * 2;
      sprite.position.set(
        Math.cos(angle) * radius * 0.85,
        (rng() - 0.5) * 0.9,
        Math.sin(angle) * radius * 0.42,
      );
      const scale = mix(0.22, 0.58, rng());
      sprite.scale.set(scale, scale, scale);
      sprite.userData.drift = new THREE.Vector3((rng() - 0.5) * 0.16, (rng() - 0.5) * 0.12, (rng() - 0.5) * 0.1);
      this.fogCloud.add(sprite);
      this.fogSprites.push(sprite);
    }

    this.fogCloud.visible = false;
    this.scene.add(this.fogCloud);
  }

  createFogTexture() {
    const canvas = document.createElement("canvas");
    canvas.width = 128;
    canvas.height = 128;
    const ctx = canvas.getContext("2d");
    const gradient = ctx.createRadialGradient(64, 64, 4, 64, 64, 62);
    gradient.addColorStop(0, "rgba(255,255,255,0.88)");
    gradient.addColorStop(0.34, "rgba(245,248,250,0.34)");
    gradient.addColorStop(1, "rgba(235,240,244,0)");
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, 128, 128);
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  createSatinNormalTexture() {
    const size = 128;
    const canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext("2d");
    const image = ctx.createImageData(size, size);
    const rng = this.seededRandom(501);
    const values = new Float32Array(size * size);
    for (let i = 0; i < values.length; i += 1) {
      values[i] = rng();
    }
    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        const i = y * size + x;
        const left = values[y * size + ((x - 1 + size) % size)];
        const right = values[y * size + ((x + 1) % size)];
        const up = values[((y - 1 + size) % size) * size + x];
        const down = values[((y + 1) % size) * size + x];
        const dx = (left - right) * 4.5;
        const dy = (up - down) * 4.5;
        const nz = 1;
        const invLen = 1 / Math.hypot(dx, dy, nz);
        const o = i * 4;
        image.data[o] = Math.round((dx * invLen * 0.5 + 0.5) * 255);
        image.data[o + 1] = Math.round((dy * invLen * 0.5 + 0.5) * 255);
        image.data[o + 2] = Math.round((nz * invLen * 0.5 + 0.5) * 255);
        image.data[o + 3] = 255;
      }
    }
    ctx.putImageData(image, 0, 0);
    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.repeat.set(18, 22);
    texture.colorSpace = THREE.NoColorSpace;
    texture.needsUpdate = true;
    return texture;
  }

  createSatinRoughnessTexture() {
    const size = 128;
    const canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext("2d");
    const image = ctx.createImageData(size, size);
    const rng = this.seededRandom(997);
    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        const verticalSweep = 0.88 + Math.sin((x / size) * Math.PI * 2) * 0.05;
        const fine = (rng() - 0.5) * 0.025;
        const value = Math.round(clamp(verticalSweep + fine, 0.78, 0.96) * 255);
        const o = (y * size + x) * 4;
        image.data[o] = value;
        image.data[o + 1] = value;
        image.data[o + 2] = value;
        image.data[o + 3] = 255;
      }
    }
    ctx.putImageData(image, 0, 0);
    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.repeat.set(6, 10);
    texture.colorSpace = THREE.NoColorSpace;
    texture.needsUpdate = true;
    return texture;
  }

  createScreenSheenTexture() {
    const width = 256;
    const height = 512;
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, width, height);

    let gradient = ctx.createLinearGradient(0, height * 0.18, width, height * 0.82);
    gradient.addColorStop(0, "rgba(255,255,255,0)");
    gradient.addColorStop(0.42, "rgba(255,255,255,0.62)");
    gradient.addColorStop(0.5, "rgba(255,255,255,0.16)");
    gradient.addColorStop(0.68, "rgba(255,255,255,0)");
    ctx.fillStyle = gradient;
    ctx.beginPath();
    ctx.moveTo(width * -0.16, height * 0.18);
    ctx.lineTo(width * 0.42, height * 0.04);
    ctx.lineTo(width * 1.16, height * 0.72);
    ctx.lineTo(width * 0.55, height * 0.95);
    ctx.closePath();
    ctx.fill();

    gradient = ctx.createLinearGradient(0, 0, 0, height);
    gradient.addColorStop(0, "rgba(255,255,255,0.18)");
    gradient.addColorStop(0.22, "rgba(255,255,255,0)");
    gradient.addColorStop(0.82, "rgba(255,255,255,0)");
    gradient.addColorStop(1, "rgba(255,255,255,0.09)");
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, width, height);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.wrapS = THREE.ClampToEdgeWrapping;
    texture.wrapT = THREE.ClampToEdgeWrapping;
    texture.needsUpdate = true;
    return texture;
  }

  seededRandom(seed) {
    let value = seed;
    return () => {
      value = (value * 1664525 + 1013904223) % 4294967296;
      return value / 4294967296;
    };
  }

  updateScroll() {
    const forcedProgress = new URLSearchParams(window.location.search).get("progress");
    if (forcedProgress !== null) {
      this.progress = clamp(Number(forcedProgress) || 0);
      return;
    }

    const rect = this.getBoundingClientRect();
    const travel = Math.max(rect.height - window.innerHeight, 1);
    this.progress = clamp(-rect.top / travel);
  }

  resize() {
    const rect = this.stage.getBoundingClientRect();
    const width = Math.max(1, rect.width);
    const height = Math.max(1, rect.height);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
  }

  animate() {
    this.frame = requestAnimationFrame(() => this.animate());
    const dt = Math.min(this.clock.getDelta(), 0.05);
    const speed = this.reducedMotion.matches ? 1 : 1 - Math.pow(0.001, dt);
    this.smoothProgress = mix(this.smoothProgress, this.progress, speed);
    this.pointer.lerp(this.targetPointer, 0.08);
    this.applyTimeline(this.smoothProgress, dt);
    this.renderer.render(this.scene, this.camera);
  }

  applyTimeline(progress, dt) {
    const p = this.reducedMotion.matches ? 0.48 : progress;
    const pop = smoother(0.02, 0.24, p);
    const poseSettle = smoother(0.02, 0.34, p);
    const unfold = smoother(0.23, 0.44, p);
    const fold = this.exitMode === "screen-zoom" ? 0 : smoother(0.55, 0.72, p);
    const spin = smoother(0.72, 0.90, p);
    const vanish = smoother(0.80, 1.0, p);
    const screenFace = this.exitMode === "screen-zoom" ? smoother(0.56, 0.76, p) : 0;
    const screenZoom = this.exitMode === "screen-zoom" ? smoother(0.74, 1.0, p) : 0;
    const fogVanish = this.exitMode === "screen-zoom" ? 0 : vanish;
    const openScreenPresentation = this.exitMode === "screen-zoom"
      ? screenFace
      : smoother(0.39, 0.51, p) * (1 - smoother(0.56, 0.70, p));

    const closed = this.exitMode === "screen-zoom"
      ? mix(0.96, 0, unfold)
      : p < 0.53 ? mix(0.96, 0, unfold) : mix(0, 1.0, fold);
    this.currentClosed = closed;
    this.applyFold(closed);
    this.applyInnerVisibility(closed);

    const scaleOut = this.exitMode === "screen-zoom" ? mix(1, 1.76, screenZoom) : mix(1, 0.24, vanish);
    const scale = this.baseScale ? this.baseScale * mix(0.32, 1.08, pop) * scaleOut : 1;
    this.phoneGroup.scale.setScalar(scale);
    this.phoneGroup.position.z = mix(-2.1, 0.05, pop) + (this.exitMode === "screen-zoom" ? 0 : mix(0, -0.65, vanish));
    this.phoneGroup.position.y = mix(-0.04, 0.02, pop) + mix(0, 0.01, screenZoom);
    this.phoneGroup.position.x = mix(0, -0.02, screenZoom);

    const spinAmount = this.exitMode === "screen-zoom" ? 0 : spin;
    this.showGroup.rotation.x = mix(0.08, -0.04, poseSettle) + this.pointer.y * 0.035 * (1 - screenZoom) + spinAmount * Math.PI * 0.5;
    const presentationY = mix(1.15, 0.12, poseSettle) + this.pointer.x * 0.06 * (1 - screenZoom) + spinAmount * Math.PI * 5.1;
    this.showGroup.rotation.y = mix(presentationY, Math.PI + 0.08, openScreenPresentation);
    this.showGroup.rotation.z = mix(-0.08, 0.02, poseSettle) + spinAmount * Math.PI * 0.72;
    if (this.exitMode === "screen-zoom") {
      this.showGroup.rotation.x = mix(this.showGroup.rotation.x, -0.04, screenFace);
      this.showGroup.rotation.z = mix(this.showGroup.rotation.z, 0.02, screenFace);
    }

    this.camera.position.z = mix(9.5, 4.8, pop) + (this.exitMode === "screen-zoom" ? mix(0, -0.18, screenZoom) : 0);
    this.camera.position.x = mix(-0.28, 0.0, pop) + this.pointer.x * 0.08;
    this.camera.position.y = mix(0.18, 0.08, pop) - this.pointer.y * 0.04;
    if (this.exitMode === "screen-zoom") {
      this.camera.position.x = mix(this.camera.position.x, -0.02, screenZoom);
      this.camera.position.y = mix(this.camera.position.y, 0.0, screenZoom);
    }
    this.camera.lookAt(0, 0, 0);

    this.scene.fog.density = this.exitMode === "screen-zoom" ? mix(0.011, 0.004, screenZoom) : mix(0.011, 0.058, vanish);
    this.keyLight.intensity = this.exitMode === "screen-zoom" ? mix(1.34, 1.1, screenZoom) : mix(1.34, 0.75, vanish);
    this.rimLight.intensity = mix(1.06, 1.85, spin);
    if (this.cameraBumpStripLight) {
      this.cameraBumpStripLight.intensity = mix(3.2, 1.35, screenZoom);
    }
    if (this.edgeGrazingLight) {
      this.edgeGrazingLight.intensity = mix(3.25, 1.15, screenZoom);
    }
    if (this.backSilkLight) {
      this.backSilkLight.intensity = mix(3.1, 1.25, screenZoom);
    }
    if (this.lensGlintLight) {
      this.lensGlintLight.intensity = mix(4.4, 1.2, screenZoom);
    }
    const modelOpacity = this.exitMode === "screen-zoom" ? 1 : clamp(1 - vanish * 1.18);
    this.setModelOpacity(modelOpacity);
    this.applyScreenZoomVisibility(openScreenPresentation, modelOpacity);
    this.applyInnerPresentationAccents(openScreenPresentation, modelOpacity);
    if (this.phoneGroup) {
      this.phoneGroup.visible = modelOpacity > 0.02;
    }
    this.applyFogCloud(fogVanish, spin, dt);
    this.applyCssParallax(p, pop, this.exitMode === "screen-zoom" ? screenZoom : vanish);
  }

  applyFold(closed) {
    if (!this.leftPivot || !this.rightPivot) return;
    const angle = closed * (Math.PI / 2);
    const hingeReveal = smoother(0.1, 0.74, closed);
    const stackedHalfThickness = hingeReveal * 0.074;
    const hingeCenter = this.hingeCenter || new THREE.Vector3();
    this.leftPivot.position.set(hingeCenter.x, hingeCenter.y + stackedHalfThickness, hingeCenter.z);
    this.rightPivot.position.set(hingeCenter.x, hingeCenter.y - stackedHalfThickness, hingeCenter.z);
    this.leftPivot.rotation.z = angle;
    this.rightPivot.rotation.z = -angle;
    this.leftPivot.rotation.y = 0;
    this.rightPivot.rotation.y = 0;
    if (this.hingeGroup) {
      this.hingeGroup.visible = hingeReveal > 0.04;
      this.hingeGroup.position.copy(hingeCenter);
      this.hingeGroup.position.y += (hingeReveal - 1) * 0.018;
      this.hingeGroup.scale.setScalar(mix(0.58, 1, hingeReveal));
    }
  }

  applyInnerVisibility(closed) {
    const visible = closed < 0.78;
    this.innerOnlyMeshes.forEach((mesh) => {
      mesh.visible = visible;
    });
  }

  setModelOpacity(opacity) {
    this.meshMaterials.forEach((material) => {
      material.transparent = opacity < 0.995;
      material.opacity = opacity;
      material.depthWrite = opacity > 0.3;
    });
  }

  applyScreenZoomVisibility(screenFace, modelOpacity) {
    if (!this.screenZoomShellMeshes.length) return;
    const presentation = clamp(screenFace);
    const shellOpacity = modelOpacity * (1 - smoother(0.14, 0.88, presentation));

    this.screenZoomShellMeshes.forEach((mesh) => {
      mesh.visible = shellOpacity > 0.025;
      this.setMeshOpacity(mesh, shellOpacity);
    });

    this.screenZoomCoreMeshes.forEach((mesh) => {
      const keepInnerVisible = presentation > 0.04;
      if (keepInnerVisible || !this.isInnerOnlyMesh(mesh.name)) {
        mesh.visible = true;
      }
      this.setMeshOpacity(mesh, modelOpacity);
    });

    if (presentation <= 0.04) {
      this.applyInnerVisibility(this.currentClosed ?? 0);
    }
  }

  applyInnerPresentationAccents(screenFace, modelOpacity) {
    const presentation = clamp(screenFace);
    if (this.innerCreaseBridge) {
      this.innerCreaseBridge.visible = presentation > 0.05 && modelOpacity > 0.02;
    }
    this.innerPresentationMaterials.forEach((material) => {
      material.opacity = modelOpacity * smoother(0.12, 0.86, presentation);
      material.transparent = true;
      material.depthWrite = false;
    });
  }

  setMeshOpacity(mesh, opacity) {
    const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
    materials.forEach((material) => {
      if (!material) return;
      material.transparent = opacity < 0.995;
      material.opacity = opacity;
      material.depthWrite = opacity > 0.3;
    });
  }

  applyFogCloud(vanish, spin, dt) {
    if (!this.fogCloud) return;
    this.fogCloud.visible = vanish > 0.01;
    this.fogCloud.position.set(0, 0, mix(0.02, -0.18, vanish));
    this.fogCloud.scale.setScalar(mix(0.5, 2.9, vanish));
    this.fogCloud.rotation.y += dt * mix(0.12, 1.2, spin);
    this.fogSprites.forEach((sprite, index) => {
      const pulse = 0.75 + Math.sin(performance.now() * 0.0007 + index) * 0.25;
      sprite.position.addScaledVector(sprite.userData.drift, dt * vanish);
      sprite.material.opacity = vanish * mix(0.12, 0.44, pulse);
    });
  }

  applyCssParallax(progress, pop, vanish) {
    const washX = mix(-36, 28, pop) + this.pointer.x * 18;
    const washY = mix(28, -16, pop) + this.pointer.y * 10;
    this.stage.style.setProperty("--wash-x", `${washX.toFixed(2)}px`);
    this.stage.style.setProperty("--wash-y", `${washY.toFixed(2)}px`);
    this.stage.style.setProperty("--fog-x", `${mix(42, 50, vanish).toFixed(1)}%`);
    this.stage.style.setProperty("--haze-opacity", String(mix(0.18, 0.78, vanish)));
    const smoke = this.exitMode === "screen-zoom" ? 0 : smoother(0.12, 1, vanish);
    this.stage.style.setProperty("--smoke-opacity", String(smoke * 0.82));
    this.stage.style.setProperty("--smoke-scale", String(mix(0.35, 1.55, smoke)));
    this.stage.style.setProperty("--screen-zoom", this.exitMode === "screen-zoom" ? vanish.toFixed(4) : "0");
    if (this.exitMode === "screen-zoom") {
      const handoff = smoother(0.78, 1, vanish);
      this.stage.style.setProperty("--screen-fill-opacity", String(handoff));
      this.stage.style.setProperty("--screen-takeover-opacity", "0");
      this.stage.style.setProperty("--screen-scale", "1");
      this.stage.style.setProperty("--screen-radius", "0px");
      this.stage.style.setProperty("--screen-shift-x", "0px");
      this.stage.style.setProperty("--screen-shift-y", "0px");
      this.stage.style.setProperty("--screen-sheen", "0");
    } else {
      this.stage.style.setProperty("--screen-fill-opacity", "0");
      this.stage.style.setProperty("--screen-takeover-opacity", "0");
      this.stage.style.setProperty("--screen-scale", "0.42");
      this.stage.style.setProperty("--screen-radius", "34px");
      this.stage.style.setProperty("--screen-shift-x", "0px");
      this.stage.style.setProperty("--screen-shift-y", "0px");
      this.stage.style.setProperty("--screen-sheen", "0");
    }
    this.style.setProperty("--progress", progress.toFixed(4));
  }
}

customElements.define("pixel-fold-showtime", PixelFoldShowtime);
