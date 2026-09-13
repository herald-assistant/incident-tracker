import {
  AnalysisAiActivityEvent,
  AnalysisAiUsage,
  AnalysisJobStepResponse
} from '../../core/models/analysis.models';
import { OperationalContextWritableType } from './operational-context-maintenance.models';

export type OperationalContextAssistanceMode = 'CREATE_AREA' | 'IMPROVE_ENTITY' | 'RESOLVE_FINDING';
export type OperationalContextAssistanceTargetKind = 'ENTITY' | 'VALIDATION_FINDING' | 'OPEN_QUESTION';
export type OperationalContextAssistanceStatus =
  | 'QUEUED' | 'COLLECTING_CONTEXT' | 'AI_PREPARATION' | 'ANALYZING'
  | 'COMPLETED' | 'PARTIAL' | 'BLOCKED' | 'FAILED';

export interface OperationalContextAssistanceTarget {
  kind: OperationalContextAssistanceTargetKind;
  entityType: OperationalContextWritableType;
  entityId: string;
  id?: string;
}

export interface OperationalContextAssistanceRequest {
  mode: OperationalContextAssistanceMode;
  description: string;
  target?: OperationalContextAssistanceTarget;
  gitLabSource?: { project: string; ref: string } | { projectUrl: string; ref: string };
  repositoryFacts?: OperationalContextAssistanceRepositoryFacts;
}

export type OperationalContextRepositoryUsage = 'UNKNOWN' | 'DEPLOYED_SYSTEM' | 'SHARED_LIBRARY' | 'EXISTING_SYSTEM';

export interface OperationalContextAssistanceRepositoryFacts {
  usage: OperationalContextRepositoryUsage;
  systemName?: string;
  runtimeServiceName?: string;
  systemIds?: string[];
}

export interface OperationalContextAssistancePrefill {
  mode: OperationalContextAssistanceMode;
  target?: OperationalContextAssistanceTarget;
  description?: string;
}

export interface OperationalContextAssistanceSourceOptions {
  configuredBaseUrl: string | null;
  configuredGroup: string | null;
  projects: { project: string; projectPath: string }[];
}

export interface OperationalContextAssistanceFieldChange {
  path: string;
  before?: unknown;
  after: unknown;
  reason: string;
  basis: 'USER_STATEMENT' | 'SOURCE_OBSERVATION' | 'AI_INTERPRETATION';
  sourceRefs: string[];
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  requiresConfirmation: boolean;
}

export interface OperationalContextAssistanceProposal {
  operation: 'CREATE' | 'UPDATE';
  entityType: OperationalContextWritableType;
  entityId: string;
  changes: OperationalContextAssistanceFieldChange[];
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  requiresConfirmation: boolean;
  questions: string[];
  visibilityLimits: string[];
}

export interface OperationalContextAssistanceDraft {
  proposals: OperationalContextAssistanceProposal[];
  questions: string[];
  visibilityLimits: string[];
}

export interface OperationalContextAssistancePreview {
  proposalIndex: number;
  valid: boolean;
  validationStatus: 'VALID' | 'INVALID' | 'DEFERRED';
  candidatePayload: Record<string, unknown> | null;
  violations: { code: string; fingerprint: string; ruleCode: string; severity: string }[];
  fieldErrors: { pointer: string; message: string }[];
}

export interface OperationalContextAssistanceProposalDecision {
  proposalIndex: number;
  action: 'APPLY' | 'SKIP';
  selectedPaths: string[];
  editedValues?: Record<string, unknown>;
  completedAt: string;
  catalogDigest?: string | null;
}

export interface OperationalContextAssistanceProposalDecisionRequest {
  action: 'APPLY' | 'SKIP';
  selectedPaths: string[];
  confirmedPaths: string[];
  editedValues?: Record<string, unknown>;
}

export interface OperationalContextAssistanceBatchReviewRequest {
  decisions: OperationalContextAssistanceProposalDecisionRequest[];
  candidateDigest?: string;
}

export interface OperationalContextAssistanceBatchPreview {
  expectedDigest: string;
  candidateDigest: string;
  valid: boolean;
  entities: {
    type: OperationalContextWritableType;
    id: string;
    sourceFile: string;
    payload: Record<string, unknown>;
  }[];
  violations: { code: string; fingerprint: string; ruleCode: string; severity: string }[];
}

export interface OperationalContextAssistanceJob {
  jobId: string;
  status: OperationalContextAssistanceStatus;
  currentStepCode: string;
  currentStepLabel: string;
  errorCode?: string | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt?: string | null;
  steps: AnalysisJobStepResponse[];
  aiActivityEvents: AnalysisAiActivityEvent[];
  usage?: AnalysisAiUsage | null;
  catalogDigest?: string | null;
  sourceRevision?: { project: string; requestedRef: string; commitId: string } | null;
  sourceRefs: string[];
  visibilityLimits: string[];
  draft?: OperationalContextAssistanceDraft | null;
  previews: OperationalContextAssistancePreview[];
  proposalDecisions?: OperationalContextAssistanceProposalDecision[];
  preparedPrompt?: string | null;
}

export function isTerminalAssistanceStatus(status: OperationalContextAssistanceStatus): boolean {
  return status === 'COMPLETED' || status === 'PARTIAL' || status === 'BLOCKED' || status === 'FAILED';
}
