import {
  AnalysisAiActivityEvent,
  AnalysisAiUsage,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse
} from '../../../core/models/analysis.models';

export type ChangeVerificationJobMode =
  | 'CHECK_COMPLIANCE'
  | 'GENERATE_SMOKE_PACK'
  | 'EXECUTE_SMOKE_PACK';

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
  modes?: ChangeVerificationJobMode[];
  checkStoryCompliance?: boolean;
  checkInstructionCompliance?: boolean;
  userInstructions?: string;
  environment?: string;
  databaseApplication?: string;
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

export interface ChangeVerificationCompliance {
  storyComplianceRequested: boolean;
  instructionComplianceRequested: boolean;
  status: string;
  findings: ChangeVerificationFinding[];
  suggestedActions: string[];
  visibilityLimits: string[];
}

export interface ChangeVerificationSmokeTest {
  id: string;
  name: string;
  method: string;
  path: string;
  purpose: string;
  headers: ChangeVerificationNameValue[];
  queryParams: ChangeVerificationNameValue[];
  requestBody: string | null;
  responseAssertions: ChangeVerificationSmokeAssertion[];
  dbAssertions: string[];
  dbAssertionSpecs: ChangeVerificationSmokeDbAssertion[];
  cleanup: ChangeVerificationSmokeCleanup | null;
  cleanupHints: string[];
  sourceRefs: string[];
  riskCovered: string | null;
  reviewStatus: string | null;
}

export interface ChangeVerificationSmokeDbAssertion {
  id: string;
  sql: string;
  operator: string | null;
  expectedValue: string | null;
  description: string | null;
}

export interface ChangeVerificationNameValue {
  name: string;
  value: string;
  enabled: boolean;
}

export interface ChangeVerificationSmokeAssertion {
  type: string;
  target: string;
  operator: string;
  expectedValue: string;
}

export interface ChangeVerificationSmokeCleanup {
  strategy: string;
  method: string | null;
  path: string | null;
  requestBody: string | null;
  manualSql: string | null;
  hints: string[];
}

export interface ChangeVerificationSmokePack {
  requested: boolean;
  status: string;
  postmanCollectionName: string | null;
  tests: ChangeVerificationSmokeTest[];
  visibilityLimits: string[];
  suggestedActions: string[];
  confidence: string | null;
}

export interface ChangeVerificationSmokeExecutionRequest {
  baseUrl: string;
  environment?: string;
  databaseApplication?: string;
  selectedTestIds?: string[];
  variables?: Record<string, string>;
  executeCleanup?: boolean;
}

export interface ChangeVerificationSmokeHttpResult {
  method: string;
  url: string;
  statusCode: number | null;
  durationMillis: number;
  bodyExcerpt: string | null;
  headers: ChangeVerificationNameValue[];
  errorMessage: string | null;
}

export interface ChangeVerificationSmokeAssertionResult {
  type: string;
  target: string;
  status: string;
  message: string;
}

export interface ChangeVerificationSmokeCleanupResult {
  strategy: string;
  status: string;
  action: string | null;
  manualSql: string | null;
  message: string;
}

export interface ChangeVerificationSmokeTestExecution {
  testId: string;
  name: string;
  status: string;
  http: ChangeVerificationSmokeHttpResult | null;
  responseAssertions: ChangeVerificationSmokeAssertionResult[];
  dbAssertions: ChangeVerificationSmokeAssertionResult[];
  cleanup: ChangeVerificationSmokeCleanupResult | null;
}

export interface ChangeVerificationExecution {
  requested: boolean;
  status: string;
  executedTestIds: string[];
  testResults: ChangeVerificationSmokeTestExecution[];
  cleanupActions: string[];
  manualCleanupSql: string | null;
  visibilityLimits: string[];
}

export interface ChangeVerificationResult {
  status: string;
  issueKey: string;
  issueUrl: string;
  modes: ChangeVerificationJobMode[];
  prompt: string;
  compliance: ChangeVerificationCompliance;
  smokePack: ChangeVerificationSmokePack;
  execution: ChangeVerificationExecution;
  usage: AnalysisAiUsage | null;
}

export interface ChangeVerificationJobStateSnapshot {
  jobId: string;
  issueKey: string;
  issueUrl: string;
  modes: ChangeVerificationJobMode[];
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
}
