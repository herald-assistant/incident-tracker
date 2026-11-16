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

  it('shows the three slogans below the visual scroll story', async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformLandingPageComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    const fixture = TestBed.createComponent(PlatformLandingPageComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const story = compiled.querySelector('.platform-landing__capabilities')!;
    const orbit = story.querySelector('.platform-landing__orbit')!;
    const copy = orbit.nextElementSibling!;
    expect(Array.from(copy.querySelectorAll('h3'), heading => heading.getAttribute('aria-label'))).toEqual([
      'Twoje narzędzia. Jedno miejsce.',
      'AI dopasowane do zadania. Od incydentu po wydanie.',
      'Wystarczy wiedzieć, co chcesz zrobić. Workspace prowadzi Cię do wyniku.'
    ]);
    expect(Array.from(copy.querySelectorAll('.platform-landing__slogan-line'),
      line => line.textContent.trim()
    )).toEqual([
      'Twoje narzędzia.',
      'Jedno miejsce.',
      'AI dopasowane do zadania.',
      'Od incydentu po wydanie.',
      'Wystarczy wiedzieć, co chcesz zrobić.',
      'Workspace prowadzi Cię do wyniku.'
    ]);
    expect(orbit.getAttribute('aria-hidden')).toBe('true');
    const guidanceCard = orbit.querySelector(
      '.platform-landing__orbit-result > .platform-landing__guidance-card'
    )!;
    expect(Array.from(guidanceCard.querySelectorAll('.platform-landing__guidance-step span'),
      icon => icon.textContent.trim()
    )).toEqual(['search', 'construction', 'auto_awesome']);
    expect(guidanceCard.querySelector('.platform-landing__guidance-document-result')?.textContent.trim())
      .toBe('markdown_document');
    expect(guidanceCard.querySelector('.platform-landing__guidance-action')).not.toBeNull();
    expect(orbit.querySelectorAll('.platform-landing__orbit-feature')).toHaveLength(9);
    expect(orbit.querySelectorAll('.platform-landing__orbit-flip img')).toHaveLength(9);
    expect(Array.from(orbit.querySelectorAll('.platform-landing__orbit-face--feature span'),
      icon => icon.textContent.trim()
    )).toEqual([
      'troubleshoot',
      'account_tree',
      'screen_search_desktop',
      'ads_click',
      'fact_check',
      'build_circle',
      'domain_add',
      'engineering',
      'finance'
    ]);
  });
});
