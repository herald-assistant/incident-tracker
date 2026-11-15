import { afterNextRender, Component, DestroyRef, ElementRef, inject, viewChild } from '@angular/core';
import { AppUiConfigService } from '../../core/services/app-ui-config.service';
import { RouterLink } from '@angular/router';
import { SpecialThemeBannerComponent } from '../../special-themes/special-theme-banner';
import { SpecialThemeService } from '../../special-themes/special-theme.service';

const TOOL_GROUPS = [
  {
    id: 'verification',
    title: 'Weryfikacja i wydania',
    tools: [
      { title: 'Change Verification', route: '/change-verification' },
      { title: 'Config Drift Viewer', route: '/config-drift-viewer' },
      { title: 'Release summary', route: '/release-summary' }
    ]
  },
  {
    id: 'analysis',
    title: 'Analiza i dokumentacja',
    tools: [
      { title: 'Incident Analysis', route: '/incident-analysis' },
      { title: 'Flow Explorer', route: '/flow-explorer' },
      { title: 'UI Explorer', route: '/ui-explorer' },
      { title: 'UX Inspector', route: '/ux-inspector' }
    ]
  },
  {
    id: 'assessment',
    title: 'Ocena i trendy',
    tools: [
      { title: 'Behavior Change Assessment', route: '/delivery-complexity-assessment' },
      { title: 'Technical Change Assessment', route: '/delivery-scope-complexity' },
      { title: 'Delivery Trends', route: '/delivery-complexity-trends' }
    ]
  }
] as const;

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
  readonly toolGroups = TOOL_GROUPS;
  readonly toolIcons = TOOL_ICONS;

  private readonly destroyRef = inject(DestroyRef);
  private readonly benefitsStory = viewChild.required<ElementRef<HTMLElement>>('benefitsStory');
  private readonly benefitsTrack = viewChild.required<ElementRef<HTMLElement>>('benefitsTrack');
  private readonly benefitsStage = viewChild.required<ElementRef<HTMLElement>>('benefitsStage');

  constructor() {
    this.uiConfig.load();
    afterNextRender(() => this.connectScrollMotion());
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
