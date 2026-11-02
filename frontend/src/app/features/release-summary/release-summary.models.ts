import { GitLabBranchesResponse } from '../../core/services/gitlab-system-branches-api.service';
import { AnalysisReport, AnalysisAiUsage, AnalysisAiActivityEvent, AnalysisAiToolFeedback,
  AnalysisEvidenceSection, AnalysisJobStepResponse, AnalysisChatMessageResponse } from '../../core/models/analysis.models';
export type ReleaseRefKind = 'BRANCH' | 'TAG' | 'COMMIT';
export interface ReleaseRef { kind: ReleaseRefKind; name: string }
export interface ReleaseSummaryRequest {
  systemId: string; mode: 'LIQUIBASE'; baselineRef: ReleaseRef; targetRef: ReleaseRef;
  model?: string; reasoningEffort?: string;
}
export interface ReleaseSummaryChangedFile {
  repositoryId: string; project: string;
  change: {oldPath: string | null; newPath: string | null; oldBlobId: string | null; newBlobId: string | null;
    oldMode: string | null; newMode: string | null; patch: string | null; patchComplete: boolean};
}
export interface ReleaseSummaryEvidence {
  changeListComplete: boolean; changes: ReleaseSummaryChangedFile[];
  gaps: {code: string; repositoryId: string; side: string; path: string; detail: string; blocking: boolean}[];
}
export interface ReleaseSummaryResult {
  resultContract: 'release-summary-diff-report-v1'; status: string; changeListComplete: boolean;
  changedFiles: number; documentedTables: number; readSources: number; semanticCoverage: 'AI_ASSESSED' | 'NOT_APPLICABLE';
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

