export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  fieldErrors: ApiFieldError[];
  authStartUrl?: string | null;
}

export type CopilotAuthMode = 'LOCAL_TOKEN' | 'GITHUB_APP';

export interface GitHubAuthStatus {
  mode: CopilotAuthMode;
  required: boolean;
  connected: boolean;
  githubLogin?: string | null;
  displayName?: string | null;
  tokenExpiresAt?: string | null;
  reauthRequired: boolean;
  authStartUrl?: string | null;
}

export interface AuthRequiredError {
  code:
    | 'GITHUB_COPILOT_AUTH_REQUIRED'
    | 'GITHUB_COPILOT_REAUTH_REQUIRED'
    | 'COPILOT_LOCAL_TOKEN_MISSING';
  message: string;
  authStartUrl?: string | null;
}

export type AnalysisJobStatus =
  | 'QUEUED'
  | 'COLLECTING_EVIDENCE'
  | 'ANALYZING'
  | 'COMPLETED'
  | 'FAILED'
  | 'NOT_FOUND'
  | string;

export type AnalysisJobStepStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'FAILED'
  | 'SKIPPED'
  | string;

export interface AnalysisResultResponse {
  status: string;
  correlationId: string;
  environment: string;
  gitLabBranch: string;
  summary: string;
  detectedProblem: string;
  recommendedAction: string;
  rationale: string;
  affectedFunction: string;
  affectedProcess: string;
  affectedBoundedContext: string;
  affectedTeam: string;
  prompt: string;
  usage: AnalysisAiUsage | null;
}

export interface AnalysisAiUsage {
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheWriteTokens: number;
  totalTokens: number;
  cost: number;
  apiDurationMs: number;
  apiCallCount: number;
  model: string;
  contextTokenLimit: number | null;
  contextCurrentTokens: number | null;
  contextMessages: number | null;
}

export interface AnalysisAiActivityEvent {
  eventId: string;
  parentEventId: string;
  type: string;
  category: string;
  status: string;
  title: string;
  summary: string;
  turnId: string;
  interactionId: string;
  toolCallId: string;
  toolName: string;
  timestamp: string;
  details: Record<string, unknown>;
}

export interface AnalysisStartRequest {
  correlationId: string;
  model?: string;
  reasoningEffort?: string;
}

export interface AnalysisChatMessageRequest {
  message: string;
}

export interface AnalysisAiModelOption {
  id: string;
  name: string;
  supportsReasoningEffort: boolean;
  reasoningEfforts: string[];
  defaultReasoningEffort: string;
}

export interface AnalysisAiModelOptionsResponse {
  defaultModel: string;
  defaultReasoningEffort: string;
  defaultReasoningEfforts: string[];
  models: AnalysisAiModelOption[];
}

export interface AnalysisEvidenceAttribute {
  name: string;
  value: string;
}

export interface AnalysisEvidenceItem {
  title: string;
  attributes: AnalysisEvidenceAttribute[];
}

export interface AnalysisEvidenceSection {
  provider: string;
  category: string;
  items: AnalysisEvidenceItem[];
}

export interface AnalysisEvidenceReference {
  provider: string;
  category: string;
}

export interface AnalysisJobStepResponse {
  code: string;
  label: string;
  phase?: string;
  status: AnalysisJobStepStatus;
  message: string;
  itemCount: number | null;
  startedAt: string;
  completedAt: string;
  consumesEvidence?: AnalysisEvidenceReference[];
  producesEvidence?: AnalysisEvidenceReference[];
  usage?: AnalysisAiUsage | null;
}

export type AnalysisChatMessageRole = 'USER' | 'ASSISTANT' | string;
export type AnalysisChatMessageStatus = 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | string;

export interface AnalysisChatMessageResponse {
  id: string;
  role: AnalysisChatMessageRole;
  status: AnalysisChatMessageStatus;
  content: string;
  errorCode: string;
  errorMessage: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string;
  toolEvidenceSections: AnalysisEvidenceSection[];
  aiActivityEvents: AnalysisAiActivityEvent[];
  prompt: string;
}

export interface AnalysisJobStateSnapshot {
  analysisId: string;
  correlationId: string;
  aiModel: string;
  reasoningEffort: string;
  status: AnalysisJobStatus;
  currentStepCode: string;
  currentStepLabel: string;
  environment: string;
  gitLabBranch: string;
  errorCode: string;
  errorMessage: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string;
  steps: AnalysisJobStepResponse[];
  evidenceSections: AnalysisEvidenceSection[];
  toolEvidenceSections: AnalysisEvidenceSection[];
  aiActivityEvents: AnalysisAiActivityEvent[];
  chatMessages: AnalysisChatMessageResponse[];
  preparedPrompt: string;
  result: AnalysisResultResponse | null;
}

export interface ExportState {
  origin: 'live' | 'imported';
  exportedAt: string;
  fileName: string;
  job: AnalysisJobStateSnapshot;
}

export interface TransportErrorState {
  code: string;
  message: string;
  details: string[];
  status: number;
  authStartUrl?: string | null;
}

export interface AnalysisExportEnvelope {
  schema: string;
  version: number;
  exportedAt: string;
  payload: {
    type: 'analysis-job';
    job: AnalysisJobStateSnapshot;
  };
}
