import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import type { WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter, Router } from '@angular/router';

import { App } from './app';
import { routes } from './app.routes';
import type { FormTourSource } from './core/models/form-tour';
import { DeliveryComplexityTrendsPageComponent } from './features/delivery-complexity-trends/pages/delivery-complexity-trends-page/delivery-complexity-trends-page';
import { importAssessmentTrendFiles } from './features/delivery-complexity-trends/utils/delivery-complexity-trends-import.utils';

const FEATURE_TOURS = [
  { route: '/flow-explorer', page: 'app-flow-explorer-page', count: 10 },
  { route: '/ux-inspector', page: 'app-ux-inspector-page', count: 1 },
  { route: '/change-verification', page: 'app-change-verification-page', count: 6 },
  { route: '/release-summary', page: 'app-release-summary-page', count: 8 },
  { route: '/config-drift-viewer', page: 'app-config-drift-viewer-page', count: 7 },
  { route: '/delivery-complexity-assessment', page: 'app-delivery-complexity-assessment-page', count: 5 },
  { route: '/delivery-scope-complexity', page: 'app-delivery-scope-complexity-page', count: 5 },
  { route: '/delivery-complexity-trends', page: 'app-delivery-complexity-trends-page', count: 1 }
];

describe('Analysis feature form tours', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideAnimationsAsync('noop'), provideHttpClient(), provideHttpClientTesting(),
        provideLocationMocks(), provideRouter(routes)
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(App);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  it.each(FEATURE_TOURS)('walks through $route using rendered controls without submitting a job', async ({ route, page, count }) => {
    await openPage(route);
    await walkTour(page, count);
    await router.navigateByUrl('/');
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.app-shell__tour-trigger')).toBeNull();
    http.verify();
  });

  it.each([
    { ...FEATURE_TOURS[0], busy: 'isSubmitting', facade: false },
    { ...FEATURE_TOURS[1], busy: 'isSubmitting', facade: true },
    { ...FEATURE_TOURS[2], busy: 'isSubmitting', facade: false },
    { ...FEATURE_TOURS[3], busy: 'starting', facade: false },
    { ...FEATURE_TOURS[4], busy: 'submitting', facade: false },
    { ...FEATURE_TOURS[5], busy: 'submitting', facade: false },
    { ...FEATURE_TOURS[6], busy: 'submitting', facade: false },
    { ...FEATURE_TOURS[7], busy: 'importing', facade: false }
  ])('hides the start tour while $route is busy', async ({ route, page, busy, facade }) => {
    await openPage(route);
    const component = fixture.debugElement.query(By.css(page)).componentInstance;
    const state = (facade ? component.facade : component) as Record<string, WritableSignal<boolean>>;
    state[busy].set(true);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.app-shell__tour-trigger')).toBeNull();
    state[busy].set(false);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.app-shell__tour-trigger')).not.toBeNull();
    http.verify();
  });

  it('guides the UX Inspector configuration after a capture arrives', async () => {
    await openPage('/ux-inspector?captureId=cap_crm_contact');
    await walkTour('app-ux-inspector-page', 9);
    http.verify();
  });

  it('guides the captured element with an empty frontend catalog', async () => {
    await openPage('/ux-inspector?captureId=cap_crm_contact', {
      '/api/ux-inspector/input-options': { featureId: 'ux-inspector', systems: [], configurationFindings: [] }
    });
    await walkTour('app-ux-inspector-page', 3);
    http.verify();
  });

  it('guides Flow Explorer users to register an application when the catalog is empty', async () => {
    await openPage('/flow-explorer', { '/api/flow-explorer/systems': [] });
    await walkTour('app-flow-explorer-page', 2);
    http.verify();
  });

  it('keeps Release summary import guidance available when new runs are disabled', async () => {
    await openPage('/release-summary', {
      '/api/ui/config': { ...startupResponses()['/api/ui/config'], systemAssistantEnabled: false, releaseSummaryEnabled: false }
    });
    await walkTour('app-release-summary-page', 1);
    http.verify();
  });

  it('guides Delivery Trends filters after CSV is imported', async () => {
    await openPage('/delivery-complexity-trends');
    const page = fixture.debugElement.query(By.directive(DeliveryComplexityTrendsPageComponent)).componentInstance as DeliveryComplexityTrendsPageComponent;
    const csv = [
      'issueKey;issueUrl;summary;issueType;doneAt;teamId;teamName;teamFieldId;mergeRequestUrls;mergeRequestAuthorIds;mergeRequestAuthorNames;deliveryUnitId;assessmentStatus;outcomeBreadth;domainDecisionComplexity;applicationFlowComplexity;boundaryAndDataComplexity;verificationStateSpace;implementedCompatibilityScope;parameterizationComplexity;score100;deliveredStoryPoints;pointsForAggregation',
      'CRM-1;https://jira.example.com/browse/CRM-1;Edycja kontaktu;Story;2026-10-15T10:00:00+02:00;crm-team;CRM Team;customfield_10000;https://gitlab.example.com/CRM/contacts/-/merge_requests/1;101;CRM Author;crm-unit-1;COMPLETED;1;1;1;1;1;1;1;25;3;3'
    ].join('\n');
    const file = new File([csv], 'crm-october.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', { value: async () => csv });
    page.dataset.set(await importAssessmentTrendFiles([file]));
    await fixture.whenStable();

    await walkTour('app-delivery-complexity-trends-page', 8);
    http.verify();
  });

  async function openPage(route: string, overrides: Record<string, object> = {}): Promise<void> {
    const responses: Record<string, object> = { ...startupResponses(), ...overrides };
    await router.navigateByUrl(route);
    fixture.detectChanges();
    let requests = http.match(() => true);
    while (requests.length) {
      for (const request of requests) {
        expect(request.request.method).toBe('GET');
        const response = responses[request.request.url];
        expect(response, request.request.url).toBeDefined();
        request.flush(response);
      }
      fixture.detectChanges();
      requests = http.match(() => true);
    }
    await fixture.whenStable();
  }

  async function walkTour(selector: string, count: number): Promise<void> {
    const source = fixture.debugElement.query(By.css(selector)).componentInstance as FormTourSource;
    const steps = source.formTourSteps();
    expect(steps).toHaveLength(count);
    const page = fixture.nativeElement.querySelector(selector) as HTMLElement;
    for (const step of steps) {
      expect(page.querySelectorAll('[data-form-tour="' + step.anchor + '"]'), step.title).toHaveLength(1);
    }
    const before = formValues(page);
    const shell = fixture.nativeElement as HTMLElement;
    shell.querySelector<HTMLButtonElement>('.app-shell__tour-trigger')!.click();
    await fixture.whenStable();

    const tour = document.querySelector('app-form-tour')!;
    const next = tour.querySelector<HTMLButtonElement>('.primary-button')!;
    for (const [index, step] of steps.entries()) {
      expect(tour.querySelector('h2')?.textContent).toBe(step.title);
      expect(tour.querySelector('.form-tour__progress')?.textContent?.trim()).toBe('Krok ' + (index + 1) + ' z ' + count);
      next.click();
      await fixture.whenStable();
    }
    expect(document.querySelector('app-form-tour')).toBeNull();
    expect(formValues(page)).toEqual(before);
  }
});

function formValues(page: HTMLElement) {
  return Array.from(page.querySelectorAll<HTMLInputElement>('input,textarea'), (input) => ({
    value: input.value, checked: input.checked, disabled: input.disabled
  }));
}

function startupResponses(): Record<string, Record<string, unknown> | object[]> {
  const system = { systemId: 'crm', label: 'CRM Portal', name: 'CRM Portal', summary: 'Obsługa kontaktów CRM',
    shortName: 'CRM', kind: 'internal-application', lifecycleStatus: 'active', operationalStatus: 'healthy',
    criticality: 'medium', aliases: [], repositoryCount: 1, codeSearchScopeCount: 1, ownerTeamIds: [] };
  return {
    '/api/ui/config': { title: 'CRM Workspace', defaultTitle: 'Team Delivery Workspace', defaultBranch: 'main', systemAssistantEnabled: false, releaseSummaryEnabled: true },
    '/api/auth/github/status': { configured: false, settingsUrl: '/workspace-settings' },
    '/api/analysis/ai/options': { defaultModel: 'crm-model', defaultReasoningEffort: 'low',
      defaultReasoningEfforts: ['low'], models: [{ id: 'crm-model', name: 'CRM Model', supportsReasoningEffort: true,
        reasoningEfforts: ['low'], defaultReasoningEffort: 'low' }] },
    '/api/flow-explorer/systems': [system],
    '/api/ux-inspector/input-options': { featureId: 'ux-inspector', systems: [system], configurationFindings: [] },
    '/api/ux-inspector/captures/cap_crm_contact': captureSnapshot(),
    '/api/gitlab/systems/crm/branches': { systemId: 'crm', branches: [{ name: 'main', isDefault: true }], warnings: [], truncated: false },
    '/api/ux-inspector/views': { systemId: 'crm', systemLabel: 'CRM Portal', sourceRevision: { branch: 'main' },
      status: 'READY', views: [], diagnostics: [], limitations: [] },
    '/api/release-summary/applications': [system],
    '/api/config-drift-viewer/v1/input-options': { modes: ['BASIC', 'DEEP'], branches: ['crm-test', 'crm-stage'],
      repositories: [{ id: 'crm-config', label: 'CRM Config' }],
      systems: [{ id: 'crm', label: 'CRM', configurationDirectory: 'crm' }] }
  };
}

function captureSnapshot(): Record<string, unknown> {
  return {
    captureId: 'cap_crm_contact',
    capture: {
      schema: 'tdw.ux-inspector-capture', version: 1, captureId: 'cap_crm_contact',
      capturedAt: '2026-10-15T10:00:00Z', captureProfile: 'ELEMENT_CONTEXT',
      page: { origin: 'https://crm.example.com', path: '/contacts', title: 'CRM', language: 'pl', queryParameterNames: [] },
      target: {
        tag: 'button', role: 'button', accessibleName: 'Zapisz kontakt', text: 'Zapisz kontakt',
        domFingerprint: { stableAttributes: {}, selectorCandidates: [], componentBoundaryTags: [], labelFor: null },
        state: { disabled: false, ariaDisabled: false, readOnly: false, required: false, invalid: false,
          checked: null, expanded: null, hidden: false },
        bounds: { x: 20, y: 40, width: 180, height: 42 }
      },
      ancestors: [], traversal: { observedDepth: 1, emittedNodeCount: 1, omittedNodeCount: 0, reachedDocumentRoot: true },
      signals: { shadowBoundaryCount: 0, frame: 'TOP_LEVEL', redactions: [] }, limits: [],
      client: { name: 'TDW Browser Tools', version: '1', featureId: 'ux-inspector' }
    },
    formFields: { status: 'UNAVAILABLE', fields: [], omittedSensitiveFields: 0, reason: 'PROFILE_EXCLUDED' },
    store: { status: 'UNAVAILABLE', state: null, redactions: 0, reason: 'GETTER_MISSING' },
    httpHistory: { status: 'UNAVAILABLE', entries: [], capturedAt: null, source: null, redactions: 0, reason: 'GETTER_MISSING' }
  };
}
