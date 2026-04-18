import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ElasticLogSearchPayload {
  correlationId: string;
}

export interface GitLabRepositorySearchPayload {
  correlationId?: string;
  branch?: string;
  projectHints: string[];
  operationNames: string[];
  keywords: string[];
}

export interface GitLabSourceResolvePayload {
  gitlabBaseUrl: string;
  groupPath: string;
  projectPath: string;
  ref?: string;
  symbol: string;
}

@Injectable({
  providedIn: 'root'
})
export class EvidenceApiService {
  private readonly http = inject(HttpClient);

  searchElasticLogs(payload: ElasticLogSearchPayload): Observable<unknown> {
    return this.http.post('/api/elasticsearch/logs/search', payload);
  }

  searchGitLabRepository(payload: GitLabRepositorySearchPayload): Observable<unknown> {
    return this.http.post('/api/gitlab/repository/search', payload);
  }

  resolveGitLabSource(payload: GitLabSourceResolvePayload, preview: boolean): Observable<unknown> {
    const url = preview ? '/api/gitlab/source/resolve/preview' : '/api/gitlab/source/resolve';
    return this.http.post(url, payload);
  }
}
