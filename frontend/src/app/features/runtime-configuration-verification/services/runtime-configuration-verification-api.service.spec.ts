import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { RuntimeConfigurationVerificationApiService } from './runtime-configuration-verification-api.service';

describe('RuntimeConfigurationVerificationApiService', () => {
  let service: RuntimeConfigurationVerificationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RuntimeConfigurationVerificationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should load input options and encode DEEP preflight scope', () => {
    service.getInputOptions().subscribe();
    const options = http.expectOne('/api/runtime-configuration-verification/input-options');
    expect(options.request.method).toBe('GET');
    options.flush({ modes: [], branches: [], repositories: [], systems: [] });

    service.getDeepPreflight('runtime/config', 'backend/system', ' release/42 ').subscribe();
    const preflight = http.expectOne(
      (request) =>
        request.url === '/api/runtime-configuration-verification/deep-preflight'
        && request.params.get('repositoryId') === 'runtime/config'
        && request.params.get('systemId') === 'backend/system'
        && request.params.get('codeRef') === 'release/42'
    );
    expect(preflight.request.method).toBe('GET');
    preflight.flush({ status: 'READY' });
  });

  it('should start, poll and import a verification result', () => {
    const start = {
      mode: 'BASIC' as const,
      repositoryId: 'runtime-config',
      systemIds: ['backend'],
      sourceBranch: 'dev1',
      targetBranch: 'zt001'
    };
    service.startJob(start).subscribe();
    const startRequest = http.expectOne('/api/runtime-configuration-verification/jobs');
    expect(startRequest.request.body).toEqual(start);
    startRequest.flush({ jobId: 'job-1' });

    service.getJob('job/1').subscribe();
    const pollRequest = http.expectOne('/api/runtime-configuration-verification/jobs/job%2F1');
    expect(pollRequest.request.method).toBe('GET');
    pollRequest.flush({ jobId: 'job/1' });

    service.importResult({ schema: 'safe' }).subscribe();
    const importRequest = http.expectOne('/api/runtime-configuration-verification/imports');
    expect(importRequest.request.method).toBe('POST');
    expect(importRequest.request.body).toEqual({ schema: 'safe' });
    importRequest.flush({ imported: true });
  });

  it('should request a readonly Workbench preview with the exact selected scope', () => {
    const payload = {
      mode: 'DEEP' as const,
      repositoryId: 'runtime-config',
      systemId: 'backend',
      sourceBranch: 'dev1',
      targetBranch: 'zt001',
      codeRef: 'release/42'
    };

    service.preview(payload).subscribe();

    const request = http.expectOne(
      '/api/runtime-configuration-verification/workbench/preview'
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ mode: 'DEEP' });
  });

  it('should request Workbench snapshot details through dedicated lazy endpoints', () => {
    const previewId = 'preview/1';
    const base = '/api/runtime-configuration-verification/workbench/preview/preview%2F1';

    service.getWorkbenchSource(previewId).subscribe();
    const source = http.expectOne(`${base}/source`);
    expect(source.request.method).toBe('GET');
    source.flush({ previewId });

    service.getWorkbenchConfigurationDiff(previewId).subscribe();
    const configurationDiff = http.expectOne(`${base}/configuration-diff`);
    expect(configurationDiff.request.method).toBe('GET');
    configurationDiff.flush({ previewId, configurationDiff: { files: [] } });

    service.getWorkbenchMapping(previewId, 100, 50, false).subscribe();
    const mapping = http.expectOne((request) =>
      request.url === `${base}/mapping`
      && request.params.get('offset') === '100'
      && request.params.get('limit') === '50'
      && request.params.get('changedOnly') === 'false'
    );
    mapping.flush({ previewId, items: [] });

    service.getWorkbenchAnonymization(previewId, 200, 100).subscribe();
    const anonymization = http.expectOne((request) =>
      request.url === `${base}/anonymization`
      && request.params.get('offset') === '200'
      && request.params.get('limit') === '100'
    );
    anonymization.flush({ previewId, items: [] });

    service.getWorkbenchDeep(previewId).subscribe();
    http.expectOne(`${base}/deep`).flush({ previewId, requested: true });

    service.getWorkbenchAiInput(previewId).subscribe();
    http.expectOne(`${base}/ai-input`).flush({
      previewId,
      generated: true,
      characterCount: 4,
      prompt: 'safe'
    });

    service
      .getWorkbenchArtifact(previewId, 'runtime-configuration/configuration-tree.yaml')
      .subscribe();
    const artifact = http.expectOne((request) =>
      request.url === `${base}/artifact`
      && request.params.get('name')
        === 'runtime-configuration/configuration-tree.yaml'
    );
    artifact.flush({ previewId, content: 'safe' });
  });
});
