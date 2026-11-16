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
});
