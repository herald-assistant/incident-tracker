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

export interface GitLabMergeRequestSearchPayload {
  group?: string;
  issueKey: string;
  maxResults?: number;
}

export interface GitLabInstructionContextPayload {
  repositoryKey: string;
  ref: string;
  changedFilePaths: string[];
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

export interface GitLabJavaMethodUseCaseContextPayload {
  group: string;
  projectName: string;
  branch: string;
  filePath?: string;
  className: string;
  methodName: string;
  lineNumber?: number;
  parameterCount?: number;
  parameterTypes?: string[];
  maxDepth?: number;
  maxResults?: number;
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

export interface GitLabFrontendCatalogPayload {
  group: string;
  projectName: string;
  ref: string;
  pathPrefixes: string[];
}

export interface GitLabFrontendScreenContextPayload extends GitLabFrontendCatalogPayload {
  screenId: string;
  expectedRevision?: string;
}

export type GitLabFrontendScreenReachabilityPayload = GitLabFrontendScreenContextPayload;

export interface GitLabAngularRouteBranchSlicePayload extends GitLabFrontendCatalogPayload {
  screenId: string;
  expectedRevision?: string;
  includeDescendantRoutes?: boolean;
  maxCharacters?: number;
}

export type GitLabTypeScriptSymbolKind =
  | 'AUTO'
  | 'METHOD'
  | 'PROPERTY'
  | 'GETTER'
  | 'SETTER'
  | 'CONSTRUCTOR'
  | 'FUNCTION';

export interface GitLabTypeScriptSymbolSelector {
  name: string;
  kind?: GitLabTypeScriptSymbolKind;
  lineStart?: number;
}

export interface GitLabTypeScriptSymbolSlicePayload extends GitLabFrontendCatalogPayload {
  filePath: string;
  declaringTypeName?: string;
  templatePath?: string;
  includeTemplateBindings?: boolean;
  symbolSelectors: GitLabTypeScriptSymbolSelector[];
  includeLocalHelpers?: boolean;
  includeRelevantFields?: boolean;
  includeRelevantImports?: boolean;
  maxCharacters?: number;
}

export interface GitLabFrontendSourceReference {
  path?: string | null;
  symbol?: string | null;
  startLine?: number | null;
  endLine?: number | null;
}

export interface GitLabFrontendRouteTarget {
  symbol?: string | null;
  sourcePath?: string | null;
}

export interface GitLabFrontendScreenIdentity {
  screenId: string;
  routeNodeId: string;
  routePattern: string;
  outlet: string;
  viewTarget: GitLabFrontendRouteTarget;
}

export interface GitLabFrontendRouteConfiguration {
  kind: string;
  key?: string | null;
  referencedSymbols: string[];
  staticValue?: string | null;
  status: string;
  source?: GitLabFrontendSourceReference | null;
  limitations: string[];
}

export interface GitLabFrontendRouteNode {
  nodeId: string;
  parentNodeId?: string | null;
  screen?: GitLabFrontendScreenIdentity | null;
  label?: string | null;
  pathSegment?: string | null;
  routePattern: string;
  outlet: string;
  kind: string;
  status: string;
  lazyBoundary: boolean;
  routeParameters: string[];
  redirectTarget?: string | null;
  viewTarget?: GitLabFrontendRouteTarget | null;
  lazyTarget?: GitLabFrontendRouteTarget | null;
  configuration: GitLabFrontendRouteConfiguration[];
  routeSource: GitLabFrontendSourceReference;
  limitations: string[];
}

export interface GitLabFrontendWorkspaceSignal {
  kind?: string | null;
  value?: string | null;
  sourcePath?: string | null;
}

export interface GitLabFrontendGraphDiagnostic {
  severity?: string | null;
  code?: string | null;
  message?: string | null;
  nodeId?: string | null;
  edgeId?: string | null;
  source?: GitLabFrontendSourceReference | null;
}

export interface GitLabFrontendGraphCoverage {
  status: string;
  visitedRouteNodeCount: number;
  visitedRouteFileCount: number;
  sourceReadCount: number;
  aliasResolutionCount: number;
  unresolvedEdgeCount: number;
  limitReached: boolean;
  limitations: string[];
}

export interface GitLabFrontendCatalogResponse {
  scope: GitLabFrontendCatalogPayload;
  sourceRevision?: { ref?: string | null; commitId?: string | null } | null;
  workspaceSignals: GitLabFrontendWorkspaceSignal[];
  nodes: GitLabFrontendRouteNode[];
  diagnostics: GitLabFrontendGraphDiagnostic[];
  coverage: GitLabFrontendGraphCoverage;
}

export interface GitLabFrontendSourceFile {
  path?: string | null;
  roles: string[];
  content?: string | null;
  returnedCharacters: number;
  truncated: boolean;
}

export interface GitLabFrontendTechnicalSignal {
  kind?: string | null;
  description?: string | null;
  confidence?: string | null;
  source?: GitLabFrontendSourceReference | null;
}

export interface GitLabFrontendContextCoverage {
  category?: string | null;
  status?: string | null;
  detail?: string | null;
}

export interface GitLabFrontendScreenContextResponse {
  scope: GitLabFrontendCatalogPayload;
  sourceRevision?: { ref?: string | null; commitId?: string | null } | null;
  screenNode?: GitLabFrontendRouteNode | null;
  graphCoverage: GitLabFrontendGraphCoverage;
  sourceFiles: GitLabFrontendSourceFile[];
  technicalSignals: GitLabFrontendTechnicalSignal[];
  coverage: GitLabFrontendContextCoverage[];
  diagnostics: GitLabFrontendGraphDiagnostic[];
  totalReturnedCharacters: number;
  contextLimitReached: boolean;
}

export interface GitLabFrontendRouteChainSegment {
  nodeId: string;
  pathSegment?: string | null;
  routePattern: string;
  outlet: string;
  configuration: GitLabFrontendRouteConfiguration[];
  source: GitLabFrontendSourceReference;
}

export interface GitLabFrontendEffectiveRouteChain {
  screen: GitLabFrontendScreenIdentity;
  segments: GitLabFrontendRouteChainSegment[];
  routeParameters: string[];
}

export interface GitLabFrontendReachabilityComponent {
  componentId: string;
  breadthFirstOrder: number;
  depth: number;
  connectedToSelectedScreen: boolean;
  discoveryKind: string;
  symbol?: string | null;
  selector?: string | null;
  sourcePath?: string | null;
  templatePath?: string | null;
  status: string;
  templateBindings: GitLabTypeScriptTemplateBinding[];
  entrySymbols: GitLabTypeScriptSymbolCandidate[];
  includedSymbols: GitLabTypeScriptSymbolCandidate[];
  dependencyIds: string[];
  childComponentIds: string[];
  sliceContent: string;
  sourceCharacters: number;
  returnedCharacters: number;
  truncated: boolean;
  limitations: string[];
}

export interface GitLabFrontendReachabilityComponentLevel {
  depth: number;
  components: GitLabFrontendReachabilityComponent[];
}

export interface GitLabFrontendReachabilityDependency {
  dependencyId: string;
  discoveryOrder: number;
  kind: string;
  category: 'FUNCTIONAL' | 'SUPPORTING_CODE' | 'REACTIVE' | 'FRAMEWORK' | 'DATA_MODEL';
  symbol?: string | null;
  sourcePath?: string | null;
  moduleSpecifier?: string | null;
  status: string;
  methods: string[];
  usedBy: string[];
  downstreamDependencyIds: string[];
  sliceContent: string;
  sourceCharacters: number;
  returnedCharacters: number;
  truncated: boolean;
  limitations: string[];
}

export interface GitLabFrontendReachabilityEdge {
  fromId: string;
  toId: string;
  kind: string;
  label?: string | null;
  sourcePath?: string | null;
  sourceSymbol?: string | null;
  memberSymbol?: string | null;
}

export interface GitLabFrontendScreenReachabilityResponse {
  scope: GitLabFrontendCatalogPayload;
  sourceRevision?: { ref?: string | null; commitId?: string | null } | null;
  status: string;
  screenNode: GitLabFrontendRouteNode;
  effectiveRouteChain: GitLabFrontendEffectiveRouteChain;
  componentLevels: GitLabFrontendReachabilityComponentLevel[];
  dependencies: GitLabFrontendReachabilityDependency[];
  edges: GitLabFrontendReachabilityEdge[];
  technicalSignals: GitLabFrontendTechnicalSignal[];
  diagnostics: GitLabFrontendGraphDiagnostic[];
  sourceFileCount: number;
  sourceCharacters: number;
  sliceCharacters: number;
  outlineCharacters: number;
  contextLimitReached: boolean;
  limitations: string[];
  readableOutline: string;
}

export interface GitLabAngularRouteBranchSliceFile {
  path: string;
  content: string;
  sourceCharacters: number;
  returnedCharacters: number;
  includedRouteNodeIds: string[];
  includedImports: string[];
  includedLocalDeclarations: string[];
  unresolvedSymbols: string[];
  omittedImportCount: number;
  omittedSiblingRouteCount: number;
  truncated: boolean;
}

export interface GitLabAngularRouteChildReference {
  sliceRef: string;
  nodeId: string;
  screenId?: string | null;
  routePattern: string;
  label?: string | null;
  kind: string;
  status: string;
  viewTarget?: GitLabFrontendRouteTarget | null;
  lazyTarget?: GitLabFrontendRouteTarget | null;
  redirectTarget?: string | null;
  structural: boolean;
  samePathAsParent: boolean;
  hasChildren: boolean;
}

export interface GitLabAngularRouteBranchSliceResponse {
  scope: GitLabFrontendCatalogPayload;
  sourceRevision?: { ref?: string | null; commitId?: string | null } | null;
  status: string;
  screenNode?: GitLabFrontendRouteNode | null;
  files: GitLabAngularRouteBranchSliceFile[];
  childRoutes: GitLabAngularRouteChildReference[];
  sourceCharacters: number;
  returnedCharacters: number;
  savedCharacters: number;
  omittedImportCount: number;
  omittedSiblingRouteCount: number;
  truncated: boolean;
  limitations: string[];
  diagnostics: GitLabFrontendGraphDiagnostic[];
}

export interface GitLabTypeScriptSymbolCandidate {
  declaringTypeName?: string | null;
  symbolName: string;
  kind: GitLabTypeScriptSymbolKind;
  signature?: string | null;
  lineStart: number;
  lineEnd: number;
}

export interface GitLabTypeScriptDownstreamReference {
  kind: string;
  sourceSymbol: string;
  ownerSymbol: string;
  memberSymbol: string;
  targetSymbol?: string | null;
  moduleSpecifier?: string | null;
  targetSourcePath?: string | null;
}

export interface GitLabTypeScriptTemplateBinding {
  kind: string;
  target: string;
  expression: string;
  referencedSymbols: string[];
  lineStart: number;
}

export interface GitLabTypeScriptSymbolSliceResponse {
  scope: GitLabFrontendCatalogPayload;
  filePath: string;
  status: string;
  declaringTypeName?: string | null;
  lineStart: number;
  lineEnd: number;
  totalLines: number;
  sourceCharacters: number;
  templatePath?: string | null;
  templateCharacters: number;
  templateBindings: GitLabTypeScriptTemplateBinding[];
  content: string;
  returnedCharacters: number;
  savedCharacters: number;
  truncated: boolean;
  includedImports: string[];
  includedFields: string[];
  entrySymbols: GitLabTypeScriptSymbolCandidate[];
  includedSymbols: GitLabTypeScriptSymbolCandidate[];
  omittedImportCount: number;
  omittedFieldCount: number;
  omittedSymbolCount: number;
  downstreamReferences: GitLabTypeScriptDownstreamReference[];
  candidates: GitLabTypeScriptSymbolCandidate[];
  limitations: string[];
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

export interface GitLabJavaMethodUseCaseEntryCandidate {
  filePath?: string | null;
  declaringTypeSimpleName?: string | null;
  declaringTypeRelativeName?: string | null;
  declaringTypeQualifiedName?: string | null;
  declaringTypeKind?: string | null;
  methodName?: string | null;
  signature?: string | null;
  lineStart: number;
  lineEnd: number;
  parameterCount: number;
  parameterTypes: string[];
  parameterNames: string[];
  returnType?: string | null;
  confidence?: string | null;
  reason?: string | null;
}

export interface GitLabJavaMethodUseCaseEntryMethod extends GitLabJavaMethodUseCaseEntryCandidate {
  status: string;
  requestedClassName?: string | null;
  requestedMethodName?: string | null;
  candidates: GitLabJavaMethodUseCaseEntryCandidate[];
  limitations: string[];
}

export interface GitLabJavaMethodUseCaseContextLimits {
  maxDepth: number;
  maxResults: number;
  maxReadFiles: number;
  maxDepthReached: boolean;
  maxResultsReached: boolean;
  readFileCount: number;
  readFileLimitReached: boolean;
}

export interface GitLabJavaMethodUseCaseContextResponse {
  repository?: GitLabEndpointUseCaseRepositoryContext | null;
  entryMethod?: GitLabJavaMethodUseCaseEntryMethod | null;
  files: GitLabEndpointUseCaseFileCandidate[];
  relations: GitLabEndpointUseCaseRelation[];
  unresolved: GitLabEndpointUseCaseUnresolvedReference[];
  limitations: string[];
  suggestedNextReads: string[];
  limits: GitLabJavaMethodUseCaseContextLimits;
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

export interface GitLabMergeRequestCommit {
  id?: string | null;
  shortId?: string | null;
  title?: string | null;
  authorName?: string | null;
  createdAt?: string | null;
}

export interface GitLabMergeRequestChangedFile {
  oldPath?: string | null;
  newPath?: string | null;
  newFile: boolean;
  renamedFile: boolean;
  deletedFile: boolean;
}

export interface GitLabMergeRequest {
  id?: number | null;
  iid?: number | null;
  projectId?: number | null;
  projectPath?: string | null;
  title?: string | null;
  state?: string | null;
  webUrl?: string | null;
  sourceBranch?: string | null;
  targetBranch?: string | null;
  authorName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  mergedAt?: string | null;
  changesCount?: string | null;
  commits: GitLabMergeRequestCommit[];
  changedFiles: GitLabMergeRequestChangedFile[];
  limitations: string[];
}

export interface GitLabMergeRequestSearchResponse {
  issueKey?: string | null;
  group?: string | null;
  mergeRequests: GitLabMergeRequest[];
  limitations: string[];
}

export interface GitLabInstructionSource {
  repositoryKey?: string | null;
  ref?: string | null;
  path?: string | null;
  kind?: string | null;
  content?: string | null;
  truncated: boolean;
  referencedBy?: string | null;
  applicableChangedFiles: string[];
}

export interface GitLabInstructionContextResponse {
  sources: GitLabInstructionSource[];
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

  searchGitLabMergeRequests(
    payload: GitLabMergeRequestSearchPayload
  ): Observable<GitLabMergeRequestSearchResponse> {
    return this.http.post<GitLabMergeRequestSearchResponse>(
      '/api/gitlab/repository/merge-requests/by-issue',
      payload
    );
  }

  discoverGitLabInstructionContext(
    payload: GitLabInstructionContextPayload
  ): Observable<GitLabInstructionContextResponse> {
    return this.http.post<GitLabInstructionContextResponse>(
      '/api/gitlab/repository/instructions/context',
      payload
    );
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

  buildGitLabJavaMethodUseCaseContext(
    payload: GitLabJavaMethodUseCaseContextPayload
  ): Observable<GitLabJavaMethodUseCaseContextResponse> {
    return this.http.post<GitLabJavaMethodUseCaseContextResponse>(
      '/api/gitlab/repository/java-method-use-case-context',
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

  discoverGitLabFrontendCatalog(
    payload: GitLabFrontendCatalogPayload
  ): Observable<GitLabFrontendCatalogResponse> {
    return this.http.post<GitLabFrontendCatalogResponse>('/api/gitlab/frontend/catalog', payload);
  }

  buildGitLabFrontendScreenContext(
    payload: GitLabFrontendScreenContextPayload
  ): Observable<GitLabFrontendScreenContextResponse> {
    return this.http.post<GitLabFrontendScreenContextResponse>(
      '/api/gitlab/frontend/screen-context',
      payload
    );
  }

  buildGitLabFrontendScreenReachability(
    payload: GitLabFrontendScreenReachabilityPayload
  ): Observable<GitLabFrontendScreenReachabilityResponse> {
    return this.http.post<GitLabFrontendScreenReachabilityResponse>(
      '/api/gitlab/frontend/screen-reachability',
      payload
    );
  }

  readGitLabAngularRouteBranchSlice(
    payload: GitLabAngularRouteBranchSlicePayload
  ): Observable<GitLabAngularRouteBranchSliceResponse> {
    return this.http.post<GitLabAngularRouteBranchSliceResponse>(
      '/api/gitlab/frontend/route-branch-slice',
      payload
    );
  }

  readGitLabTypeScriptSymbolSlice(
    payload: GitLabTypeScriptSymbolSlicePayload
  ): Observable<GitLabTypeScriptSymbolSliceResponse> {
    return this.http.post<GitLabTypeScriptSymbolSliceResponse>(
      '/api/gitlab/frontend/typescript-symbol-slice',
      payload
    );
  }
}
