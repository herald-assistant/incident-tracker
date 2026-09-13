import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AnalysisApiService } from './analysis-api.service';

describe('AnalysisApiService', () => {
  let service: AnalysisApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(AnalysisApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should include a trimmed problem description in the CSV multipart request', () => {
    const file = new File(['timestamp,message\n2026-07-04T10:00:00Z,slow request'], 'crm-logs.csv', {
      type: 'text/csv'
    });
    service.startAnalysis({
      source: 'CSV_UPLOAD',
      logFile: file,
      problemDescription: '  Customer search takes too long.  '
    }).subscribe();

    const request = http.expectOne('/api/analysis/jobs');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    const body = request.request.body as FormData;
    expect(body.get('source')).toBe('CSV_UPLOAD');
    expect(body.get('logFile')).toBeInstanceOf(File);
    expect((body.get('logFile') as File).name).toBe('crm-logs.csv');
    expect(body.get('problemDescription')).toBe('Customer search takes too long.');
    request.flush({});
  });

  it('should omit a blank problem description from the Elasticsearch multipart request', () => {
    service.startAnalysis({
      source: 'ELASTICSEARCH',
      correlationId: 'corr-123',
      problemDescription: '   '
    }).subscribe();

    const request = http.expectOne('/api/analysis/jobs');
    const body = request.request.body as FormData;
    expect(body.get('source')).toBe('ELASTICSEARCH');
    expect(body.get('correlationId')).toBe('corr-123');
    expect(body.has('problemDescription')).toBe(false);
    request.flush({});
  });
});
