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

  it('should render the analysis console shell on the root route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-analysis-console')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__info-trigger')).toBeNull();
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
    const incidentLink = compiled.querySelector(
      'a.app-shell__nav-item[aria-label="Incident Analysis"]'
    );

    expect(shell?.classList.contains('app-shell--sidebar-collapsed')).toBe(false);
    expect(toggle?.getAttribute('aria-label')).toBe('Zwiń panel nawigacji');
    expect(toggleIcon?.textContent?.trim()).toBe('dock_to_right');
    expect(incidentLink?.getAttribute('title')).toBeNull();

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
    expect(incidentLink?.getAttribute('title')).toBe('Incident Analysis');
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
    http.expectOne('/flow-explorer/config').flush({ defaultBranch: 'main' });
    http.expectOne('/flow-explorer/systems').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const navLink = compiled.querySelector(
      'a.app-shell__nav-item[aria-label="Flow Explorer"]'
    );

    expect(compiled.querySelector('app-flow-explorer-page')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__breadcrumb')?.textContent).toContain(
      'Analysis Features'
    );
    expect(compiled.querySelector('.app-shell__title-block h1')?.textContent).toContain(
      'Flow Explorer'
    );
    expect(compiled.querySelector('.app-shell__info-trigger')).toBeNull();
    expect(navLink).not.toBeNull();
    expect(compiled.textContent).toContain('Endpoint documentation workspace');
  });
});
