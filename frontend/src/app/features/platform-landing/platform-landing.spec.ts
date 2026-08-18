import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PlatformLandingPageComponent } from './platform-landing';

describe('PlatformLandingPageComponent', () => {
  it('should keep feature navigation in the feature grid instead of duplicating selected actions', async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformLandingPageComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    const fixture = TestBed.createComponent(PlatformLandingPageComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const featureLinks = Array.from(
      compiled.querySelectorAll<HTMLAnchorElement>('.platform-landing__feature-grid a')
    );
    const featureGrid = compiled.querySelector<HTMLElement>('.platform-landing__feature-grid');
    const plannedFeature = compiled.querySelector<HTMLElement>(
      '.platform-landing__planned-feature'
    );

    expect(compiled.querySelector('.platform-landing__actions')).toBeNull();
    expect(compiled.querySelectorAll('.platform-landing__intro a')).toHaveLength(0);
    expect(compiled.querySelector('#platformLandingFeatures')).not.toBeNull();
    expect(featureLinks.map((link) => link.getAttribute('href'))).toEqual([
      '/incident-analysis',
      '/flow-explorer',
      '/ui-explorer',
      '/change-verification',
      '/config-drift-viewer',
      '/delivery-effectiveness-assessment'
    ]);
    expect(
      featureLinks.map((link) =>
        Array.from(link.classList).find((className) =>
          className.startsWith('platform-landing__feature-card--')
        )
      )
    ).toEqual([
      'platform-landing__feature-card--incident',
      'platform-landing__feature-card--flow',
      'platform-landing__feature-card--ui',
      'platform-landing__feature-card--change',
      'platform-landing__feature-card--config',
      'platform-landing__feature-card--delivery'
    ]);
    expect(featureGrid?.textContent).toContain('Delivery Effectiveness Assessment');
    expect(featureGrid?.textContent).not.toContain('Data Diagnostics');
    expect(
      featureLinks[2]?.querySelector('.platform-landing__feature-icon')?.textContent?.trim()
    ).toBe('screen_search_desktop');
    expect(plannedFeature?.textContent).toContain('W planie');
    expect(plannedFeature?.textContent).toContain('Data Diagnostics');
    expect(plannedFeature?.querySelector('a')).toBeNull();
  });
});
