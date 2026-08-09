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
    expect(featureLinks.map((link) => link.getAttribute('href'))).toEqual([
      '/incident-analysis',
      '/flow-explorer',
      '/change-verification',
      '/config-drift-viewer'
    ]);
    expect(featureGrid?.textContent).not.toContain('Data Diagnostics');
    expect(plannedFeature?.textContent).toContain('W planie');
    expect(plannedFeature?.textContent).toContain('Data Diagnostics');
    expect(plannedFeature?.querySelector('a')).toBeNull();
  });
});
