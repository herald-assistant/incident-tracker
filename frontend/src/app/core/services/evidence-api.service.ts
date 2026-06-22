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
  maxScannedFiles?: number;
}

export interface GitLabEndpointUseCaseContextPayload {
  group: string;
  projectName: string;
  branch: string;
  endpointId?: string;
  httpMethod?: string;
  endpointPath?: string;
  maxDepth?: number;
  maxFiles?: number;
}

export interface GitLabRepositoryFilesByPathPayload {
  group: string;
  projectName: string;
  branch: string;
  filePaths: string[];
  maxCharactersPerFile?: number;
  maxTotalCharacters?: number;
}

export interface GitLabJavaMethodSlicePayload {
  group: string;
  projectName: string;
  branch: string;
  filePath: string;
  declaringTypeName?: string;
  methodSelectors: GitLabJavaMethodSliceMethodSelector[];
  includeDirectPrivateHelpers?: boolean;
  includeRelevantFields?: boolean;
  includeRelevantImports?: boolean;
  maxCharacters?: number;
}

export interface GitLabOpenApiEndpointSlicePayload {
  group: string;
  projectName: string;
  branch: string;
  filePath: string;
  httpMethod: string;
  endpointPath: string;
  includeReferencedSchemas?: boolean;
  schemaDepth?: number;
  maxCharacters?: number;
}

export interface GitLabJavaMethodSliceMethodSelector {
  methodName: string;
  lineStart?: number | null;
}

export interface GitLabRepositoryEndpointParameterDocumentation {
  name?: string | null;
  in?: string | null;
  required: boolean;
  type?: string | null;
  description?: string | null;
}

export interface GitLabRepositoryEndpointDocumentation {
  source?: string | null;
  summary?: string | null;
  description?: string | null;
  operationId?: string | null;
  tags: string[];
  parameters: GitLabRepositoryEndpointParameterDocumentation[];
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
  documentation?: GitLabRepositoryEndpointDocumentation | null;
  confidence?: string | null;
  limitations: string[];
  suggestedNextReads: string[];
}

export interface GitLabEndpointUseCaseRepositoryContext {
  group?: string | null;
  projectName?: string | null;
  branch?: string | null;
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
  documentation?: GitLabRepositoryEndpointDocumentation | null;
  confidence?: string | null;
  limitations: string[];
  suggestedNextReads: string[];
}

export interface GitLabEndpointUseCaseFileCandidate {
  path?: string | null;
  role?: string | null;
  priority: number;
  symbols: string[];
  methods?: GitLabEndpointUseCaseMethodCandidate[];
  reason?: string | null;
  confidence?: string | null;
}

export interface GitLabEndpointUseCaseMethodCandidate {
  filePath?: string | null;
  signature?: string | null;
  methodName?: string | null;
  lineStart: number;
  lineEnd: number;
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

export interface GitLabRepositoryFileByPathResult {
  group?: string | null;
  projectName?: string | null;
  branch?: string | null;
  filePath?: string | null;
  content?: string | null;
  truncated: boolean;
  inferredRole?: string | null;
  returnedCharacters: number;
  sizeBytes?: number | null;
  contentSha256?: string | null;
  blobId?: string | null;
  commitId?: string | null;
  lastCommitId?: string | null;
  lastModifiedAt?: string | null;
  metadataStatus?: string | null;
  metadataError?: string | null;
  error?: string | null;
}

export interface GitLabRepositoryFilesByPathResponse {
  group: string;
  projectName: string;
  branch: string;
  requestedFileCount: number;
  processedFileCount: number;
  returnedFileCount: number;
  failedFileCount: number;
  totalReturnedCharacters: number;
  fileCountTruncated: boolean;
  totalCharacterLimitReached: boolean;
  files: GitLabRepositoryFileByPathResult[];
}

export interface GitLabJavaMethodSliceMethodCandidate {
  declaringTypeName?: string | null;
  methodName?: string | null;
  signature?: string | null;
  lineStart: number;
  lineEnd: number;
  parameterCount: number;
  parameterTypes: string[];
}

export interface GitLabJavaMethodSliceResponse {
  group: string;
  projectName: string;
  branch: string;
  filePath: string;
  status: string;
  declaringTypeName?: string | null;
  requestedMethods: GitLabJavaMethodSliceMethodSelector[];
  returnedLineStart: number;
  returnedLineEnd: number;
  totalLines: number;
  content?: string | null;
  returnedCharacters: number;
  truncated: boolean;
  includedImports: string[];
  includedFields: string[];
  includedMethods: GitLabJavaMethodSliceMethodCandidate[];
  omittedFieldCount: number;
  omittedMethodCount: number;
  candidates: GitLabJavaMethodSliceMethodCandidate[];
  limitations: string[];
}

export interface GitLabOpenApiEndpointSliceResponse {
  group: string;
  projectName: string;
  branch: string;
  filePath: string;
  status: string;
  specType?: string | null;
  specVersion?: string | null;
  httpMethod: string;
  endpointPath: string;
  matchedPath?: string | null;
  operationId?: string | null;
  summary?: string | null;
  description?: string | null;
  tags: string[];
  sourceRef?: string | null;
  content?: string | null;
  returnedCharacters: number;
  truncated: boolean;
  limitations: string[];
}

export interface GitLabRepositoryEndpointsResponse {
  group: string;
  projectName: string;
  branch: string;
  endpointPathPrefix?: string | null;
  httpMethod?: string | null;
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

  readGitLabRepositoryFilesByPath(
    payload: GitLabRepositoryFilesByPathPayload
  ): Observable<GitLabRepositoryFilesByPathResponse> {
    return this.http.post<GitLabRepositoryFilesByPathResponse>(
      '/api/gitlab/repository/files/by-path',
      payload
    );
  }

  readGitLabJavaMethodSlice(
    payload: GitLabJavaMethodSlicePayload
  ): Observable<GitLabJavaMethodSliceResponse> {
    return this.http.post<GitLabJavaMethodSliceResponse>(
      '/api/gitlab/repository/java-method-slice',
      payload
    );
  }

  readGitLabOpenApiEndpointSlice(
    payload: GitLabOpenApiEndpointSlicePayload
  ): Observable<GitLabOpenApiEndpointSliceResponse> {
    return this.http.post<GitLabOpenApiEndpointSliceResponse>(
      '/api/gitlab/repository/openapi-endpoint-slice',
      payload
    );
  }
}
