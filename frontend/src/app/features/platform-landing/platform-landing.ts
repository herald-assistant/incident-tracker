import {
  afterNextRender,
  afterRenderEffect,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
  viewChildren
} from '@angular/core';
import { AppUiConfigService } from '../../core/services/app-ui-config.service';
import { RouterLink } from '@angular/router';
import { SpecialThemeBannerComponent } from '../../special-themes/special-theme-banner';
import { SpecialThemeService } from '../../special-themes/special-theme.service';

interface OverviewFeature {
  readonly title: string;
  readonly route: string;
  readonly icon: string;
  readonly description: string;
  readonly image?: string;
}

const FEATURES: readonly OverviewFeature[] = [
  {
    title: 'Incident Analysis',
    description: 'Ustal wpływ incydentu',
    route: '/incident-analysis',
    icon: 'troubleshoot',
    image: '/assets/brand/workspace-overview-incident-analysis.jpg'
  },
  {
    title: 'Flow Explorer',
    description: 'Zobacz, od czego zależy proces',
    route: '/flow-explorer',
    icon: 'account_tree',
    image: '/assets/brand/workspace-overview-flow-explorer.jpg'
  },
  {
    title: 'UI Explorer',
    description: 'Udokumentuj działanie ekranu',
    route: '/ui-explorer',
    icon: 'screen_search_desktop',
    image: '/assets/brand/workspace-overview-ui-explorer.jpg'
  },
  {
    title: 'UX Inspector',
    description: 'Wyjaśnij zachowanie interfejsu',
    route: '/ux-inspector',
    icon: 'ads_click',
    image: '/assets/brand/workspace-overview-ux-inspector.jpg'
  },
  {
    title: 'Change Verification',
    description: 'Zweryfikuj zmianę przed wydaniem',
    route: '/change-verification',
    icon: 'fact_check',
    image: '/assets/brand/workspace-overview-change-verification.jpg'
  },
  {
    title: 'Config Drift Viewer',
    description: 'Zrozum różnice między środowiskami',
    route: '/config-drift-viewer',
    icon: 'build_circle',
    image: '/assets/brand/workspace-overview-config-drift-viewer.jpg'
  },
  {
    title: 'Release summary',
    description: 'Zobacz, co wydanie zmienia w bazie danych',
    route: '/release-summary',
    icon: 'deployed_code',
    image: '/assets/brand/workspace-overview-release-summary.jpg'
  },
  {
    title: 'Behavior Change Assessment',
    description: 'Oceń złożoność zmian w produkcie',
    route: '/delivery-complexity-assessment',
    icon: 'domain_add',
    image: '/assets/brand/workspace-overview-behavior-change-assessment.jpg'
  },
  {
    title: 'Technical Change Assessment',
    description: 'Oceń złożoność zmian w systemie',
    route: '/delivery-scope-complexity',
    icon: 'engineering',
    image: '/assets/brand/workspace-overview-technical-change-assessment.jpg'
  },
  {
    title: 'Delivery Trends',
    description: 'Porównaj złożoność pracy w czasie',
    route: '/delivery-complexity-trends',
    icon: 'finance',
    image: '/assets/brand/workspace-overview-delivery-trends.jpg'
  }
];

const TOOL_ICONS = [
  'openshift-icon.svg',
  'gitlab-icon.svg',
  'jenkins-icon.svg',
  'angular-icon.svg',
  'spring-boot-icon.svg',
  'oracle-icon.svg',
  'dynatrace-icon.svg',
  'elastic-kibana-icon.svg',
  'atlassian-confluence-icon.svg',
  'atlassian-jira-icon.svg'
] as const;

const SLOGANS = [
  { scene: 'tools', lines: ['Twoje narzędzia.', 'Jedno miejsce.'] },
  { scene: 'features', lines: ['AI dopasowane do zadania.', 'Od incydentu po wydanie.'] },
  {
    scene: 'guidance',
    lines: ['Wystarczy wiedzieć, co chcesz zrobić.', 'Workspace prowadzi Cię do wyniku.']
  }
] as const;

const SCROLL_SCENES = [
  { property: '--story-tool-progress', duration: 4200 },
  { property: '--story-feature-progress', duration: 4500 },
  { property: '--story-guidance-progress', duration: 5000 }
] as const;

const WHEEL_GESTURE_GAP_MS = 160;

@Component({
  selector: 'app-platform-landing-page',
  imports: [RouterLink, SpecialThemeBannerComponent],
  templateUrl: './platform-landing.html',
  styleUrl: './platform-landing.scss'
})
export class PlatformLandingPageComponent {
  readonly uiConfig = inject(AppUiConfigService);
  readonly activeTheme = inject(SpecialThemeService).activeTheme;
  readonly features = FEATURES;
  protected readonly orbitFeatures = FEATURES.filter(feature => feature.route !== '/release-summary');
  readonly toolIcons = TOOL_ICONS;
  protected readonly slogans = SLOGANS.map(({ scene, lines }) => ({
    scene,
    label: lines.join(' '),
    lines: lines.map(line => Array.from(line))
  }));
  protected readonly canScrollBack = signal(false);
  protected readonly canScrollForward = signal(true);

  private readonly destroyRef = inject(DestroyRef);
  private readonly featureList = viewChild.required<ElementRef<HTMLUListElement>>('featureList');
  private readonly featureSlides = viewChildren<ElementRef<HTMLLIElement>>('featureSlide');
  private readonly benefitsStory = viewChild.required<ElementRef<HTMLElement>>('benefitsStory');
  private readonly benefitsTrack = viewChild.required<ElementRef<HTMLElement>>('benefitsTrack');
  private readonly benefitsStage = viewChild.required<ElementRef<HTMLElement>>('benefitsStage');

  constructor() {
    this.uiConfig.load();
    afterRenderEffect({
      read: (onCleanup) => {
        const firstSlide = this.featureSlides()[0]?.nativeElement;
        this.updateFeatureCarousel();
        if (typeof ResizeObserver !== 'function') {
          return;
        }

        const observer = new ResizeObserver(() => this.updateFeatureCarousel());
        observer.observe(this.featureList().nativeElement);
        if (firstSlide) {
          observer.observe(firstSlide);
        }
        onCleanup(() => observer.disconnect());
      }
    });
    afterNextRender(() => this.connectScrollMotion());
  }

  protected updateFeatureCarousel(): void {
    const { scrollLeft, scrollWidth, clientWidth } = this.featureList().nativeElement;
    this.canScrollBack.set(scrollLeft > 1);
    this.canScrollForward.set(scrollLeft < scrollWidth - clientWidth - 1);
  }

  protected scrollFeatureCarousel(direction: -1 | 1): void {
    const [first, second] = this.featureSlides();
    if (!first || !second) {
      return;
    }

    const list = this.featureList().nativeElement;
    const step = second.nativeElement.getBoundingClientRect().left -
      first.nativeElement.getBoundingClientRect().left;
    const nextIndex = direction > 0
      ? Math.floor((list.scrollLeft + 1) / step) + 1
      : Math.ceil((list.scrollLeft - 1) / step) - 1;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    list.scrollTo({ left: Math.max(0, nextIndex * step), behavior: reducedMotion ? 'instant' : 'smooth' });
  }

  private connectScrollMotion(): void {
    if (typeof window.matchMedia !== 'function') {
      return;
    }

    const story = this.benefitsStory().nativeElement;
    const track = this.benefitsTrack().nativeElement;
    const stage = this.benefitsStage().nativeElement;
    const motion = window.matchMedia('(prefers-reduced-motion: no-preference) and (min-height: 680px)');
    const listeners = new AbortController();
    const { signal } = listeners;
    let frame: number | undefined;
    let nextScene = 0;
    let lastScrollY = window.scrollY;
    let lastWheelAt = -Infinity;
    let touchStart: { x: number; y: number; consumed: boolean } | undefined;

    const stickyTop = () => Number.parseFloat(window.getComputedStyle(stage).top);

    const isAtStage = () => {
      const bounds = track.getBoundingClientRect();
      const top = stickyTop();
      return bounds.top <= top + 2 && bounds.bottom >= top + stage.offsetHeight - 2;
    };

    const keepStageInPlace = () => {
      const target = window.scrollY + track.getBoundingClientRect().top - stickyTop();
      const maxScroll = document.documentElement.scrollHeight - window.innerHeight;
      const top = Math.max(0, Math.min(target, maxScroll));
      if (Math.abs(window.scrollY - top) > 1) {
        window.scrollTo({ top, behavior: 'instant' });
      }
    };

    const startNextScene = () => {
      const scene = SCROLL_SCENES[nextScene];
      if (!motion.matches || frame !== undefined || !scene || !isAtStage()) {
        return false;
      }

      let startedAt: number | undefined;
      const draw = (now: number) => {
        startedAt ??= now;
        const progress = Math.min(1, (now - startedAt) / scene.duration);
        story.style.setProperty(scene.property, progress.toFixed(4));
        if (progress === 1) {
          nextScene++;
          frame = undefined;
          return;
        }
        frame = window.requestAnimationFrame(draw);
      };

      if (touchStart) {
        touchStart.consumed = true;
      }
      frame = window.requestAnimationFrame(draw);
      keepStageInPlace();
      return true;
    };

    const reset = () => {
      if (frame !== undefined) {
        window.cancelAnimationFrame(frame);
        frame = undefined;
      }
      nextScene = 0;
      for (const { property } of SCROLL_SCENES) {
        story.style.removeProperty(property);
      }
    };

    const onScroll = () => {
      const scrollY = window.scrollY;
      const down = scrollY > lastScrollY;
      lastScrollY = scrollY;
      if (!motion.matches) {
        return;
      }
      if (frame !== undefined) {
        keepStageInPlace();
        return;
      }
      if (nextScene > 0 && track.getBoundingClientRect().top >= window.innerHeight) {
        reset();
      }
      if (nextScene === 0 && down) {
        startNextScene();
      }
    };

    const onResize = () => {
      if (motion.matches && frame !== undefined) {
        keepStageInPlace();
      }
    };

    const onWheel = (event: WheelEvent) => {
      if (!motion.matches || event.ctrlKey || Math.abs(event.deltaY) <= Math.abs(event.deltaX)) {
        return;
      }

      // Momentum and wheel events received during playback do not queue another scene.
      const now = performance.now();
      const newGesture = now - lastWheelAt >= WHEEL_GESTURE_GAP_MS;
      lastWheelAt = now;
      if (frame !== undefined || (event.deltaY > 0 && newGesture && startNextScene())) {
        event.preventDefault();
      }
    };

    const onKeyDown = (event: KeyboardEvent) => {
      if (!motion.matches || event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) {
        return;
      }
      const target = event.target instanceof Element ? event.target : undefined;
      const space = event.key === ' ';
      if (target?.closest('input, textarea, select, [contenteditable]') ||
          (space && target?.closest('button, [role="button"]'))) {
        return;
      }

      const down = ['ArrowDown', 'PageDown', 'End'].includes(event.key) || (space && !event.shiftKey);
      const up = ['ArrowUp', 'PageUp', 'Home'].includes(event.key) || (space && event.shiftKey);
      if ((down || up) && (frame !== undefined || (down && !event.repeat && startNextScene()))) {
        event.preventDefault();
      }
    };

    const onTouchStart = (event: TouchEvent) => {
      const touch = event.touches.length === 1 ? event.touches[0] : undefined;
      touchStart = touch && { x: touch.clientX, y: touch.clientY, consumed: frame !== undefined };
    };

    const onTouchMove = (event: TouchEvent) => {
      if (!motion.matches || !touchStart || event.touches.length !== 1) {
        return;
      }
      const touch = event.touches[0];
      const distance = touchStart.y - touch.clientY;
      const swipingDown = distance >= 8 && distance > Math.abs(touch.clientX - touchStart.x);
      if (frame !== undefined || touchStart.consumed || (swipingDown && startNextScene())) {
        touchStart.consumed = true;
        event.preventDefault();
      }
    };

    const syncMotion = () => {
      story.toggleAttribute('data-scroll-motion', motion.matches);
      lastScrollY = window.scrollY;
      if (!motion.matches) {
        reset();
      }
    };

    window.addEventListener('scroll', onScroll, { passive: true, signal });
    window.addEventListener('resize', onResize, { signal });
    window.addEventListener('wheel', onWheel, { passive: false, signal });
    window.addEventListener('keydown', onKeyDown, { signal });
    window.addEventListener('touchstart', onTouchStart, { passive: true, signal });
    window.addEventListener('touchmove', onTouchMove, { passive: false, signal });
    motion.addEventListener('change', syncMotion, { signal });
    syncMotion();

    this.destroyRef.onDestroy(() => {
      listeners.abort();
      reset();
    });
  }
}
