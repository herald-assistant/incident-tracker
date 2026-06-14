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

export interface GitLabEndpointUseCaseContextPayload {
  group: string;
  projectName: string;
  branch: string;
  endpointId?: string;
  httpMethod?: string;
  endpointPath?: string;
  sourcePathPrefix?: string;
  maxDepth?: number;
  maxFiles?: number;
  reason?: string;
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

export interface GitLabEndpointUseCaseRepositoryContext {
  group?: string | null;
  projectName?: string | null;
  branch?: string | null;
  sourcePathPrefix?: string | null;
}

export interface GitLabEndpointUseCaseEndpointContext {
  endpointId?: string | null;
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

export interface GitLabEndpointUseCaseFileCandidate {
  path?: string | null;
  role?: string | null;
  priority: number;
  symbols: string[];
  reason?: string | null;
  confidence?: string | null;
}

export interface GitLabEndpointUseCaseRelation {
  from?: string | null;
  to?: string | null;
  kind?: string | null;
  confidence?: string | null;
  reason?: string | null;
}

export interface GitLabEndpointUseCaseUnresolvedReference {
  symbol?: string | null;
  ownerPath?: string | null;
  reason?: string | null;
  searchedKeywords: string[];
  candidates: string[];
}

export interface GitLabEndpointUseCaseLimits {
  maxDepth: number;
  maxFiles: number;
  maxReadFiles: number;
  maxDepthReached: boolean;
  maxFilesReached: boolean;
  readFileCount: number;
  readFileLimitReached: boolean;
}

export interface GitLabEndpointUseCaseContextResponse {
  repository?: GitLabEndpointUseCaseRepositoryContext | null;
  endpoint?: GitLabEndpointUseCaseEndpointContext | null;
  files: GitLabEndpointUseCaseFileCandidate[];
  relations: GitLabEndpointUseCaseRelation[];
  unresolved: GitLabEndpointUseCaseUnresolvedReference[];
  limitations: string[];
  suggestedNextReads: string[];
  limits: GitLabEndpointUseCaseLimits;
  confidence?: string | null;
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

  buildGitLabEndpointUseCaseContext(
    payload: GitLabEndpointUseCaseContextPayload
  ): Observable<GitLabEndpointUseCaseContextResponse> {
    return this.http.post<GitLabEndpointUseCaseContextResponse>(
      '/api/gitlab/repository/endpoint-use-case-context',
      payload
    );
  }
}
