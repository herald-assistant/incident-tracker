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
      systemId: 'backend',
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
});
