import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { Router, provideRouter } from '@angular/router';

import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    window.localStorage.removeItem('team-delivery-workspace.sidebar.collapsed');

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideAnimationsAsync('noop'),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideLocationMocks(),
        provideRouter(routes)
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the platform landing shell on the root route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const http = TestBed.inject(HttpTestingController);

    await router.navigateByUrl('/');
    fixture.detectChanges();
    flushUiConfig(http);
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-platform-landing-page')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__title-block h1')?.textContent).toContain(
      'Team Delivery Workspace'
    );
    expect(compiled.querySelector('.app-shell__breadcrumb')).toBeNull();
    expect(compiled.textContent).toContain('Sprawdź incydent');
    expect(compiled.textContent).toContain('Flow Explorer');
    expect(compiled.querySelector('.app-shell__info-trigger')).toBeNull();
  });

  it('should render the incident analysis shell on the incident analysis route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const http = TestBed.inject(HttpTestingController);

    await router.navigateByUrl('/incident-analysis');
    fixture.detectChanges();
    flushUiConfig(http);
    http.expectOne('/api/auth/github/status').flush({
      mode: 'LOCAL_TOKEN',
      required: false,
      connected: false,
      githubLogin: null,
      displayName: null,
      tokenExpiresAt: null,
      reauthRequired: false,
      authStartUrl: null
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-analysis-console')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__breadcrumb-link')?.textContent?.trim()).toBe(
      'Team Delivery Workspace'
    );
    expect(compiled.querySelector('.app-shell__breadcrumb-link')?.getAttribute('href')).toBe('/');
    expect(compiled.querySelector('.app-shell__breadcrumb-current')?.textContent?.trim()).toBe(
      'Incident Analysis'
    );
    expect(compiled.querySelector('.app-shell__title-block h1')?.textContent).toContain(
      'Incident Analysis'
    );
    expect(compiled.querySelector('.app-shell__info-trigger')).toBeNull();
  });

  it('should render the analysis history shell on the analysis history route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const http = TestBed.inject(HttpTestingController);

    await router.navigateByUrl('/analysis-history');
    fixture.detectChanges();
    flushUiConfig(http, 'ChatCLP');
    http.expectOne('/analysis/runs').flush({ runs: [] });
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const navLink = compiled.querySelector(
      'a.app-shell__nav-item[aria-label="Historia analiz"]'
    );

    expect(compiled.querySelector('app-analysis-history-page')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__breadcrumb-link')?.textContent?.trim()).toBe(
      'ChatCLP'
    );
    expect(compiled.querySelector('.app-shell__breadcrumb-current')?.textContent?.trim()).toBe(
      'Historia analiz'
    );
    expect(compiled.querySelector('.app-shell__title-block h1')?.textContent).toContain(
      'Historia analiz'
    );
    expect(compiled.querySelector('.app-shell__info-trigger')).toBeNull();
    expect(navLink).not.toBeNull();
    expect(compiled.textContent).toContain('Brak lokalnych analiz');
  });

  it('should collapse the left navigation into an icon rail', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const shell = compiled.querySelector('.app-shell');
    const toggle = compiled.querySelector(
      '.app-shell__sidebar-toggle'
    ) as HTMLButtonElement | null;
    const toggleIcon = toggle?.querySelector('.material-symbols-outlined');
    const homeLink = compiled.querySelector(
      'a.app-shell__nav-item[aria-label="Workspace Overview"]'
    );

    expect(shell?.classList.contains('app-shell--sidebar-collapsed')).toBe(false);
    expect(toggle?.getAttribute('aria-label')).toBe('Zwiń panel nawigacji');
    expect(toggleIcon?.textContent?.trim()).toBe('dock_to_right');
    expect(homeLink?.getAttribute('title')).toBeNull();

    toggle?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(shell?.classList.contains('app-shell--sidebar-collapsed')).toBe(true);
    expect(compiled.querySelector('.app-shell__sidebar-toggle')).toBeNull();
    const brandToggle = compiled.querySelector(
      '.app-shell__brand-toggle'
    ) as HTMLButtonElement | null;
    expect(brandToggle?.getAttribute('aria-label')).toBe('Rozwiń panel nawigacji');
    expect(brandToggle?.querySelector('.app-shell__brand-toggle-logo')).not.toBeNull();
    expect(brandToggle?.querySelector('.app-shell__brand-toggle-icon')?.textContent?.trim()).toBe(
      'dock_to_right'
    );
    expect(homeLink?.getAttribute('title')).toBe('Workspace Overview');
    expect(window.localStorage.getItem('team-delivery-workspace.sidebar.collapsed')).toBe('true');

    brandToggle?.click();
    fixture.detectChanges();

    expect(shell?.classList.contains('app-shell--sidebar-collapsed')).toBe(false);
    expect(shell?.classList.contains('app-shell--sidebar-expanding')).toBe(true);
    expect(compiled.querySelector('.app-shell__brand-toggle')).not.toBeNull();

    await new Promise((resolve) => setTimeout(resolve, 190));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(shell?.classList.contains('app-shell--sidebar-expanding')).toBe(false);
    expect(compiled.querySelector('.app-shell__brand-toggle')).toBeNull();
    expect(compiled.querySelector('.app-shell__sidebar-toggle')).not.toBeNull();
    expect(window.localStorage.getItem('team-delivery-workspace.sidebar.collapsed')).toBe('false');
  });

  it('should render the elastic console shell on the elastic route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/elastic');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-elastic-evidence-console')).not.toBeNull();
    expect(compiled.querySelector('.workbench-header')).toBeNull();
    expect(compiled.querySelector('.app-shell__info-trigger')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__info-tooltip')?.textContent).toContain(
      'POST /api/elasticsearch/logs/*'
    );
    expect(compiled.textContent).toContain('HTTP Call Summary');
    expect(compiled.textContent).not.toContain('Endpoint Inventory');
  });

  it('should render the gitlab console shell on the gitlab route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/gitlab');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-gitlab-evidence-console')).not.toBeNull();
    expect(compiled.querySelector('.workbench-header')).toBeNull();
    expect(compiled.querySelector('.app-shell__info-trigger')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__info-tooltip')?.textContent).toContain(
      'POST /api/gitlab/*'
    );
    expect(compiled.textContent).toContain('Endpoint Inventory');
    expect(compiled.textContent).toContain('Endpoint Use Case Context');
    expect(compiled.textContent).not.toContain('HTTP Call Summary');
  });

  it('should redirect the legacy evidence route to elastic', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/evidence');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/elastic');
  });

  it('should render the database console shell on the database route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/database');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-database-console')).not.toBeNull();
    expect(compiled.querySelector('.workbench-header')).toBeNull();
    expect(compiled.querySelector('.app-shell__info-trigger')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__info-tooltip')?.textContent).toContain(
      'POST /api/database/*'
    );
  });

  it('should render the operational context shell on the operational context route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/operational-context');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-context-home-page')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__info-trigger')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__info-tooltip')?.textContent).toContain(
      'GET /api/operational-context/*'
    );
  });

  it('should render the flow explorer shell on the flow explorer route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const http = TestBed.inject(HttpTestingController);

    await router.navigateByUrl('/flow-explorer');
    fixture.detectChanges();
    flushUiConfig(http, 'ChatCLP');
    http.expectOne('/api/flow-explorer/config').flush({ defaultBranch: 'main' });
    http.expectOne('/api/flow-explorer/systems').flush([]);
    http.expectOne('/analysis/ai/options').flush({
      defaultModel: 'gpt-5.4',
      defaultReasoningEffort: 'medium',
      defaultReasoningEfforts: ['low', 'medium', 'high'],
      models: []
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const navLink = compiled.querySelector(
      'a.app-shell__nav-item[aria-label="Flow Explorer"]'
    );

    expect(compiled.querySelector('app-flow-explorer-page')).not.toBeNull();
    const breadcrumbLink = compiled.querySelector(
      '.app-shell__breadcrumb-link'
    ) as HTMLAnchorElement | null;
    expect(breadcrumbLink?.textContent?.trim()).toBe('ChatCLP');
    expect(breadcrumbLink?.getAttribute('href')).toBe('/');
    expect(compiled.querySelector('.app-shell__breadcrumb-current')?.textContent?.trim()).toBe(
      'Flow Explorer'
    );
    expect(compiled.querySelector('.app-shell__title-block h1')?.textContent).toContain(
      'Flow Explorer'
    );
    expect(compiled.querySelector('.app-shell__info-trigger')).toBeNull();
    expect(navLink).not.toBeNull();
    expect(compiled.textContent).toContain('Endpoint documentation workspace');

    breadcrumbLink?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/');
    expect(compiled.querySelector('app-platform-landing-page')).not.toBeNull();
  });
});

function flushUiConfig(http: HttpTestingController, title = 'Team Delivery Workspace'): void {
  http.expectOne('/api/ui/config').flush({
    title,
    subtitle: title === 'Team Delivery Workspace' ? null : 'Team Delivery Workspace',
    defaultTitle: 'Team Delivery Workspace'
  });
}
