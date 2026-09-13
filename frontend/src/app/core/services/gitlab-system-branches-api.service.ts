import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface GitLabBranchOption {
  name: string;
  isDefault: boolean;
}

export interface GitLabBranchesResponse {
  branches: GitLabBranchOption[];
  truncated: boolean;
  warnings: string[];
}

export interface GitLabSystemBranchesResponse extends GitLabBranchesResponse {
  systemId: string;
}

@Injectable({ providedIn: 'root' })
export class GitLabSystemBranchesApiService {
  private readonly http = inject(HttpClient);

  getBranches(systemId: string, search = ''): Observable<GitLabSystemBranchesResponse> {
    const params = search.trim() ? new HttpParams().set('search', search.trim()) : new HttpParams();
    return this.http.get<GitLabSystemBranchesResponse>(
      `/api/gitlab/systems/${encodeURIComponent(systemId)}/branches`,
      { params }
    );
  }
}
