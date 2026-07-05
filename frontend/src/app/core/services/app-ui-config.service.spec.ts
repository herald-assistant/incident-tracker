import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AppUiConfigService } from './app-ui-config.service';

describe('AppUiConfigService', () => {
  let service: AppUiConfigService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(AppUiConfigService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should load UI config from backend', () => {
    service.load();

    const request = http.expectOne('/api/ui/config');
    expect(request.request.method).toBe('GET');
    request.flush({
      title: 'CRM Operations Workspace',
      subtitle: 'Team Delivery Workspace',
      defaultTitle: 'Team Delivery Workspace'
    });

    expect(service.config()).toEqual({
      title: 'CRM Operations Workspace',
      subtitle: 'Team Delivery Workspace',
      defaultTitle: 'Team Delivery Workspace'
    });
  });

  it('should keep default title without subtitle when backend returns empty values', () => {
    service.load();

    const request = http.expectOne('/api/ui/config');
    request.flush({
      title: ' ',
      subtitle: ' ',
      defaultTitle: 'Team Delivery Workspace'
    });

    expect(service.config()).toEqual({
      title: 'Team Delivery Workspace',
      subtitle: null,
      defaultTitle: 'Team Delivery Workspace'
    });
  });
});
