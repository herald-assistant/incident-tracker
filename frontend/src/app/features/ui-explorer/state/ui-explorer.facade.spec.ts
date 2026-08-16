import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { NEVER, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AiOptionsApiService } from '../../../core/services/ai-options-api.service';
import { AnalysisJobPollingOptions, AnalysisJobPollingService } from '../../../core/services/analysis-job-polling.service';
import { AnalysisRunHistoryApiService } from '../../../core/services/analysis-run-history-api.service';
import { AppUiConfigService } from '../../../core/services/app-ui-config.service';
import {
  UiExplorerInputOptionsResponse,
  UiExplorerJobStartRequest,
  UiExplorerJobStateSnapshot,
  UiExplorerScreenCatalogResponse
} from '../models/ui-explorer.models';
import { UiExplorerApiService } from '../services/ui-explorer-api.service';
import { UiExplorerFacade } from './ui-explorer.facade';

describe('UiExplorerFacade', () => {
  const appUiConfig = signal({
    title: 'CRM Workspace',
    subtitle: 'Team Delivery Workspace',
    defaultTitle: 'Team Delivery Workspace',
    defaultBranch: 'main'
  });
  const appUiConfigService = {
    config: appUiConfig,
    load: vi.fn()
  };
  const inputOptions = crmInputOptions();
  const screenCatalog = crmScreenCatalog();
  const api = {
    getInputOptions: vi.fn(() => of(inputOptions)),
    getScreens: vi.fn(() => of(screenCatalog)),
    startJob: vi.fn((_request: UiExplorerJobStartRequest) => of(crmJobSnapshot('QUEUED'))),
    getJob: vi.fn(() => of(crmJobSnapshot('COMPLETED'))),
    exportJob: vi.fn(() => of(crmPortableEnvelope())),
    importAnalysis: vi.fn((_document: unknown) => of(crmReadableSnapshot('COMPLETED')))
  };
  const historyApi = {
    getRun: vi.fn(() =>
      of({
        analysisId: 'crm-ui-history-1',
        feature: 'ui-explorer',
        name: 'CRM contact creation documentation',
        status: 'COMPLETED',
        createdAt: '2026-08-15T10:00:00Z',
        updatedAt: '2026-08-15T10:02:00Z',
        completedAt: '2026-08-15T10:02:00Z',
        exportEnvelope: crmLocalEnvelope(),
        continuationEnabled: false
      })
    )
  };
  const polling = {
    poll: vi.fn(<T>(options: AnalysisJobPollingOptions<T>) => options.load())
  };
  const aiOptionsApi = {
    getOptions: vi.fn(() =>
      of({
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
      })
    )
  };

  beforeEach(() => {
    vi.clearAllMocks();
    appUiConfig.update((config) => ({ ...config, defaultBranch: 'main' }));
    TestBed.configureTestingModule({
      providers: [
        UiExplorerFacade,
        { provide: UiExplorerApiService, useValue: api },
        { provide: AnalysisRunHistoryApiService, useValue: historyApi },
        { provide: AiOptionsApiService, useValue: aiOptionsApi },
        { provide: AnalysisJobPollingService, useValue: polling },
        { provide: AppUiConfigService, useValue: appUiConfigService }
      ]
    });
  });

  it('keeps a CRM branch selected before shared platform config is applied', () => {
    appUiConfig.update((config) => ({ ...config, defaultBranch: 'crm-release' }));
    const facade = TestBed.inject(UiExplorerFacade);
    facade.changeBranch('crm-review');

    facade.initialize();

    expect(facade.branch()).toBe('crm-review');
    expect(api.getScreens).toHaveBeenCalledWith('crm-agent-portal', 'crm-review');
  });

  it('builds real defaults and automatically loads screens for the selected CRM frontend', () => {
    const facade = TestBed.inject(UiExplorerFacade);

    facade.initialize();

    expect(facade.selectedSystemId()).toBe('crm-agent-portal');
    expect(facade.sectionModes()).toEqual({ OVERVIEW: 'DEEP', FORMS_AND_RULES: 'COMPACT' });
    expect(facade.selectedModel()).toBe('crm-doc-model');
    expect(facade.selectedReasoningEffort()).toBe('medium');
    expect(api.getScreens).toHaveBeenCalledWith('crm-agent-portal', 'main');
    expect(facade.sourceRevision()?.revision).toBe('crm-revision-a1b2c3');
  });

  it('clears the selected screen and source revision when the ref changes', () => {
    const facade = TestBed.inject(UiExplorerFacade);
    facade.initialize();
    facade.selectScreen('crm-contact-create');
    expect(facade.configurationReady()).toBe(true);

    facade.changeBranch('crm-review');

    expect(facade.selectedScreenId()).toBe('');
    expect(facade.sourceRevision()).toBeNull();
    expect(facade.screenState()).toBe('idle');
    expect(facade.configurationReady()).toBe(false);
  });

  it('requires at least one active documentation section', () => {
    const facade = TestBed.inject(UiExplorerFacade);
    facade.initialize();
    facade.selectScreen('crm-contact-create');

    facade.selectSectionMode('OVERVIEW', 'OFF');
    facade.selectSectionMode('FORMS_AND_RULES', 'OFF');

    expect(facade.hasActiveSection()).toBe(false);
    expect(facade.configurationReady()).toBe(false);
  });

  it('starts and polls a bounded CRM run with the selected source revision', () => {
    const facade = TestBed.inject(UiExplorerFacade);
    facade.initialize();
    facade.selectScreen('crm-contact-create');
    facade.updateScenarioDescription('Describe the anonymized CRM contact creation flow.');

    facade.startJob();

    expect(api.startJob).toHaveBeenCalledWith({
      systemId: 'crm-agent-portal',
      branch: 'main',
      screenId: 'crm-contact-create',
      sourceRevision: 'crm-revision-a1b2c3',
      sectionModes: { OVERVIEW: 'DEEP', FORMS_AND_RULES: 'COMPACT' },
      scenarioDescription: 'Describe the anonymized CRM contact creation flow.',
      model: 'crm-doc-model',
      reasoningEffort: 'medium'
    });
    expect(polling.poll).toHaveBeenCalledTimes(1);
    expect(api.getJob).toHaveBeenCalledWith('crm-ui-job-1');
    expect(facade.job()?.status).toBe('COMPLETED');
    expect(facade.pollingActive()).toBe(false);
  });

  it.each(['COMPLETED', 'PARTIAL', 'BLOCKED', 'FAILED'] as const)(
    'treats %s as a terminal status without starting polling',
    (status) => {
      api.startJob.mockReturnValueOnce(of(crmJobSnapshot(status)));
      const facade = TestBed.inject(UiExplorerFacade);
      facade.initialize();
      facade.selectScreen('crm-contact-create');

      facade.startJob();

      expect(facade.job()?.status).toBe(status);
      expect(facade.isJobTerminal()).toBe(true);
      expect(polling.poll).not.toHaveBeenCalled();
    }
  );

  it('preserves the latest snapshot after a polling error and supports an explicit retry', () => {
    polling.poll
      .mockReturnValueOnce(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 503,
              error: { code: 'CRM_POLLING_UNAVAILABLE', message: 'CRM run status is temporarily unavailable.' }
            })
        )
      )
      .mockReturnValueOnce(of(crmJobSnapshot('COMPLETED')));
    const facade = TestBed.inject(UiExplorerFacade);
    facade.initialize();
    facade.selectScreen('crm-contact-create');

    facade.startJob();

    expect(facade.job()?.status).toBe('QUEUED');
    expect(facade.jobError()).toBe('CRM run status is temporarily unavailable.');
    expect(facade.canRetryPolling()).toBe(true);

    facade.retryPolling();

    expect(polling.poll).toHaveBeenCalledTimes(2);
    expect(facade.job()?.status).toBe('COMPLETED');
    expect(facade.jobError()).toBe('');
  });

  it('surfaces a safe authentication action when the CRM run cannot start', () => {
    api.startJob.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: {
              code: 'GITHUB_COPILOT_AUTH_REQUIRED',
              message: 'Connect GitHub before documenting the CRM view.',
              authStartUrl: '/api/auth/github/start?returnUrl=%2Fui-explorer'
            }
          })
      )
    );
    const facade = TestBed.inject(UiExplorerFacade);
    facade.initialize();
    facade.selectScreen('crm-contact-create');

    facade.startJob();

    expect(facade.job()).toBeNull();
    expect(facade.jobError()).toBe('Connect GitHub before documenting the CRM view.');
    expect(facade.authStartUrl()).toBe('/api/auth/github/start?returnUrl=%2Fui-explorer');
    expect(polling.poll).not.toHaveBeenCalled();
  });

  it('locks configuration during an active run and rejects a stale screen selection', () => {
    polling.poll.mockReturnValueOnce(NEVER);
    const facade = TestBed.inject(UiExplorerFacade);
    facade.initialize();
    facade.selectScreen('crm-contact-create');

    facade.startJob();
    facade.changeBranch('crm-stale-review');

    expect(facade.controlsLocked()).toBe(true);
    expect(facade.branch()).toBe('main');

    facade.job.set(null);
    facade.pollingActive.set(false);
    facade.changeBranch('crm-stale-review');
    facade.startJob();

    expect(api.startJob).toHaveBeenCalledTimes(1);
    expect(facade.jobError()).toContain('nieaktualny');
  });

  it('omits empty optional AI and scenario fields from the start request', () => {
    const facade = TestBed.inject(UiExplorerFacade);
    facade.initialize();
    facade.selectScreen('crm-contact-create');
    facade.selectModel('');
    facade.selectReasoningEffort('');

    facade.startJob();

    const request = api.startJob.mock.calls.at(-1)?.[0];
    expect(request).not.toHaveProperty('scenarioDescription');
    expect(request).not.toHaveProperty('model');
    expect(request).not.toHaveProperty('reasoningEffort');
  });

  it('stops the active polling subscription when the feature facade is destroyed', () => {
    const pollingStream = new Subject<UiExplorerJobStateSnapshot>();
    polling.poll.mockReturnValueOnce(pollingStream);
    const facade = TestBed.inject(UiExplorerFacade);
    facade.initialize();
    facade.selectScreen('crm-contact-create');

    facade.startJob();
    expect(pollingStream.observed).toBe(true);

    TestBed.resetTestingModule();
    expect(pollingStream.observed).toBe(false);
  });

  it('restores a completed CRM history run as read-only without polling or continuation', () => {
    const facade = TestBed.inject(UiExplorerFacade);

    facade.loadLocalRun('crm-ui-history-1');

    expect(historyApi.getRun).toHaveBeenCalledWith('crm-ui-history-1');
    expect(facade.job()?.jobId).toBe('crm-ui-history-1');
    expect(facade.resultSource()).toEqual({
      origin: 'history',
      exportedAt: '2026-08-15T10:02:00Z',
      fileName: '',
      localRunId: 'crm-ui-history-1',
      localRunName: 'CRM contact creation documentation'
    });
    expect(facade.isReadOnlyResult()).toBe(true);
    expect(polling.poll).not.toHaveBeenCalled();
  });

  it('delegates an untrusted portable CRM document to the backend and keeps the import read-only', () => {
    const facade = TestBed.inject(UiExplorerFacade);
    const document = crmPortableEnvelope();

    facade.importAnalysis(document, 'crm-contact-documentation.json');

    expect(api.importAnalysis).toHaveBeenCalledWith(document);
    expect(facade.job()?.status).toBe('COMPLETED');
    expect(facade.resultSource()).toEqual({
      origin: 'imported',
      exportedAt: '',
      fileName: 'crm-contact-documentation.json'
    });
    expect(facade.isReadOnlyResult()).toBe(true);
    expect(polling.poll).not.toHaveBeenCalled();
  });

  it('shows the backend rejection for an unsupported portable CRM version', () => {
    api.importAnalysis.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'UI_EXPLORER_IMPORT_VERSION_UNSUPPORTED',
              message: 'UI Explorer export version 2 is not supported.'
            }
          })
      )
    );
    const facade = TestBed.inject(UiExplorerFacade);

    facade.importAnalysis({ ...crmPortableEnvelope(), version: 2 }, 'crm-newer-version.json');

    expect(facade.job()).toBeNull();
    expect(facade.portabilityError()).toBe('UI Explorer export version 2 is not supported.');
  });
});

function crmInputOptions(): UiExplorerInputOptionsResponse {
  return {
    featureId: 'ui-explorer',
    executionAvailability: {
      status: 'AVAILABLE',
      code: 'READY',
      message: 'CRM UI documentation is available.',
      missingCapabilities: []
    },
    systems: [
      { systemId: 'crm-agent-portal', label: 'CRM Agent Portal', summary: 'Synthetic CRM operator frontend.' }
    ],
    defaultSectionModes: [
      { sectionId: 'OVERVIEW', mode: 'DEEP' },
      { sectionId: 'FORMS_AND_RULES', mode: 'COMPACT' }
    ],
    sections: [
      { sectionId: 'OVERVIEW', label: 'Cel widoku', description: 'CRM screen purpose.' },
      { sectionId: 'FORMS_AND_RULES', label: 'Formularze', description: 'CRM form rules.' }
    ],
    modes: [
      { mode: 'OFF', label: 'Pomiń', description: 'Skip.' },
      { mode: 'COMPACT', label: 'Skrót', description: 'Compact.' },
      { mode: 'DEEP', label: 'Pogłęb', description: 'Deep.' }
    ],
    configurationFindings: []
  };
}

function crmScreenCatalog(): UiExplorerScreenCatalogResponse {
  return {
    systemId: 'crm-agent-portal',
    systemLabel: 'CRM Agent Portal',
    sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' },
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
      visitedRouteNodeCount: 2,
      visitedRouteFileCount: 2,
      sourceReadCount: 9,
      aliasResolutionCount: 3,
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

function crmJobSnapshot(status: UiExplorerJobStateSnapshot['status']): UiExplorerJobStateSnapshot {
  const terminal = ['COMPLETED', 'PARTIAL', 'BLOCKED', 'FAILED'].includes(status);
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
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-08-15T10:00:00Z',
    updatedAt: '2026-08-15T10:00:01Z',
    completedAt: terminal ? '2026-08-15T10:00:02Z' : null,
    steps: [],
    contextSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    toolFeedback: [],
    preparedPrompt: null,
    result: null,
    report: null,
    usage: null,
    sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' },
    outputAvailability: {
      status: terminal ? 'AVAILABLE' : 'BLOCKED',
      code: terminal ? 'READY' : 'IN_PROGRESS',
      message: terminal ? 'CRM documentation is ready.' : 'CRM documentation is being prepared.',
      missingCapabilities: []
    },
    exportAvailable: terminal
  };
}

function crmReadableSnapshot(
  status: 'COMPLETED' | 'PARTIAL'
): UiExplorerJobStateSnapshot {
  const snapshot = crmJobSnapshot(status);
  return {
    ...snapshot,
    jobId: status === 'COMPLETED' ? 'crm-ui-history-1' : 'crm-ui-history-partial-1',
    result: {
      screen: {
        systemId: 'crm-agent-portal',
        screenId: 'crm-contact-create',
        label: 'Utworzenie kontaktu CRM',
        routePattern: '/contacts/new',
        navigationContext: 'Kontakty CRM > Nowy kontakt'
      },
      scenarioDescription: 'Describe the anonymized CRM contact creation flow.',
      sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' },
      functionalOverview: 'Synthetic CRM contact documentation.',
      sections: [],
      crossSectionDependencies: [],
      overallConfidence: 'CONFIRMED',
      visibilityLimits: [],
      unresolvedQuestions: [],
      usage: null
    },
    report: {
      reportId: 'crm-ui-report-1',
      header: 'UI Explorer: Utworzenie kontaktu CRM',
      subHeader: 'main @ crm-revision-a1b2c3',
      markdownSummary: 'Synthetic CRM contact documentation.',
      sections: [],
      meta: {
        references: [],
        visibilityLimits: [],
        openQuestions: [],
        gaps: [],
        confidence: 'CONFIRMED',
        warnings: []
      }
    },
    exportAvailable: true
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
      job: crmReadableSnapshot('COMPLETED')
    }
  };
}

function crmPortableEnvelope() {
  return {
    schema: 'tdw.ui-explorer-export' as const,
    version: 3 as const,
    exportedAt: '2026-08-15T10:03:00Z',
    payload: {
      type: 'ui-explorer-analysis' as const,
      resultContract: 'ui-explorer-result-v3' as const,
      job: crmReadableSnapshot('COMPLETED')
    }
  };
}
