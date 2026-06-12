import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ElasticLogSearchPayload {
  correlationId: string;
}

export interface ElasticHttpCallSummaryPayload {
  pathPattern: string;
  method?: string;
  serviceName?: string;
  timeWindowDays?: number;
  sampleSize?: number;
}

export type ElasticLogDetailLevel = 'SUMMARY' | 'COMPACT' | 'FULL';

export interface ElasticHttpCallLogsPayload {
  correlationId?: string;
  path?: string;
  status?: number;
  method?: string;
  timeWindowDays?: number;
  size?: number;
  detailLevel?: ElasticLogDetailLevel;
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

export interface GitLabRepositoryEndpointsPayload {
  group: string;
  projectName: string;
  branch: string;
  endpointPathPrefix?: string;
  httpMethod?: string;
  sourcePathPrefix?: string;
  maxScannedFiles?: number;
}

export interface GitLabRepositoryEndpoint {
  endpointId: string;
  httpMethods: string[];
  path?: string | null;
  pathExpression?: string | null;
  controllerClass?: string | null;
  handlerMethod?: string | null;
  filePath?: string | null;
  lineStart: number;
  lineEnd: number;
  requestTypes: string[];
  responseTypes: string[];
  annotations: string[];
  confidence?: string | null;
  limitations: string[];
  suggestedNextReads: string[];
}

export interface GitLabRepositoryEndpointsResponse {
  group: string;
  projectName: string;
  branch: string;
  endpointPathPrefix?: string | null;
  httpMethod?: string | null;
  sourcePathPrefix?: string | null;
  candidateFileCount: number;
  scannedFileCount: number;
  scannedFileLimitReached: boolean;
  endpoints: GitLabRepositoryEndpoint[];
  limitations: string[];
}

@Injectable({
  providedIn: 'root'
})
export class EvidenceApiService {
  private readonly http = inject(HttpClient);

  searchElasticLogs(payload: ElasticLogSearchPayload): Observable<unknown> {
    return this.http.post('/api/elasticsearch/logs/search', payload);
  }

  summarizeElasticHttpCalls(payload: ElasticHttpCallSummaryPayload): Observable<unknown> {
    return this.http.post('/api/elasticsearch/logs/http-calls/summary', payload);
  }

  fetchElasticHttpCallLogs(payload: ElasticHttpCallLogsPayload): Observable<unknown> {
    return this.http.post('/api/elasticsearch/logs/http-calls/fetch', payload);
  }

  searchGitLabRepository(payload: GitLabRepositorySearchPayload): Observable<unknown> {
    return this.http.post('/api/gitlab/repository/search', payload);
  }

  resolveGitLabSource(payload: GitLabSourceResolvePayload, preview: boolean): Observable<unknown> {
    const url = preview ? '/api/gitlab/source/resolve/preview' : '/api/gitlab/source/resolve';
    return this.http.post(url, payload);
  }

  listGitLabRepositoryEndpoints(
    payload: GitLabRepositoryEndpointsPayload
  ): Observable<GitLabRepositoryEndpointsResponse> {
    return this.http.post<GitLabRepositoryEndpointsResponse>(
      '/api/gitlab/repository/endpoints',
      payload
    );
  }
}
