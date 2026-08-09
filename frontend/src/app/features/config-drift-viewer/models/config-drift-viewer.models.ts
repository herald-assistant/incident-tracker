import {
  AnalysisAiActivityEvent,
  AnalysisAiUsage,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse,
  AnalysisReport
} from '../../../core/models/analysis.models';

export type ConfigDriftViewerMode = 'BASIC' | 'DEEP';
export type ConfigDriftViewerStatus =
  | 'NO_BLOCKING_ANOMALIES'
  | 'REVIEW_REQUIRED'
  | 'LIKELY_CONFIGURATION_ERROR'
  | 'INCOMPLETE';

export interface ConfigDriftViewerJobStartRequest {
  mode: ConfigDriftViewerMode;
  repositoryId: string;
  systemIds: string[];
  sourceBranch: string;
  targetBranch: string;
  codeRef?: string;
  model?: string;
  reasoningEffort?: string;
}

export interface ConfigDriftViewerRepositoryOption {
  id: string;
  label: string;
}

export interface ConfigDriftViewerSystemOption {
  id: string;
  label: string;
  configurationDirectory: string;
}

export interface ConfigDriftViewerInputOptions {
  modes: ConfigDriftViewerMode[];
  branches: string[];
  repositories: ConfigDriftViewerRepositoryOption[];
  systems: ConfigDriftViewerSystemOption[];
}

export interface ConfigDriftViewerDeepPreflightBlocker {
  code: string;
  message: string;
}

export interface ConfigDriftViewerDeepRepositoryScope {
  scopeId: string;
  repositoryId: string;
  role: string;
  priority?: number | null;
  projectPath: string;
  projectName: string;
  searchMode?: string | null;
  pathPrefixes?: string[];
  requestedRef: string | null;
  usedRef: string | null;
  refSource: string;
  refExists: boolean;
  deploymentRefConfirmed: boolean;
  ready: boolean;
  visibilityLimits: string[];
}

export interface ConfigDriftViewerDeepPreflight {
  status: 'READY' | 'BLOCKED';
  repositoryId: string;
  systemId: string;
  systemLabel: string;
  resolvedConfigurationDirectory: string;
  repositories: ConfigDriftViewerDeepRepositoryScope[];
  blockers: ConfigDriftViewerDeepPreflightBlocker[];
  visibilityLimits: string[];
  ready: boolean;
}

export interface ConfigDriftViewerBranchCoverage {
  branch: string;
  branchExists: boolean;
  files: ConfigDriftViewerFileCoverage[];
  complete: boolean;
}

export interface ConfigDriftViewerFileCoverage {
  role: string;
  path: string;
  status: string;
  commitId: string | null;
  lastCommitId: string | null;
  lastModifiedAt: string | null;
  sizeBytes: number | null;
  errorCode: string | null;
}

export interface SanitizedConfigurationNode {
  name: string;
  path: string;
  sourceType: string | null;
  targetType: string | null;
  relation: string;
  sensitivity: string;
  sourceValueToken: string | null;
  targetValueToken: string | null;
  sourceCardinality: number | null;
  targetCardinality: number | null;
  children: SanitizedConfigurationNode[];
}

export interface SanitizedConfigurationDocument {
  role: string;
  sourcePath: string | null;
  targetPath: string | null;
  documentIndex: number;
  sourcePresent: boolean;
  targetPresent: boolean;
  sourceProfileToken: string | null;
  targetProfileToken: string | null;
  root: SanitizedConfigurationNode;
}

export interface ConfigDriftViewerDifference {
  differenceId: string;
  role: string;
  documentIndex: number;
  path: string;
  kind: string;
  sourceType: string | null;
  targetType: string | null;
  sensitivity: string;
  sourceValueToken: string | null;
  targetValueToken: string | null;
}

export interface ConfigDriftViewerFinding {
  findingId: string;
  code: string;
  severity: string;
  path: string;
  differenceIds: string[];
  referenceIds: string[];
  filePath?: string | null;
  line?: number | null;
}

export interface ConfigDriftViewerReference {
  referenceId: string;
  sourceRole: string;
  documentIndex: number;
  sourcePath: string;
  targetPath: string;
  referenceKind: string;
  sourceStatus: string;
  targetStatus: string;
}

export interface ConfigDriftViewerDeterministicContext {
  repositoryId: string;
  systemId: string;
  systemLabel: string;
  configurationDirectory: string;
  sourceBranch: string;
  targetBranch: string;
  status: string;
  sourceCoverage: ConfigDriftViewerBranchCoverage | null;
  targetCoverage: ConfigDriftViewerBranchCoverage | null;
  documents: SanitizedConfigurationDocument[];
  references: ConfigDriftViewerReference[];
  differences: ConfigDriftViewerDifference[];
  findings: ConfigDriftViewerFinding[];
}

export interface ConfigDriftViewerDiffValue {
  presence: 'PRESENT' | 'ABSENT';
  type: string | null;
  value: unknown;
  cardinality: number | null;
}

export interface ConfigDriftViewerDiffNode {
  name: string;
  path: string;
  changeKind: string;
  source: ConfigDriftViewerDiffValue;
  target: ConfigDriftViewerDiffValue;
  sourceEffective?: ConfigDriftViewerDiffValue | null;
  targetEffective?: ConfigDriftViewerDiffValue | null;
  differenceIds: string[];
  children: ConfigDriftViewerDiffNode[];
}

export interface ConfigDriftViewerDiffDocument {
  documentIndex: number;
  sourcePresent: boolean;
  targetPresent: boolean;
  sourceProfile: ConfigDriftViewerDiffValue;
  targetProfile: ConfigDriftViewerDiffValue;
  root: ConfigDriftViewerDiffNode;
}

export interface ConfigDriftViewerDiffFile {
  role: string;
  format: 'YAML' | 'VAR';
  sourcePath: string | null;
  targetPath: string | null;
  sourcePresent: boolean;
  targetPresent: boolean;
  documents: ConfigDriftViewerDiffDocument[];
}

export interface ConfigDriftViewerDiffProjection {
  sourceBranch: string;
  targetBranch: string;
  files: ConfigDriftViewerDiffFile[];
}

export interface ConfigDriftViewerDiffAnnotation {
  sourceId: string;
  kind: 'OBSERVATION' | 'FUNCTIONAL_IMPACT';
  comment: string;
  confidence: string | null;
  hypothesis: boolean;
  differenceIds: string[];
  findingIds: string[];
}

export interface ConfigDriftViewerAiObservation {
  observationId: string;
  type: 'GROUNDED_OBSERVATION' | 'HYPOTHESIS';
  summary: string;
  explanation: string;
  differenceIds: string[];
  findingIds: string[];
  contextIds: string[];
  codeGroundingIds: string[];
}

export interface ConfigDriftViewerFunctionalImpact {
  impactId: string;
  affectedFunctionality: string;
  impact: string;
  confidence: string;
  hypothesis: boolean;
  systemIds: string[];
  differenceIds: string[];
  findingIds: string[];
  contextIds: string[];
  codeGroundingIds: string[];
}

export interface ConfigDriftViewerAiSecondOpinion {
  executionStatus: 'COMPLETED' | 'INCOMPLETE';
  conclusion: string;
  confidence: string;
  summary: string;
  observations: ConfigDriftViewerAiObservation[];
  recommendedHumanChecks: string[];
  functionalImpacts: ConfigDriftViewerFunctionalImpact[];
  visibilityLimits: string[];
}

export interface ConfigDriftViewerAgreement {
  status: string;
  explanation: string;
  alignedFindingIds: string[];
  disputedFindingIds: string[];
}

export interface ConfigDriftViewerAffectedEntity {
  contextId: string;
  type: string;
  entityId: string;
  label: string;
  summary: string;
  evidenceKind: string;
  confidence: string;
  differenceIds: string[];
  codeGroundingIds: string[];
}

export interface ConfigDriftViewerCodeGrounding {
  groundingId: string;
  scopeId: string;
  repositoryId: string;
  projectPath: string;
  usedRef: string;
  filePath: string;
  lineNumber: number | null;
  symbol: string;
  matchedPropertyPath: string;
  differenceId: string;
  usageKind: string;
  confidence: string;
}

export interface ConfigDriftViewerOwner {
  targetType: string;
  targetId: string;
  targetLabel: string;
  ownerTeamIds: string[];
  ownerLabel: string;
  source: string;
  confidence: string;
}

export interface ConfigDriftViewerOwnership {
  situationType: string;
  primaryOwners: ConfigDriftViewerOwner[];
  partnerOwners: ConfigDriftViewerOwner[];
  resolutionPath: string[];
  handoffReason: string | null;
  visibilityLimits: string[];
}

export interface ConfigDriftViewerDeepContext {
  status: 'COMPLETE' | 'PARTIAL' | 'UNAVAILABLE';
  preflight?: ConfigDriftViewerDeepPreflight | null;
  primarySystem: {
    systemId: string;
    label: string;
    kind: string;
    resolvedConfigurationDirectory: string;
    configurationDirectoryResolution: string;
    codeSearchScopeIds: string[];
  } | null;
  affectedSystems: ConfigDriftViewerAffectedEntity[];
  integrations: ConfigDriftViewerAffectedEntity[];
  processes: ConfigDriftViewerAffectedEntity[];
  boundedContexts: ConfigDriftViewerAffectedEntity[];
  codeGrounding: ConfigDriftViewerCodeGrounding[];
  ownership: ConfigDriftViewerOwnership | null;
  coverage?: {
    repositoriesPlanned: number;
    repositoriesSearched: number;
    keysSearched: number;
    filesInspected: number;
    codeMatches: number;
    unavailableRepositoryIds: string[];
    systemsWithoutCodeSearchScope: string[];
  } | null;
  visibilityLimits: string[];
}

export interface ConfigDriftViewerWorkbenchPreviewRequest {
  mode: ConfigDriftViewerMode;
  repositoryId: string;
  systemId: string;
  sourceBranch: string;
  targetBranch: string;
  codeRef?: string;
}

export type ConfigDriftViewerValueRepresentation =
  | 'PSEUDONYMIZED'
  | 'SUPPRESSED'
  | 'STRUCTURE_ONLY'
  | 'NOT_PRESENT';

export interface ConfigDriftViewerWorkbenchSourceSummary {
  configurationDirectory: string;
  sourceBranchExists: boolean;
  sourceComplete: boolean;
  targetBranchExists: boolean;
  targetComplete: boolean;
}

export interface ConfigDriftViewerWorkbenchCounts {
  documents: number;
  nodes: number;
  differences: number;
  findings: number;
  references: number;
}

export interface ConfigDriftViewerWorkbenchAnonymizationSummary {
  totalNodes: number;
  pseudonymizedRepresentations: number;
  suppressedRepresentations: number;
  structureOnlyRepresentations: number;
  notPresentRepresentations: number;
}

export interface ConfigDriftViewerWorkbenchDeepSummary {
  requested: boolean;
  status: string | null;
  preflightStatus: string | null;
  repositoryScopes: number;
  blockers: number;
  codeGroundings: number;
  primaryOwners: number;
}

export interface ConfigDriftViewerWorkbenchArtifactSummary {
  name: string;
  mediaType: string;
  characterCount: number;
  truncated: boolean;
}

export interface ConfigDriftViewerWorkbenchPreviewResponse {
  previewId: string;
  expiresAt: string;
  mode: ConfigDriftViewerMode;
  repositoryId: string;
  systemId: string;
  sourceBranch: string;
  targetBranch: string;
  codeRef: string | null;
  source: ConfigDriftViewerWorkbenchSourceSummary;
  counts: ConfigDriftViewerWorkbenchCounts;
  anonymization: ConfigDriftViewerWorkbenchAnonymizationSummary;
  deep: ConfigDriftViewerWorkbenchDeepSummary;
  aiInputGenerated: boolean;
  artifacts: ConfigDriftViewerWorkbenchArtifactSummary[];
  visibilityLimits: string[];
}

export interface ConfigDriftViewerWorkbenchConfigurationDiffResponse {
  previewId: string;
  configurationDiff: ConfigDriftViewerDiffProjection;
}

export interface ConfigDriftViewerWorkbenchSourceResponse {
  previewId: string;
  configurationDirectory: string;
  source: ConfigDriftViewerBranchCoverage | null;
  target: ConfigDriftViewerBranchCoverage | null;
}

export interface ConfigDriftViewerWorkbenchMappingItem {
  role: string;
  documentIndex: number;
  depth: number;
  originalName: string;
  originalPath: string;
  sanitizedName: string;
  sanitizedPath: string;
  sourceType: string | null;
  targetType: string | null;
  changeKind: string;
  sensitivity: string;
  sourceValueToken: string | null;
  targetValueToken: string | null;
  differenceIds: string[];
}

export interface ConfigDriftViewerWorkbenchMappingPage {
  previewId: string;
  offset: number;
  limit: number;
  totalItems: number;
  totalNodes: number;
  changedOnly: boolean;
  items: ConfigDriftViewerWorkbenchMappingItem[];
}

export interface ConfigDriftViewerWorkbenchAnonymizationItem {
  role: string;
  documentIndex: number;
  path: string;
  relation: string;
  sensitivity: string;
  sourceType: string | null;
  targetType: string | null;
  sourceRepresentation: ConfigDriftViewerValueRepresentation;
  targetRepresentation: ConfigDriftViewerValueRepresentation;
  sourceValueToken: string | null;
  targetValueToken: string | null;
}

export interface ConfigDriftViewerWorkbenchAnonymizationPage {
  previewId: string;
  offset: number;
  limit: number;
  totalItems: number;
  items: ConfigDriftViewerWorkbenchAnonymizationItem[];
}

export interface ConfigDriftViewerWorkbenchDeepResponse {
  previewId: string;
  requested: boolean;
  context: ConfigDriftViewerDeepContext | null;
}

export interface ConfigDriftViewerWorkbenchAiInputResponse {
  previewId: string;
  generated: boolean;
  characterCount: number;
  prompt: string | null;
}

export interface ConfigDriftViewerWorkbenchArtifactResponse {
  previewId: string;
  name: string;
  mediaType: string;
  characterCount: number;
  truncated: boolean;
  content: string;
}

export interface ConfigDriftViewerResult {
  status: ConfigDriftViewerStatus;
  mode: ConfigDriftViewerMode;
  deterministicResult: ConfigDriftViewerDeterministicContext;
  configurationDiff: ConfigDriftViewerDiffProjection;
  configurationDiffAnnotations: ConfigDriftViewerDiffAnnotation[];
  aiSecondOpinion: ConfigDriftViewerAiSecondOpinion | null;
  agreement: ConfigDriftViewerAgreement | null;
  deepAnalysis: ConfigDriftViewerDeepContext | null;
  visibilityLimits: string[];
  prompt: string | null;
  usage: AnalysisAiUsage | null;
}

export interface ConfigDriftViewerComponentRunSnapshot {
  componentRunId: string;
  systemId: string;
  systemLabel: string | null;
  configurationDirectory: string | null;
  status: string;
  currentStepCode: string | null;
  currentStepLabel: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  steps: AnalysisJobStepResponse[];
  contextSections: AnalysisEvidenceSection[];
  toolEvidenceSections: AnalysisEvidenceSection[];
  aiActivityEvents: AnalysisAiActivityEvent[];
  preparedPrompt: string | null;
  result: ConfigDriftViewerResult | null;
  report: AnalysisReport | null;
}

export interface ConfigDriftViewerJobStateSnapshot {
  jobId: string;
  mode: ConfigDriftViewerMode;
  repositoryId: string;
  systemIds: string[];
  sourceBranch: string;
  targetBranch: string;
  codeRef: string | null;
  aiModel: string | null;
  reasoningEffort: string | null;
  status: string;
  currentStepCode: string | null;
  currentStepLabel: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  steps: AnalysisJobStepResponse[];
  components: ConfigDriftViewerComponentRunSnapshot[];
  imported: boolean;
}

export interface ConfigDriftViewerExportEnvelope {
  schema: 'tdw.config-drift-viewer-export';
  version: 1;
  exportedAt: string;
  payload: {
    type: 'config-drift-viewer-analysis';
    resultContract: 'config-drift-viewer-result-v1';
    job: ConfigDriftViewerJobStateSnapshot;
  };
}
