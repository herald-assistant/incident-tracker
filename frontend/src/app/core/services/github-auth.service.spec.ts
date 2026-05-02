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

  it('should load GitHub auth status', () => {
    let statusMode = '';

    service.getStatus().subscribe((status) => {
      statusMode = status.mode;
    });

    const request = http.expectOne('/api/auth/github/status');
    expect(request.request.method).toBe('GET');
    request.flush({
      mode: 'GITHUB_APP',
      required: true,
      connected: true,
      githubLogin: 'octocat',
      displayName: 'octocat',
      tokenExpiresAt: '2026-05-02T18:42:00Z',
      reauthRequired: false,
      authStartUrl: '/api/auth/github/start'
    });

    expect(statusMode).toBe('GITHUB_APP');
  });

  it('should build the GitHub OAuth start URL with an encoded returnUrl', () => {
    expect(service.connectUrl('/evidence?analysisId=123')).toBe(
      '/api/auth/github/start?returnUrl=%2Fevidence%3FanalysisId%3D123'
    );
  });

  it('should call logout endpoint', () => {
    let completed = false;

    service.logout().subscribe(() => {
      completed = true;
    });

    const request = http.expectOne('/api/auth/github/logout');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(null);

    expect(completed).toBe(true);
  });
});
