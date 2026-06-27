import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FlowExplorerApiService } from './flow-explorer-api.service';

describe('FlowExplorerApiService', () => {
  let service: FlowExplorerApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(FlowExplorerApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should load systems from the feature API', () => {
    service.getSystems().subscribe((systems) => expect(systems).toEqual([]));

    const request = http.expectOne('/api/flow-explorer/systems');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('should load Flow Explorer config from the feature API', () => {
    service.getConfig().subscribe((config) => expect(config.defaultBranch).toBe('main'));

    const request = http.expectOne('/api/flow-explorer/config');
    expect(request.request.method).toBe('GET');
    request.flush({ defaultBranch: 'main' });
  });

  it('should load endpoint inventory with trimmed optional filters', () => {
    service
      .getEndpointInventory('crm/service', {
        branch: ' main ',
        endpointPathPrefix: '/api/customers',
        httpMethod: ' GET '
      })
      .subscribe();

    const request = http.expectOne(
      '/api/flow-explorer/systems/crm%2Fservice/endpoints?branch=main&endpointPathPrefix=/api/customers&httpMethod=GET'
    );
    expect(request.request.method).toBe('GET');
    request.flush({ endpoints: [] });
  });

  it('should start and read jobs', () => {
    service.startJob({ systemId: 'crm-service', endpointId: 'GET /customers' }).subscribe();

    const startRequest = http.expectOne('/api/flow-explorer/jobs');
    expect(startRequest.request.method).toBe('POST');
    expect(startRequest.request.body).toEqual({
      systemId: 'crm-service',
      endpointId: 'GET /customers'
    });
    startRequest.flush({ jobId: 'job-123' });

    service.getJob('job/123').subscribe();

    const getRequest = http.expectOne('/api/flow-explorer/jobs/job%2F123');
    expect(getRequest.request.method).toBe('GET');
    getRequest.flush({ jobId: 'job/123' });
  });

  it('should send follow-up chat messages for a job', () => {
    service.sendChatMessage('job/123', { message: 'Gdzie jest walidacja?' }).subscribe();

    const request = http.expectOne('/api/flow-explorer/jobs/job%2F123/chat/messages');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'Gdzie jest walidacja?' });
    request.flush({ jobId: 'job/123', chatMessages: [] });
  });

  it('should refine a Flow Explorer result section', () => {
    service
      .refineSection('job/123', 'PERSISTENCE', { message: 'Doprecyzuj persistence.' })
      .subscribe();

    const request = http.expectOne(
      '/api/flow-explorer/jobs/job%2F123/sections/PERSISTENCE/refine'
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'Doprecyzuj persistence.' });
    request.flush({ jobId: 'job/123', chatMessages: [] });
  });
});
