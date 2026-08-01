import {
  AnalysisAiActivityEvent,
  AnalysisAiUsage,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse,
  AnalysisReport
} from '../../../core/models/analysis.models';

export type RuntimeConfigurationVerificationMode = 'BASIC' | 'DEEP';
export type RuntimeConfigurationVerificationStatus =
  | 'NO_BLOCKING_ANOMALIES'
  | 'REVIEW_REQUIRED'
  | 'LIKELY_CONFIGURATION_ERROR'
  | 'INCOMPLETE';

export interface RuntimeConfigurationVerificationJobStartRequest {
  mode: RuntimeConfigurationVerificationMode;
  repositoryId: string;
  systemId: string;
  sourceBranch: string;
  targetBranch: string;
  codeRef?: string;
  model?: string;
  reasoningEffort?: string;
}

export interface RuntimeConfigurationRepositoryOption {
  id: string;
  label: string;
}

export interface RuntimeConfigurationSystemOption {
  id: string;
  label: string;
  configurationDirectory: string;
}

export interface RuntimeConfigurationVerificationInputOptions {
  modes: RuntimeConfigurationVerificationMode[];
  branches: string[];
  repositories: RuntimeConfigurationRepositoryOption[];
  systems: RuntimeConfigurationSystemOption[];
}

export interface RuntimeConfigurationDeepPreflightBlocker {
  code: string;
  message: string;
}

export interface RuntimeConfigurationDeepRepositoryScope {
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

export interface RuntimeConfigurationDeepPreflight {
  status: 'READY' | 'BLOCKED';
  repositoryId: string;
  systemId: string;
  systemLabel: string;
  resolvedConfigurationDirectory: string;
  repositories: RuntimeConfigurationDeepRepositoryScope[];
  blockers: RuntimeConfigurationDeepPreflightBlocker[];
  visibilityLimits: string[];
  ready: boolean;
}

export interface RuntimeConfigurationBranchCoverage {
  branch: string;
  branchExists: boolean;
  files: RuntimeConfigurationFileCoverage[];
  complete: boolean;
}

export interface RuntimeConfigurationFileCoverage {
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

export interface RuntimeConfigurationDifference {
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

export interface RuntimeConfigurationFinding {
  findingId: string;
  code: string;
  severity: string;
  path: string;
  differenceIds: string[];
  referenceIds: string[];
  filePath?: string | null;
  line?: number | null;
}

export interface RuntimeConfigurationReference {
  referenceId: string;
  sourceRole: string;
  documentIndex: number;
  sourcePath: string;
  targetPath: string;
  referenceKind: string;
  sourceStatus: string;
  targetStatus: string;
}

export interface RuntimeConfigurationDeterministicContext {
  repositoryId: string;
  systemId: string;
  systemLabel: string;
  configurationDirectory: string;
  sourceBranch: string;
  targetBranch: string;
  status: string;
  sourceCoverage: RuntimeConfigurationBranchCoverage | null;
  targetCoverage: RuntimeConfigurationBranchCoverage | null;
  documents: SanitizedConfigurationDocument[];
  references: RuntimeConfigurationReference[];
  differences: RuntimeConfigurationDifference[];
  findings: RuntimeConfigurationFinding[];
}

export interface RuntimeConfigurationDiffValue {
  presence: 'PRESENT' | 'ABSENT';
  type: string | null;
  value: unknown;
  cardinality: number | null;
}

export interface RuntimeConfigurationDiffNode {
  name: string;
  path: string;
  changeKind: string;
  source: RuntimeConfigurationDiffValue;
  target: RuntimeConfigurationDiffValue;
  sourceEffective?: RuntimeConfigurationDiffValue | null;
  targetEffective?: RuntimeConfigurationDiffValue | null;
  differenceIds: string[];
  children: RuntimeConfigurationDiffNode[];
}

export interface RuntimeConfigurationDiffDocument {
  documentIndex: number;
  sourcePresent: boolean;
  targetPresent: boolean;
  sourceProfile: RuntimeConfigurationDiffValue;
  targetProfile: RuntimeConfigurationDiffValue;
  root: RuntimeConfigurationDiffNode;
}

export interface RuntimeConfigurationDiffFile {
  role: string;
  format: 'YAML' | 'VAR';
  sourcePath: string | null;
  targetPath: string | null;
  sourcePresent: boolean;
  targetPresent: boolean;
  documents: RuntimeConfigurationDiffDocument[];
}

export interface RuntimeConfigurationDiffProjection {
  sourceBranch: string;
  targetBranch: string;
  files: RuntimeConfigurationDiffFile[];
}

export interface RuntimeConfigurationDiffAnnotation {
  sourceId: string;
  kind: 'OBSERVATION' | 'FUNCTIONAL_IMPACT';
  comment: string;
  confidence: string | null;
  hypothesis: boolean;
  differenceIds: string[];
  findingIds: string[];
}

export interface RuntimeConfigurationAiObservation {
  observationId: string;
  type: 'GROUNDED_OBSERVATION' | 'HYPOTHESIS';
  summary: string;
  explanation: string;
  differenceIds: string[];
  findingIds: string[];
  contextIds: string[];
  codeGroundingIds: string[];
}

export interface RuntimeConfigurationFunctionalImpact {
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

export interface RuntimeConfigurationAiSecondOpinion {
  executionStatus: 'COMPLETED' | 'INCOMPLETE';
  conclusion: string;
  confidence: string;
  summary: string;
  observations: RuntimeConfigurationAiObservation[];
  recommendedHumanChecks: string[];
  functionalImpacts: RuntimeConfigurationFunctionalImpact[];
  visibilityLimits: string[];
}

export interface RuntimeConfigurationAgreement {
  status: string;
  explanation: string;
  alignedFindingIds: string[];
  disputedFindingIds: string[];
}

export interface RuntimeConfigurationAffectedEntity {
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

export interface RuntimeConfigurationCodeGrounding {
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

export interface RuntimeConfigurationOwner {
  targetType: string;
  targetId: string;
  targetLabel: string;
  ownerTeamIds: string[];
  ownerLabel: string;
  source: string;
  confidence: string;
}

export interface RuntimeConfigurationOwnership {
  situationType: string;
  primaryOwners: RuntimeConfigurationOwner[];
  partnerOwners: RuntimeConfigurationOwner[];
  resolutionPath: string[];
  handoffReason: string | null;
  visibilityLimits: string[];
}

export interface RuntimeConfigurationDeepContext {
  status: 'COMPLETE' | 'PARTIAL' | 'UNAVAILABLE';
  preflight?: RuntimeConfigurationDeepPreflight | null;
  primarySystem: {
    systemId: string;
    label: string;
    kind: string;
    resolvedConfigurationDirectory: string;
    configurationDirectoryResolution: string;
    codeSearchScopeIds: string[];
  } | null;
  affectedSystems: RuntimeConfigurationAffectedEntity[];
  integrations: RuntimeConfigurationAffectedEntity[];
  processes: RuntimeConfigurationAffectedEntity[];
  boundedContexts: RuntimeConfigurationAffectedEntity[];
  codeGrounding: RuntimeConfigurationCodeGrounding[];
  ownership: RuntimeConfigurationOwnership | null;
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

export interface RuntimeConfigurationWorkbenchPreviewRequest {
  mode: RuntimeConfigurationVerificationMode;
  repositoryId: string;
  systemId: string;
  sourceBranch: string;
  targetBranch: string;
  codeRef?: string;
}

export type RuntimeConfigurationValueRepresentation =
  | 'PSEUDONYMIZED'
  | 'SUPPRESSED'
  | 'STRUCTURE_ONLY'
  | 'NOT_PRESENT';

export interface RuntimeConfigurationWorkbenchSourceSummary {
  configurationDirectory: string;
  sourceBranchExists: boolean;
  sourceComplete: boolean;
  targetBranchExists: boolean;
  targetComplete: boolean;
}

export interface RuntimeConfigurationWorkbenchCounts {
  documents: number;
  nodes: number;
  differences: number;
  findings: number;
  references: number;
}

export interface RuntimeConfigurationWorkbenchAnonymizationSummary {
  totalNodes: number;
  pseudonymizedRepresentations: number;
  suppressedRepresentations: number;
  structureOnlyRepresentations: number;
  notPresentRepresentations: number;
}

export interface RuntimeConfigurationWorkbenchDeepSummary {
  requested: boolean;
  status: string | null;
  preflightStatus: string | null;
  repositoryScopes: number;
  blockers: number;
  codeGroundings: number;
  primaryOwners: number;
}

export interface RuntimeConfigurationWorkbenchArtifactSummary {
  name: string;
  mediaType: string;
  characterCount: number;
  truncated: boolean;
}

export interface RuntimeConfigurationWorkbenchPreviewResponse {
  previewId: string;
  expiresAt: string;
  mode: RuntimeConfigurationVerificationMode;
  repositoryId: string;
  systemId: string;
  sourceBranch: string;
  targetBranch: string;
  codeRef: string | null;
  source: RuntimeConfigurationWorkbenchSourceSummary;
  counts: RuntimeConfigurationWorkbenchCounts;
  anonymization: RuntimeConfigurationWorkbenchAnonymizationSummary;
  deep: RuntimeConfigurationWorkbenchDeepSummary;
  aiInputGenerated: boolean;
  artifacts: RuntimeConfigurationWorkbenchArtifactSummary[];
  visibilityLimits: string[];
}

export interface RuntimeConfigurationWorkbenchConfigurationDiffResponse {
  previewId: string;
  configurationDiff: RuntimeConfigurationDiffProjection;
}

export interface RuntimeConfigurationWorkbenchSourceResponse {
  previewId: string;
  configurationDirectory: string;
  source: RuntimeConfigurationBranchCoverage | null;
  target: RuntimeConfigurationBranchCoverage | null;
}

export interface RuntimeConfigurationWorkbenchMappingItem {
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

export interface RuntimeConfigurationWorkbenchMappingPage {
  previewId: string;
  offset: number;
  limit: number;
  totalItems: number;
  totalNodes: number;
  changedOnly: boolean;
  items: RuntimeConfigurationWorkbenchMappingItem[];
}

export interface RuntimeConfigurationWorkbenchAnonymizationItem {
  role: string;
  documentIndex: number;
  path: string;
  relation: string;
  sensitivity: string;
  sourceType: string | null;
  targetType: string | null;
  sourceRepresentation: RuntimeConfigurationValueRepresentation;
  targetRepresentation: RuntimeConfigurationValueRepresentation;
  sourceValueToken: string | null;
  targetValueToken: string | null;
}

export interface RuntimeConfigurationWorkbenchAnonymizationPage {
  previewId: string;
  offset: number;
  limit: number;
  totalItems: number;
  items: RuntimeConfigurationWorkbenchAnonymizationItem[];
}

export interface RuntimeConfigurationWorkbenchDeepResponse {
  previewId: string;
  requested: boolean;
  context: RuntimeConfigurationDeepContext | null;
}

export interface RuntimeConfigurationWorkbenchAiInputResponse {
  previewId: string;
  generated: boolean;
  characterCount: number;
  prompt: string | null;
}

export interface RuntimeConfigurationWorkbenchArtifactResponse {
  previewId: string;
  name: string;
  mediaType: string;
  characterCount: number;
  truncated: boolean;
  content: string;
}

export interface RuntimeConfigurationVerificationResult {
  status: RuntimeConfigurationVerificationStatus;
  mode: RuntimeConfigurationVerificationMode;
  deterministicResult: RuntimeConfigurationDeterministicContext;
  configurationDiff: RuntimeConfigurationDiffProjection | null;
  configurationDiffAnnotations?: RuntimeConfigurationDiffAnnotation[];
  aiSecondOpinion: RuntimeConfigurationAiSecondOpinion | null;
  agreement: RuntimeConfigurationAgreement | null;
  deepAnalysis: RuntimeConfigurationDeepContext | null;
  visibilityLimits: string[];
  prompt: string | null;
  usage: AnalysisAiUsage | null;
}

export interface RuntimeConfigurationVerificationJobStateSnapshot {
  jobId: string;
  mode: RuntimeConfigurationVerificationMode;
  repositoryId: string;
  systemId: string;
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
  contextSections: AnalysisEvidenceSection[];
  toolEvidenceSections: AnalysisEvidenceSection[];
  aiActivityEvents: AnalysisAiActivityEvent[];
  preparedPrompt: string | null;
  result: RuntimeConfigurationVerificationResult | null;
  report: AnalysisReport | null;
  imported: boolean;
}

export interface RuntimeConfigurationExportEnvelope {
  schema: 'tdw.runtime-configuration-verification-export';
  version: 1;
  exportedAt: string;
  payload: {
    type: 'runtime-configuration-verification-analysis';
    resultContract: 'runtime-configuration-verification-result-v1';
    job: RuntimeConfigurationVerificationJobStateSnapshot;
  };
}
