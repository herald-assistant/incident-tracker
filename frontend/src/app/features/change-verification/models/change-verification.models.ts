import {
  AnalysisAiActivityEvent,
  AnalysisAiUsage,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse,
  AnalysisReport
} from '../../../core/models/analysis.models';

export type ChangeVerificationJobStatus = 'QUEUED' | 'COMPLETED' | 'FAILED' | string;
export type ChangeVerificationDecisionStatus =
  | 'READY'
  | 'NEEDS_ACTION'
  | 'NEEDS_EVIDENCE'
  | 'INCONCLUSIVE';
export type ChangeVerificationRuleScope = 'STORY' | 'INSTRUCTION' | 'ADDITIONAL';
export type ChangeVerificationRuleOutcome = 'SATISFIED' | 'NOT_SATISFIED' | 'NOT_VERIFIED';
export type ChangeVerificationReleaseImpact = 'NONE' | 'REVIEW' | 'BLOCKER';
export type ChangeVerificationInterpretationType =
  | 'EXPLICIT'
  | 'NORMALIZED'
  | 'CONFLICTING'
  | 'NOT_VERIFIABLE'
  | 'INFERRED';
export type ChangeVerificationRuleSourceType =
  | 'ACCEPTANCE_CRITERION'
  | 'JIRA_DESCRIPTION'
  | 'JIRA_COMMENT'
  | 'CONFLUENCE'
  | 'REPOSITORY_INSTRUCTION'
  | 'OPERATOR_INSTRUCTION'
  | 'AI_SUGGESTION';

export interface ChangeVerificationJobStartRequest {
  issueKey?: string;
  issueUrl?: string;
  checkStoryCompliance?: boolean;
  checkInstructionCompliance?: boolean;
  userInstructions?: string;
  model?: string;
  reasoningEffort?: string;
}

export interface ChangeVerificationRuleSource {
  type: ChangeVerificationRuleSourceType;
  label: string;
  reference: string;
  quote: string;
}

export interface ChangeVerificationRuleEvidence {
  summary: string;
  reference: string;
}

export interface ChangeVerificationRuleResult {
  id: string;
  scope: ChangeVerificationRuleScope;
  source: ChangeVerificationRuleSource;
  normalizedRule: string;
  interpretationType: ChangeVerificationInterpretationType;
  outcome: ChangeVerificationRuleOutcome;
  releaseImpact: ChangeVerificationReleaseImpact;
  conclusion: string;
  evidence: ChangeVerificationRuleEvidence[];
  missingEvidence: string[];
  action: string | null;
  rationale: string | null;
  riskIfOmitted: string | null;
  signals: string[];
  confidence: string | null;
}

export interface ChangeVerificationVisibilityLimit {
  message: string;
  affectedRuleIds: string[];
}

export interface ChangeVerificationDecision {
  status: ChangeVerificationDecisionStatus;
  totalRules: number;
  satisfied: number;
  notSatisfied: number;
  notVerified: number;
}

export interface ChangeVerificationRuleLedger {
  storyComplianceRequested: boolean;
  instructionComplianceRequested: boolean;
  decision: ChangeVerificationDecision;
  rules: ChangeVerificationRuleResult[];
  additionalChecks: ChangeVerificationRuleResult[];
  visibilityLimits: ChangeVerificationVisibilityLimit[];
}

export interface ChangeVerificationResult {
  status: string;
  issueKey: string;
  issueUrl: string;
  prompt: string;
  ruleLedger: ChangeVerificationRuleLedger;
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
