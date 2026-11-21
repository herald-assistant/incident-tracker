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
  readonly task: string;
  readonly image?: string;
}

const FEATURES: readonly OverviewFeature[] = [
  {
    title: 'Incident Analysis',
    task: 'Ustal wpływ incydentu',
    route: '/incident-analysis',
    icon: 'troubleshoot',
    image: '/assets/brand/workspace-overview-incident-analysis.jpg'
  },
  {
    title: 'Flow Explorer',
    task: 'Zobacz, od czego zależy proces',
    route: '/flow-explorer',
    icon: 'account_tree',
    image: '/assets/brand/workspace-overview-flow-explorer.jpg'
  },
  {
    title: 'UI Explorer',
    task: 'Udokumentuj działanie ekranu',
    route: '/ui-explorer',
    icon: 'screen_search_desktop',
    image: '/assets/brand/workspace-overview-ui-explorer.jpg'
  },
  {
    title: 'UX Inspector',
    task: 'Wyjaśnij zachowanie interfejsu',
    route: '/ux-inspector',
    icon: 'ads_click',
    image: '/assets/brand/workspace-overview-ux-inspector.jpg'
  },
  {
    title: 'Change Verification',
    task: 'Zweryfikuj zmianę przed wydaniem',
    route: '/change-verification',
    icon: 'fact_check',
    image: '/assets/brand/workspace-overview-change-verification.jpg'
  },
  {
    title: 'Config Drift Viewer',
    task: 'Zrozum różnice między środowiskami',
    route: '/config-drift-viewer',
    icon: 'build_circle',
    image: '/assets/brand/workspace-overview-config-drift-viewer.jpg'
  },
  {
    title: 'Release summary',
    task: 'Zobacz, co wydanie zmienia w bazie danych',
    route: '/release-summary',
    icon: 'deployed_code',
    image: '/assets/brand/workspace-overview-release-summary.jpg'
  },
  {
    title: 'Behavior Change Assessment',
    task: 'Oceń złożoność zmian w produkcie',
    route: '/delivery-complexity-assessment',
    icon: 'domain_add',
    image: '/assets/brand/workspace-overview-behavior-change-assessment.jpg'
  },
  {
    title: 'Technical Change Assessment',
    task: 'Oceń złożoność zmian w systemie',
    route: '/delivery-scope-complexity',
    icon: 'engineering',
    image: '/assets/brand/workspace-overview-technical-change-assessment.jpg'
  },
  {
    title: 'Delivery Trends',
    task: 'Porównaj złożoność pracy w czasie',
    route: '/delivery-complexity-trends',
    icon: 'finance',
    image: '/assets/brand/workspace-overview-delivery-trends.jpg'
  },
  { title: 'System Assistant', task: 'Zapytaj o dane i wykonaj zadanie w systemie', route: '/system-assistant', icon: 'forum' }
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

const STORY_SCENES = [
  { id: 'tools', duration: 4200, lines: ['Wszystkie Twoje aplikacje.', 'Wreszcie grają razem.'] },
  { id: 'features', duration: 4800, lines: ['Rozwiązuj codzienne problemy.', 'Od incydentu po wdrożenie.'] },
  {
    id: 'guidance',
    duration: 5000,
    lines: ['Wbudowana wiedza ekspercka.', 'Działasz efektywnie od pierwszego dnia.']
  },
  { id: 'closing', duration: 3600, lines: ['Czas na Twój ruch.', 'Wybierz pierwsze zadanie.'] }
] as const;
const STORY_CAPTION_HOLD_MS = 1400;

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
  protected readonly storyFeatures = FEATURES.slice(0, 6);
  readonly toolIcons = TOOL_ICONS;
  protected readonly toolOrbitResultIndex = 2;
  protected readonly storyScenes = STORY_SCENES.map(({ lines, duration, ...scene }) => ({
    ...scene,
    duration: duration + STORY_CAPTION_HOLD_MS,
    motionShare: duration / (duration + STORY_CAPTION_HOLD_MS),
    label: lines.join(' '),
    lines: lines.map(line => Array.from(line))
  }));
  protected readonly storyIndex = signal(0);
  protected readonly storyPlaying = signal(false);
  protected readonly storyFinished = signal(false);
  protected readonly canScrollBack = signal(false);
  protected readonly canScrollForward = signal(true);

  private readonly destroyRef = inject(DestroyRef);
  private readonly featureList = viewChild.required<ElementRef<HTMLUListElement>>('featureList');
  private readonly featureSlides = viewChildren<ElementRef<HTMLLIElement>>('featureSlide');
  private readonly benefitsStory = viewChild.required<ElementRef<HTMLElement>>('benefitsStory');
  private storyInView = false;
  private storyElapsed = 0;
  private storyFrame: number | undefined;
  private storyFrameAt: number | undefined;

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
    afterNextRender(() => this.connectStoryPlayback());
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

  protected selectStoryScene(index: number): void {
    if (index !== this.storyIndex()) {
      this.resetStoryScene(index);
    }
  }

  protected toggleStoryPlayback(): void {
    if (this.storyFinished()) {
      this.resetStoryScene(0);
    }
    this.storyPlaying.update(playing => !playing);
    this.syncStoryPlayback();
  }

  private connectStoryPlayback(): void {
    const story = this.benefitsStory().nativeElement;
    const motion = window.matchMedia?.('(prefers-reduced-motion: reduce)');
    const visibility = new AbortController();
    const { signal } = visibility;
    const sync = () => this.syncStoryPlayback();
    const observer = typeof IntersectionObserver === 'function'
      ? new IntersectionObserver(([entry]) => {
          this.storyInView = entry.isIntersecting && entry.intersectionRatio >= 0.4;
          sync();
        }, { threshold: 0.4 })
      : undefined;

    this.storyPlaying.set(!motion?.matches);
    this.storyInView = !observer;
    observer?.observe(story);
    document.addEventListener('visibilitychange', sync, { signal });
    motion?.addEventListener('change', () => {
      if (motion.matches) {
        this.storyPlaying.set(false);
        sync();
      }
    }, { signal });
    sync();

    this.destroyRef.onDestroy(() => {
      visibility.abort();
      observer?.disconnect();
      this.stopStoryFrame();
    });
  }

  private resetStoryScene(index: number): void {
    this.stopStoryFrame();
    this.storyElapsed = 0;
    this.storyIndex.set(index);
    this.storyFinished.set(false);
    const story = this.benefitsStory().nativeElement;
    story.removeAttribute('data-story-frame');
    story.style.removeProperty('--story-playhead');
    this.syncStoryPlayback();
  }

  private syncStoryPlayback(): void {
    if (!this.storyPlaying() || !this.storyInView || document.visibilityState === 'hidden') {
      this.stopStoryFrame();
      return;
    }
    if (this.storyFrame === undefined) {
      const story = this.benefitsStory().nativeElement;
      story.setAttribute('data-story-frame', '');
      story.style.setProperty('--story-playhead', this.storyProgress().toFixed(4));
      this.storyFrame = window.requestAnimationFrame(this.paintStoryFrame);
    }
  }

  private storyProgress(): number {
    return Math.min(1, this.storyElapsed / this.storyScenes[this.storyIndex()].duration);
  }

  private readonly paintStoryFrame = (now: number): void => {
    this.storyFrame = undefined;
    this.storyElapsed += now - (this.storyFrameAt ?? now);
    this.storyFrameAt = now;
    const progress = this.storyProgress();
    this.benefitsStory().nativeElement.style.setProperty('--story-playhead', progress.toFixed(4));
    if (progress < 1) {
      this.storyFrame = window.requestAnimationFrame(this.paintStoryFrame);
    } else if (this.storyIndex() + 1 < this.storyScenes.length) {
      this.resetStoryScene(this.storyIndex() + 1);
    } else {
      this.storyFinished.set(true);
      this.storyPlaying.set(false);
      this.stopStoryFrame();
    }
  };

  private stopStoryFrame(): void {
    if (this.storyFrame !== undefined) {
      window.cancelAnimationFrame(this.storyFrame);
      this.storyFrame = undefined;
    }
    this.storyFrameAt = undefined;
  }
}
