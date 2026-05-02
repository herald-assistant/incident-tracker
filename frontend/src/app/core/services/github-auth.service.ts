import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { GitHubAuthStatus } from '../models/analysis.models';

@Injectable({
  providedIn: 'root'
})
export class GithubAuthService {
  private readonly http = inject(HttpClient);

  getStatus(): Observable<GitHubAuthStatus> {
    return this.http.get<GitHubAuthStatus>('/api/auth/github/status');
  }

  connect(returnUrl = window.location.pathname + window.location.search): void {
    window.location.assign(this.connectUrl(returnUrl));
  }

  connectUrl(returnUrl = window.location.pathname + window.location.search): string {
    const encoded = encodeURIComponent(returnUrl || '/');
    return `/api/auth/github/start?returnUrl=${encoded}`;
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/github/logout', {});
  }
}
