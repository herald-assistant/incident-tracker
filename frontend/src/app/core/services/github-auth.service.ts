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

  openSettings(): void {
    window.location.assign('/workspace-settings');
  }
}
