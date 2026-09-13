import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OperationalContextAssistanceApiService } from './operational-context-assistance-api.service';

describe('OperationalContextAssistanceApiService', () => {
  it('starts a scoped assistance job and loads its encoded id', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(OperationalContextAssistanceApiService);
    const http = TestBed.inject(HttpTestingController);

    api.sourceOptions().subscribe();
    const options = http.expectOne('/api/operational-context/assistance/source-options');
    expect(options.request.method).toBe('GET');
    options.flush({ configuredBaseUrl: 'https://gitlab.example.com', configuredGroup: 'unicam-group', projects: [] });

    api.sourceBranches({ project: 'nested/customer-api' }, ' release ').subscribe();
    const branches = http.expectOne((request) => request.url === '/api/operational-context/assistance/source-options/branches');
    expect(branches.request.method).toBe('GET');
    expect(branches.request.params.get('project')).toBe('nested/customer-api');
    expect(branches.request.params.get('search')).toBe('release');
    branches.flush({ branches: [{ name: 'release/2026', isDefault: false }], truncated: false, warnings: [] });

    api.sourceBranches({ projectUrl: 'https://gitlab.example.com/unicam-group/nested/customer-api' }).subscribe();
    const urlBranches = http.expectOne((request) => request.url === '/api/operational-context/assistance/source-options/branches');
    expect(urlBranches.request.params.get('projectUrl')).toBe('https://gitlab.example.com/unicam-group/nested/customer-api');
    expect(urlBranches.request.params.has('project')).toBe(false);
    urlBranches.flush({ branches: [], truncated: false, warnings: [] });

    api.start({
      mode: 'CREATE_AREA', description: 'Opis obszaru',
      gitLabSource: { project: 'customer-api', ref: 'main' }
    }).subscribe();
    const start = http.expectOne('/api/operational-context/assistance/jobs');
    expect(start.request.method).toBe('POST');
    expect(start.request.body).toEqual({
      mode: 'CREATE_AREA', description: 'Opis obszaru',
      gitLabSource: { project: 'customer-api', ref: 'main' }
    });
    start.flush({ jobId: 'job/a' });

    api.get('job/a').subscribe();
    const get = http.expectOne('/api/operational-context/assistance/jobs/job%2Fa');
    expect(get.request.method).toBe('GET');
    get.flush({ jobId: 'job/a', status: 'COMPLETED' });

    const decisions = [
      { action: 'APPLY' as const, selectedPaths: ['name'], confirmedPaths: ['name'] },
      { action: 'SKIP' as const, selectedPaths: [], confirmedPaths: [] }
    ];
    api.previewBatch('job/a', { decisions }).subscribe();
    const preview = http.expectOne('/api/operational-context/assistance/jobs/job%2Fa/batch/preview');
    expect(preview.request.method).toBe('POST');
    expect(preview.request.body).toEqual({ decisions });
    preview.flush({ expectedDigest: 'old', candidateDigest: 'candidate', valid: true, entities: [], violations: [] });

    api.decideBatch('job/a', { decisions, candidateDigest: 'candidate' }).subscribe();
    const decision = http.expectOne('/api/operational-context/assistance/jobs/job%2Fa/batch/decision');
    expect(decision.request.method).toBe('POST');
    expect(decision.request.body).toEqual({ decisions, candidateDigest: 'candidate' });
    decision.flush({ jobId: 'job/a', proposalDecisions: [{ proposalIndex: 0, action: 'APPLY' }, { proposalIndex: 1, action: 'SKIP' }] });
    http.verify();
  });
});
