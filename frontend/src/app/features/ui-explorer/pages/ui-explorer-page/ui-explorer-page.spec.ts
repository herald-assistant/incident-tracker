import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import {
  UiExplorerInputOptionsResponse,
  UiExplorerJobStateSnapshot,
  UiExplorerScreenCatalogResponse
} from '../../models/ui-explorer.models';
import { UiExplorerPageComponent } from './ui-explorer-page';

describe('UiExplorerPageComponent', () => {
  let queryParamMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(async () => {
    queryParamMap = new BehaviorSubject(convertToParamMap({}));
    await TestBed.configureTestingModule({
      imports: [UiExplorerPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { queryParamMap } },
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } }
      ]
    }).compileComponents();
  });

  afterEach(() => vi.restoreAllMocks());

  it('renders a real CRM screen catalog and produces a complete keyboard-accessible configuration', async () => {
    const fixture = TestBed.createComponent(UiExplorerPageComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    flushPlatformConfig(http);
    http.expectOne('/api/ui-explorer/input-options').flush(crmInputOptions());
    http.expectOne(
      (request) =>
        request.url === '/api/ui-explorer/screens' &&
        request.params.get('systemId') === 'crm-agent-portal' &&
        request.params.get('branch') === 'main'
    ).flush(crmScreenCatalog('main', 'crm-revision-a1b2c3'));
    http.expectOne('/api/analysis/ai/options').flush(crmAiOptions());
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Screen documentation workspace');
    expect(compiled.querySelector('.ui-explorer-target-grid')).not.toBeNull();
    expect(compiled.querySelector('.ui-explorer-scope-grid')).not.toBeNull();
    expect(compiled.querySelector('.ui-explorer-workspace__catalog')).toBeNull();
    expect(compiled.textContent).toContain('Prepare the first UI Explorer run');

    const sectionModesControl = compiled.querySelector<HTMLButtonElement>(
      'button[aria-haspopup="dialog"]'
    );
    sectionModesControl?.click();
    fixture.detectChanges();
    expect(compiled.querySelectorAll('.ui-explorer-section-mode-row')).toHaveLength(8);
    expect(compiled.querySelectorAll('[role="radio"][aria-checked="true"]')).toHaveLength(8);

    const screenControl = compiled.querySelector<HTMLButtonElement>(
      '.ui-explorer-screen-select__control'
    );
    screenControl?.click();
    fixture.detectChanges();
    expect(compiled.textContent).toContain('Utworzenie kontaktu CRM');
    expect(compiled.textContent).toContain('crm-revision-a1b2c3');

    const screenOption = compiled.querySelector<HTMLButtonElement>('.ui-explorer-screen-option');
    expect(screenOption).not.toBeNull();
    screenOption?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.facade.configurationReady()).toBe(true);
    expect(compiled.textContent).toContain('Ready for the first UI Explorer run');
    http.verify();
  });

  it('clears a stale screen and reloads the catalog when Enter is pressed in the ref field', async () => {
    const fixture = TestBed.createComponent(UiExplorerPageComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    flushPlatformConfig(http);
    http.expectOne('/api/ui-explorer/input-options').flush(crmInputOptions());
    http.expectOne((request) => request.url === '/api/ui-explorer/screens').flush(
      crmScreenCatalog('main', 'crm-revision-a1b2c3')
    );
    http.expectOne('/api/analysis/ai/options').flush(crmAiOptions());
    await fixture.whenStable();
    fixture.detectChanges();

    fixture.componentInstance.facade.selectScreen('crm-contact-create');
    const branchInput = fixture.nativeElement.querySelector(
      'input[autocomplete="off"][maxlength="160"]'
    ) as HTMLInputElement;
    branchInput.value = 'crm-review';
    branchInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.componentInstance.facade.selectedScreenId()).toBe('');
    expect(fixture.componentInstance.facade.sourceRevision()).toBeNull();

    branchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/ui-explorer/screens' && candidate.params.get('branch') === 'crm-review'
    );
    request.flush(crmScreenCatalog('crm-review', 'crm-revision-d4e5f6'));
    fixture.detectChanges();

    expect(fixture.componentInstance.facade.sourceRevision()?.revision).toBe('crm-revision-d4e5f6');
    http.verify();
  });

  it('starts, polls and presents a completed CRM run through the shared analysis workspace', async () => {
    const fixture = TestBed.createComponent(UiExplorerPageComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    flushPlatformConfig(http);
    http.expectOne('/api/ui-explorer/input-options').flush(crmInputOptions());
    http.expectOne((request) => request.url === '/api/ui-explorer/screens').flush(
      crmScreenCatalog('main', 'crm-revision-a1b2c3')
    );
    http.expectOne('/api/analysis/ai/options').flush(crmAiOptions());
    await fixture.whenStable();
    fixture.detectChanges();

    fixture.componentInstance.facade.selectScreen('crm-contact-create');
    fixture.componentInstance.facade.updateScenarioDescription(
      'Describe the anonymized CRM contact creation flow.'
    );
    fixture.detectChanges();

    const runButton = fixture.nativeElement.querySelector(
      '.ui-explorer-composer__footer .primary-button'
    ) as HTMLButtonElement;
    expect(runButton.disabled).toBe(false);
    runButton.click();

    const startRequest = http.expectOne('/api/ui-explorer/jobs');
    expect(startRequest.request.method).toBe('POST');
    expect(startRequest.request.body).toEqual({
      systemId: 'crm-agent-portal',
      branch: 'main',
      screenId: 'crm-contact-create',
      sourceRevision: 'crm-revision-a1b2c3',
      sectionModes: {
        OVERVIEW: 'DEEP',
        NAVIGATION_AND_ACCESS: 'COMPACT',
        SCREEN_STRUCTURE: 'COMPACT',
        ACTIONS_AND_OUTCOMES: 'COMPACT',
        FORMS_AND_RULES: 'COMPACT',
        DATA_AND_SERVICES: 'COMPACT',
        STATE_AND_SYNCHRONIZATION: 'COMPACT',
        VARIANTS_AND_FAILURES: 'COMPACT'
      },
      scenarioDescription: 'Describe the anonymized CRM contact creation flow.',
      model: 'crm-doc-model',
      reasoningEffort: 'medium'
    });
    startRequest.flush(crmJobSnapshot('QUEUED'));

    const pollingRequest = http.expectOne('/api/ui-explorer/jobs/crm-ui-job-1');
    expect(pollingRequest.request.method).toBe('GET');
    pollingRequest.flush(crmJobSnapshot('COMPLETED'));
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('UI Explorer: Utworzenie kontaktu CRM');
    expect(compiled.textContent).toContain('Co robi ten widok');
    expect(compiled.textContent).toContain('Formularz kontaktu CRM zachowuje reguły segmentu.');
    expect(compiled.querySelector('app-ui-explorer-result')).not.toBeNull();
    expect(compiled.textContent).toContain('CRM Agent Portal · main · crm-revision-a1b2c3');
    expect(compiled.querySelector('app-analysis-feature-aside')).not.toBeNull();
    expect(compiled.querySelectorAll('app-analysis-steps-panel')).toHaveLength(3);
    http.verify();
  });

  it('shows explicit blocked and failed terminal states without inventing a report', async () => {
    const fixture = TestBed.createComponent(UiExplorerPageComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    flushPlatformConfig(http);
    http.expectOne('/api/ui-explorer/input-options').flush(crmInputOptions());
    http.expectOne((request) => request.url === '/api/ui-explorer/screens').flush(
      crmScreenCatalog('main', 'crm-revision-a1b2c3')
    );
    http.expectOne('/api/analysis/ai/options').flush(crmAiOptions());
    await fixture.whenStable();

    fixture.componentInstance.facade.job.set(crmJobSnapshot('BLOCKED'));
    fixture.detectChanges();
    let compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('UI Explorer run is blocked');
    expect(compiled.textContent).toContain('CRM source context was insufficient for a safe report.');
    expect(compiled.querySelector('app-ui-explorer-result')).toBeNull();

    fixture.componentInstance.facade.job.set(crmJobSnapshot('FAILED'));
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('UI Explorer run failed');
    expect(compiled.textContent).toContain('CRM report generation failed without exposing source details.');
    expect(compiled.querySelector('app-ui-explorer-result')).toBeNull();
    http.verify();
  });

  it('opens a completed CRM run from Analysis History as a read-only report after restart', async () => {
    queryParamMap.next(convertToParamMap({ localRunId: 'crm-ui-history-1' }));
    const fixture = TestBed.createComponent(UiExplorerPageComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    flushPlatformConfig(http);
    http.expectOne('/api/ui-explorer/input-options').flush(crmInputOptions());
    http.expectOne((request) => request.url === '/api/ui-explorer/screens').flush(
      crmScreenCatalog('main', 'crm-revision-a1b2c3')
    );
    http.expectOne('/api/analysis/ai/options').flush(crmAiOptions());
    const historyRequest = http.expectOne('/api/analysis/runs/crm-ui-history-1');
    expect(historyRequest.request.method).toBe('GET');
    historyRequest.flush({
      analysisId: 'crm-ui-history-1',
      feature: 'ui-explorer',
      name: 'CRM contact creation documentation',
      status: 'COMPLETED',
      createdAt: '2026-08-15T10:00:00Z',
      updatedAt: '2026-08-15T10:02:00Z',
      completedAt: '2026-08-15T10:02:00Z',
      exportEnvelope: crmLocalEnvelope(),
      continuationEnabled: false
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Analysis History · CRM contact creation documentation');
    expect(compiled.textContent).toContain('UI Explorer · read-only');
    expect(compiled.textContent).toContain('Konfiguracja i raport zostały odtworzone bez wznawiania sesji AI.');
    expect(compiled.querySelector('app-ui-explorer-configuration')).toBeNull();
    expect(compiled.querySelector('app-ui-explorer-result')).not.toBeNull();
    expect(fixture.componentInstance.facade.isReadOnlyResult()).toBe(true);
    http.expectNone('/api/ui-explorer/jobs/crm-ui-history-1');
    http.verify();
  });

  it('imports a server-validated CRM export and renders it read-only', async () => {
    const fixture = TestBed.createComponent(UiExplorerPageComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    flushPlatformConfig(http);
    http.expectOne('/api/ui-explorer/input-options').flush(crmInputOptions());
    http.expectOne((request) => request.url === '/api/ui-explorer/screens').flush(
      crmScreenCatalog('main', 'crm-revision-a1b2c3')
    );
    http.expectOne('/api/analysis/ai/options').flush(crmAiOptions());
    const portable = crmPortableEnvelope();
    fixture.componentInstance.facade.importAnalysis(portable, 'crm-contact-documentation.json');
    const importRequest = http.expectOne('/api/ui-explorer/imports');
    expect(importRequest.request.body).toEqual(portable);
    importRequest.flush({ ...crmJobSnapshot('COMPLETED'), jobId: 'ui-explorer-import-crm-1' });
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Imported JSON · crm-contact-documentation.json');
    expect(compiled.querySelector('app-ui-explorer-configuration')).toBeNull();
    expect(compiled.querySelector('app-ui-explorer-result')).not.toBeNull();
    expect(fixture.componentInstance.facade.resultSource()?.origin).toBe('imported');
    http.verify();
  });

  it('exports a live completed CRM result through the versioned backend contract', async () => {
    const fixture = TestBed.createComponent(UiExplorerPageComponent);
    const http = TestBed.inject(HttpTestingController);
    const createObjectUrlSpy = vi.fn(() => 'blob:crm-ui-export');
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: createObjectUrlSpy
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn()
    });
    fixture.detectChanges();

    flushPlatformConfig(http);
    http.expectOne('/api/ui-explorer/input-options').flush(crmInputOptions());
    http.expectOne((request) => request.url === '/api/ui-explorer/screens').flush(
      crmScreenCatalog('main', 'crm-revision-a1b2c3')
    );
    http.expectOne('/api/analysis/ai/options').flush(crmAiOptions());
    fixture.componentInstance.facade.job.set(crmJobSnapshot('COMPLETED'));
    fixture.componentInstance.facade.resultSource.set({
      origin: 'live',
      exportedAt: '',
      fileName: ''
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const exportButton = Array.from(
      compiled.querySelectorAll<HTMLButtonElement>('.ui-explorer-run__header-actions button')
    ).find((button) => button.textContent?.includes('Export JSON'));
    expect(exportButton).toBeDefined();
    exportButton?.click();
    const exportRequest = http.expectOne('/api/ui-explorer/jobs/crm-ui-job-1/export');
    expect(exportRequest.request.method).toBe('GET');
    exportRequest.flush(crmPortableEnvelope());
    await fixture.whenStable();

    expect(createObjectUrlSpy).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    http.verify();
  });
});

function flushPlatformConfig(http: HttpTestingController): void {
  http.expectOne('/api/ui/config').flush({
    title: 'CRM Workspace',
    subtitle: 'Team Delivery Workspace',
    defaultTitle: 'Team Delivery Workspace',
    defaultBranch: 'main'
  });
}

function crmInputOptions(): UiExplorerInputOptionsResponse {
  const sections: UiExplorerInputOptionsResponse['sections'] = [
    ['OVERVIEW', 'Cel i kontekst widoku'],
    ['NAVIGATION_AND_ACCESS', 'Nawigacja i dostęp'],
    ['SCREEN_STRUCTURE', 'Struktura widoku'],
    ['ACTIONS_AND_OUTCOMES', 'Akcje i rezultaty'],
    ['FORMS_AND_RULES', 'Formularze i reguły'],
    ['DATA_AND_SERVICES', 'Dane i usługi'],
    ['STATE_AND_SYNCHRONIZATION', 'Stan i synchronizacja'],
    ['VARIANTS_AND_FAILURES', 'Warianty i sytuacje wyjątkowe']
  ].map(([sectionId, label]) => ({
    sectionId: sectionId as UiExplorerInputOptionsResponse['sections'][number]['sectionId'],
    label,
    description: `Syntetyczny zakres CRM: ${label.toLocaleLowerCase()}.`
  }));

  return {
    featureId: 'ui-explorer',
    executionAvailability: {
      status: 'AVAILABLE',
      code: 'READY',
      message: 'CRM UI documentation is available.',
      missingCapabilities: []
    },
    systems: [
      { systemId: 'crm-agent-portal', label: 'CRM Agent Portal', summary: 'Syntetyczny frontend CRM.' }
    ],
    defaultSectionModes: sections.map((section) => ({
      sectionId: section.sectionId,
      mode: section.sectionId === 'OVERVIEW' ? 'DEEP' : 'COMPACT'
    })),
    sections,
    modes: [
      { mode: 'OFF', label: 'Pomiń', description: 'Nie uwzględniaj sekcji.' },
      { mode: 'COMPACT', label: 'Skrót', description: 'Najważniejsze informacje.' },
      { mode: 'DEEP', label: 'Pogłęb', description: 'Szczegółowe opracowanie.' }
    ],
    configurationFindings: []
  };
}

function crmScreenCatalog(branch: string, revision: string): UiExplorerScreenCatalogResponse {
  return {
    systemId: 'crm-agent-portal',
    systemLabel: 'CRM Agent Portal',
    sourceRevision: { branch, revision },
    status: 'READY',
    screens: [
      {
        screenId: 'crm-contact-create',
        label: 'Utworzenie kontaktu CRM',
        routePattern: '/contacts/new',
        parentRoutePattern: '/contacts',
        status: 'READY',
        lazyLoaded: true,
        guards: ['crm-role-guard'],
        routeParameters: [],
        limitations: []
      }
    ],
    diagnostics: [],
    limitations: [],
    boundary: {
      visitedRouteNodeCount: 3,
      visitedRouteFileCount: 3,
      sourceReadCount: 12,
      aliasResolutionCount: 4,
      unresolvedEdgeCount: 0,
      limitReached: false,
      maxRouteNodes: 400,
      maxRouteFiles: 80,
      maxSourceReads: 300,
      maxAliasResolutions: 500,
      maxImportDepth: 12
    }
  };
}

function crmAiOptions() {
  return {
    defaultModel: 'crm-doc-model',
    defaultReasoningEffort: 'medium',
    defaultReasoningEfforts: ['low', 'medium'],
    models: [
      {
        id: 'crm-doc-model',
        name: 'CRM Documentation Model',
        supportsReasoningEffort: true,
        reasoningEfforts: ['low', 'medium'],
        defaultReasoningEffort: 'medium'
      }
    ]
  };
}

function crmJobSnapshot(status: UiExplorerJobStateSnapshot['status']): UiExplorerJobStateSnapshot {
  const terminal = ['COMPLETED', 'PARTIAL', 'BLOCKED', 'FAILED'].includes(status);
  const reportAvailable = status === 'COMPLETED' || status === 'PARTIAL';
  return {
    jobId: 'crm-ui-job-1',
    request: {
      systemId: 'crm-agent-portal',
      systemLabel: 'CRM Agent Portal',
      branch: 'main',
      screenId: 'crm-contact-create',
      sourceRevision: 'crm-revision-a1b2c3',
      sectionModes: [
        { sectionId: 'OVERVIEW', mode: 'DEEP' },
        { sectionId: 'FORMS_AND_RULES', mode: 'COMPACT' }
      ],
      scenarioDescription: 'Describe the anonymized CRM contact creation flow.',
      aiModel: 'crm-doc-model',
      reasoningEffort: 'medium'
    },
    status,
    currentStepCode: terminal ? null : 'SCREEN_DISCOVERY',
    currentStepLabel: terminal ? null : 'Recognizing CRM view',
    errorCode: status === 'BLOCKED' ? 'CRM_CONTEXT_BLOCKED' : status === 'FAILED' ? 'CRM_RUN_FAILED' : null,
    errorMessage:
      status === 'BLOCKED'
        ? 'CRM source context was insufficient for a safe report.'
        : status === 'FAILED'
          ? 'CRM report generation failed without exposing source details.'
          : null,
    createdAt: '2026-08-15T10:00:00Z',
    updatedAt: '2026-08-15T10:00:01Z',
    completedAt: terminal ? '2026-08-15T10:00:02Z' : null,
    steps: [
      {
        code: 'SCREEN_DISCOVERY',
        label: 'Recognize CRM view',
        phase: 'CONTEXT',
        status: terminal ? 'COMPLETED' : 'IN_PROGRESS',
        message: terminal ? 'CRM view recognized.' : 'Recognizing CRM view.',
        itemCount: terminal ? 1 : null,
        startedAt: '2026-08-15T10:00:00Z',
        completedAt: terminal ? '2026-08-15T10:00:01Z' : ''
      }
    ],
    contextSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    toolFeedback: [],
    preparedPrompt: null,
    result: reportAvailable ? crmResult() : null,
    report: reportAvailable ? crmReport() : null,
    usage: null,
    sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' },
    outputAvailability: {
      status: reportAvailable ? 'AVAILABLE' : 'BLOCKED',
      code: reportAvailable ? 'READY' : terminal ? 'UNAVAILABLE' : 'IN_PROGRESS',
      message: reportAvailable
        ? 'CRM documentation is ready.'
        : terminal
          ? 'CRM documentation is unavailable.'
          : 'CRM documentation is being prepared.',
      missingCapabilities: []
    },
    exportAvailable: reportAvailable
  };
}

function crmReport(): NonNullable<UiExplorerJobStateSnapshot['report']> {
  return {
    reportId: 'crm-ui-report-1',
    header: 'UI Explorer: Utworzenie kontaktu CRM',
    subHeader: 'main @ crm-revision-a1b2c3',
    markdownSummary: 'Widok umożliwia utworzenie zanonimizowanego kontaktu CRM.',
    sections: [
      {
        id: 'OVERVIEW',
        title: 'Cel i kontekst widoku',
        order: 0,
        markdown: 'Widok rozpoczyna obsługę nowego kontaktu CRM.',
        meta: crmReportMeta('CONFIRMED')
      },
      {
        id: 'FORMS_AND_RULES',
        title: 'Formularze i reguły',
        order: 4,
        markdown: 'Formularz kontaktu CRM zachowuje reguły segmentu.',
        meta: crmReportMeta('INFERRED')
      }
    ],
    meta: {
      ...crmReportMeta('INFERRED'),
      visibilityLimits: ['Reguły segmentu dostarczane runtime nie były widoczne.'],
      openQuestions: ['Która rola CRM zatwierdza kontakt po zapisie?']
    }
  };
}

function crmReportMeta(confidence: string) {
  return {
    references: [
      {
        type: 'source',
        label: 'CrmContactForm',
        target: 'crm-agent-portal:src/app/contacts/contact-form.ts#L12-L48',
        description: 'Anonymized CRM UI source'
      }
    ],
    visibilityLimits: [],
    openQuestions: [],
    gaps: [],
    confidence,
    warnings: []
  };
}

function crmResult(): NonNullable<UiExplorerJobStateSnapshot['result']> {
  return {
    screen: {
      systemId: 'crm-agent-portal',
      screenId: 'crm-contact-create',
      label: 'Utworzenie kontaktu CRM',
      routePattern: '/contacts/new',
      navigationContext: 'Kontakty CRM > Nowy kontakt'
    },
    scenarioDescription: 'Describe the anonymized CRM contact creation flow.',
    sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' },
    functionalOverview: 'Widok umożliwia utworzenie zanonimizowanego kontaktu CRM.',
    sections: [],
    crossSectionDependencies: [
      {
        sourceSection: 'FORMS_AND_RULES',
        targetSection: 'ACTIONS_AND_OUTCOMES',
        description: 'Walidacja segmentu CRM kontroluje dostępność zapisu.'
      }
    ],
    overallConfidence: 'INFERRED',
    visibilityLimits: ['Reguły segmentu dostarczane runtime nie były widoczne.'],
    unresolvedQuestions: ['Która rola CRM zatwierdza kontakt po zapisie?'],
    usage: null
  };
}

function crmLocalEnvelope() {
  return {
    schema: 'tdw.ui-explorer-local-run',
    version: 3,
    storedAt: '2026-08-15T10:02:00Z',
    payload: {
      type: 'ui-explorer-analysis',
      resultContract: 'ui-explorer-result-v3',
      job: crmJobSnapshot('COMPLETED')
    }
  };
}

function crmPortableEnvelope() {
  return {
    schema: 'tdw.ui-explorer-export',
    version: 3,
    exportedAt: '2026-08-15T10:03:00Z',
    payload: {
      type: 'ui-explorer-analysis',
      resultContract: 'ui-explorer-result-v3',
      job: crmJobSnapshot('COMPLETED')
    }
  };
}
