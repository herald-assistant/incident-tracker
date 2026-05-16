import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OperationalContextApiService } from './operational-context-api.service';

describe('OperationalContextApiService', () => {
  let service: OperationalContextApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(OperationalContextApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should build catalogue URLs', () => {
    service.getSummary().subscribe();
    const request = http.expectOne('/api/operational-context/summary');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('should build search URL with q parameter', () => {
    service.search('app-core').subscribe();

    const request = http.expectOne('/api/operational-context/search?q=app-core');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('should build profiled search URL for AI preview', () => {
    service.getProfiledSearch('app core', 'default').subscribe();

    const request = http.expectOne('/api/operational-context/search?q=app%20core&profile=default');
    expect(request.request.method).toBe('GET');
    request.flush({});
    expect(service.profiledSearchUrl('app core', 'expanded')).toBe(
      '/api/operational-context/search?q=app%20core&profile=expanded'
    );
  });

  it('should pass entity id as a query parameter', () => {
    service.getEntity('bounded-context', 'core/context').subscribe();

    const request = http.expectOne(
      '/api/operational-context/entities/bounded-context?id=core/context'
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('should pass profile when loading an AI API entity preview', () => {
    service.getProfiledEntity('bounded-context', 'core/context', 'default').subscribe();

    const request = http.expectOne(
      '/api/operational-context/entities/bounded-context?id=core/context&profile=default'
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('should pass read model entity id as a query parameter', () => {
    service.getBlastRadiusReadModel('bounded-context', 'core/context').subscribe();

    const request = http.expectOne(
      '/api/operational-context/read-model/entities/bounded-context/blast-radius?id=core/context'
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('should expose profiled preview request URLs for AI-facing reads', () => {
    const requests = service.getAiApiPreviewRequests(
      'bounded-context',
      'core/context',
      'default',
      true
    );

    expect(requests.map((request) => request.url)).toEqual([
      '/api/operational-context/entities/bounded-context?id=core/context&profile=default',
      '/api/operational-context/read-model/entities/bounded-context/relations?id=core/context&profile=default',
      '/api/operational-context/read-model/entities/bounded-context/code-search?id=core/context&profile=default',
      '/api/operational-context/read-model/entities/bounded-context/implementations?id=core/context&profile=default',
      '/api/operational-context/read-model/entities/bounded-context/flow?id=core/context&profile=default',
      '/api/operational-context/read-model/entities/bounded-context/blast-radius?id=core/context&profile=default'
    ]);
  });
});
