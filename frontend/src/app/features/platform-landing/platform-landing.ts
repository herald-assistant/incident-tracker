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
  readonly toolIcons = TOOL_ICONS;
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
    let frame: number | undefined;
    let lastOrbitProgress = '';
    let lastCopyProgress = '';

    const draw = () => {
      frame = undefined;
      const bounds = track.getBoundingClientRect();
      const stageStyle = window.getComputedStyle(stage);
      const stickyTop = Number.parseFloat(stageStyle.top);
      const revealDistance = Number.parseFloat(stageStyle.getPropertyValue('--story-reveal-distance'));
      const travel = Math.max(1, bounds.height - stage.getBoundingClientRect().height);
      const travelled = stickyTop - bounds.top;
      const orbitProgress = scrollProgress(travelled / travel);
      const copyProgress = scrollProgress((travelled - travel) / revealDistance);

      if (orbitProgress !== lastOrbitProgress) {
        story.style.setProperty('--story-progress', orbitProgress);
        lastOrbitProgress = orbitProgress;
      }
      if (copyProgress !== lastCopyProgress) {
        story.style.setProperty('--story-copy-progress', copyProgress);
        lastCopyProgress = copyProgress;
      }
    };

    const requestDraw = () => {
      if (motion.matches && frame === undefined) {
        frame = window.requestAnimationFrame(draw);
      }
    };

    const syncMotion = () => {
      story.toggleAttribute('data-scroll-motion', motion.matches);
      if (motion.matches) {
        requestDraw();
      } else if (frame !== undefined) {
        window.cancelAnimationFrame(frame);
        frame = undefined;
      }
    };

    window.addEventListener('scroll', requestDraw, { passive: true });
    window.addEventListener('resize', requestDraw);
    motion.addEventListener('change', syncMotion);
    syncMotion();

    this.destroyRef.onDestroy(() => {
      window.removeEventListener('scroll', requestDraw);
      window.removeEventListener('resize', requestDraw);
      motion.removeEventListener('change', syncMotion);
      if (frame !== undefined) {
        window.cancelAnimationFrame(frame);
      }
    });
  }
}

function scrollProgress(fraction: number): string {
  return Math.max(0, Math.min(1, fraction)).toFixed(4);
}
