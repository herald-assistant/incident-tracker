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

export type GitLabEndpointUseCaseOutputMode = 'COMPACT' | 'GRAPH' | 'BUSINESS' | 'DEBUG';

export interface GitLabEndpointUseCaseContextPayload {
  group: string;
  projectName: string;
  branch: string;
  endpointId?: string;
  httpMethod?: string;
  endpointPath?: string;
  sourcePathPrefix?: string;
  outputMode?: GitLabEndpointUseCaseOutputMode;
  maxDepth?: number;
  maxNodes?: number;
  includeAsyncConsumers?: boolean;
  reason?: string;
}

export interface GitLabEndpointUseCaseContextResponse {
  repository: {
    group?: string | null;
    projectName?: string | null;
    requestedBranch?: string | null;
    sourcePathPrefix?: string | null;
    indexStatus?: string | null;
  } | null;
  endpoint: {
    endpointId?: string | null;
    httpMethods: string[];
    inputPath?: string | null;
    matchedPathPattern?: string | null;
    controllerClass?: string | null;
    controllerMethod?: string | null;
    sourcePath?: string | null;
    lineStart: number;
    lineEnd: number;
  } | null;
  useCaseSummary: {
    mainResponsibility?: string | null;
    businessObjects: string[];
    sideEffects: string[];
    externalSystems: string[];
    asyncBoundaries: string[];
  };
  graph: {
    nodes: Array<{
      id: string;
      kind?: string | null;
      classFqn?: string | null;
      methodSignature?: string | null;
      role?: string | null;
      depth: number;
      sourcePath?: string | null;
      lineStart: number;
      lineEnd: number;
      terminal: boolean;
      terminalReason?: string | null;
    }>;
    edges: Array<{
      from: string;
      to: string;
      kind?: string | null;
      resolutionKind?: string | null;
      call?: string | null;
      line?: number | null;
      confidence?: string | null;
      ambiguous: boolean;
    }>;
  };
  classList: Array<{
    classFqn: string;
    role?: string | null;
    depth: number;
    methods: string[];
    terminal: boolean;
    reason?: string | null;
  }>;
  warnings: Array<{
    code?: string | null;
    severity?: string | null;
    message?: string | null;
    sourcePath?: string | null;
    line?: number | null;
    candidates: string[];
  }>;
  evidence: Array<{
    kind?: string | null;
    message?: string | null;
    sourcePath?: string | null;
    line?: number | null;
  }>;
  suggestedNextReads: string[];
  limits: {
    maxDepth: number;
    maxNodes: number;
    maxDepthReached: boolean;
    maxNodesReached: boolean;
  };
  confidence?: string | null;
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
