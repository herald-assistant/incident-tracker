import {
  AnalysisAiActivityEvent,
  AnalysisAiUsage,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse,
  AnalysisReport
} from '../../../core/models/analysis.models';

export type ChangeVerificationJobStatus = 'QUEUED' | 'COMPLETED' | 'FAILED' | string;

export type ChangeVerificationFindingSeverity =
  | 'INFO'
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'
  | 'BLOCKER';

export interface ChangeVerificationJobStartRequest {
  issueKey?: string;
  issueUrl?: string;
  checkStoryCompliance?: boolean;
  checkInstructionCompliance?: boolean;
  userInstructions?: string;
  model?: string;
  reasoningEffort?: string;
}

export interface ChangeVerificationFinding {
  id: string;
  severity: ChangeVerificationFindingSeverity;
  source: string;
  summary: string;
  details: string;
  references: string[];
  suggestedAction: string;
}

export interface ChangeVerificationVerificationCheck {
  id: string;
  origin: 'DEFINED' | 'INFERRED_CRITICAL' | string;
  scope: string;
  criterionSource: string;
  criterionQuote: string;
  interpretationType: string;
  criticality: string | null;
  inferenceRationale: string | null;
  inferenceSignals: string[];
  riskIfOmitted: string | null;
  confidence: string | null;
  expectedCriterion: string;
  verificationStatus: string;
  verifiedAgainst: string;
  analysis: string;
  evidenceRefs: string[];
  gaps: string[];
  suggestedAction: string;
}

export interface ChangeVerificationCompliance {
  storyComplianceRequested: boolean;
  instructionComplianceRequested: boolean;
  status: string;
  verificationChecks: ChangeVerificationVerificationCheck[];
  findings: ChangeVerificationFinding[];
  suggestedActions: string[];
  visibilityLimits: string[];
}

export interface ChangeVerificationResult {
  status: string;
  issueKey: string;
  issueUrl: string;
  prompt: string;
  compliance: ChangeVerificationCompliance;
  usage: AnalysisAiUsage | null;
}

export interface ChangeVerificationJobStateSnapshot {
  jobId: string;
  issueKey: string;
  issueUrl: string;
  checkStoryCompliance: boolean;
  checkInstructionCompliance: boolean;
  aiModel: string;
  reasoningEffort: string;
  status: ChangeVerificationJobStatus;
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
  preparedPrompt: string;
  result: ChangeVerificationResult | null;
  report: AnalysisReport | null;
}
