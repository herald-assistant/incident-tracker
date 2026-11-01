import { GitLabBranchesResponse } from '../../core/services/gitlab-system-branches-api.service';
import { AnalysisReport, AnalysisAiUsage, AnalysisAiActivityEvent, AnalysisAiToolFeedback,
  AnalysisEvidenceSection, AnalysisJobStepResponse, AnalysisChatMessageResponse } from '../../core/models/analysis.models';
export type ReleaseRefKind = 'BRANCH' | 'TAG' | 'COMMIT';
export interface ReleaseRef { kind: ReleaseRefKind; name: string }
export interface ReleaseSummaryRequest {
  systemId: string; mode: 'LIQUIBASE'; baselineRef: ReleaseRef; targetRef: ReleaseRef;
  model?: string; reasoningEffort?: string;
}
export interface ReleaseSummaryUnit {
  unitId: string; repositoryId: string; path: string; changeType: string;
  baselineSource: string | null; targetSource: string | null; structurallyVerified: boolean;
  elements: {elementId: string; kind: string; locator: string; before: unknown; after: unknown}[];
}
export interface ReleaseSummaryEvidence {
  scannedFiles: number; sourceComplete: boolean; units: ReleaseSummaryUnit[];
  sources: {sourceRef: string; repositoryId: string; project: string; side: string; commitId: string;
    path: string; blobId: string; content: string; dependencies: string[]}[];
  gaps: {code: string; repositoryId: string; side: string; path: string; detail: string; blocking: boolean}[];
  sections: AnalysisEvidenceSection[];
}
export interface ReleaseSummaryResult {
  resultContract: 'release-summary-analyst-report-v1'; status: string; sourceComplete: boolean;
  expectedUnits: number; documentedUnits: number; expectedElements: number; documentedElements: number;
  coverage: {unitId: string; expectedElements: number; documentedElements: number; structurallyVerified: boolean; gaps: string[]}[];
  visibilityLimits: string[];
}
export interface ReleaseSummarySnapshot {
  jobId: string; status: string; request: ReleaseSummaryRequest;
  comparison: {systemId: string; systemName: string; group: string; catalogDigest: string; entities: unknown[];
    repositories: {repositoryId: string; project: string; prefixes: string[]; baselineRef: string; baselineCommit: string; targetRef: string; targetCommit: string}[]} | null;
  evidence: ReleaseSummaryEvidence | null;
  errorCode: string | null; error: string | null; createdAt: string; updatedAt: string; completedAt: string | null;
  steps: AnalysisJobStepResponse[]; contextSections: AnalysisEvidenceSection[]; toolEvidenceSections: AnalysisEvidenceSection[];
  aiActivityEvents: AnalysisAiActivityEvent[]; toolFeedback: AnalysisAiToolFeedback[]; preparedPrompt: string | null;
  report: AnalysisReport | null; result: ReleaseSummaryResult | null; usage: AnalysisAiUsage | null;
  chatMessages: AnalysisChatMessageResponse[]; chatAvailability: {available: boolean; code: string; message: string};
  exportAvailable: boolean;
}
export interface ReleaseRefOptions extends GitLabBranchesResponse { nextPage: number | null }
export function releaseRef(value: string): ReleaseRef {
  if (value.startsWith('refs/heads/')) return {kind: 'BRANCH', name: value.slice(11)};
  if (value.startsWith('refs/tags/')) return {kind: 'TAG', name: value.slice(10)};
  return {kind: 'COMMIT', name: value};
}
export function terminalReleaseSummary(job: ReleaseSummarySnapshot): boolean {
  return !['QUEUED', 'RUNNING'].includes(job.status) && !job.chatMessages.some(m=>m.status==='IN_PROGRESS');
}

