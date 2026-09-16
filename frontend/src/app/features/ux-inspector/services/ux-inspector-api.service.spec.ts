import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UxInspectorApiService } from './ux-inspector-api.service';

describe('UxInspectorApiService', () => {
  let service: UxInspectorApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(UxInspectorApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses only dedicated UX Inspector endpoints', () => {
    service.getInputOptions().subscribe();
    expect(http.expectOne('/api/ux-inspector/input-options').request.method).toBe('GET');

    service.getViews('crm-agent-portal', 'review/crm').subscribe();
    const views = http.expectOne('/api/ux-inspector/views?systemId=crm-agent-portal&branch=review/crm');
    expect(views.request.method).toBe('GET');

    const body = { question: 'Dlaczego przycisk jest zablokowany?' } as never;
    service.startJob(body).subscribe();
    const start = http.expectOne('/api/ux-inspector/jobs');
    expect(start.request.method).toBe('POST');
    expect(start.request.body).toBe(body);

    service.getJob('job/crm').subscribe();
    expect(http.expectOne('/api/ux-inspector/jobs/job%2Fcrm').request.method).toBe('GET');

    service.exportJob('job/crm').subscribe();
    expect(http.expectOne('/api/ux-inspector/jobs/job%2Fcrm/export').request.method).toBe('GET');

    service.importAnalysis({ schema: 'tdw.ux-inspector-export' }).subscribe();
    expect(http.expectOne('/api/ux-inspector/imports').request.method).toBe('POST');
  });
});
