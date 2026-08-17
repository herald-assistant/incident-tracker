import {
  AnalysisAiActivityEvent,
  AnalysisAiUsage,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse
} from '../../../core/models/analysis.models';

export interface DeliveryEffectivenessAssessmentJobStartRequest {
  jiraProject: string;
  fromDate: string;
  toDate: string;
  model?: string;
  reasoningEffort?: string;
}

export interface DeliveryAssessmentDimensions {
  outcomeBreadth: number;
  domainDecisionComplexity: number;
  applicationFlowComplexity: number;
  boundaryAndDataComplexity: number;
  verificationStateSpace: number;
  implementedCompatibilityScope: number;
}

export interface DeliveryAssessmentIssue {
  issueKey: string;
  issueUrl: string;
  summary: string;
  issueType: string;
  doneAt: string;
}

export interface DeliveryAssessmentMergeRequest {
  identity: string;
  projectPath: string;
  iid: number | null;
  title: string;
  webUrl: string;
  mergedAt: string;
  changedPaths: string[];
}

export interface DeliveryAssessmentResult {
  deliveredStoryPoints: number;
  score100: number;
  dimensions: DeliveryAssessmentDimensions;
  confidence: number;
  evidenceSummary: string[];
  qualityFlags: string[];
}

export interface DeliveryAssessmentUnit {
  unitId: string;
  status: string;
  issues: DeliveryAssessmentIssue[];
  mergeRequests: DeliveryAssessmentMergeRequest[];
  assessment: DeliveryAssessmentResult | null;
  visibilityLimits: string[];
  errorCode: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  preparedPrompt: string | null;
  promptPreparedAt: string | null;
  usage: AnalysisAiUsage | null;
}

export interface DeliveryAssessmentAggregate {
  totalDeliveredStoryPoints: number;
  distribution: Record<string, number>;
  totalUnits: number;
  assessedUnits: number;
  excludedUnits: number;
  notScorableUnits: number;
  failedUnits: number;
  coverage: number;
  confidence: string;
  usage: AnalysisAiUsage | null;
}

export interface DeliveryEffectivenessAssessmentJobStateSnapshot {
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
  units: DeliveryAssessmentUnit[];
  aggregate: DeliveryAssessmentAggregate;
}

export interface DeliveryEffectivenessAssessmentExportEnvelope {
  schema: 'tdw.delivery-effectiveness-assessment-export';
  version: 2;
  exportedAt: string;
  payload: {
    type: 'delivery-effectiveness-assessment';
    resultContract: 'delivery-effectiveness-assessment-v2';
    job: DeliveryEffectivenessAssessmentJobStateSnapshot;
  };
}
