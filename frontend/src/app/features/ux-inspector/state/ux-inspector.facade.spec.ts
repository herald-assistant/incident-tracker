import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AiOptionsApiService } from '../../../core/services/ai-options-api.service';
import { AnalysisJobPollingOptions, AnalysisJobPollingService } from '../../../core/services/analysis-job-polling.service';
import { AnalysisRunHistoryApiService } from '../../../core/services/analysis-run-history-api.service';
import { UxInspectorCapture, UxInspectorJobStartRequest, UxInspectorJobStateSnapshot, UxInspectorVisibleFormField } from '../models/ux-inspector.models';
import { UxInspectorApiService } from '../services/ux-inspector-api.service';
import { UxInspectorCaptureIngressService } from '../services/ux-inspector-capture-ingress.service';
import { UxInspectorFacade } from './ux-inspector.facade';

describe('UxInspectorFacade', () => {
  const captureSignal = signal<UxInspectorCapture | null>(captureFixture());
  const ingress = {
    capture: captureSignal,
    status: signal<'received' | 'idle'>('received'),
    error: signal(''),
    storeState: signal<Record<string, unknown> | null>(null),
    storeStatus: signal<'unavailable' | 'receiving' | 'available'>('unavailable'),
    formFields: signal<UxInspectorVisibleFormField[] | null>(null),
    formStatus: signal<'unavailable' | 'receiving' | 'available'>('unavailable'),
    start: vi.fn(),
    consumeCapture: vi.fn(() => captureSignal.set(null))
  };
  const api = {
    getInputOptions: vi.fn(() => of({
      featureId: 'ux-inspector' as const,
      systems: [{ systemId: 'crm-agent-portal', label: 'CRM Agent Portal', summary: 'Fikcyjny CRM', defaultBranch: 'main' }],
      configurationFindings: []
    })),
    getViews: vi.fn(() => of({
      systemId: 'crm-agent-portal', systemLabel: 'CRM Agent Portal',
      sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' }, status: 'READY',
      views: [{ viewId: 'crm-contact-create', label: 'Nowy kontakt', routePattern: '/contacts/new',
        componentSelectors: ['crm-contact-create'], status: 'READY', limitations: [] }],
      diagnostics: [], limitations: []
    })),
    startJob: vi.fn((_request: UxInspectorJobStartRequest) => of(snapshot('QUEUED'))),
    uploadStoreSnapshot: vi.fn(() => of({ storeSnapshotRef: 'store-ref' })),
    uploadFormFieldsSnapshot: vi.fn(() => of({ formFieldsSnapshotRef: 'form-ref' })),
    getJob: vi.fn(() => of(snapshot('COMPLETED'))),
    sendChatMessage: vi.fn(() => of(chatSnapshot())),
    exportJob: vi.fn(), importAnalysis: vi.fn()
  };
  const polling = { poll: vi.fn(<T>(options: AnalysisJobPollingOptions<T>) => options.load()) };
  const history = { getRun: vi.fn(), sendChatMessage: vi.fn() };
  const aiOptions = { getOptions: vi.fn(() => of({
    defaultModel: 'gpt-crm', defaultReasoningEffort: 'medium', defaultReasoningEfforts: ['low', 'medium'],
    models: [{ id: 'gpt-crm', name: 'CRM model', supportsReasoningEffort: true,
      reasoningEfforts: ['low', 'medium'], defaultReasoningEffort: 'medium' }]
  })) };

  beforeEach(() => {
    vi.clearAllMocks();
    captureSignal.set(captureFixture());
    ingress.status.set('received');
    ingress.storeState.set(null);
    ingress.storeStatus.set('unavailable');
    ingress.formStatus.set('unavailable');
    ingress.formFields.set(null);
    TestBed.configureTestingModule({ providers: [
      UxInspectorFacade,
      { provide: UxInspectorApiService, useValue: api },
      { provide: UxInspectorCaptureIngressService, useValue: ingress },
      { provide: AnalysisRunHistoryApiService, useValue: history },
      { provide: AnalysisJobPollingService, useValue: polling },
      { provide: AiOptionsApiService, useValue: aiOptions }
    ] });
  });

  it('requires capture and sends the exact capture v2 with confirmed source selection', () => {
    const facade = TestBed.inject(UxInspectorFacade);
    facade.initialize();
    facade.loadViews();
    facade.selectView('crm-contact-create');
    facade.updateQuestion('Dlaczego przycisk jest zablokowany?');

    expect(facade.canStartJob()).toBe(true);
    facade.startJob();

    expect(api.startJob).toHaveBeenCalledWith({
      systemId: 'crm-agent-portal', branch: 'main', viewId: 'crm-contact-create',
      sourceRevision: 'crm-revision-a1b2c3', question: 'Dlaczego przycisk jest zablokowany?',
      capture: captureFixture(), model: 'gpt-crm', reasoningEffort: 'medium'
    });
    expect(ingress.consumeCapture).toHaveBeenCalledTimes(1);
    expect(facade.startedRunId()).toBe('ux-crm-job');
    expect(polling.poll).toHaveBeenCalledTimes(1);
    expect(facade.job()?.status).toBe('COMPLETED');
    expect(facade.capture()?.captureId).toBe('cap_crm_contact_save');
  });

  it('uploads the captured store before starting the job', () => {
    ingress.storeState.set({ contact: { editable: false } });
    ingress.storeStatus.set('available');
    const facade = TestBed.inject(UxInspectorFacade);
    facade.initialize();
    facade.loadViews();
    facade.selectView('crm-contact-create');
    facade.updateQuestion('Dlaczego nie można edytować kontaktu?');
    facade.startJob();
    expect(api.uploadStoreSnapshot).toHaveBeenCalledWith({
      captureId: 'cap_crm_contact_save', origin: 'https://crm.example.com',
      state: { contact: { editable: false } }
    });
    expect(api.startJob).toHaveBeenCalledWith(expect.objectContaining({ storeSnapshotRef: 'store-ref' }));
  });

  it('uploads captured visible fields before the job', () => {
    const field: UxInspectorVisibleFormField = {
      tag: 'input', type: 'text', name: 'contactName', id: 'contactName', testId: '', label: 'Kontakt',
      disabled: false, value: 'CRM contact', display: 'CRM contact', source: 'dom', checked: null,
      indeterminate: null, invalid: null, errors: [], descriptions: [], nativeInvalid: null,
      nativeValidationMessage: ''
    };
    ingress.formFields.set([field]);
    ingress.formStatus.set('available');
    const facade = TestBed.inject(UxInspectorFacade);
    facade.initialize();
    facade.loadViews();
    facade.selectView('crm-contact-create');
    facade.updateQuestion('Jakie pola są widoczne?');
    facade.startJob();
    expect(api.uploadFormFieldsSnapshot).toHaveBeenCalledWith({
      captureId: 'cap_crm_contact_save', origin: 'https://crm.example.com', fields: [field]
    });
    expect(api.startJob).toHaveBeenCalledWith(expect.objectContaining({ formFieldsSnapshotRef: 'form-ref' }));
  });

  it('starts the job without form fields when their upload fails', () => {
    ingress.formFields.set([]);
    ingress.formStatus.set('available');
    api.uploadFormFieldsSnapshot.mockReturnValueOnce(throwError(() => new Error('upload failed')));
    const facade = TestBed.inject(UxInspectorFacade);
    facade.initialize();
    facade.loadViews();
    facade.selectView('crm-contact-create');
    facade.updateQuestion('Jakie pola są widoczne?');
    facade.startJob();
    expect(api.startJob).toHaveBeenCalledWith(expect.not.objectContaining({ formFieldsSnapshotRef: expect.anything() }));
  });

  it('starts a job without store when upload fails', () => {
    ingress.storeState.set({ contact: { editable: false } });
    ingress.storeStatus.set('available');
    api.uploadStoreSnapshot.mockReturnValueOnce(throwError(() => new Error('upload failed')));
    const facade = TestBed.inject(UxInspectorFacade);
    facade.initialize();
    facade.loadViews();
    facade.selectView('crm-contact-create');
    facade.updateQuestion('Dlaczego nie można edytować kontaktu?');
    facade.startJob();
    expect(api.startJob).toHaveBeenCalledWith(expect.not.objectContaining({ storeSnapshotRef: expect.anything() }));
    expect(facade.startedRunId()).toBe('ux-crm-job');
  });

  it('does not create a manual or capture-less start path', () => {
    captureSignal.set(null);
    ingress.status.set('idle');
    const facade = TestBed.inject(UxInspectorFacade);
    facade.initialize();
    facade.loadViews();
    facade.selectView('crm-contact-create');
    facade.updateQuestion('Skad sa dane?');

    facade.startJob();

    expect(api.startJob).not.toHaveBeenCalled();
    expect(facade.jobError()).toContain('TDW Browser Tools');
  });

  it('points to Workspace Settings when a PAT blocks the run', () => {
    api.startJob.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 503,
      error: { code: 'COPILOT_PAT_REQUIRED', message: 'Dodaj fine-grained PAT.' }
    })));
    const facade = TestBed.inject(UxInspectorFacade);
    facade.initialize();
    facade.loadViews();
    facade.selectView('crm-contact-create');
    facade.updateQuestion('Dlaczego przycisk jest zablokowany?');

    facade.startJob();

    expect(facade.job()).toBeNull();
    expect(facade.jobError()).toBe('Dodaj fine-grained PAT.');
    expect(facade.authSettingsUrl()).toBe('/workspace-settings');
  });

  it('loads the cached view catalog after branch confirmation and explicitly refreshes it on demand', () => {
    const facade = TestBed.inject(UxInspectorFacade);
    facade.initialize();

    facade.changeBranch('main');

    expect(api.getViews).toHaveBeenCalledWith('crm-agent-portal', 'main', false);
    expect(facade.viewState()).toBe('ready');

    facade.loadViews(true);

    expect(api.getViews).toHaveBeenLastCalledWith('crm-agent-portal', 'main', true);
  });

  it('selects the unique best view from the captured route after the catalog loads', () => {
    api.getViews.mockReturnValue(of(viewCatalog([
      view('crm-contact-details', '/contacts/:contactId'),
      view('crm-contact-create', '/contacts/new')
    ])));
    const facade = TestBed.inject(UxInspectorFacade);

    facade.initialize();
    facade.loadViews();
    TestBed.tick();

    expect(facade.selectedViewId()).toBe('crm-contact-create');
    expect(facade.viewMatchedFromCapture()).toBe(true);
  });

  it('selects the matching view when capture arrives after the catalog', () => {
    captureSignal.set(null);
    api.getViews.mockReturnValue(of(viewCatalog([
      view('crm-contact-details', '/contacts/:contactId'),
      view('crm-contact-create', '/contacts/new')
    ])));
    const facade = TestBed.inject(UxInspectorFacade);
    facade.initialize();
    facade.loadViews();
    TestBed.tick();

    captureSignal.set(captureFixture());
    TestBed.tick();

    expect(facade.selectedViewId()).toBe('crm-contact-create');
    expect(facade.viewMatchedFromCapture()).toBe(true);
  });

  it('leaves the view empty when captured route matching is ambiguous', () => {
    captureSignal.set({
      ...captureFixture(),
      page: { ...captureFixture().page, path: '/contacts/customer-a7' }
    });
    api.getViews.mockReturnValue(of(viewCatalog([
      view('crm-contact-by-id', '/contacts/:contactId'),
      view('crm-contact-by-key', '/contacts/:contactKey')
    ])));
    const facade = TestBed.inject(UxInspectorFacade);

    facade.initialize();
    facade.loadViews();
    TestBed.tick();

    expect(facade.selectedViewId()).toBe('');
    expect(facade.viewMatchedFromCapture()).toBe(false);
  });

  it('uses the nearest captured component boundary to resolve equally specific views', () => {
    captureSignal.set({
      ...captureFixture(),
      page: { ...captureFixture().page, path: '/contacts/customer-a7' },
      target: {
        ...captureFixture().target,
        domFingerprint: {
          ...captureFixture().target.domFingerprint,
          componentBoundaryTags: ['crm-contact-details', 'crm-contact-shell']
        }
      }
    });
    api.getViews.mockReturnValue(of(viewCatalog([
      view('crm-contact-shell', '/contacts/:contactId', ['crm-contact-shell']),
      view('crm-contact-details', '/contacts/:contactKey', ['crm-contact-details'])
    ])));
    const facade = TestBed.inject(UxInspectorFacade);

    facade.initialize();
    facade.loadViews();
    TestBed.tick();

    expect(facade.selectedViewId()).toBe('crm-contact-details');
    expect(facade.viewMatchedFromCapture()).toBe(true);
  });

  it('keeps a manual override and removes the capture-route marker', () => {
    api.getViews.mockReturnValue(of(viewCatalog([
      view('crm-contact-details', '/contacts/:contactId'),
      view('crm-contact-create', '/contacts/new')
    ])));
    const facade = TestBed.inject(UxInspectorFacade);
    facade.initialize();
    facade.loadViews();
    TestBed.tick();

    facade.selectView('crm-contact-details');
    TestBed.tick();

    expect(facade.selectedViewId()).toBe('crm-contact-details');
    expect(facade.viewMatchedFromCapture()).toBe(false);
  });

  it('continues a live run through the UX Inspector chat endpoint', () => {
    const facade = TestBed.inject(UxInspectorFacade);
    facade.job.set(chatSnapshot());
    facade.resultSource.set({ origin: 'live', fileName: '' });

    expect(facade.canUseChat()).toBe(true);
    facade.sendChatMessage('Co jeszcze blokuje zapis?');

    expect(api.sendChatMessage).toHaveBeenCalledWith('ux-crm-job', 'Co jeszcze blokuje zapis?');
    expect(facade.chatMessages().at(-1)?.content).toContain('Uprawnienie');
  });

  it('does not show analysis progress while polling a completed run for chat', () => {
    const facade = TestBed.inject(UxInspectorFacade);
    facade.job.set(chatSnapshot());
    facade.pollingActive.set(true);

    expect(facade.workflowIsRunning()).toBe(false);
  });
});

function view(viewId: string, routePattern: string, componentSelectors: string[] = []) {
  return { viewId, label: viewId, routePattern, componentSelectors, status: 'READY', limitations: [] };
}

function viewCatalog(views: ReturnType<typeof view>[]) {
  return {
    systemId: 'crm-agent-portal', systemLabel: 'CRM Agent Portal',
    sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' }, status: 'READY',
    views, diagnostics: [], limitations: []
  };
}

function captureFixture(): UxInspectorCapture {
  return {
    schema: 'tdw.ux-inspector-capture', version: 2, captureId: 'cap_crm_contact_save', capturedAt: '2026-09-15T10:00:00Z',
    captureProfile: 'ELEMENT_CONTEXT',
    page: { origin: 'https://crm.example.com', path: '/contacts/new', title: 'CRM', language: 'pl', queryParameterNames: [] },
    target: { tag: 'button', role: 'button', accessibleName: 'Zapisz kontakt', text: 'Zapisz kontakt',
      domFingerprint: { stableAttributes: { 'data-testid': 'contact-save' },
        selectorCandidates: ['button[data-testid="contact-save"]'], componentBoundaryTags: [], labelFor: null },
      state: { disabled: true, ariaDisabled: false, readOnly: false, required: false, invalid: false, checked: null, expanded: null, hidden: false },
      bounds: { x: 20, y: 40, width: 180, height: 42 } },
    ancestors: [],
    traversal: { observedDepth: 2, emittedNodeCount: 1, omittedNodeCount: 1, reachedDocumentRoot: true },
    signals: { shadowBoundaryCount: 0, frame: 'TOP_LEVEL', redactions: [] }, limits: [],
    client: { name: 'TDW UX Inspector', version: '1.0.0', featureId: 'ux-inspector' }
  };
}

function snapshot(status: UxInspectorJobStateSnapshot['status']): UxInspectorJobStateSnapshot {
  const terminal = status === 'COMPLETED';
  return {
    jobId: 'ux-crm-job', request: { systemId: 'crm-agent-portal', systemLabel: 'CRM Agent Portal', branch: 'main',
      viewId: 'crm-contact-create', sourceRevision: 'crm-revision-a1b2c3', question: 'Dlaczego przycisk jest zablokowany?',
      capture: captureFixture(), aiModel: 'gpt-crm', reasoningEffort: 'medium', targetResolutionStatus: 'RESOLVED', targetCandidateCount: 1 },
    status, currentStepCode: terminal ? null : 'TARGET_RESOLUTION', currentStepLabel: terminal ? null : 'Resolve target',
    errorCode: null, errorMessage: null, createdAt: '2026-09-15T10:00:00Z', updatedAt: '2026-09-15T10:00:01Z',
    completedAt: terminal ? '2026-09-15T10:00:02Z' : null, steps: [], contextSections: [], toolEvidenceSections: [],
    aiActivityEvents: [], toolFeedback: [], preparedPrompt: null, result: null, report: null, usage: null,
    sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' },
    outputAvailability: { status: 'BLOCKED', code: terminal ? 'DONE' : 'RUNNING', message: '', missingCapabilities: [] },
    exportAvailable: terminal
  };
}

function chatSnapshot(): UxInspectorJobStateSnapshot {
  return {
    ...snapshot('COMPLETED'),
    result: {} as UxInspectorJobStateSnapshot['result'],
    report: { reportId: 'ux-inspector-report-crm', header: 'UX Inspector', subHeader: 'Kontakt', markdownSummary: 'Zapis kontaktu',
      sections: [], meta: { references: [], visibilityLimits: [], openQuestions: [], gaps: [], confidence: 'high', warnings: [] } },
    chatMessages: [{ id: 'assistant-1', role: 'ASSISTANT', status: 'COMPLETED',
      content: 'Uprawnienie operatora wpływa na zapis.', errorCode: '', errorMessage: '',
      createdAt: '2026-09-15T10:00:03Z', updatedAt: '2026-09-15T10:00:04Z', completedAt: '2026-09-15T10:00:04Z',
      toolEvidenceSections: [], aiActivityEvents: [], toolFeedback: [], prompt: '', usage: null }],
    chatAvailability: { available: true, code: 'UX_INSPECTOR_CHAT_AVAILABLE', message: null }
  };
}
