import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { GithubAuthService } from './github-auth.service';

describe('GithubAuthService', () => {
  let service: GithubAuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(GithubAuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('should load fine-grained PAT status', () => {
    let configured = false;

    service.getStatus().subscribe((status) => {
      configured = status.configured;
    });

    const request = http.expectOne('/api/auth/github/status');
    expect(request.request.method).toBe('GET');
    request.flush({
      configured: true,
      settingsUrl: '/workspace-settings'
    });

    expect(configured).toBe(true);
  });
});
