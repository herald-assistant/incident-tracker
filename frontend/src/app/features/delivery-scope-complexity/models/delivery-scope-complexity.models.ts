import {
  AnalysisAiActivityEvent,
  AnalysisAiUsage,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse
} from '../../../core/models/analysis.models';

export interface DeliveryScopeComplexityJobStartRequest {
  jiraProject: string;
  fromDate: string;
  toDate: string;
  model?: string;
  reasoningEffort?: string;
}

export interface DeliveryScopeDimensionScore {
  score: number;
  scopeSignal: number;
  scope: number;
  scaledScore: number;
  weight: number;
  points: number;
  evidence: string[];
}

export interface DeliveryScopeDimensions {
  novelty: DeliveryScopeDimensionScore;
  structuralAndLogic: DeliveryScopeDimensionScore;
  businessAndInvariants: DeliveryScopeDimensionScore;
  robustnessAndTests: DeliveryScopeDimensionScore;
  refactorAndArchitecture: DeliveryScopeDimensionScore;
  distribution: DeliveryScopeDimensionScore;
}

export interface DeliveryScopeIssue {
  issueKey: string;
  issueUrl: string;
  summary: string;
  issueType: string;
  doneAt: string;
  team: DeliveryScopeTeam | null;
}

export interface DeliveryScopeTeam {
  id: string | null;
  name: string;
  fieldId: string;
}

export interface DeliveryScopeMergeRequest {
  identity: string;
  projectPath: string;
  iid: number | null;
  title: string;
  webUrl: string;
  mergedAt: string;
  authorId: number | null;
  authorName: string;
  changedPaths: string[];
}

export interface DeliveryScopeResult {
  finalScore: number;
  dimensions: DeliveryScopeDimensions;
  confidence: number;
  evidenceSummary: string[];
  qualityFlags: string[];
}

export interface DeliveryScopeUnit {
  unitId: string;
  status: string;
  issues: DeliveryScopeIssue[];
  mergeRequests: DeliveryScopeMergeRequest[];
  assessment: DeliveryScopeResult | null;
  visibilityLimits: string[];
  errorCode: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  preparedPrompt: string | null;
  promptPreparedAt: string | null;
  rawAiResponse: string | null;
  usage: AnalysisAiUsage | null;
}

export interface DeliveryScopeAggregate {
  totalComplexityPoints: number;
  averageComplexityScore: number;
  totalUnits: number;
  assessedUnits: number;
  excludedUnits: number;
  notScorableUnits: number;
  failedUnits: number;
  coverage: number;
  confidence: string;
  usage: AnalysisAiUsage | null;
}

export interface DeliveryScopeComplexityJobStateSnapshot {
  jobId: string;
  jiraProject: string;
  fromDate: string;
  toDate: string;
  aiModel: string;
  reasoningEffort: string;
  status: string;
  currentStepCode: string;
  currentStepLabel: string;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  discoveredIssues: number;
  processedIssues: number;
  totalIssues: number;
  effectiveJql: string;
  steps: AnalysisJobStepResponse[];
  contextSections: AnalysisEvidenceSection[];
  aiActivityEvents: AnalysisAiActivityEvent[];
  units: DeliveryScopeUnit[];
  aggregate: DeliveryScopeAggregate;
}

export interface DeliveryScopeComplexityExportEnvelope {
  schema: 'tdw.delivery-scope-complexity-export';
  version: 1;
  exportedAt: string;
  payload: {
    type: 'delivery-scope-complexity';
    resultContract: 'delivery-scope-complexity-v1';
    job: DeliveryScopeComplexityJobStateSnapshot;
  };
}
