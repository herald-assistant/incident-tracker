import {
  AnalysisAiActivityEvent,
  AnalysisAiToolFeedback,
  AnalysisAiUsage,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse,
  AnalysisReport
} from '../../../core/models/analysis.models';

export type UiExplorerSectionId =
  | 'OVERVIEW'
  | 'NAVIGATION_AND_ACCESS'
  | 'SCREEN_STRUCTURE'
  | 'ACTIONS_AND_OUTCOMES'
  | 'FORMS_AND_RULES'
  | 'DATA_AND_SERVICES'
  | 'STATE_AND_SYNCHRONIZATION'
  | 'VARIANTS_AND_FAILURES';

export type UiExplorerSectionMode = 'OFF' | 'COMPACT' | 'DEEP';
export type UiExplorerScreenCatalogStatus = 'READY' | 'PARTIAL' | 'BLOCKED';
export type UiExplorerLoadingState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';
export type UiExplorerClaimConfidence = 'CONFIRMED' | 'INFERRED' | 'UNKNOWN';
export type UiExplorerCoverageStatus = 'READY' | 'PARTIAL' | 'BLOCKED';

export interface UiExplorerOutputAvailability {
  status: 'AVAILABLE' | 'BLOCKED';
  code: string;
  message: string;
  missingCapabilities: string[];
}

export interface UiExplorerSystemOption {
  systemId: string;
  label: string;
  summary: string;
}

export interface UiExplorerSectionModeAssignment {
  sectionId: UiExplorerSectionId;
  mode: UiExplorerSectionMode;
}

export interface UiExplorerSectionOption {
  sectionId: UiExplorerSectionId;
  label: string;
  description: string;
}

export interface UiExplorerModeOption {
  mode: UiExplorerSectionMode;
  label: string;
  description: string;
}

export interface UiExplorerConfigurationFinding {
  severity: string;
  code: string;
  message: string;
  entityType: string;
  entityId: string;
}

export interface UiExplorerInputOptionsResponse {
  featureId: string;
  executionAvailability: UiExplorerOutputAvailability;
  systems: UiExplorerSystemOption[];
  defaultSectionModes: UiExplorerSectionModeAssignment[];
  sections: UiExplorerSectionOption[];
  modes: UiExplorerModeOption[];
  configurationFindings: UiExplorerConfigurationFinding[];
}

export interface UiExplorerSourceRevision {
  branch: string;
  revision: string;
}

export interface UiExplorerScreenCatalogEntry {
  screenId: string;
  label: string;
  routePattern: string;
  parentRoutePattern: string;
  status: string;
  lazyLoaded: boolean;
  guards: string[];
  routeParameters: string[];
  limitations: string[];
}

export interface UiExplorerScreenCatalogDiagnostic {
  severity: string;
  code: string;
  message: string;
  sourcePath: string;
}

export interface UiExplorerScreenCatalogBoundary {
  visitedRouteNodeCount: number;
  visitedRouteFileCount: number;
  sourceReadCount: number;
  aliasResolutionCount: number;
  unresolvedEdgeCount: number;
  limitReached: boolean;
  maxRouteNodes: number;
  maxRouteFiles: number;
  maxSourceReads: number;
  maxAliasResolutions: number;
  maxImportDepth: number;
}

export interface UiExplorerScreenCatalogResponse {
  systemId: string;
  systemLabel: string;
  sourceRevision: UiExplorerSourceRevision;
  status: UiExplorerScreenCatalogStatus;
  screens: UiExplorerScreenCatalogEntry[];
  diagnostics: UiExplorerScreenCatalogDiagnostic[];
  limitations: string[];
  boundary: UiExplorerScreenCatalogBoundary;
}

export interface UiExplorerConfigurationSnapshot {
  systemId: string;
  branch: string;
  screenId: string;
  sourceRevision: string;
  sectionModes: Partial<Record<UiExplorerSectionId, UiExplorerSectionMode>>;
  scenarioDescription: string;
  model: string;
  reasoningEffort: string;
}

export interface UiExplorerJobStartRequest {
  systemId: string;
  branch: string;
  screenId: string;
  sourceRevision: string;
  sectionModes: Partial<Record<UiExplorerSectionId, UiExplorerSectionMode>>;
  scenarioDescription?: string;
  model?: string;
  reasoningEffort?: string;
}

export interface UiExplorerJobRequestSnapshot {
  systemId: string;
  systemLabel: string;
  branch: string;
  screenId: string;
  sourceRevision: string;
  sectionModes: UiExplorerSectionModeAssignment[];
  scenarioDescription: string | null;
  aiModel: string | null;
  reasoningEffort: string | null;
}

export interface UiExplorerScreenIdentity {
  systemId: string;
  screenId: string;
  label: string;
  routePattern: string;
  navigationContext: string;
}

export interface UiExplorerSourceReference {
  repository: string;
  path: string;
  symbol: string;
  startLine: number | null;
  endLine: number | null;
}

export interface UiExplorerResultSection {
  sectionId: UiExplorerSectionId;
  mode: UiExplorerSectionMode;
  coverage: UiExplorerCoverageStatus;
  confidence: UiExplorerClaimConfidence;
  markdown: string;
  sourceReferences: UiExplorerSourceReference[];
  visibilityLimits: string[];
  openQuestions: string[];
}

export interface UiExplorerResultResponse {
  screen: UiExplorerScreenIdentity;
  scenarioDescription: string | null;
  sourceRevision: UiExplorerSourceRevision;
  functionalOverview: string;
  sections: UiExplorerResultSection[];
  overallConfidence: UiExplorerClaimConfidence;
  visibilityLimits: string[];
  unresolvedQuestions: string[];
  usage: AnalysisAiUsage | null;
}

export type UiExplorerJobStatus =
  | 'QUEUED'
  | 'DISCOVERING_SCREEN'
  | 'BUILDING_CONTEXT'
  | 'ANALYZING'
  | 'COMPLETED'
  | 'PARTIAL'
  | 'BLOCKED'
  | 'FAILED'
  | string;

export interface UiExplorerJobStateSnapshot {
  jobId: string;
  request: UiExplorerJobRequestSnapshot;
  status: UiExplorerJobStatus;
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
  toolFeedback: AnalysisAiToolFeedback[];
  preparedPrompt: string | null;
  result: UiExplorerResultResponse | null;
  report: AnalysisReport | null;
  usage: AnalysisAiUsage | null;
  sourceRevision: UiExplorerSourceRevision | null;
  outputAvailability: UiExplorerOutputAvailability;
  exportAvailable: boolean;
}

export interface UiExplorerExportEnvelope {
  schema: 'tdw.ui-explorer-export';
  version: 5;
  exportedAt: string;
  payload: {
    type: 'ui-explorer-analysis';
    resultContract: 'ui-explorer-result-v5';
    job: UiExplorerJobStateSnapshot;
  };
}

export interface UiExplorerResultSource {
  origin: 'live' | 'history' | 'imported';
  exportedAt: string;
  fileName: string;
  localRunId?: string;
  localRunName?: string;
}
