import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PlatformLandingPageComponent } from './platform-landing';

describe('PlatformLandingPageComponent', () => {
  it('presents the hero and animations before the available feature cards', async () => {
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
    expect(compiled.querySelector('.platform-landing__intro .section-eyebrow')).toBeNull();
    expect(Array.from(compiled.querySelectorAll('#platformLandingTitle > span'),
      span => span.textContent?.trim()
    )).toEqual(['Twój osobisty', 'zespół ekspertów.']);
    const sections = compiled.querySelectorAll(
      '.platform-landing__intro, .platform-landing__capabilities, #platformLandingFeatures'
    );
    expect(Array.from(sections, section => section.className)).toEqual([
      'platform-landing__intro',
      'platform-landing__capabilities',
      'platform-landing__features'
    ]);
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
    expect(featureLinks[0].querySelector('h4')?.textContent?.trim()).toBe('Ustal wpływ incydentu');
    expect(featureLinks[0].querySelector('p')?.textContent?.trim()).toBe('Incident Analysis');
    for (const link of featureLinks) {
      const task = link.querySelector('h4');
      const name = link.querySelector('p');
      expect(task?.textContent?.trim()).toBeTruthy();
      expect(name?.textContent?.trim()).toBeTruthy();
      expect(link.getAttribute('aria-labelledby')).toBe(task?.id);
      expect(link.getAttribute('aria-describedby')).toBe(name?.id);
      const visual = link.querySelector<HTMLElement>('.platform-landing__feature-visual');
      expect(visual?.style.backgroundImage || visual?.textContent?.trim()).toBeTruthy();
    }
    expect(compiled.textContent).not.toContain('Data Diagnostics');
  });

  it('provides the complete conversation to screen readers without duplicating the animated transcript', async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformLandingPageComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    const fixture = TestBed.createComponent(PlatformLandingPageComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const chat = compiled.querySelector('[role="group"][aria-label="Przykładowa rozmowa"]')!;
    const transcript = chat.querySelectorAll('.platform-landing__accessible-description p');
    expect(transcript[0].textContent?.trim()).toBe('Ty: W czym możesz mi pomóc?');
    expect(transcript[1].textContent?.trim()).toBe(
      'Team Delivery Workspace: Witaj! 👋 Odkryję zależności w Twoim systemie. '
      + 'Opiszę działanie funkcji biznesowych i technicznych. Wyjaśnię problemy w działaniu systemu. '
      + 'Zweryfikuję zmianę przed wdrożeniem. Pomogę udokumentować system. Od czego zaczynamy? 🚀'
    );
    expect(chat.querySelector('.platform-landing__intro-thread')?.getAttribute('aria-hidden')).toBe('true');
    expect(chat.querySelector('.platform-landing__intro-user .chat-message__text')?.textContent?.trim())
      .toBe('W czym możesz mi pomóc?');
    const visibleLines = Array.from(chat.querySelectorAll('.platform-landing__intro-reply-line'),
      line => Array.from(line.querySelectorAll('.platform-landing__word'),
        word => word.textContent?.trim()
      ).join(' ')
    );
    expect(visibleLines.join(' ')).toBe(transcript[1].textContent?.trim().replace('Team Delivery Workspace: ', ''));
  });

  it('plays the conversation only while visible and releases its observers on destroy', async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformLandingPageComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    const visibilityCallbacks = new Map<Element, (visible: boolean) => void>();
    const disconnects: ReturnType<typeof vi.fn>[] = [];
    vi.stubGlobal('IntersectionObserver', class {
      readonly disconnect = vi.fn();
      constructor(private readonly notify: IntersectionObserverCallback) {
        disconnects.push(this.disconnect);
      }
      observe(target: Element): void {
        visibilityCallbacks.set(target, visible => this.notify([{
          target, isIntersecting: visible, intersectionRatio: visible ? 1 : 0
        } as IntersectionObserverEntry], this as unknown as IntersectionObserver));
      }
      unobserve(): void {}
    });
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    const fixture = TestBed.createComponent(PlatformLandingPageComponent);

    try {
      fixture.detectChanges();
      const intro = (fixture.nativeElement as HTMLElement).querySelector('.platform-landing__intro')!;
      expect(intro.hasAttribute('data-intro-playing')).toBe(false);
      visibilityCallbacks.get(intro)!(true);
      expect(intro.hasAttribute('data-intro-playing')).toBe(true);

      visibility.mockReturnValue('hidden');
      document.dispatchEvent(new Event('visibilitychange'));
      expect(intro.hasAttribute('data-intro-playing')).toBe(false);
      visibility.mockReturnValue('visible');
      document.dispatchEvent(new Event('visibilitychange'));
      expect(intro.hasAttribute('data-intro-playing')).toBe(true);
      visibilityCallbacks.get(intro)!(false);
      expect(intro.hasAttribute('data-intro-playing')).toBe(false);
    } finally {
      fixture.destroy();
      visibility.mockRestore();
      vi.unstubAllGlobals();
    }
    expect(disconnects.length).toBeGreaterThan(0);
    for (const disconnect of disconnects) {
      expect(disconnect).toHaveBeenCalledOnce();
    }
  });

  it.each([false, true])('scrolls to each selected section and moves focus (reduced motion: %s)', async reducedMotion => {
    await TestBed.configureTestingModule({
      imports: [PlatformLandingPageComponent],
      providers: [provideRouter([])]
    }).compileComponents();
    vi.stubGlobal('matchMedia', () => Object.assign(new EventTarget(), { matches: reducedMotion }));
    const fixture = TestBed.createComponent(PlatformLandingPageComponent);

    try {
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const buttons = compiled.querySelectorAll<HTMLButtonElement>('.platform-landing__intro-actions button');
      expect(Array.from(buttons, button => button.textContent?.trim()))
        .toEqual(['Jak działa platforma', 'Wybierz pierwsze zadanie']);

      for (const [index, id] of ['platformLandingStory', 'platformLandingFeatures'].entries()) {
        const section = compiled.querySelector<HTMLElement>(`#${id}`)!;
        const scroll = vi.fn();
        Object.defineProperty(section, 'scrollIntoView', { value: scroll, configurable: true });
        const focus = vi.spyOn(section, 'focus');
        expect(buttons[index].getAttribute('aria-controls')).toBe(id);
        expect(section.getAttribute('tabindex')).toBe('-1');
        buttons[index].click();
        expect(scroll).toHaveBeenCalledExactlyOnceWith({
          block: 'start', behavior: reducedMotion ? 'instant' : 'smooth'
        });
        expect(focus).toHaveBeenCalledExactlyOnceWith({ preventScroll: true });
      }
    } finally {
      fixture.destroy();
      vi.unstubAllGlobals();
    }
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
    const storyTitle = story.querySelector('h3');
    expect(storyTitle?.textContent?.trim()).toBe('Jak działa Team Delivery Workspace');
    expect(story.getAttribute('aria-labelledby')).toBe(storyTitle?.id);
    expect(Array.from(story.querySelectorAll('h4'), heading => heading.getAttribute('aria-label'))).toEqual([
      'Wszystkie Twoje aplikacje. Wreszcie grają razem.',
      'Rozwiązuj codzienne problemy. Od incydentu po wdrożenie.',
      'Wbudowana wiedza ekspercka. Działasz efektywnie od pierwszego dnia.',
      'Czas na Twój ruch. Wybierz pierwsze zadanie.'
    ]);
    expect(Array.from(story.querySelectorAll('.platform-landing__slogan-line'),
      line => line.textContent.trim()
    )).toEqual([
      'Wszystkie Twoje aplikacje.',
      'Wreszcie grają razem.',
      'Rozwiązuj codzienne problemy.',
      'Od incydentu po wdrożenie.',
      'Wbudowana wiedza ekspercka.',
      'Działasz efektywnie od pierwszego dnia.',
      'Czas na Twój ruch.',
      'Wybierz pierwsze zadanie.'
    ]);
    expect(orbit.getAttribute('aria-hidden')).toBe('true');
    expect(orbit.querySelectorAll('.platform-landing__orbit-tool--result')).toHaveLength(1);
    expect(Array.from(story.querySelectorAll('[data-scene="closing"] .platform-landing__slogan-phrase'),
      phrase => phrase.textContent.trim()
    )).toEqual(['Czas na Twój ruch.', 'Wybierz pierwsze zadanie.']);
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
      constructor(private readonly notify: IntersectionObserverCallback) {}
      observe(target: Element): void {
        if (target.classList.contains('platform-landing__capabilities')) {
          setVisibility = visible => this.notify([{
            isIntersecting: visible,
            intersectionRatio: visible ? 1 : 0
          } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
        }
      }
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
