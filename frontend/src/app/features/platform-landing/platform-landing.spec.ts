import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PlatformLandingPageComponent } from './platform-landing';

describe('PlatformLandingPageComponent', () => {
  it('lists the available feature cards in reading order below the hero', async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformLandingPageComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    const fixture = TestBed.createComponent(PlatformLandingPageComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const featureLinks = Array.from(
      compiled.querySelectorAll<HTMLAnchorElement>('.platform-landing__feature-card')
    );

    expect(compiled.querySelector('.platform-landing__actions')).toBeNull();
    expect(compiled.querySelectorAll('.platform-landing__intro a')).toHaveLength(0);
    expect(compiled.querySelector('#platformLandingFeatures')).not.toBeNull();
    expect(featureLinks.map((link) => link.getAttribute('href'))).toEqual([
      '/incident-analysis',
      '/flow-explorer',
      '/ui-explorer',
      '/ux-inspector',
      '/change-verification',
      '/config-drift-viewer',
      '/delivery-complexity-assessment',
      '/delivery-scope-complexity',
      '/delivery-complexity-trends'
    ]);
    for (const link of featureLinks) {
      expect(link.querySelector('h4')?.textContent?.trim()).toBeTruthy();
      expect(link.querySelector('p')?.textContent?.trim()).toBeTruthy();
      const visual = link.querySelector<HTMLElement>('.platform-landing__feature-visual');
      expect(visual?.style.backgroundImage || visual?.textContent?.trim()).toBeTruthy();
    }
    expect(compiled.textContent).not.toContain('Data Diagnostics');
  });

  it('renders one animation and caption per card', async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformLandingPageComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    const fixture = TestBed.createComponent(PlatformLandingPageComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const story = compiled.querySelector('.platform-landing__capabilities')!;
    const orbit = story.querySelector('.platform-landing__orbit')!;
    expect(story.querySelectorAll('.platform-landing__story-card')).toHaveLength(4);
    expect(Array.from(story.querySelectorAll('h3'), heading => heading.getAttribute('aria-label'))).toEqual([
      'Wszystkie Twoje aplikacje. Wreszcie grają razem.',
      'Rozwiązuje Twoje problemy. Od incydentu po wdrożenie.',
      'Twój osobisty ekspert. Od pierwszego dnia.',
      'Od czego zaczynamy? Wybierz zadanie w Workspace.'
    ]);
    expect(Array.from(story.querySelectorAll('.platform-landing__slogan-line'),
      line => line.textContent.trim()
    )).toEqual([
      'Wszystkie Twoje aplikacje.',
      'Wreszcie grają razem.',
      'Rozwiązuje Twoje problemy.',
      'Od incydentu po wdrożenie.',
      'Twój osobisty ekspert.',
      'Od pierwszego dnia.',
      'Od czego zaczynamy?',
      'Wybierz zadanie w Workspace.'
    ]);
    expect(orbit.getAttribute('aria-hidden')).toBe('true');
    expect(orbit.querySelectorAll('.platform-landing__orbit-tool--result')).toHaveLength(1);
    expect(Array.from(story.querySelectorAll('[data-scene="closing"] .platform-landing__slogan-phrase'),
      phrase => phrase.textContent.trim()
    )).toEqual(['Od czego zaczynamy?', 'Wybierz zadanie w Workspace.']);
    const guidanceCard = story.querySelector(
      '.platform-landing__orbit-result > .platform-landing__guidance-card'
    )!;
    expect(Array.from(guidanceCard.querySelectorAll('.platform-landing__guidance-step span'),
      icon => icon.textContent.trim()
    )).toEqual(['search', 'construction', 'auto_awesome']);
    expect(guidanceCard.querySelector('.platform-landing__guidance-document-result')?.textContent.trim())
      .toBe('markdown_document');
    expect(guidanceCard.querySelector('.platform-landing__guidance-action')).not.toBeNull();
    const carousel = story.querySelector('.platform-landing__story-carousel')!;
    expect(carousel.querySelectorAll('.platform-landing__story-feature')).toHaveLength(6);
    expect(carousel.querySelectorAll('.platform-landing__story-feature-flip img')).toHaveLength(6);
    expect(Array.from(carousel.querySelectorAll('.platform-landing__story-feature-face--details .material-symbols-outlined'),
      icon => icon.textContent.trim()
    )).toEqual([
      'troubleshoot',
      'account_tree',
      'screen_search_desktop',
      'ads_click',
      'fact_check',
      'build_circle'
    ]);
    expect(Array.from(carousel.querySelectorAll('.platform-landing__story-feature-name'),
      name => name.textContent.trim()
    )).toEqual([
      'Incident Analysis',
      'Flow Explorer',
      'UI Explorer',
      'UX Inspector',
      'Change Verification',
      'Config Drift Viewer'
    ]);
  });

  it('pauses the timeline, advances between cards and can replay after the finale', async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformLandingPageComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    let setVisibility!: (visible: boolean) => void;
    vi.stubGlobal('IntersectionObserver', class {
      constructor(notify: IntersectionObserverCallback) {
        setVisibility = visible => notify([{
          isIntersecting: visible,
          intersectionRatio: visible ? 1 : 0
        } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
      }
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
    });
    vi.stubGlobal('matchMedia', () => Object.assign(new EventTarget(), { matches: false }));
    vi.useFakeTimers({ toFake: ['performance', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    const fixture = TestBed.createComponent(PlatformLandingPageComponent);

    try {
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const story = compiled.querySelector<HTMLElement>('.platform-landing__capabilities')!;
      const buttons = story.querySelectorAll<HTMLButtonElement>('.platform-landing__story-indicator');
      const playback = story.querySelector<HTMLButtonElement>('.platform-landing__story-play')!;
      const progress = () => story.style.getPropertyValue('--story-playhead');
      const current = () => story.querySelector('.platform-landing__story-card[data-active]')?.id;
      const click = (button: HTMLButtonElement) => {
        button.click();
        fixture.detectChanges();
      };

      vi.advanceTimersByTime(500);
      expect(progress()).toBe('');
      setVisibility(true);
      vi.advanceTimersByTime(1000);
      expect(Number(progress())).toBeGreaterThan(0.15);
      const initialProgress = progress();

      const wheel = new WheelEvent('wheel', { deltaY: 100, cancelable: true });
      window.dispatchEvent(wheel);
      expect(wheel.defaultPrevented).toBe(false);

      click(playback);
      vi.advanceTimersByTime(8000);
      expect(progress()).toBe(initialProgress);
      expect(current()).toBe('workspace-scene-tools');

      click(playback);
      vi.advanceTimersByTime(500);
      expect(Number(progress())).toBeGreaterThan(Number(initialProgress));
      setVisibility(false);
      const offscreenProgress = progress();
      vi.advanceTimersByTime(8000);
      expect(progress()).toBe(offscreenProgress);

      setVisibility(true);
      vi.advanceTimersByTime(4300);
      fixture.detectChanges();
      expect(current()).toBe('workspace-scene-features');
      expect(buttons[1].getAttribute('aria-current')).toBe('true');

      vi.advanceTimersByTime(6400);
      fixture.detectChanges();
      expect(current()).toBe('workspace-scene-guidance');

      click(buttons[3]);
      expect(current()).toBe('workspace-scene-closing');
      expect(progress()).toBe('0.0000');
      vi.advanceTimersByTime(5100);
      fixture.detectChanges();
      expect(progress()).toBe('1.0000');
      expect(playback.getAttribute('aria-label')).toBe('Odtwórz animacje od początku');

      click(playback);
      expect(current()).toBe('workspace-scene-tools');
      expect(progress()).toBe('0.0000');
    } finally {
      fixture.destroy();
      vi.useRealTimers();
      vi.unstubAllGlobals();
    }
  });
});
