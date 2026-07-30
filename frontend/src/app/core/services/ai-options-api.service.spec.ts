import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AiOptionsApiService } from './ai-options-api.service';

describe('AiOptionsApiService', () => {
  let service: AiOptionsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AiOptionsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should load the shared AI model catalog', () => {
    service.getOptions().subscribe((response) => expect(response.defaultModel).toBe('gpt-5.4'));

    const request = http.expectOne('/api/analysis/ai/options');
    expect(request.request.method).toBe('GET');
    request.flush({ models: [], reasoningEfforts: [], defaultModel: 'gpt-5.4' });
  });
});
