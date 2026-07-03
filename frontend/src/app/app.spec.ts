import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { By } from '@angular/platform-browser';
import { MatTooltip } from '@angular/material/tooltip';
import { Router, provideRouter } from '@angular/router';

import { App } from './app';
import { routes } from './app.routes';
import { FlowExplorerPageComponent } from './features/flow-explorer/pages/flow-explorer-page/flow-explorer-page';
import {
  FlowExplorerEndpointInventoryResponse,
  FlowExplorerSystemOption
} from './features/flow-explorer/models/flow-explorer.models';

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
    http.expectOne('/api/analysis/runs').flush({ runs: [] });
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const navLink = compiled.querySelector(
      'a.app-shell__nav-item[aria-label="Analysis History"]'
    );

    expect(compiled.querySelector('app-analysis-history-page')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__breadcrumb-link')?.textContent?.trim()).toBe(
      'ChatCLP'
    );
    expect(compiled.querySelector('.app-shell__breadcrumb-current')?.textContent?.trim()).toBe(
      'Analysis History'
    );
    expect(compiled.querySelector('.app-shell__title-block h1')?.textContent).toContain(
      'Analysis History'
    );
    expect(compiled.querySelector('.app-shell__info-trigger')).toBeNull();
    expect(navLink).not.toBeNull();
    expect(compiled.textContent).toContain('No local analyses');
  });

  it('should render the workspace settings shell on the workspace settings route', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const http = TestBed.inject(HttpTestingController);

    await router.navigateByUrl('/workspace-settings');
    fixture.detectChanges();
    flushUiConfig(http, 'ChatCLP');
    http.expectOne('/api/workspace/settings').flush(workspaceSettingsResponse());
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const navLink = compiled.querySelector(
      'a.app-shell__nav-item[aria-label="Workspace Settings"]'
    );

    expect(compiled.querySelector('app-workspace-settings-page')).not.toBeNull();
    expect(compiled.querySelector('.app-shell__breadcrumb-link')?.textContent?.trim()).toBe(
      'ChatCLP'
    );
    expect(compiled.querySelector('.app-shell__breadcrumb-current')?.textContent?.trim()).toBe(
      'Workspace Settings'
    );
    expect(compiled.querySelector('.app-shell__title-block h1')?.textContent).toContain(
      'Workspace Settings'
    );
    expect(compiled.querySelector('.app-shell__info-trigger')).toBeNull();
    expect(navLink).not.toBeNull();
    expect(compiled.textContent).toContain('tdw-data/settings.json');
    expect(compiled.textContent).toContain('Elasticsearch');
    expect(compiled.textContent).toContain('Dynatrace');
    expect(compiled.querySelector('.workspace-settings-baseline')).toBeNull();
    expect(compiled.textContent).not.toContain('analysis.gitlab');
    expect(compiled.textContent).not.toContain('analysis.elasticsearch');
    expect(compiled.textContent).not.toContain('analysis.dynatrace');
    expect(compiled.textContent).not.toContain('application.properties');
    expect(compiled.textContent).not.toContain('Use application.properties');

    const sourceBadges = Array.from(
      compiled.querySelectorAll<HTMLElement>('.workspace-settings-source')
    );
    expect(sourceBadges.map((badge) => badge.textContent?.trim())).toEqual([
      'DEFAULT',
      'DEFAULT',
      'CUSTOM',
      'CUSTOM',
      'CUSTOM',
      'DEFAULT',
      'CUSTOM',
      'CUSTOM',
      'CUSTOM',
      'CUSTOM'
    ]);

    const sourceBadgeTooltips = fixture.debugElement
      .queryAll(By.css('.workspace-settings-source'))
      .map((element) => element.injector.get(MatTooltip));
    expect(sourceBadgeTooltips.map((tooltip) => tooltip.message)).toEqual([
      'Default: ChatCLP',
      'Default: https://gitlab.example.com',
      'Default: platform/app',
      '',
      'Default: https://elastic.example.com',
      'Default: default',
      'Default: logs-*',
      '',
      'Default: https://dynatrace.example.com',
      ''
    ]);
    expect(sourceBadgeTooltips.map((tooltip) => tooltip.disabled)).toEqual([
      false,
      false,
      false,
      true,
      false,
      false,
      false,
      true,
      false,
      true
    ]);

    const resetButtons = Array.from(
      compiled.querySelectorAll<HTMLButtonElement>('.workspace-settings-field-reset-button')
    );
    expect(resetButtons.map((button) => button.getAttribute('aria-label'))).toEqual([
      'Restore default for Group',
      'Restore default for Token',
      'Restore default for Elasticsearch Base URL',
      'Restore default for Index pattern',
      'Restore default for Authorization header',
      'Restore default for Dynatrace Base URL',
      'Restore default for Dynatrace API token'
    ]);
    expect(
      fixture.debugElement
        .queryAll(By.css('.workspace-settings-field-reset-button'))
        .map((element) => element.injector.get(MatTooltip).message)
    ).toEqual([
      'Restore default',
      'Restore default',
      'Restore default',
      'Restore default',
      'Restore default',
      'Restore default',
      'Restore default'
    ]);

    resetButtons[0].click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const groupInput = compiled.querySelector<HTMLInputElement>('#gitlabGroup');
    const updatedSourceBadges = Array.from(
      compiled.querySelectorAll<HTMLElement>('.workspace-settings-source')
    );
    expect(groupInput?.value).toBe('platform/app');
    expect(updatedSourceBadges.map((badge) => badge.textContent?.trim())).toEqual([
      'DEFAULT',
      'DEFAULT',
      'DEFAULT',
      'CUSTOM',
      'CUSTOM',
      'DEFAULT',
      'CUSTOM',
      'CUSTOM',
      'CUSTOM',
      'CUSTOM'
    ]);
    expect(compiled.querySelectorAll('.workspace-settings-field-reset-button')).toHaveLength(6);
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
    http.expectOne('/api/analysis/ai/options').flush({
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

  it('should remount an active analysis feature from the sidebar as a fresh run screen', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const http = TestBed.inject(HttpTestingController);

    await router.navigateByUrl('/flow-explorer');
    fixture.detectChanges();
    flushUiConfig(http);
    flushFlowExplorerStartup(http);
    await fixture.whenStable();
    fixture.detectChanges();

    const firstFlowExplorer = flowExplorerComponent(fixture);
    firstFlowExplorer.selectSystem(firstFlowExplorer.systems()[0]);
    fixture.detectChanges();
    http
      .expectOne(
        (request) =>
          request.url === '/api/flow-explorer/systems/crm-service/endpoints' &&
          request.params.get('branch') === 'main'
      )
      .flush(flowExplorerEndpointInventory());
    await fixture.whenStable();
    fixture.detectChanges();

    expect(firstFlowExplorer.selectedSystemId()).toBe('crm-service');
    expect(fixture.nativeElement.textContent).toContain('CRM Service');

    const flowExplorerNavLink = fixture.nativeElement.querySelector(
      'a.app-shell__nav-item[aria-label="Flow Explorer"]'
    ) as HTMLAnchorElement | null;

    flowExplorerNavLink?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    flushFlowExplorerStartup(http);
    await fixture.whenStable();
    fixture.detectChanges();

    const freshFlowExplorer = flowExplorerComponent(fixture);
    expect(router.url).toBe('/flow-explorer');
    expect(freshFlowExplorer).not.toBe(firstFlowExplorer);
    expect(freshFlowExplorer.selectedSystemId()).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Select application');
  });
});

function flushUiConfig(http: HttpTestingController, title = 'Team Delivery Workspace'): void {
  http.expectOne('/api/ui/config').flush({
    title,
    subtitle: title === 'Team Delivery Workspace' ? null : 'Team Delivery Workspace',
    defaultTitle: 'Team Delivery Workspace'
  });
}

function workspaceSettingsResponse(): Record<string, unknown> {
  return {
    workspaceEnabled: true,
    settingsPath: 'tdw-data/settings.json',
    values: {
      appUi: {
        title: {
          propertyKey: 'app.ui.title',
          value: 'ChatCLP',
          applicationValue: 'ChatCLP',
          workspaceValue: null,
          source: 'APPLICATION_PROPERTIES',
          secret: false
        }
      },
      gitLab: {
        baseUrl: {
          propertyKey: 'analysis.gitlab.base-url',
          value: 'https://gitlab.example.com',
          applicationValue: 'https://gitlab.example.com',
          workspaceValue: null,
          source: 'APPLICATION_PROPERTIES',
          secret: false
        },
        group: {
          propertyKey: 'analysis.gitlab.group',
          value: 'platform/backend',
          applicationValue: 'platform/app',
          workspaceValue: 'platform/backend',
          source: 'WORKSPACE_SETTINGS',
          secret: false
        },
        token: {
          propertyKey: 'analysis.gitlab.token',
          value: 'glpat_secret',
          applicationValue: '',
          workspaceValue: 'glpat_secret',
          source: 'WORKSPACE_SETTINGS',
          secret: true
        }
      },
      elasticsearch: {
        baseUrl: {
          propertyKey: 'analysis.elasticsearch.base-url',
          value: 'https://elastic.workspace.example.com',
          applicationValue: 'https://elastic.example.com',
          workspaceValue: 'https://elastic.workspace.example.com',
          source: 'WORKSPACE_SETTINGS',
          secret: false
        },
        kibanaSpaceId: {
          propertyKey: 'analysis.elasticsearch.kibana-space-id',
          value: 'default',
          applicationValue: 'default',
          workspaceValue: null,
          source: 'APPLICATION_PROPERTIES',
          secret: false
        },
        indexPattern: {
          propertyKey: 'analysis.elasticsearch.index-pattern',
          value: 'logs-platform-*',
          applicationValue: 'logs-*',
          workspaceValue: 'logs-platform-*',
          source: 'WORKSPACE_SETTINGS',
          secret: false
        },
        authorizationHeader: {
          propertyKey: 'analysis.elasticsearch.authorization-header',
          value: 'Bearer elastic-secret',
          applicationValue: '',
          workspaceValue: 'Bearer elastic-secret',
          source: 'WORKSPACE_SETTINGS',
          secret: true
        }
      },
      dynatrace: {
        baseUrl: {
          propertyKey: 'analysis.dynatrace.base-url',
          value: 'https://dynatrace.workspace.example.com',
          applicationValue: 'https://dynatrace.example.com',
          workspaceValue: 'https://dynatrace.workspace.example.com',
          source: 'WORKSPACE_SETTINGS',
          secret: false
        },
        apiToken: {
          propertyKey: 'analysis.dynatrace.api-token',
          value: 'dt0c01_secret',
          applicationValue: '',
          workspaceValue: 'dt0c01_secret',
          source: 'WORKSPACE_SETTINGS',
          secret: true
        }
      }
    }
  };
}

type FlowExplorerComponentDriver = {
  systems: () => FlowExplorerSystemOption[];
  selectedSystemId: () => string;
  selectSystem(system: FlowExplorerSystemOption): void;
};

function flowExplorerComponent(fixture: ComponentFixture<App>): FlowExplorerComponentDriver {
  const debugElement = fixture.debugElement.query(By.directive(FlowExplorerPageComponent));
  expect(debugElement).not.toBeNull();
  return debugElement.componentInstance as unknown as FlowExplorerComponentDriver;
}

function flushFlowExplorerStartup(http: HttpTestingController): void {
  http.expectOne('/api/flow-explorer/config').flush({ defaultBranch: 'main' });
  http.expectOne('/api/flow-explorer/systems').flush([flowExplorerSystem('crm-service')]);
  http.expectOne('/api/analysis/ai/options').flush({
    defaultModel: 'gpt-5.4',
    defaultReasoningEffort: 'medium',
    defaultReasoningEfforts: ['low', 'medium', 'high'],
    models: []
  });
}

function flowExplorerSystem(systemId: string): FlowExplorerSystemOption {
  return {
    systemId,
    name: 'CRM Service',
    shortName: 'CRM',
    kind: 'internal-application',
    lifecycleStatus: 'active',
    operationalStatus: 'healthy',
    criticality: 'high',
    summary: 'Customer relationship core API.',
    aliases: ['crm'],
    repositoryCount: 2,
    codeSearchScopeCount: 1,
    ownerTeamIds: ['team-crm']
  };
}

function flowExplorerEndpointInventory(): FlowExplorerEndpointInventoryResponse {
  return {
    systemId: 'crm-service',
    requestedBranch: 'main',
    resolvedRef: 'main',
    gitLabGroup: 'platform/backend',
    endpointPathPrefix: '',
    httpMethod: '',
    repositoryCount: 1,
    scannedRepositoryCount: 1,
    endpointCount: 1,
    candidateFileCount: 1,
    scannedFileCount: 1,
    scannedFileLimitReached: false,
    dataCollectedAt: '2026-06-18T10:00:00Z',
    repositories: [],
    endpoints: [
      {
        endpointId: 'crm-api:GET /api/customers/{id}',
        method: 'GET',
        methods: ['GET'],
        path: '/api/customers/{id}',
        pathExpression: '/api/customers/{id}',
        summary: 'Customer lookup',
        description: 'Returns customer details.',
        operationId: 'getCustomer',
        tags: ['customers'],
        controllerClass: 'CustomerController',
        handlerMethod: 'getCustomer',
        source: null,
        parameters: [],
        confidence: 'high',
        limitations: [],
        suggestedNextReads: [],
        tooltipDetails: null
      }
    ],
    limitations: []
  };
}
