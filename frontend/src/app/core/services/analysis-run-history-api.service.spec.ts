import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AnalysisRunHistoryApiService } from './analysis-run-history-api.service';

describe('AnalysisRunHistoryApiService', () => {
  let service: AnalysisRunHistoryApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(AnalysisRunHistoryApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should list local analysis runs from the lightweight index API', () => {
    service.listRuns().subscribe((response) => expect(response.runs).toHaveLength(1));

    const request = http.expectOne('/api/analysis/runs');
    expect(request.request.method).toBe('GET');
    request.flush({
      runs: [
        {
          analysisId: 'analysis-1',
          feature: 'incident-analysis',
          name: 'corr-123',
          status: 'COMPLETED',
          createdAt: '2026-05-02T10:00:00Z',
          updatedAt: '2026-05-02T10:01:00Z',
          completedAt: '2026-05-02T10:01:00Z'
        }
      ]
    });
  });

  it('should load a full local run only by analysis id', () => {
    service.getRun('analysis/1').subscribe((response) => expect(response.analysisId).toBe('analysis/1'));

    const request = http.expectOne('/api/analysis/runs/analysis%2F1');
    expect(request.request.method).toBe('GET');
    request.flush({ analysisId: 'analysis/1' });
  });

  it('should export a local run envelope without loading detail metadata', () => {
    service.exportRun('analysis/1').subscribe((response) =>
      expect((response as { schema: string }).schema).toBe('tdw.analysis-export')
    );

    const request = http.expectOne('/api/analysis/runs/analysis%2F1/export');
    expect(request.request.method).toBe('GET');
    request.flush({ schema: 'tdw.analysis-export', version: 6 });
  });

  it('should rename a local run', () => {
    service.renameRun('analysis/1', { name: 'Nowa nazwa' }).subscribe();

    const request = http.expectOne('/api/analysis/runs/analysis%2F1/name');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ name: 'Nowa nazwa' });
    request.flush({ analysisId: 'analysis/1', name: 'Nowa nazwa' });
  });

  it('should send a follow-up message to a local run', () => {
    service.sendChatMessage('analysis/1', { message: 'Dopytaj o repo.' }).subscribe();

    const request = http.expectOne('/api/analysis/runs/analysis%2F1/chat/messages');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'Dopytaj o repo.' });
    request.flush({ analysisId: 'analysis/1' });
  });

  it('should delete a local run', () => {
    service.deleteRun('analysis/1').subscribe();

    const request = http.expectOne('/api/analysis/runs/analysis%2F1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
