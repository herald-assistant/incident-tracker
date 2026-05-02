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

  it('should encode entity detail URL', () => {
    service.getEntity('bounded-context', 'core/context').subscribe();

    const request = http.expectOne(
      '/api/operational-context/entities/bounded-context/core%2Fcontext'
    );
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
