import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UiExplorerApiService } from './ui-explorer-api.service';

describe('UiExplorerApiService', () => {
  let service: UiExplorerApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(UiExplorerApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads UI Explorer input options from the feature API', () => {
    service.getInputOptions().subscribe((response) => {
      expect(response.systems[0].systemId).toBe('crm-agent-portal');
    });

    const request = http.expectOne('/api/ui-explorer/input-options');
    expect(request.request.method).toBe('GET');
    request.flush({
      featureId: 'ui-explorer',
      executionAvailability: {
        status: 'AVAILABLE',
        code: 'READY',
        message: 'CRM UI documentation is available.',
        missingCapabilities: []
      },
      systems: [{ systemId: 'crm-agent-portal', label: 'CRM Agent Portal', summary: 'Synthetic CRM UI.' }],
      defaultSectionModes: [],
      sections: [],
      modes: [],
      configurationFindings: []
    });
  });

  it('keeps system and ref as bounded screen catalog query parameters', () => {
    service.getScreens('crm-agent-portal', 'crm-review').subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/ui-explorer/screens' &&
        candidate.params.get('systemId') === 'crm-agent-portal' &&
        candidate.params.get('branch') === 'crm-review'
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      systemId: 'crm-agent-portal',
      systemLabel: 'CRM Agent Portal',
      sourceRevision: { branch: 'crm-review', revision: 'crm-revision-a1b2c3' },
      status: 'READY',
      screens: [],
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
    });
  });

  it('starts a UI Explorer job and safely encodes its identifier during polling', () => {
    const requestBody = {
      systemId: 'crm-agent-portal',
      branch: 'crm-review',
      screenId: 'crm-contact-create',
      sourceRevision: 'crm-revision-a1b2c3',
      sectionModes: { OVERVIEW: 'DEEP' as const },
      scenarioDescription: 'Describe the anonymized CRM contact flow.',
      model: 'crm-doc-model',
      reasoningEffort: 'medium'
    };

    service.startJob(requestBody).subscribe();
    const startRequest = http.expectOne('/api/ui-explorer/jobs');
    expect(startRequest.request.method).toBe('POST');
    expect(startRequest.request.body).toEqual(requestBody);
    startRequest.flush({ jobId: 'crm/job-1', status: 'QUEUED' });

    service.getJob('crm/job-1').subscribe();
    const pollRequest = http.expectOne('/api/ui-explorer/jobs/crm%2Fjob-1');
    expect(pollRequest.request.method).toBe('GET');
    pollRequest.flush({ jobId: 'crm/job-1', status: 'COMPLETED' });
  });

  it('uses dedicated feature endpoints for portable export and server-validated import', () => {
    const portableDocument = {
      schema: 'tdw.ui-explorer-export',
      version: 5,
      exportedAt: '2026-08-15T10:05:00Z',
      payload: {
        type: 'ui-explorer-analysis',
        resultContract: 'ui-explorer-result-v5',
        job: { jobId: 'crm/ui-job-1' }
      }
    };

    service.exportJob('crm/ui-job-1').subscribe();
    const exportRequest = http.expectOne('/api/ui-explorer/jobs/crm%2Fui-job-1/export');
    expect(exportRequest.request.method).toBe('GET');
    exportRequest.flush(portableDocument);

    service.importAnalysis(portableDocument).subscribe();
    const importRequest = http.expectOne('/api/ui-explorer/imports');
    expect(importRequest.request.method).toBe('POST');
    expect(importRequest.request.body).toBe(portableDocument);
    importRequest.flush({ jobId: 'ui-explorer-import-crm-1', status: 'COMPLETED' });
  });
});
